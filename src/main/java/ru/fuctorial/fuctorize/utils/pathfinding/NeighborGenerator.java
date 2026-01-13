package ru.fuctorial.fuctorize.utils.pathfinding;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;
import ru.fuctorial.fuctorize.utils.pathfinding.YMath;

/**
 * Generates valid neighboring nodes for the A* pathfinder.
 * FIXED: Added Head Clearance check for Descend to prevent getting stuck on lintels.
 */
final class NeighborGenerator {
    private static final boolean DEBUG_LOGS = false;
    private final World world;
    private final EntityPlayer player;
    private final double maxDropHeight;
    private final boolean allowDiagonal;

    private static final int[][] CARDINAL = { {1,0}, {-1,0}, {0,1}, {0,-1} };
    private static final int[][] DIAGONAL = { {1,1}, {1,-1}, {-1,1}, {-1,-1} };

    NeighborGenerator(World world, EntityPlayer player, boolean allowDiagonal, double maxDropHeight) {
        this.world = world;
        this.player = player;
        this.allowDiagonal = allowDiagonal;
        this.maxDropHeight = maxDropHeight;
    }

    List<PathNode> generateNeighbors(PathNode current) {
        ArrayList<PathNode> neighbors = new ArrayList<>();

        addCardinalMoves(current, neighbors);
        if (allowDiagonal) {
            addDiagonalMoves(current, neighbors);
        }
        addPillar(current, neighbors);
        addBridge(current, neighbors);
        addDigging(current, neighbors);

        return neighbors;
    }

    private void addCardinalMoves(PathNode current, List<PathNode> neighbors) {
        for (int[] dir : CARDINAL) {
            attemptMove(current, dir[0], dir[1], neighbors);
        }
    }

    private void addDiagonalMoves(PathNode current, List<PathNode> neighbors) {
        for (int[] dir : DIAGONAL) {
            int feetY = YMath.feetFromGround(current.y);
            // Для диагонали проверяем, чтобы не срезать углы через стены
            if (!isPassable(current.x + dir[0], feetY, current.z) && !isPassable(current.x, feetY, current.z + dir[1])) {
                continue;
            }
            attemptMove(current, dir[0], dir[1], neighbors);
        }
    }

    private void attemptMove(PathNode current, int dx, int dz, List<PathNode> neighbors) {
        int nx = current.x + dx;
        int nz = current.z + dz;
        int baseY = current.y; // Опорный блок (пол)

        boolean diagonal = (dx != 0 && dz != 0);

        // --- 1. ASCEND (Подъем) ---
        int ascendY = baseY + 1;
        if (isStandSafe(nx, ascendY, nz)) {
            // Проверяем, что пространство ПЕРЕД нами на уровне головы свободно (чтобы не прыгнуть в стену)
            if (isPassable(nx, YMath.headFromGround(baseY), nz)) {
                // И что над новой точкой есть место для головы
                if (isPassable(nx, YMath.headFromGround(ascendY), nz)) {
                    addNode(nx, ascendY, nz, diagonal ? MovementType.DIAGONAL_ASCEND : MovementType.ASCEND, neighbors, true);
                }
            }
        }

        // --- 2. TRAVERSE (Идти прямо) ---
        boolean addedTraverse = false;
        // Проверка: Можем стоять в новой точке + Проходим на уровне ног + !!! Проходим на уровне головы !!!
        // Если перед лицом блок, мы не можем пройти, даже если под ногами пол.
        boolean headClear = isPassable(nx, YMath.headFromGround(baseY), nz);
        boolean feetClear = isPassable(nx, YMath.feetFromGround(baseY), nz);

        if (isStandSafe(nx, baseY, nz) && feetClear && headClear) {
            addNode(nx, baseY, nz, MovementType.TRAVERSE, neighbors);
            addedTraverse = true;
        }

        // --- 3. DIG (Копание прохода прямо) ---
        // Если нельзя пройти (мешает стена), но пол есть - добавляем узел с копанием
        if (!addedTraverse && isSolid(nx, baseY, nz)) { // isSolid проверяет пол, но нам надо проверить стены
            // Проверяем, что мешает именно стена (feet или head), а пол при этом нормальный
            if (PathfindingUtils.canStandOn(world, nx, baseY, nz)) {
                int breaks = 0;
                boolean canBreak = true;

                if (!isPassable(nx, YMath.feetFromGround(baseY), nz)) {
                    if (isBreakable(nx, YMath.feetFromGround(baseY), nz)) breaks++; else canBreak = false;
                }
                if (!isPassable(nx, YMath.headFromGround(baseY), nz)) {
                    if (isBreakable(nx, YMath.headFromGround(baseY), nz)) breaks++; else canBreak = false;
                }

                if (canBreak && breaks > 0) {
                    addNode(nx, baseY, nz, MovementType.DIG, neighbors, 0, breaks, false);
                }
            }
        }

        // --- 4. DESCEND / FALL (Спуск) ---
        boolean canTraverseOrAscend = false;
        for(PathNode node : neighbors) {
            if (node.x == nx && node.z == nz && node.y >= baseY) { // Если уже нашли путь прямо или вверх
                canTraverseOrAscend = true;
                break;
            }
        }

        if (!canTraverseOrAscend) {
            // !!! ВАЖНЕЙШЕЕ ИСПРАВЛЕНИЕ !!!
            // Перед тем как пытаться спрыгнуть, проверяем, не ударимся ли мы головой о притолоку.
            // Блок: nx, headY, nz (прямо перед лицом) должен быть ПРОХОДИМ.
            if (!isPassable(nx, YMath.headFromGround(baseY), nz)) {
                // Если перед лицом блок, мы не можем шагуть в пропасть.
                // Но мы можем попробовать ЕГО СЛОМАТЬ (Dig to Descend logic)
                if (isBreakable(nx, YMath.headFromGround(baseY), nz)) {
                    // Генерируем DESCEND но с флагом breakBlocks? 
                    // Нет, A* ожидает breakBlocks только в целевом узле.
                    // Поэтому мы добавляем узел DIG на текущей высоте (baseY), 
                    // чтобы сломать преграду, а потом уже в следующем шаге A* найдет спуск.
                    // Это хитро: мы говорим "иди в nx,nz,baseY" (хотя там воздух под ногами?), нет.

                    // Решение: Просто блокируем спуск. Если нужно копать, сработает addDigging (который ниже).
                    // Бот поймет, что спуск невозможен, и либо найдет другой путь, либо addDigStaircase поможет.
                    return;
                } else {
                    return; // Неразрушимый блок перед лицом, спуск невозможен.
                }
            }

            for (int drop = 1; drop <= maxDropHeight; drop++) {
                int descendY = baseY - drop;
                if (descendY < 0) break;

                // Проверяем колонну падения
                boolean pathClear = true;
                for (int y = baseY; y > descendY; y--) {
                    // Проверяем, что ноги не застрянут при падении
                    if (!isPassable(nx, YMath.feetFromGround(y), nz)) {
                        pathClear = false; break;
                    }
                    // Проверяем голову при падении
                    if (!isPassable(nx, YMath.headFromGround(y), nz)) {
                        pathClear = false; break;
                    }
                }

                if (pathClear && isStandSafe(nx, descendY, nz)) {
                    if (player != null && PathfindingUtils.estimateFallDamage(drop) >= player.getHealth()) {
                        continue;
                    }
                    addNode(nx, descendY, nz, diagonal ? MovementType.DIAGONAL_DESCEND : MovementType.DESCEND, neighbors);
                    break; // Нашли ближайший пол внизу
                }
            }
        }
    }

    private void addPillar(PathNode current, List<PathNode> neighbors) {
        if (!isSolid(current.x, current.y, current.z)) return;

        int x = current.x, y = current.y, z = current.z;
        int upY = y + 1;
        int newFeet = YMath.feetFromGround(upY);
        int newHead = YMath.headFromGround(upY);
        if (isPassable(x, newFeet, z) && isPassable(x, newHead, z)) {
            addNode(x, upY, z, MovementType.PILLAR, neighbors, 1, 0, true);
        }
    }

    private void addBridge(PathNode current, List<PathNode> neighbors) {
        if (!isSolid(current.x, current.y, current.z)) return;

        for (int[] dir : CARDINAL) {
            int nx = current.x + dir[0];
            int nz = current.z + dir[1];
            int placeY = current.y;

            if (isPassable(nx, placeY, nz) && isPassable(nx, YMath.feetFromGround(placeY), nz) && isPassable(nx, YMath.headFromGround(placeY), nz)) {
                addNode(nx, placeY, nz, MovementType.BRIDGE, neighbors, 1, 0, false);
            }
        }
    }

    private void addDigging(PathNode current, List<PathNode> neighbors) {
        // 1. Копаем ВВЕРХ (Dig Up / Staircase Up)
        if (isSolid(current.x, current.y, current.z)) {
            int targetY = current.y + 1;
            int feetY = YMath.feetFromGround(targetY);
            int headY = YMath.headFromGround(targetY);

            int breakCount = 0;
            if (!isPassable(current.x, feetY, current.z)) {
                if (isBreakable(current.x, feetY, current.z)) breakCount++; else return;
            }
            if (!isPassable(current.x, headY, current.z)) {
                if (isBreakable(current.x, headY, current.z)) breakCount++; else return;
            }

            if (breakCount > 0) {
                addNode(current.x, targetY, current.z, MovementType.DIG, neighbors, 0, breakCount, true);
            }
        }

        // 2. Копаем ВНИЗ (Dig Down)
        addDownwardDig(current, neighbors);

        // 3. Копаем ПРЕГРАДУ перед лицом для спуска (Fix for "Head blocked descend")
        for (int[] dir : CARDINAL) {
            int nx = current.x + dir[0];
            int nz = current.z + dir[1];
            int baseY = current.y;

            // Если перед лицом блок, и мы хотим туда пойти (подразумевается спуск или проход)
            int headY = YMath.headFromGround(baseY);
            if (!isPassable(nx, headY, nz)) {
                if (isBreakable(nx, headY, nz)) {
                    // Если за этим блоком пропасть или пол - не важно,
                    // мы создаем узел на ТЕКУЩЕМ уровне (baseY), но с breakBlocks > 0.
                    // Это заставит бота подойти, сломать блок перед лицом.
                    // А в следующем пересчете (или шаге) он увидит, что можно сделать DESCEND.

                    // Однако, PathNode подразумевает, что мы СТАНОВИМСЯ на этот узел.
                    // Если мы сломаем блок перед лицом, мы сможем встать на nx, baseY, nz?
                    // Только если там есть пол.
                    if (PathfindingUtils.canStandOn(world, nx, baseY, nz)) {
                        // Это обычный DIG traverse, уже обработан в attemptMove.
                    } else {
                        // Там пропасть (спуск). Мы не можем встать на nx, baseY, nz.
                        // Нам нужно сломать блок, чтобы потом сделать DESCEND.
                        // В рамках A* это сложно выразить одним узлом, если A* не поддерживает "действие без перемещения".
                        // НО! Мы можем добавить узел DESCEND с флагом breakBlocks для блока, который МЕШАЕТ СПУСКУ.

                        // Ищем пол внизу
                        for (int drop = 1; drop <= maxDropHeight; drop++) {
                            int descendY = baseY - drop;
                            if (isStandSafe(nx, descendY, nz)) {
                                // Нашли куда упасть.
                                // Создаем узел падения, но добавляем стоимость поломки блока перед лицом.
                                PathNode dropNode = new PathNode(nx, descendY, nz);
                                dropNode.moveType = MovementType.DIG; // Или DESCEND c breakBlocks
                                dropNode.breakBlocks = 1; // Ломаем блок перед лицом наверху
                                // ВАЖНО: Бот должен понять, КАКОЙ блок ломать.
                                // В BotNavigator.updateState мы добавили логику getOccludingBlock.
                                // Если мы укажем этот узел, бот попытается ломать цель. Цель внизу.
                                // Но getOccludingBlock увидит стену перед лицом и начнет ломать её!
                                // Так что это должно сработать.
                                neighbors.add(dropNode);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void addDownwardDig(PathNode current, List<PathNode> neighbors) {
        if (!isSolid(current.x, current.y, current.z)) return;

        int targetY = current.y - 1;
        if (targetY < 0) return;
        if (!PathfindingUtils.canStandOn(world, current.x, targetY, current.z)) return;

        int breakCount = 0;
        int feetY = YMath.feetFromGround(targetY); // Текущий пол, который станет ногами

        // Нам нужно сломать блок под ногами (current.y)
        // В координатах targetY это блок headY (targetY + 2 = current.y + 1)? Нет.
        // current.y = 64. Block under feet is 64.
        // targetY = 63. Feet at 64. Head at 65.
        // Чтобы встать на 63, нужно чтобы 64 и 65 были свободны.
        // 64 сейчас занят блоком, на котором мы стоим.

        if (!isPassable(current.x, feetY, current.z)) {
            if (isBreakable(current.x, feetY, current.z)) breakCount++; else return;
        }
        // Проверяем голову (может там тоже что-то есть, хотя мы там стояли)
        int headY = YMath.headFromGround(targetY);
        if (!isPassable(current.x, headY, current.z)) {
            if (isBreakable(current.x, headY, current.z)) breakCount++; else return;
        }

        if (breakCount > 0 && !PathfindingUtils.isDangerousToStand(world, current.x, feetY, current.z)) {
            addNode(current.x, targetY, current.z, MovementType.DIG, neighbors, 0, breakCount, false);
        }
    }

    // --- Helper Methods ---

    private boolean isPassable(int x, int y, int z) {
        return PathfindingUtils.isBlockPassable(world, x, y, z);
    }

    private boolean isSolid(int x, int y, int z) { // "Solid" в контексте "можно оттолкнуться" (пол)
        return PathfindingUtils.canStandOn(world, x, y, z);
    }

    private boolean isBreakable(int x, int y, int z) {
        if(isPassable(x,y,z)) return true;
        Block block = world.getBlock(x, y, z);
        return block != null && block.getBlockHardness(world, x, y, z) >= 0;
    }

    private boolean isStandSafe(int x, int y, int z) {
        boolean canStand = PathfindingUtils.canStandAt(world, x + 0.5, YMath.feetFromGround(y) + 0.01, z + 0.5);
        if (!canStand) return false;
        return !PathfindingUtils.isDangerousToStand(world, x, YMath.feetFromGround(y), z);
    }

    private void addNode(int x, int y, int z, MovementType type, List<PathNode> neighbors, int place, int brake, boolean needsJump) {
        PathNode node = new PathNode(x, y, z);
        node.moveType = type;
        node.placeBlocks = place;
        node.breakBlocks = brake;
        node.needsJump = needsJump;
        neighbors.add(node);
    }
    private void addNode(int x, int y, int z, MovementType type, List<PathNode> neighbors, boolean needsJump) {
        addNode(x, y, z, type, neighbors, 0, 0, needsJump);
    }
    private void addNode(int x, int y, int z, MovementType type, List<PathNode> neighbors) {
        addNode(x, y, z, type, neighbors, 0, 0, false);
    }
}