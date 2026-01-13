package ru.fuctorial.fuctorize.utils.pathfinding;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.Sys;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import ru.fuctorial.fuctorize.utils.movement.InputUtils;
import ru.fuctorial.fuctorize.utils.movement.MovementState;
import ru.fuctorial.fuctorize.utils.movement.MovementUtils;
import ru.fuctorial.fuctorize.utils.pathfinding.PathNode;
import ru.fuctorial.fuctorize.utils.pathfinding.PathResult;
import ru.fuctorial.fuctorize.utils.pathfinding.PathfindingUtils;
import ru.fuctorial.fuctorize.utils.pathfinding.YMath;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class BotNavigator {

    public enum NavigationStatus {
        IDLE,
        NAVIGATING,
        SUCCESS,
        FAILED,
        CANCELLED
    }

    private volatile NavigationStatus status = NavigationStatus.IDLE;

    private static final Minecraft mc = FMLClientHandler.instance().getClient();

    private List<PathNode> currentPath;

    private int currentNodeIndex = 0;
    private long lastRecalculation = 0;
    private static final long RECALC_INTERVAL = 1000;

    private long replanCheckIntervalMs = 1000;

    private static final int MAX_HISTORY_NODES = 300;
    private final List<PathNode> traversedPathHistory = new ArrayList<>();

    private static final boolean ENABLE_PARKOUR_FURTHEST_SKIP = false;

    private boolean isNavigating = false;
    private double targetX, targetY, targetZ;

    private static final double ARRIVAL_THRESHOLD = 0.35;
    private static final double NODE_REACH_THRESHOLD = 1.05;
    private static final double NODE_CENTER_THRESHOLD = 0.30;
    private static final double LOOK_AHEAD_NODES = 3;
    private long lastNodeReachedTime = System.currentTimeMillis();
    private static final long NODE_TIMEOUT = 10000;

    private static final double DANGER_CHECK_RADIUS = 2.0;

    private int consecutivePathFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final int STUCK_PENALTY_RADIUS = 2;

    private int breakingBlockX = Integer.MIN_VALUE;
    private int breakingBlockY = Integer.MIN_VALUE;
    private int breakingBlockZ = Integer.MIN_VALUE;

    private boolean hadBlocksLastCheck = false;
    private long lastInventoryCheckTime = 0;
    private static final long INVENTORY_CHECK_INTERVAL = 500;

    private long breakingStartTime = 0L;
    private static final double BREAK_TIMEOUT_BUFFER_SECONDS = 0.5;

    private static final int RECALC_AHEAD_THRESHOLD = 15;

    private volatile List<PathNode> nextPathSegment = null;

    private volatile boolean isCalculatingNextSegment = false;
    private float currentBlockDamage = 0.0f;

    private final Queue<List<PathNode>> pathSegmentQueue = new LinkedList<>();

    private static final int MAX_QUEUED_SEGMENTS = 3;

    private MovementState state = MovementState.IDLE;
    private MovementState prevState = MovementState.IDLE;
    private PathNode activeNode;
    private int actionTargetX, actionTargetY, actionTargetZ;

    private boolean forceSafeDropPlacement = false;

    private final AtomicInteger activeCalculations = new AtomicInteger(0);

    private double stuckCheckX, stuckCheckY, stuckCheckZ;
    private long lastStuckCheckTime;
    private static final long STUCK_TIMEOUT_MS = 2500;
    private static final double STUCK_DISTANCE_SQ = 1.0 * 1.0;

    private static final long QUICK_STUCK_TIMEOUT_MS = 1000;
    private static final double QUICK_STUCK_DISTANCE_SQ = 0.30 * 0.30;
    private static final double STUCK_PENALTY_COST = 50000.0;

    private PathNode attemptNodeRef = null;
    private long attemptStartMs = 0L;
    private double attemptStartDistSq = 0.0;
    private static final long PARKOUR_FAIL_TIMEOUT_MS = 2000L;
    private static final double PARKOUR_MIN_PROGRESS_SQ = 0.20 * 0.20;
    private static final long BREAKING_FAIL_TIMEOUT_MS = 7000L;

    private boolean hasVisitedNodeCenter = false;

    public void stop() {

        if (mc.thePlayer != null) {
            mc.thePlayer.setSprinting(false);
        }

        if (status != NavigationStatus.SUCCESS) {

            status = NavigationStatus.CANCELLED;
        }
        isNavigating = false;
        currentPath = null;
        setNodeIndex(0);
        InputUtils.resetAll();
        resetBreaking();
        MovementUtils.stopMovement();

        pathSegmentQueue.clear();
        this.traversedPathHistory.clear();
        activeCalculations.set(0);
        if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null) {
            FuctorizeClient.INSTANCE.pathfindingManager.cancelSearch();
        }
    }

    private boolean shouldBeSprinting() {
        EntityPlayer player = mc.thePlayer;

        if (currentPath == null || currentNodeIndex >= currentPath.size() || player.isOnLadder() || breakingBlockX != Integer.MIN_VALUE) {
            return false;
        }

        if (state == MovementState.PLACING_BLOCK) {
            return false;
        }

        if (currentPath != null && currentNodeIndex < currentPath.size()) {
            PathNode node = currentPath.get(currentNodeIndex);
            if (node.placeBlocks > 0 || node.breakBlocks > 0) {
                return false;
            }
            if (node.moveType != null && (node.moveType == MovementType.BRIDGE || node.moveType == MovementType.PILLAR)) {
                return false;
            }

            if (node.moveType != null && node.moveType == MovementType.PARKOUR) {
                if (currentNodeIndex > 0) {
                    PathNode prev = currentPath.get(currentNodeIndex - 1);
                    int stride = Math.max(Math.abs(node.x - prev.x), Math.abs(node.z - prev.z));
                    boolean requiresSprint = stride >= 3;

                    double dx = (node.x + 0.5) - player.posX;
                    double dz = (node.z + 0.5) - player.posZ;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist < 0.7) {
                        return false;
                    }
                    if (!requiresSprint) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        if (currentPath.size() - currentNodeIndex <= 2) {
            return false;
        }

        return true;
    }

    private void resetStuckDetector() {
        if (mc.thePlayer == null) return;
        this.lastStuckCheckTime = System.currentTimeMillis();
        this.stuckCheckX = mc.thePlayer.posX;
        this.stuckCheckY = mc.thePlayer.boundingBox.minY;
        this.stuckCheckZ = mc.thePlayer.posZ;
    }

    private void setNodeIndex(int newIndex) {
        int clamped = Math.max(0, newIndex);
        if (currentPath != null) {
            clamped = Math.min(clamped, currentPath.size());
        }
        this.currentNodeIndex = clamped;
        this.hasVisitedNodeCenter = false;
        if (currentPath != null && currentNodeIndex >= 0 && currentNodeIndex < currentPath.size()) {
            this.activeNode = currentPath.get(currentNodeIndex);
        } else {
            this.activeNode = null;
        }
    }

    private void advanceNodeIndex() {
        setNodeIndex(this.currentNodeIndex + 1);
    }

    public void tick() {
        if (!isNavigating || mc.thePlayer == null) return;
        mc.thePlayer.setSprinting(shouldBeSprinting());

        prevState = state;
        updateState();

        if (prevState == MovementState.BREAKING_BLOCK && state != MovementState.BREAKING_BLOCK) {
            resetBreaking();
        }

        long nowCheck = System.currentTimeMillis();
        if (nowCheck - lastRecalculation >= replanCheckIntervalMs) {
            checkAndRecalculatePath();
            lastRecalculation = nowCheck;
        }

        // --- ПРАВКА: ПРЕДЗАГРУЗКА (PREFETCH) ---
        // Если осталось меньше 15 узлов (RECALC_AHEAD_THRESHOLD) и мы еще не ищем путь — ищем заранее!
        if (currentPath != null && !currentPath.isEmpty()
                && (currentPath.size() - currentNodeIndex) < RECALC_AHEAD_THRESHOLD
                && pathSegmentQueue.isEmpty()
                && activeCalculations.get() == 0) {

            // Проверяем, что мы не у самого финиша (ближе 20 блоков), иначе будет спам запросами
            double distToFinalTarget = Math.sqrt(mc.thePlayer.getDistanceSq(targetX, targetY, targetZ));
            if (distToFinalTarget > 20.0) {
                System.out.println("[PREFETCH] Approaching segment end. Pre-calculating next chunk...");
                requestNewPathSegment(false);
            }
        }
        // ---------------------------------------

        switch (state) {
            case WALKING: handleWalkingState(); break;
            case PLACING_BLOCK: handlePlacingState(); break;
            case BREAKING_BLOCK: handleBreakingState(); break;
            case JUMPING: handleJumpingState(); break;
            default: MovementUtils.stopMovement(); break;
        }

        handleStuckAndPathEnd();
    }

    // ИСПРАВЛЕННЫЙ МЕТОД updateState С ЗАЩИТОЙ ОТ СБРОСА FAILSAFE
    private void updateState() {
        if (currentPath == null || currentNodeIndex >= currentPath.size()) {
            state = MovementState.IDLE;
            return;
        }

        // === ЗАЩИТА ОТ СБРОСА (STICKY BREAKING) ===
        // Если мы уже ломаем блок (назначенный Failsafe'ом), не даем логике ниже перебить это решение.
        if (state == MovementState.BREAKING_BLOCK) {
            // Если блок всё еще непроходим (твердый) - продолжаем ломать!
            if (!PathfindingUtils.isBlockPassable(mc.theWorld, actionTargetX, actionTargetY, actionTargetZ)) {
                return; // ВЫХОДИМ, сохраняя состояние BREAKING
            } else {
                // Блок сломан! Сбрасываемся.
                System.out.println("[UPDATE] Block broken, resuming path.");
                breakingStartTime = 0;
                InputUtils.releaseAttack();
                mc.playerController.resetBlockRemoving();
                // Даем коду ниже выбрать следующее действие (идти/прыгать)
            }
        }

        autoSkipPassedNodes();
        activeNode = currentPath.get(currentNodeIndex);

        boolean playerHasLowCeiling = hasLowCeilingAt(mc.theWorld, mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ);
        boolean targetHasLowCeiling = hasLowCeilingAt(mc.theWorld, activeNode.x + 0.5, activeNode.getFeetY(), activeNode.z + 0.5);

        if (activeNode.breakBlocks > 0) {
            int desiredY = activeNode.y;
            int targetBreakX = activeNode.x;
            // По дефолту целимся в ноги (нижний блок прохода)
            int targetBreakY = YMath.feetFromGround(desiredY);
            int targetBreakZ = activeNode.z;

            boolean overrideTarget = false; // <--- ФИКС: Флаг запрета переключения на голову

            // Если мы идем ВНИЗ (предыдущий узел выше текущего)
            if (currentNodeIndex > 0) {
                PathNode prev = currentPath.get(currentNodeIndex - 1);
                if (prev.y > activeNode.y) {
                    // Если блок под ногами (в предыдущей точке) непроходим - значит это пол, который надо прокопать
                    if (!PathfindingUtils.isBlockPassable(mc.theWorld, prev.x, prev.y, prev.z)) {
                        targetBreakX = prev.x;
                        targetBreakY = prev.y;
                        targetBreakZ = prev.z;
                        overrideTarget = true; // <--- ФИКС: Запоминаем, что цель жестко задана (копаем пол)
                    }
                }
            }

            // Если цель НЕ зафиксирована вручную (overrideTarget == false),
            // то проверяем: если ноги уже свободны, переключаемся на голову.
            // Раньше это срабатывало всегда, из-за чего при копке вниз бот целился в воздух (голову) вместо пола.
            if (!overrideTarget && PathfindingUtils.isBlockPassable(mc.theWorld, targetBreakX, targetBreakY, targetBreakZ)) {
                targetBreakX = activeNode.x;
                targetBreakY = YMath.headFromGround(desiredY);
                targetBreakZ = activeNode.z;
            }

            // Если итоговый блок непроходим - начинаем ломать
            if (!PathfindingUtils.isBlockPassable(mc.theWorld, targetBreakX, targetBreakY, targetBreakZ)) {
                int[] actual = getOccludingBlock(targetBreakX, targetBreakY, targetBreakZ);
                int finalX = actual[0];
                int finalY = actual[1];
                int finalZ = actual[2];

                if (actionTargetX != finalX || actionTargetY != finalY || actionTargetZ != finalZ || state != MovementState.BREAKING_BLOCK) {
                    breakingStartTime = System.currentTimeMillis();
                    breakingBlockX = finalX;
                    breakingBlockY = finalY;
                    breakingBlockZ = finalZ;
                }

                state = MovementState.BREAKING_BLOCK;
                actionTargetX = finalX;
                actionTargetY = finalY;
                actionTargetZ = finalZ;
                return;
            }
        }

        // --- ЛОГИКА УСТАНОВКИ (PLACING) ---
        if (activeNode.placeBlocks > 0) {
            int targetY = activeNode.y;
            if (activeNode.moveType == MovementType.PILLAR) targetY = YMath.groundFromPlayerPosY(mc.thePlayer.boundingBox.minY);
            else if (activeNode.moveType == MovementType.BRIDGE) targetY = YMath.groundBelow(activeNode.y);

            Block blockToPlace = mc.theWorld.getBlock(activeNode.x, targetY, activeNode.z);
            if (blockToPlace instanceof BlockAir || blockToPlace.getMaterial().isLiquid()) {
                state = MovementState.PLACING_BLOCK;
                actionTargetX = activeNode.x;
                actionTargetY = targetY;
                actionTargetZ = activeNode.z;
                return;
            }
        }

        // --- ПРЫЖКИ И СПУСК ---
        if (activeNode.needsJump && mc.thePlayer.onGround && mc.thePlayer.boundingBox.minY < activeNode.getFeetY() - 0.1) {
            if (!ensureHeadroomForAscendSafe()) return;
            state = MovementState.JUMPING;
            return;
        }

        if (activeNode.moveType != null && (activeNode.moveType == MovementType.DESCEND || activeNode.moveType == MovementType.DIAGONAL_DESCEND || activeNode.moveType == MovementType.FALL) && isDangerousAhead(activeNode)) {
            forceSafeDropPlacement = true;
            state = MovementState.PLACING_BLOCK;
            actionTargetX = MathHelper.floor_double(mc.thePlayer.posX);
            actionTargetY = YMath.groundFromPlayerPosY(mc.thePlayer.boundingBox.minY);
            actionTargetZ = MathHelper.floor_double(mc.thePlayer.posZ);
            return;
        }

        // Если ни одно из условий выше не сработало, значит идем
        state = MovementState.WALKING;
    }

    private void autoSkipPassedNodes() {
        if (mc.thePlayer == null || currentPath == null) return;
        if (currentNodeIndex >= currentPath.size() - 1) return;
        if (!hasVisitedNodeCenter) return;

        final int maxSkips = 4;
        int performed = 0;

        double px = mc.thePlayer.posX;
        double pz = mc.thePlayer.posZ;

        while (performed < maxSkips && currentNodeIndex < currentPath.size() - 1) {
            PathNode curr = currentPath.get(currentNodeIndex);
            PathNode next = currentPath.get(currentNodeIndex + 1);

            if (next.breakBlocks > 0 || next.placeBlocks > 0) break;

            double cx = curr.x + 0.5, cz = curr.z + 0.5;
            double nx = next.x + 0.5, nz = next.z + 0.5;

            double dcx = px - cx, dcz = pz - cz;
            double dnx = px - nx, dnz = pz - nz;
            double distCurrSq = dcx * dcx + dcz * dcz;
            double distNextSq = dnx * dnx + dnz * dnz;

            double abx = (nx - cx), abz = (nz - cz);
            double abLenSq = abx * abx + abz * abz;
            double t = 0.0;
            if (abLenSq > 1.0e-6) {
                t = (dcx * abx + dcz * abz) / abLenSq;
            }

            boolean closerToNext = distNextSq + 0.05 < distCurrSq;
            boolean pastPlane = t >= 1.0;
            boolean reachedNextRadius = distNextSq <= (NODE_REACH_THRESHOLD * NODE_REACH_THRESHOLD);

            if (closerToNext || pastPlane || reachedNextRadius) {
                advanceNodeIndex();
                performed++;
                continue;
            }
            break;
        }

        if (performed > 0) {
            resetStuckDetector();
        }
    }

    private int[] getOccludingBlock(int targetX, int targetY, int targetZ) {
        if (mc.thePlayer == null || mc.theWorld == null) return new int[]{targetX, targetY, targetZ};
        Vec3 eyes = Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 targetCenter = Vec3.createVectorHelper(targetX + 0.5, targetY + 0.5, targetZ + 0.5);
        // false, false, false -> не останавливаться на жидкости, проверять коллизию
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, targetCenter, false);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            // Если попали не в цель, и препятствие непроходимо - ломаем препятствие
            if ((mop.blockX != targetX || mop.blockY != targetY || mop.blockZ != targetZ) &&
                    !PathfindingUtils.isBlockPassable(mc.theWorld, mop.blockX, mop.blockY, mop.blockZ)) {
                return new int[]{mop.blockX, mop.blockY, mop.blockZ};
            }
        }
        return new int[]{targetX, targetY, targetZ};
    }

    private void handleWalkingState() {
        World world = mc.theWorld;
        EntityPlayer player = mc.thePlayer;

        // =================================================================================
        // === 1. АВАРИЙНАЯ ПРОВЕРКА ПРЕПЯТСТВИЙ (FAILSAFE) ===
        // Если PathFinder посчитал путь чистым, но физически там блок - ломаем его.
        // =================================================================================

        int targetX = activeNode.x;
        int targetY = activeNode.y; // Опорный блок (на котором стоят)
        int targetZ = activeNode.z;

        // Координаты, где должны оказаться ноги и голова в целевом узле
        int targetFeetY = YMath.feetFromGround(targetY);
        int targetHeadY = YMath.headFromGround(targetY);

        // A) Проверяем место для НОГ в целевом узле
        if (!PathfindingUtils.isBlockPassable(world, targetX, targetFeetY, targetZ)) {
            System.out.println("[WALK-FAILSAFE] Target feet blocked! Forcing break at " + targetX + "," + targetFeetY + "," + targetZ);
            switchToBreaking(targetX, targetFeetY, targetZ);
            return;
        }

        // B) Проверяем место для ГОЛОВЫ в целевом узле (часто мешает при прыжках)
        if (!PathfindingUtils.isBlockPassable(world, targetX, targetHeadY, targetZ)) {
            System.out.println("[WALK-FAILSAFE] Target head blocked! Forcing break at " + targetX + "," + targetHeadY + "," + targetZ);
            switchToBreaking(targetX, targetHeadY, targetZ);
            return;
        }

        // C) Проверяем ПРИТОЛОКУ (Lintel) - блок перед лицом на ТЕКУЩЕЙ высоте
        // Это критично для спуска: мы идем в узел ниже, но перед лицом стена.
        int currentHeadY = YMath.headFromPlayerPosY(player.boundingBox.minY);

        // Проверяем только если целевой узел не под нами (движение вбок)
        if (targetX != MathHelper.floor_double(player.posX) || targetZ != MathHelper.floor_double(player.posZ)) {
            // Проверяем блок по координатам X/Z цели, но на высоте НАШЕЙ головы
            if (!PathfindingUtils.isBlockPassable(world, targetX, currentHeadY, targetZ)) {
                // Убедимся, что мы не стоим внутри этого блока (лагами занесло)
                double distToBlock = player.getDistanceSq(targetX + 0.5, currentHeadY + 0.5, targetZ + 0.5);
                if (distToBlock > 0.3) { // Если блок впереди
                    System.out.println("[WALK-FAILSAFE] Face blocked (Lintel)! Forcing break at " + targetX + "," + currentHeadY + "," + targetZ);
                    switchToBreaking(targetX, currentHeadY, targetZ);
                    return;
                }
            }
        }
        // =================================================================================


        // --- Стандартная логика движения (Original Logic) ---

        boolean playerHasLowCeiling = hasLowCeilingAt(world, player.posX, player.boundingBox.minY, player.posZ);
        boolean targetHasLowCeiling = hasLowCeilingAt(world, activeNode.x + 0.5, activeNode.getFeetY(), activeNode.z + 0.5);

        // RayTrace проверка (оставлена как fallback, но Failsafe выше обычно срабатывает раньше)
        if (!hasForwardLineOfSightToNode(activeNode) && !playerHasLowCeiling && !targetHasLowCeiling) {
            int px = MathHelper.floor_double(player.posX);
            int pz = MathHelper.floor_double(player.posZ);
            int dirX = Integer.signum(activeNode.x - px);
            int dirZ = Integer.signum(activeNode.z - pz);
            int bx = px + (dirX != 0 ? dirX : 0);
            int bz = pz + (dirZ != 0 ? dirZ : 0);

            int byFeet = YMath.feetFromPlayerPosY(player.boundingBox.minY);
            int byHead = YMath.headFromPlayerPosY(player.boundingBox.minY);

            if (!PathfindingUtils.isBlockPassable(world, bx, byHead, bz)) {
                switchToBreaking(bx, byHead, bz);
                return;
            } else if (!PathfindingUtils.isBlockPassable(world, bx, byFeet, bz)) {
                switchToBreaking(bx, byFeet, bz);
                return;
            }
        }

        // Parkour Skip Logic
        if (ENABLE_PARKOUR_FURTHEST_SKIP) {
            try {
                if (currentPath != null && currentNodeIndex + 1 < currentPath.size()) {
                    PathNode next = currentPath.get(currentNodeIndex + 1);
                    if (next.moveType == MovementType.PARKOUR) {
                        PathNode from = currentPath.get(currentNodeIndex);
                        int dirX = Integer.signum(next.x - from.x);
                        int dirZ = Integer.signum(next.z - from.z);

                        int furthest = currentNodeIndex + 1;
                        int maxAhead = Math.min(currentPath.size() - 1, currentNodeIndex + 4);
                        for (int i = currentNodeIndex + 2; i <= maxAhead; i++) {
                            PathNode cand = currentPath.get(i);
                            if (cand.moveType == null || cand.moveType != MovementType.PARKOUR) break;
                            int cdx = Integer.signum(cand.x - currentPath.get(i - 1).x);
                            int cdz = Integer.signum(cand.z - currentPath.get(i - 1).z);
                            if (cdx != dirX || cdz != dirZ) break;
                            if (Math.abs(cand.y - from.y) > 1) break;

                            boolean los = PathfindingUtils.hasClearLineOfSight(world, from, cand);
                            boolean stand = PathfindingUtils.canStandAt(world, cand.x + 0.5, cand.getFeetY() + 0.01, cand.z + 0.5)
                                    && !PathfindingUtils.isDangerousToStand(world, cand.x, cand.getFeetY(), cand.z);
                            if (los && stand) {
                                furthest = i;
                            } else {
                                break;
                            }
                        }
                        if (furthest > currentNodeIndex + 1) {
                            setNodeIndex(furthest);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Выбор цели для движения (LookAhead)
        PathNode moveTargetNode = getEffectiveTargetNode();
        if (moveTargetNode == null) {
            int moveAheadIndex = Math.min(currentNodeIndex, currentPath.size() - 1);
            moveTargetNode = currentPath.get(moveAheadIndex);
        }

        Vec3 moveTarget;

        // Определяем типы движения
        boolean isAscend = moveTargetNode.moveType != null &&
                (moveTargetNode.moveType == MovementType.ASCEND ||
                        moveTargetNode.moveType == MovementType.DIAGONAL_ASCEND);

        boolean isFall = moveTargetNode.moveType != null &&
                (moveTargetNode.moveType == MovementType.DESCEND ||
                        moveTargetNode.moveType == MovementType.DIAGONAL_DESCEND ||
                        moveTargetNode.moveType == MovementType.FALL);

        if (isFall) {
            // ФИКС ЗАСТРЕВАНИЯ ПРИ СПУСКЕ:
            // Если мы спускаемся/падаем, мы НЕ должны целиться в пол внизу (getFeetY).
            // Мы должны целиться в ту же горизонтальную точку, но на НАШЕЙ текущей высоте.
            // Это создает чисто горизонтальный вектор движения, бот "сшагивает" с края,
            // не пытаясь "вжаться" в стену блока под собой.
            moveTarget = Vec3.createVectorHelper(moveTargetNode.x + 0.5, mc.thePlayer.boundingBox.minY, moveTargetNode.z + 0.5);

            // Дополнительно: отключаем спринт при аккуратном спуске, чтобы не перелететь
            if (moveTargetNode.moveType != MovementType.FALL) { // При FALL можно и спринтовать, если далеко
                mc.thePlayer.setSprinting(false);
            }
        } else if (isAscend && mc.thePlayer.onGround) {
            // При подъеме (перед прыжком) тоже целимся по своей высоте, чтобы не упереться в ступеньку
            moveTarget = Vec3.createVectorHelper(moveTargetNode.x + 0.5, mc.thePlayer.boundingBox.minY, moveTargetNode.z + 0.5);
        } else {
            // Стандартное движение по ровной поверхности
            moveTarget = Vec3.createVectorHelper(moveTargetNode.x + 0.5, moveTargetNode.getFeetY(), moveTargetNode.z + 0.5);
        }

        // Pillar / Bridge Detection while walking
        if (currentPath != null && currentNodeIndex + 1 < currentPath.size()) {
            PathNode upcoming = currentPath.get(currentNodeIndex + 1);
            if (upcoming.moveType == MovementType.PILLAR || upcoming.moveType == MovementType.BRIDGE) {
                double ndx = mc.thePlayer.posX - (activeNode.x + 0.5);
                double ndz = mc.thePlayer.posZ - (activeNode.z + 0.5);
                double nh = Math.sqrt(ndx * ndx + ndz * ndz);
                if (mc.thePlayer.onGround && nh < 1.8) {
                    advanceNodeIndex();
                    state = MovementState.PLACING_BLOCK;
                    return;
                }
            }
        }

        // Parkour Jump Logic
        if (activeNode.moveType != null && activeNode.moveType == MovementType.PARKOUR) {
            PathNode prevNode = (currentNodeIndex > 0 ? currentPath.get(currentNodeIndex - 1) : null);
            if (prevNode != null) {
                int dirX = Integer.signum(activeNode.x - prevNode.x);
                int dirZ = Integer.signum(activeNode.z - prevNode.z);

                // Если застряли перед прыжком
                if (mc.thePlayer.fallDistance > 0.9 ||
                        mc.thePlayer.boundingBox.minY + 0.2 < Math.min(prevNode.getFeetY(), activeNode.getFeetY()) - 0.8) {
                    if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null) {
                        FuctorizeClient.INSTANCE.pathfindingManager.addTemporaryPenalty(activeNode, STUCK_PENALTY_COST);
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastRecalculation > 1000) {
                        lastRecalculation = now;
                        requestNewPathSegment(false);
                    }
                    return;
                }

                // Логика прыжка (JumpHelper logic inlined or called)
                int stride = Math.max(Math.abs(activeNode.x - prevNode.x), Math.abs(activeNode.z - prevNode.z));
                boolean shortJump = stride <= 2;

                // Рассчет дистанции до края
                double launchBlockX = prevNode.x + dirX + 0.5;
                double launchBlockZ = prevNode.z + dirZ + 0.5;
                // Упрощенная проверка для 1.7.10
                double distToTarget = Math.sqrt(Math.pow(activeNode.x+0.5 - player.posX, 2) + Math.pow(activeNode.z+0.5 - player.posZ, 2));

                // Прыгаем если близко к краю блока
                // (Полная логика тут объемная, оставляем ваш старый код, если он работал, или JumpHelper)
                ru.fuctorial.fuctorize.utils.pathfinding.JumpHelper.applyParkourJump(mc, activeNode, prevNode);
            }
        }

        // Regular Jump Logic
        if (activeNode != null && mc.thePlayer.onGround) {
            boolean ascendMove = (activeNode.moveType == MovementType.ASCEND || activeNode.moveType == MovementType.DIAGONAL_ASCEND);
            boolean shouldJumpForNode = activeNode.needsJump || ascendMove;

            if (shouldJumpForNode) {
                if (!ensureHeadroomForAscendSafe()) return;
                ru.fuctorial.fuctorize.utils.pathfinding.JumpHelper.applyAscendJump(mc, activeNode);
            }

            // Step-up check (если перед нами блок 1-й высоты)
            int pbx = MathHelper.floor_double(mc.thePlayer.posX);
            int pby = YMath.groundFromPlayerPosY(mc.thePlayer.boundingBox.minY);
            int pbz = MathHelper.floor_double(mc.thePlayer.posZ);
            int dirX = Integer.signum(activeNode.x - pbx);
            int dirZ = Integer.signum(activeNode.z - pbz);
            if (dirX != 0 || dirZ != 0) {
                int frontX = pbx + dirX;
                int frontZ = pbz + dirZ;
                int feetY = YMath.feetFromGround(pby);
                // Если спереди блок и надо подняться
                if (!PathfindingUtils.isBlockPassable(mc.theWorld, frontX, feetY, frontZ)) {
                    double dyToTarget = activeNode.getFeetY() - mc.thePlayer.boundingBox.minY;
                    if (dyToTarget > 0.05 && dyToTarget <= 1.2) {
                        if (PathfindingUtils.isBlockPassable(mc.theWorld, frontX, YMath.headFromGround(pby), frontZ)) {
                            MovementUtils.jump();
                        } else {
                            // Стена (feet+head blocked) - Failsafe выше уже должен был это поймать, но на всякий случай:
                            switchToBreaking(frontX, feetY, frontZ);
                            return;
                        }
                    }
                }
            }
        }

        // Применяем движение
        MovementUtils.updateMovementOnly(moveTarget);

        // Проверка достижения центра узла
        double dx = mc.thePlayer.posX - (activeNode.x + 0.5);
        double dy = mc.thePlayer.boundingBox.minY - activeNode.getFeetY();
        double dz = mc.thePlayer.posZ - (activeNode.z + 0.5);
        double horizontalDistSq = dx * dx + dz * dz;
        double motionSpeed = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);

        double centerThresholdSq = NODE_CENTER_THRESHOLD * NODE_CENTER_THRESHOLD;
        boolean atNodeCenter = horizontalDistSq <= centerThresholdSq && Math.abs(dy) < 0.75;
        if (atNodeCenter) {
            hasVisitedNodeCenter = true;
        }

        boolean allowAdvance;
        if (activeNode != null && activeNode.moveType == MovementType.PARKOUR) {
            allowAdvance = atNodeCenter && mc.thePlayer.onGround;
        } else {
            allowAdvance = atNodeCenter;
        }

        if (allowAdvance) {
            System.out.println("[NODE] Reached node " + currentNodeIndex + ", moving to " + (currentNodeIndex + 1) +
                    " dist=" + String.format("%.2f", Math.sqrt(horizontalDistSq)) + " motionSpeed=" + String.format("%.3f", motionSpeed));
            advanceNodeIndex();
            lastNodeReachedTime = System.currentTimeMillis();
            attemptNodeRef = null;
            attemptStartMs = 0L;
            attemptStartDistSq = 0.0;
            resetStuckDetector();
        }
    }

    // Вспомогательный метод для переключения в режим ломания из любого места
    private void switchToBreaking(int x, int y, int z) {
        // ЛОГ: Видим, какой конкретно блок вызвал остановку
        System.out.println("[FAILSAFE] LOCKED target: " + x + "," + y + "," + z);

        state = MovementState.BREAKING_BLOCK;
        actionTargetX = x;
        actionTargetY = y;
        actionTargetZ = z;

        // Сбрасываем таймер застревания на старт
        breakingStartTime = System.currentTimeMillis();
        breakingBlockX = x;
        breakingBlockY = y;
        breakingBlockZ = z;

        // Принудительно останавливаемся
        MovementUtils.stopMovement();
    }

    private boolean hasForwardLineOfSightToNode(PathNode node) {
        if (mc.thePlayer == null || mc.theWorld == null || node == null) return true;
        Vec3 start = Vec3.createVectorHelper(mc.thePlayer.posX,
                mc.thePlayer.boundingBox.minY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 end = Vec3.createVectorHelper(node.x + 0.5,
                node.getFeetY() + 0.1, node.z + 0.5);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(start, end, false);
        if (hit == null || hit.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) return true;

        double dx = hit.hitVec.xCoord - start.xCoord;
        double dy = hit.hitVec.yCoord - start.yCoord;
        double dz = hit.hitVec.zCoord - start.zCoord;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist < 0.35) return true;

        double minLineY = Math.min(start.yCoord, end.yCoord) + 1.2;
        if (hit.hitVec.yCoord > minLineY) return true;

        return false;
    }

    public MovementState getState() {
        return this.state;
    }

    public int getActionTargetX() {
        return this.actionTargetX;
    }

    public int getActionTargetY() {
        return this.actionTargetY;
    }

    public int getActionTargetZ() {
        return this.actionTargetZ;
    }

    public PathNode getActiveNode() {
        if (currentPath == null || currentNodeIndex >= currentPath.size()) {
            return null;
        }
        return currentPath.get(currentNodeIndex);
    }

    private void handleJumpingState() {
        MovementUtils.jump();

        state = MovementState.WALKING;
    }

    private void handlePlacingState() {
        boolean success;
        if (activeNode.moveType != null && activeNode.moveType == MovementType.BRIDGE) {

            if (findBlockSlotInHotbar() == -1) {
                if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null) {
                    FuctorizeClient.INSTANCE.pathfindingManager.addTemporaryPenalty(activeNode, STUCK_PENALTY_COST);
                }
                recalculatePath();
                return;
            }
            success = placeBlockForBridgeSmart();
        } else if (activeNode.moveType != null && activeNode.moveType == MovementType.PILLAR) {

            if (findBlockSlotInHotbar() == -1) {
                if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null) {
                    FuctorizeClient.INSTANCE.pathfindingManager.addTemporaryPenalty(activeNode, STUCK_PENALTY_COST);
                }
                recalculatePath();
                return;
            }
            MovementUtils.jump();
            success = placeBlockUnderFeet();
            if (success) {

                if (currentPath != null && currentNodeIndex + 1 < currentPath.size()) {
                    PathNode nextNode = currentPath.get(currentNodeIndex + 1);
                    if (nextNode.y > activeNode.y) {
                        advanceNodeIndex();
                    }
                }
            }
        } else if (forceSafeDropPlacement) {

            if (findBlockSlotInHotbar() == -1) {
                if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null) {
                    FuctorizeClient.INSTANCE.pathfindingManager.addTemporaryPenalty(activeNode, STUCK_PENALTY_COST);
                }
                forceSafeDropPlacement = false;
                recalculatePath();
                return;
            }
            success = placeBlockUnderFeet();
            forceSafeDropPlacement = false;
        } else {
            success = true;
        }

        if (success) {
            state = MovementState.WALKING;
        }

    }

    private void handleBreakingState() {
        // 1. Если блок уже сломан (стал проходимым), сбрасываемся и идем дальше
        if (PathfindingUtils.isBlockPassable(mc.theWorld, actionTargetX, actionTargetY, actionTargetZ)) {
            InputUtils.releaseAttack();
            mc.playerController.resetBlockRemoving();
            breakingStartTime = 0;
            state = MovementState.WALKING;
            return;
        }

        // 2. Останавливаемся и смотрим на блок
        MovementUtils.stopMovement();
        MovementUtils.lookAtBlock(actionTargetX, actionTargetY, actionTargetZ);

        // 3. Обновляем mc.objectMouseOver, чтобы наводка была идеальной
        Vec3 eyes = Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 blockVec = Vec3.createVectorHelper(actionTargetX + 0.5, actionTargetY + 0.5, actionTargetZ + 0.5);

        int side = 1;
        double dx = blockVec.xCoord - eyes.xCoord;
        double dy = blockVec.yCoord - eyes.yCoord;
        double dz = blockVec.zCoord - eyes.zCoord;

        if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Math.abs(dz)) side = dx > 0 ? 4 : 5;
        else if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > Math.abs(dz)) side = dy > 0 ? 0 : 1;
        else side = dz > 0 ? 2 : 3;

        mc.objectMouseOver = new MovingObjectPosition(actionTargetX, actionTargetY, actionTargetZ, side, blockVec, true);

        // 4. Зажимаем кнопку для обычной игры
        InputUtils.pressAttack();
        mc.thePlayer.swingItem();

        // 5. ФИКС ПАУЗЫ: Если открыт GUI (экран не null), ванильный майнкрафт ИГНОРИРУЕТ нажатие кнопок.
        // Поэтому в этом случае (и только в этом!) мы вручную заставляем контроллер продолжать ломать.
        if (mc.currentScreen != null) {
            mc.playerController.onPlayerDamageBlock(actionTargetX, actionTargetY, actionTargetZ, side);
        }
    }

    private boolean placeBlockForBridge() {

        int targetX = activeNode.x;
        int targetY = activeNode.y;
        int targetZ = activeNode.z;

        if (!(mc.theWorld.getBlock(targetX, targetY, targetZ) instanceof BlockAir)) {
            return true;
        }

        int blockSlot = findBlockSlotInHotbar();
        if (blockSlot == -1) {
            System.out.println("[BRIDGE] No blocks in hotbar — immediate path recalculation.");
            if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null && activeNode != null) {
                FuctorizeClient.INSTANCE.pathfindingManager.addTemporaryPenalty(activeNode, STUCK_PENALTY_COST);
            }
            recalculatePath();
            return false;
        }
        mc.thePlayer.inventory.currentItem = blockSlot;
        ItemStack blockStack = mc.thePlayer.inventory.getCurrentItem();
        if (blockStack == null) return false;

        int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
        int playerBlockY = YMath.groundFromPlayerPosY(mc.thePlayer.boundingBox.minY);
        int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);

        int side;
        int dx = targetX - playerBlockX;
        int dz = targetZ - playerBlockZ;

        if (dx > 0) side = 5;
        else if (dx < 0) side = 4;
        else if (dz > 0) side = 3;
        else if (dz < 0) side = 2;
        else return false;

        Vec3 hitVec = Vec3.createVectorHelper(playerBlockX + 0.5, playerBlockY + 0.5, playerBlockZ + 0.5);

        MovementUtils.stopMovement();
        double lookX = playerBlockX + 0.5;
        double lookY = playerBlockY + 0.5;
        double lookZ = playerBlockZ + 0.5;
        switch (side) {
            case 5:
                lookX = playerBlockX + 1.0;
                break;
            case 4:
                lookX = playerBlockX;
                break;
            case 3:
                lookZ = playerBlockZ + 1.0;
                break;
            case 2:
                lookZ = playerBlockZ;
                break;
        }
        MovementUtils.lookAt(Vec3.createVectorHelper(lookX, lookY, lookZ));

        hitVec = Vec3.createVectorHelper(lookX, lookY, lookZ);

        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, blockStack, playerBlockX, playerBlockY, playerBlockZ, side, hitVec)) {
            mc.thePlayer.swingItem();
        }

        return false;
    }

    private boolean isUpcomingPathStillValid() {
        if (currentPath == null || currentNodeIndex >= currentPath.size() || mc.theWorld == null) return true;

        final int lookahead = 4;
        final int end = Math.min(currentPath.size() - 1, currentNodeIndex + lookahead);

        boolean playerHasLowCeiling = hasLowCeilingAt(mc.theWorld, mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ);

        for (int i = currentNodeIndex; i <= end; i++) {
            PathNode node = currentPath.get(i);

            if (i == currentNodeIndex) {
                continue;
            }

            if (node.placeBlocks == 0 && node.breakBlocks == 0) {
                if (!PathfindingUtils.canStandOn(mc.theWorld, node.x, node.y, node.z)) {
                    return false;
                }
            }

            if (!PathfindingUtils.isSpaceFree(mc.theWorld, node.x + 0.5, node.getFeetY() + 0.01, node.z + 0.5)) {
                boolean nodeHasLowCeiling = hasLowCeilingAt(mc.theWorld, node.x + 0.5, node.getFeetY(), node.z + 0.5);
                if (playerHasLowCeiling || nodeHasLowCeiling) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    private int findBlockSlotInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                return i;
            }
        }
        return -1;
    }

    private Vec3 getEffectiveTarget() {
        if (currentPath == null || currentNodeIndex >= currentPath.size()) {
            return Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ);
        }

        PathNode effectiveTargetNode = currentPath.get(currentNodeIndex);

        for (int i = currentNodeIndex + 1; i < currentPath.size(); i++) {
            PathNode nextNode = currentPath.get(i);
            PathNode prevNode = currentPath.get(i - 1);

            if (nextNode.moveType == null || effectiveTargetNode.moveType == null ||
                    nextNode.moveType != effectiveTargetNode.moveType) {
                break;
            }

            if (nextNode.breakBlocks > 0 || nextNode.placeBlocks > 0) {
                break;
            }

            if (Math.abs(nextNode.y - prevNode.y) > 1) {
                break;
            }

            if (!hasForwardLineOfSightToNode(nextNode)) {
                break;
            }

            effectiveTargetNode = nextNode;
        }

        return Vec3.createVectorHelper(
                effectiveTargetNode.x + 0.5,
                effectiveTargetNode.getFeetY(),
                effectiveTargetNode.z + 0.5
        );
    }

    private void requestNewPathSegment(boolean isFirstPath) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        resetStuckDetector();
        activeCalculations.incrementAndGet();

        // 1. Определяем точку старта
        double startX, startY, startZ;
        if (isFirstPath) {
            startX = player.posX;
            startY = Math.floor(player.boundingBox.minY) + 0.01;
            startZ = player.posZ;
        } else if (!pathSegmentQueue.isEmpty()) {
            List<PathNode> lastList = ((LinkedList<List<PathNode>>) pathSegmentQueue).getLast();
            PathNode lastNode = lastList.get(lastList.size() - 1);
            startX = lastNode.x + 0.5;
            startY = lastNode.getFeetY();
            startZ = lastNode.z + 0.5;
        } else if (currentPath != null && !currentPath.isEmpty()) {
            // Prefetch case: Queue is empty, but we have a current path. Start from its end.
            PathNode lastNode = currentPath.get(currentPath.size() - 1);
            startX = lastNode.x + 0.5;
            startY = lastNode.getFeetY();
            startZ = lastNode.z + 0.5;
        } else {
            // Fallback (should not happen ideally)
            startX = player.posX;
            startY = Math.floor(player.boundingBox.minY) + 0.01;
            startZ = player.posZ;
        }

        if (FuctorizeClient.INSTANCE.pathfindingManager != null) {
            FuctorizeClient.INSTANCE.pathfindingManager.resetCancellationFlag();
        }

        World w = mc.theWorld;
        double finalTargetY = this.targetY;

        // 2. ЛОГИКА КОРРЕКЦИИ Y (Только если чанк прогружен!)
        // Если чанк загружен, проверяем, можно ли стоять в целевой точке.
        // Если нельзя (Y - залупа), ищем нормальный Y в этой же колонне.
        if (w.blockExists(MathHelper.floor_double(targetX), MathHelper.floor_double(targetY), MathHelper.floor_double(targetZ))) {
            int tx = MathHelper.floor_double(targetX);
            int ty = MathHelper.floor_double(targetY);
            int tz = MathHelper.floor_double(targetZ);

            // Проверяем, стоим ли мы в блоке или в воздухе
            if (!PathfindingUtils.canStandAt(w, targetX, targetY, targetZ) ||
                    PathfindingUtils.isDangerousToStand(w, tx, YMath.feetFromGround(ty), tz)) {

                // Ищем безопасную высоту (вверх и вниз)
                Integer safeY = PathfindingUtils.findStandingYAllowingDown(w, tx, ty, tz, 10.0, 30.0); // Ищем +/- широко
                if (safeY != null) {
                    finalTargetY = safeY;
                    // Обновляем глобальную цель, чтобы не пересчитывать каждый раз
                    this.targetY = safeY;
                    System.out.println("[NAV] Target Y adjusted to safe ground: " + safeY);
                } else {
                    // Если совсем жопа и встать негде - вот тут можно включить поиск "ближайшей точки"
                    // Но пока оставим как есть, пусть пытается подойти максимально близко
                }
            }
        }

        final double searchTargetY = finalTargetY;

        CompletableFuture<PathResult> future;

        // 3. ЗАПУСК ПОИСКА (БЕЗ ПРОВЕРОК НА TARGET_STANDABLE)
        // Сначала всегда пробуем найти путь (прямой или к границе чанка)
        // "findPathToNearest" используем ТОЛЬКО если мы уже зафейлились много раз (застряли)

        if (consecutivePathFailures >= 3) {
            // Если мы уже 3 раза не смогли найти путь — значит мы застряли или цель в жопе.
            // Только тогда расширяем радиус поиска.
            int adaptiveRadius = Math.min(32, 4 + consecutivePathFailures * 2);
            future = FuctorizeClient.INSTANCE.pathfindingManager.findPathToNearestAsync(
                    startX, startY, startZ, targetX, searchTargetY, targetZ, adaptiveRadius
            );
        } else {
            // СТАНДАРТНЫЙ РЕЖИМ: Идем в координаты.
            // Если далеко/не прогружено -> findPathTowardUnloadedTarget сам разберется и поведет к границе X/Z.
            CompletableFuture<PathResult> direct = FuctorizeClient.INSTANCE.pathfindingManager.findPathAsync(
                    startX, startY, startZ, targetX, searchTargetY, targetZ
            );

            future = direct.handle((res, ex) -> {
                if (ex != null || (res != null && !res.isSuccess())) {
                    // Прямой путь не вышел (далеко/стены). Запускаем поиск к границе прогрузки.
                    return FuctorizeClient.INSTANCE.pathfindingManager.findPathTowardUnloadedTarget(
                            startX, startY, startZ, targetX, searchTargetY, targetZ
                    );
                }
                return res;
            });
        }

        future.thenAccept(result -> {
            activeCalculations.decrementAndGet();

            if (!isNavigating) return;

            // ИЗМЕНЕНИЕ: Принимаем SUCCESS или PARTIAL (если путь достаточно длинный)
            boolean isValidPath = result.isSuccess() || (result.isPartial() && result.getPathLength() > 2);

            if (isValidPath) {
                List<PathNode> path = result.getPath();

                // Лог для отладки
                if (result.isPartial()) {
                    System.out.println("[BotNavigator] Accepted PARTIAL path of length " + result.getPathLength());
                }

                consecutivePathFailures = 0;
                if (isFirstPath) {
                    pathSegmentQueue.clear();
                    currentPath = path;
                    setNodeIndex(0);
                    checkAndSkipStartNode();
                } else {
                    pathSegmentQueue.offer(path);
                }
            } else {
                consecutivePathFailures++;
                System.out.println("Pathfinding error: " + result.getErrorMessage() + " (attempt " + consecutivePathFailures + ")");

// Если мы зафейлились уже 5 раз подряд — значит, мы застряли или стоим криво.
                // Пытаемся найти случайную точку рядом и отойти на неё, чтобы сбросить позицию.
                if (consecutivePathFailures >= 5) {
                    System.out.println("[Unstuck] Too many failures (" + consecutivePathFailures + "). Attempting emergency move...");

                    double pX = mc.thePlayer.posX;
                    double pZ = mc.thePlayer.posZ;
                    int pY = YMath.groundFromPlayerPosY(mc.thePlayer.boundingBox.minY);

                    boolean foundEscape = false;

                    // Делаем 15 попыток найти случайную точку в радиусе 6 блоков
                    for (int i = 0; i < 15; i++) {
                        double rx = pX + (Math.random() - 0.5) * 12.0; // +/- 6 блоков
                        double rz = pZ + (Math.random() - 0.5) * 12.0;

                        int ix = MathHelper.floor_double(rx);
                        int iz = MathHelper.floor_double(rz);

                        // Ищем нормальный Y (чуть выше или чуть ниже)
                        Integer safeY = PathfindingUtils.findStandingYAllowingDown(mc.theWorld, ix, pY, iz, 2.0, 3.0);

                        if (safeY != null) {
                            // Проверяем, что это не та же самая точка, где мы стоим
                            if (Math.abs(ix - MathHelper.floor_double(pX)) < 2 && Math.abs(iz - MathHelper.floor_double(pZ)) < 2) {
                                continue;
                            }

                            System.out.println("[Unstuck] Found local escape target: " + ix + "," + safeY + "," + iz);

                            // ПРИНУДИТЕЛЬНО запускаем поиск короткого пути к этой точке
                            // Используем findPathSync (синхронно, так как мы уже в колбэке), чтобы сразу применить
                            PathResult escapeResult = FuctorizeClient.INSTANCE.pathfindingManager.findPathSync(
                                    mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ,
                                    ix + 0.5, safeY, iz + 0.5
                            );

                            if (escapeResult.isSuccess() || (escapeResult.isPartial() && escapeResult.getPathLength() > 1)) {
                                System.out.println("[Unstuck] Escape path found! Executing deviation.");
                                currentPath = escapeResult.getPath();
                                setNodeIndex(0);
                                consecutivePathFailures = 0; // Сбрасываем счетчик ошибок
                                foundEscape = true;
                                break; // Выходим из цикла перебора точек
                            }
                        }
                    }

                    if (!foundEscape) {
                        // Если даже отойти не получилось - просто прыгаем на месте (иногда помогает обновить boundingBox)
                        if (mc.thePlayer.onGround) {
                            mc.thePlayer.jump();
                        }
                        // И сбрасываем счетчик, чтобы не спамило каждый тик
                        if (consecutivePathFailures > 20) consecutivePathFailures = 0;
                    }
                }
            }
        });
    }


    public NavigationStatus getStatus() {
        return this.status;
    }

    private boolean isDangerousAhead(PathNode nextNode) {
        if (nextNode == null) return false;
        if (nextNode.moveType != null && (nextNode.moveType == MovementType.ASCEND || nextNode.moveType == MovementType.DIAGONAL_ASCEND)) {
            return false;
        }
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;
        if (player == null || world == null) {
            return false;
        }

        int checkX = nextNode.x;
        int checkZ = nextNode.z;
        int targetGroundY = nextNode.y;

        if (PathfindingUtils.isDangerousToStand(world, checkX, YMath.feetFromGround(targetGroundY), checkZ)) {
            return true;
        }

        int currentFeetY = YMath.feetFromPlayerPosY(player.boundingBox.minY);
        int targetFeetY = YMath.feetFromGround(targetGroundY);
        int dropDistance = currentFeetY - targetFeetY;
        if (dropDistance <= 0) {
            return false;
        }

        if (dropDistance <= 3) {
            return false;
        }

        for (int dy = 1; dy <= dropDistance; dy++) {
            int testY = currentFeetY - dy;
            if (testY < 1) {
                return true;
            }
            if (PathfindingUtils.isDangerousToStand(world, checkX, testY, checkZ)) {
                return true;
            }
        }

        double fallDamage = PathfindingUtils.estimateFallDamage(dropDistance);
        return fallDamage >= player.getHealth();
    }

    private Vec3 getSmoothedTarget() {
        if (currentPath == null || currentNodeIndex >= currentPath.size()) {
            return Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ);
        }

        PathNode currentTargetNode = currentPath.get(currentNodeIndex);
        boolean isBreaking = (breakingBlockX != Integer.MIN_VALUE && currentTargetNode.breakBlocks > 0);

        if (isBreaking) {
            EntityPlayer player = mc.thePlayer;

            double blockCenterX = breakingBlockX + 0.5;
            double blockCenterZ = breakingBlockZ + 0.5;

            double vecX = blockCenterX - player.posX;
            double vecZ = blockCenterZ - player.posZ;
            double dist = Math.sqrt(vecX * vecX + vecZ * vecZ);

            if (dist > 0) {
                vecX /= dist;
                vecZ /= dist;
            }

            double safeDistance = 2.5;
            double targetX = blockCenterX - vecX * safeDistance;
            double targetZ = blockCenterZ - vecZ * safeDistance;

            if (dist >= safeDistance - 0.1 && dist <= 4.5) {
                System.out.println("dist >= safeDistance - 0.1 && dist <= 4.5");
                MovementUtils.stopMovement();
                return Vec3.createVectorHelper(player.posX, player.boundingBox.minY, player.posZ);
            } else {
                System.out.println("dist >= safeDistance - 0.1 && dist <= 4.5");
                return Vec3.createVectorHelper(targetX, player.boundingBox.minY, targetZ);
            }
        }

        return Vec3.createVectorHelper(
                currentTargetNode.x + 0.5,
                currentTargetNode.getFeetY(),
                currentTargetNode.z + 0.5
        );
    }

    private boolean isPathClear(Vec3 start, Vec3 end) {
        return true;
    }

    private boolean canMoveDirectlySmooth(PathNode from, PathNode to, int fromIndex, int toIndex) {
        int deltaY = to.y - from.y;
        if (Math.abs(deltaY) > 2) {
            return false;
        }

        for (int i = fromIndex; i <= toIndex; i++) {
            PathNode node = currentPath.get(i);
            if (node.breakBlocks > 0 || node.placeBlocks > 0) {
                return i == fromIndex;
            }

            if (node.moveType != null && (node.moveType == MovementType.SWIM_SURFACE ||
                    node.moveType == MovementType.SWIM_UNDERWATER)) {
                continue;
            }
        }

        return true;
    }

    private boolean canMoveDirectly(PathNode from, PathNode to) {
        int deltaY = to.y - from.y;
        if (deltaY > 1) {
            return false;
        }

        for (int i = currentNodeIndex; i <= Math.min(currentNodeIndex + (int) LOOK_AHEAD_NODES, currentPath.size() - 1); i++) {
            PathNode node = currentPath.get(i);
            if (node.breakBlocks > 0 || node.placeBlocks > 0 || node.needsJump) {
                return false;
            }
        }

        return true;
    }

    private boolean breakBlocksAtNode(PathNode node) {
        World world = mc.theWorld;

        for (int dy = 0; dy <= 1; dy++) {
            int bx = node.x;
            int by = (dy == 0) ? node.y : YMath.feetFromGround(node.y);
            int bz = node.z;
            Block block = world.getBlock(bx, by, bz);

            if (block == null || block instanceof BlockAir) {
                continue;
            }

            MovementUtils.stopMovement();

            if (breakingBlockX != bx || breakingBlockY != by || breakingBlockZ != bz) {
                InputUtils.releaseAttack();
                breakingBlockX = bx;
                breakingBlockY = by;
                breakingBlockZ = bz;
                breakingStartTime = System.currentTimeMillis();
            }

            MovementUtils.lookAtBlock(bx, by, bz);

            InputUtils.pressAttack();

            return false;
        }

        InputUtils.releaseAttack();
        resetBreaking();
        return true;
    }

    private float getDigSpeed(EntityPlayer player, Block block) {
        ItemStack heldItem = player.getHeldItem();
        float baseSpeed = 1.0f;

        if (heldItem != null) {
            baseSpeed = heldItem.getItem().getDigSpeed(heldItem, block, 0);
        }

        return baseSpeed;
    }

    private void resetBreaking() {
        InputUtils.releaseAttack();
        breakingBlockX = Integer.MIN_VALUE;
        breakingBlockY = Integer.MIN_VALUE;
        breakingBlockZ = Integer.MIN_VALUE;
        breakingStartTime = 0L;
    }

    private boolean placeBlockUnderFeet() {
        EntityPlayer player = mc.thePlayer;
        int x = MathHelper.floor_double(player.posX);
        int y = YMath.groundFromPlayerPosY(player.boundingBox.minY);
        int z = MathHelper.floor_double(player.posZ);

        if (!(mc.theWorld.getBlock(x, y, z) instanceof BlockAir)) {
            return true;
        }

        int blockSlot = findBlockSlotInHotbar();
        if (blockSlot == -1) {
            System.out.println("[PILLAR] No blocks in inventory! Requesting path recalculation...");

            if (currentPath != null && currentNodeIndex < currentPath.size()) {

                currentPath = null;
                setNodeIndex(0);

                requestNewPathSegment(false);
            }
            return false;
        }
        player.inventory.currentItem = blockSlot;
        ItemStack blockStack = player.inventory.getCurrentItem();
        if (blockStack == null) return false;

        Vec3 hitVec = Vec3.createVectorHelper(x + 0.5, y + 0.5, z + 0.5);
        if (mc.playerController.onPlayerRightClick(player, mc.theWorld, blockStack, x, YMath.groundBelow(y), z, 1, hitVec)) {
            player.swingItem();
            return true;
        }
        return false;
    }

    private boolean placeBlockForBridgeSmart() {
        if (activeNode == null || mc.thePlayer == null || mc.theWorld == null) return false;

        final int airX = activeNode.x;
        final int airY = YMath.groundBelow(activeNode.y);
        final int airZ = activeNode.z;

        if (!(mc.theWorld.getBlock(airX, airY, airZ) instanceof BlockAir)) {
            return true;
        }

        int slot = findBlockSlotInHotbar();
        if (slot == -1) {
            System.out.println("[BRIDGE] No placeable block in hotbar — immediate path recalculation.");
            if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null && activeNode != null) {
                FuctorizeClient.INSTANCE.pathfindingManager.addTemporaryPenalty(activeNode, STUCK_PENALTY_COST);
            }
            recalculatePath();
            return false;
        }
        mc.thePlayer.inventory.currentItem = slot;
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;

        int[][] dirs = new int[][]{
                {1, 0, 0, 4},
                {-1, 0, 0, 5},
                {0, 0, 1, 2},
                {0, 0, -1, 3},
                {0, 1, 0, 0},
                {0, -1, 0, 1}
        };

        Vec3 eyes = Vec3.createVectorHelper(
                mc.thePlayer.posX,
                mc.thePlayer.boundingBox.minY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ
        );

        class Candidate {
            int nx, ny, nz, side;
            double dist;
            double hx, hy, hz;
        }
        java.util.List<Candidate> candidates = new java.util.ArrayList<Candidate>();
        for (int[] d : dirs) {
            int nx = airX + d[0];
            int ny = airY + d[1];
            int nz = airZ + d[2];
            if (!mc.theWorld.blockExists(nx, ny, nz)) continue;

            Block neighbor = mc.theWorld.getBlock(nx, ny, nz);
            if (neighbor == null || neighbor instanceof BlockAir) continue;
            if (neighbor.getMaterial() == null || neighbor.getMaterial().isReplaceable()) continue;

            double hx = nx + 0.5 - 0.5 * d[0];
            double hy = ny + 0.5 - 0.5 * d[1];
            double hz = nz + 0.5 - 0.5 * d[2];

            double ddx = hx - eyes.xCoord;
            double ddy = hy - eyes.yCoord;
            double ddz = hz - eyes.zCoord;
            double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);

            Candidate c = new Candidate();
            c.nx = nx;
            c.ny = ny;
            c.nz = nz;
            c.side = d[3];
            c.dist = dist;
            c.hx = hx;
            c.hy = hy;
            c.hz = hz;
            candidates.add(c);
        }
        if (candidates.isEmpty()) return false;

        java.util.Collections.sort(candidates, new java.util.Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(a.dist, b.dist);
            }
        });

        ItemBlock itemBlock = (ItemBlock) stack.getItem();
        for (Candidate c : candidates) {
            try {
                if (!itemBlock.func_150936_a(mc.theWorld, airX, airY, airZ, c.side, mc.thePlayer, stack)) {
                    continue;
                }
            } catch (Throwable t) {

            }

            MovementUtils.stopMovement();
            MovementUtils.lookAt(Vec3.createVectorHelper(c.hx, c.hy, c.hz));

            Vec3 hit = Vec3.createVectorHelper(c.hx, c.hy, c.hz);
            boolean placed = mc.playerController.onPlayerRightClick(
                    mc.thePlayer, mc.theWorld, stack, c.nx, c.ny, c.nz, c.side, hit);
            if (placed) {
                mc.thePlayer.swingItem();
                return true;
            }
        }

        return false;
    }

    private ItemStack findBlockInInventory() {
        EntityPlayer player = mc.thePlayer;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock) {

                if (((ItemBlock) stack.getItem()).func_150936_a(mc.theWorld, 0, -1, 0, 0, player, stack)) {
                    return stack;
                }
            }
        }
        return null;
    }

    private void checkAndRecalculatePath() {
        if (isNavigating && currentPath != null && currentNodeIndex >= currentPath.size()) {
            // Path ended, but not at target. Request new segment.
            if (!pathSegmentQueue.isEmpty()) {
                currentPath = pathSegmentQueue.poll();
                setNodeIndex(0);
                checkAndSkipStartNode();
                resetStuckDetector();
            } else if (activeCalculations.get() == 0) {
                requestNewPathSegment(false);
            }
            return;
        }

        if (currentPath == null || mc.thePlayer == null || currentNodeIndex >= currentPath.size()) return;

        // --- ФИКС ОБХОДА: Блокируем пересчет, если мы заняты копанием ---
        if (state == MovementState.BREAKING_BLOCK) {
            long now = System.currentTimeMillis();

            // Единственное исключение: если мы долбим блок слишком долго (зависли/забагались)
            if (breakingStartTime > 0L && (now - breakingStartTime) >= BREAKING_FAIL_TIMEOUT_MS) {
                if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null && activeNode != null) {
                    // Штрафуем этот узел, чтобы в следующий раз путь строился не через него
                    FuctorizeClient.INSTANCE.pathfindingManager.addTemporaryPenalty(activeNode, STUCK_PENALTY_COST);
                }
                InputUtils.releaseAttack();
                System.out.println("[REPLAN] Breaking timeout (7s) -> recalculating path");
                recalculatePath();
            }

            // Если таймаут не вышел — ПРОСТО ВЫХОДИМ.
            // Не даем боту искать новый путь, пока он не доломает текущий блок.
            return;
        }
        // --------------------------------------------------------------

        try { checkInventoryAndAdapt(); } catch (Throwable ignored) {}
        try {
            if (!isUpcomingPathStillValid()) {
                System.out.println("[REPLAN] Upcoming path invalidated by world changes -> recalculating");
                recalculatePath();
                return;
            }
        } catch (Throwable ignored) {}

        // Логика проверки паркура (оставляем без изменений)
        PathNode node = currentPath.get(currentNodeIndex);
        long now = System.currentTimeMillis();
        if (attemptNodeRef != node) {
            attemptNodeRef = node;
            attemptStartMs = now;
            double adx = mc.thePlayer.posX - (node.x + 0.5);
            double adz = mc.thePlayer.posZ - (node.z + 0.5);
            attemptStartDistSq = adx * adx + adz * adz;
        }
        if (node.moveType == MovementType.PARKOUR) {
            double cdx = mc.thePlayer.posX - (node.x + 0.5);
            double cdz = mc.thePlayer.posZ - (node.z + 0.5);
            double currDistSq = cdx * cdx + cdz * cdz;
            boolean madeProgress = (attemptStartDistSq - currDistSq) >= PARKOUR_MIN_PROGRESS_SQ;
            boolean timeout = (now - attemptStartMs) >= PARKOUR_FAIL_TIMEOUT_MS;
            if (timeout && !madeProgress) {
                if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null) {
                    FuctorizeClient.INSTANCE.pathfindingManager.addTemporaryPenalty(node, STUCK_PENALTY_COST);
                }
                recalculatePath();
                return;
            }
        }
    }

    private PathNode getEffectiveTargetNode() {
        if (currentPath == null || currentNodeIndex >= currentPath.size()) {
            return null;
        }

        return currentPath.get(currentNodeIndex);
    }

    private Vec3 getLookAheadTarget() {
        if (currentPath == null || currentNodeIndex >= currentPath.size()) {
            return Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ);
        }

        int nodesLeft = currentPath.size() - currentNodeIndex;
        int lookAheadIndex;

        if (nodesLeft > 5) {
            lookAheadIndex = currentNodeIndex + 2;
        } else if (nodesLeft > 1) {
            lookAheadIndex = currentNodeIndex + 1;
        } else {
            lookAheadIndex = currentNodeIndex;
        }

        lookAheadIndex = Math.min(lookAheadIndex, currentPath.size() - 1);

        PathNode targetNode = currentPath.get(lookAheadIndex);

        return Vec3.createVectorHelper(
                targetNode.x + 0.5,
                targetNode.getFeetY(),
                targetNode.z + 0.5
        );
    }

    private void recalculatePath() {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        pathSegmentQueue.clear();
        if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null) {
            FuctorizeClient.INSTANCE.pathfindingManager.cancelSearch();
        }
        activeCalculations.set(0);

        requestNewPathSegment(true);
    }

    private boolean isPathHeadingToward(java.util.List<PathNode> path, double targetX, double targetY, double targetZ) {

        return true;
    }

    public void navigateTo(double x, double y, double z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.isNavigating = true;
        if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null) {
            FuctorizeClient.INSTANCE.pathfindingManager.clearTemporaryPenalties();
        }
        this.status = NavigationStatus.NAVIGATING;
        this.pathSegmentQueue.clear();
        this.traversedPathHistory.clear();
        this.activeCalculations.set(0);
        this.state = MovementState.IDLE;
        this.hasVisitedNodeCenter = false;
        resetStuckDetector();
        requestNewPathSegment(true);
    }

    private double getDistanceSq(EntityPlayer player, double x, double y, double z) {
        double dx = player.posX - x;
        double dy = player.boundingBox.minY - y;
        double dz = player.posZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void handleStuckAndPathEnd() {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        double finalDx = player.posX - targetX;
        double finalDy = player.boundingBox.minY - targetY;
        double finalDz = player.posZ - targetZ;
        double horizontalDistSq = finalDx * finalDx + finalDz * finalDz;
        double horizontalDist = Math.sqrt(horizontalDistSq);
        double verticalDist = Math.abs(finalDy);

        if (horizontalDistSq <= (ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD) && verticalDist <= 1.5) {
            System.out.println("Destination reached! Horizontal distance: " + String.format("%.2f", horizontalDist) +
                    ", Vertical distance: " + String.format("%.2f", verticalDist));
            status = NavigationStatus.SUCCESS;
            stop();
            return;
        }

        if (currentPath != null) {
            if (System.currentTimeMillis() - lastStuckCheckTime > QUICK_STUCK_TIMEOUT_MS) {
                double qdx = player.posX - stuckCheckX;
                double qdy = player.boundingBox.minY - stuckCheckY;
                double qdz = player.posZ - stuckCheckZ;
                double qdistSq = qdx * qdx + qdy * qdy + qdz * qdz;

                boolean centeringNode = false;
                if (!hasVisitedNodeCenter && currentPath != null && currentNodeIndex < currentPath.size()) {
                    PathNode node = currentPath.get(currentNodeIndex);
                    double ndx = player.posX - (node.x + 0.5);
                    double ndz = player.posZ - (node.z + 0.5);
                    double centerDistSq = ndx * ndx + ndz * ndz;
                    double relaxRadius = NODE_CENTER_THRESHOLD * 2.5;
                    centeringNode = centerDistSq <= (relaxRadius * relaxRadius);
                }

                // --- ИЗМЕНЕНИЕ: Добавлена проверка состояния BREAKING_BLOCK ---
                if (state == MovementState.BREAKING_BLOCK) {
                    resetStuckDetector(); // Не считаем застреванием, если мы в процессе копания
                } else if (qdistSq < QUICK_STUCK_DISTANCE_SQ) {
                    if (centeringNode) {
                        resetStuckDetector();
                    } else {
                        System.out.println("[STUCK] Quick no-move -> recalculating path");
                        resetStuckDetector();
                        recalculatePath();
                        return;
                    }
                } else if (qdistSq >= QUICK_STUCK_DISTANCE_SQ) {
                    resetStuckDetector();
                }
            }
        }

        if (currentPath != null && state == MovementState.WALKING) {
            if (System.currentTimeMillis() - lastStuckCheckTime > STUCK_TIMEOUT_MS) {
                double dx = player.posX - stuckCheckX;
                double dy = player.boundingBox.minY - stuckCheckY;
                double dz = player.posZ - stuckCheckZ;
                double distanceSq = dx * dx + dy * dy + dz * dz;

                if (distanceSq < STUCK_DISTANCE_SQ) {

                    System.out.println("STUCK DETECTED! Penalizing area and forcing full recalculation...");

                    int stuckNodeX = MathHelper.floor_double(player.posX);
                    int stuckNodeY = YMath.groundFromPlayerPosY(player.boundingBox.minY);
                    int stuckNodeZ = MathHelper.floor_double(player.posZ);

                    for (int dx_offset = -STUCK_PENALTY_RADIUS; dx_offset <= STUCK_PENALTY_RADIUS; dx_offset++) {
                        for (int dz_offset = -STUCK_PENALTY_RADIUS; dz_offset <= STUCK_PENALTY_RADIUS; dz_offset++) {
                            PathNode penaltyNode = new PathNode(stuckNodeX + dx_offset, stuckNodeY, stuckNodeZ + dz_offset);
                            if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.pathfindingManager != null) {
                                FuctorizeClient.INSTANCE.pathfindingManager.addTemporaryPenalty(penaltyNode, STUCK_PENALTY_COST);
                            }
                        }
                    }

                    recalculatePath();
                    return;
                } else {
                    resetStuckDetector();
                }
            }
        }

        if (currentPath == null || currentNodeIndex >= currentPath.size()) {
            if (!pathSegmentQueue.isEmpty()) {
                currentPath = pathSegmentQueue.poll();
                setNodeIndex(0);
                checkAndSkipStartNode();
                resetStuckDetector();
                return;
            }

            if (currentPath != null && !currentPath.isEmpty()) {
                PathNode lastPathNode = currentPath.get(currentPath.size() - 1);
                double lastNodeDx = (lastPathNode.x + 0.5) - targetX;
                double lastNodeDy = lastPathNode.getFeetY() - targetY;
                double lastNodeDz = (lastPathNode.z + 0.5) - targetZ;
                double lastNodeHorizontalDistSq = lastNodeDx * lastNodeDx + lastNodeDz * lastNodeDz;
                double lastNodeHorizontalDist = Math.sqrt(lastNodeHorizontalDistSq);
                double lastNodeVerticalDist = Math.abs(lastNodeDy);

                double playerToLastNodeDx = player.posX - (lastPathNode.x + 0.5);
                double playerToLastNodeDz = player.posZ - (lastPathNode.z + 0.5);
                double playerToLastNodeDistSq = playerToLastNodeDx * playerToLastNodeDx + playerToLastNodeDz * playerToLastNodeDz;

                if (lastNodeHorizontalDist <= 3.0 && lastNodeVerticalDist <= 2.0 && playerToLastNodeDistSq <= 4.0) {
                    System.out.println("Path completed! Last node is close to destination (" +
                            String.format("%.2f", lastNodeHorizontalDist) + " blocks away). " +
                            "Player is " + String.format("%.2f", Math.sqrt(playerToLastNodeDistSq)) +
                            " blocks from last node. Completing navigation.");
                    status = NavigationStatus.SUCCESS;
                    stop();
                    return;
                }
            }

            if (horizontalDist <= 4.0 && verticalDist <= 2.0) {

                System.out.println("Very close to destination (" + String.format("%.2f", horizontalDist) +
                        " blocks horizontal, " + String.format("%.2f", verticalDist) + " blocks vertical). " +
                        "Path ended. Completing navigation.");
                status = NavigationStatus.SUCCESS;
                stop();
                return;
            }

            if (activeCalculations.get() == 0) {
                requestNewPathSegment(false);
            }
        }
    }

    public void renderPath(float alpha) {
        try {
            PathRenderer.render(mc.thePlayer, traversedPathHistory, currentPath, currentNodeIndex, isNavigating, alpha);
        } catch (Throwable ignored) {
        }
    }

    public double getTargetX() { return targetX; }
    public double getTargetY() { return targetY; }
    public double getTargetZ() { return targetZ; }
    private Color interpolateColor(Color start, Color end, float progress) {
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        int r = (int) (start.getRed() + (end.getRed() - start.getRed()) * progress);
        int g = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * progress);
        int b = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * progress);

        return new Color(r, g, b);
    }

    public boolean isNavigating() {
        return isNavigating;
    }

    public List<PathNode> getCurrentPath() {
        return currentPath;
    }

    public int getCurrentNodeIndex() {
        return currentNodeIndex;
    }

    private boolean hasLowCeilingAt(World world, double x, double yFeet, double z) {
        if (world == null) return false;
        int bx = MathHelper.floor_double(x);
        int feetBlockY = MathHelper.floor_double(yFeet);
        int headY = feetBlockY + 1;
        int capY = feetBlockY + 2;
        int bz = MathHelper.floor_double(z);

        boolean headBlocked = !PathfindingUtils.isBlockPassable(world, bx, headY, bz);
        boolean capBlocked = !PathfindingUtils.isBlockPassable(world, bx, capY, bz);

        return headBlocked || capBlocked;
    }

    private boolean ensureHeadroomForAscendSafe() {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return true;
        final World w = mc.theWorld;
        final EntityPlayer p = mc.thePlayer;

        int pbx = MathHelper.floor_double(p.posX);
        int pbz = MathHelper.floor_double(p.posZ);
        int groundY = YMath.groundFromPlayerPosY(p.boundingBox.minY);
        int headY = YMath.headFromGround(groundY);
        int capY = headY + 1;

        if (!PathfindingUtils.isBlockPassable(w, pbx, headY, pbz)) {
            state = MovementState.BREAKING_BLOCK;
            actionTargetX = pbx;
            actionTargetY = headY;
            actionTargetZ = pbz;
            return false;
        }
        if (!PathfindingUtils.isBlockPassable(w, pbx, capY, pbz)) {
            state = MovementState.BREAKING_BLOCK;
            actionTargetX = pbx;
            actionTargetY = capY;
            actionTargetZ = pbz;
            return false;
        }
        return true;
    }

    private boolean hasBlocksInInventory() {
        EntityPlayer player = mc.thePlayer;
        if (player == null || player.inventory == null) {
            return false;
        }

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                ItemBlock itemBlock = (ItemBlock) stack.getItem();
                Block block = itemBlock.field_150939_a;

                if (block != null && block.isOpaqueCube() && stack.stackSize > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private void checkInventoryAndAdapt() {
        if (!isNavigating || currentPath == null) {
            return;
        }

        boolean hasBlocksNow = hasBlocksInInventory();

        if (!hadBlocksLastCheck && hasBlocksNow) {
            System.out.println("[ADAPTIVE] Blocks appeared in inventory! Recalculating path to use Pillar/Bridge...");

            recalculatePath();
        } else if (hadBlocksLastCheck && !hasBlocksNow) {
            System.out.println("[ADAPTIVE] Blocks depleted! Recalculating path without Pillar/Bridge...");

            requestNewPathSegment(false);
        }

        hadBlocksLastCheck = hasBlocksNow;
    }

    private void checkAndSkipStartNode() {
        if (currentPath != null && currentPath.size() > 1 && currentNodeIndex == 0) {
            PathNode first = currentPath.get(0);
            if (mc.thePlayer != null &&
                    MathHelper.floor_double(mc.thePlayer.posX) == first.x &&
                    MathHelper.floor_double(mc.thePlayer.posZ) == first.z &&
                    Math.abs(mc.thePlayer.boundingBox.minY - first.getFeetY()) < 1.0) {
                advanceNodeIndex();
                System.out.println("[BotNavigator] Skipped start node (already there).");
            }
        }
    }
}