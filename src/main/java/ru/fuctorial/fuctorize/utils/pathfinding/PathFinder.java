package ru.fuctorial.fuctorize.utils.pathfinding;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import ru.fuctorial.fuctorize.utils.pathfinding.YMath;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Supplier;

public class PathFinder {

    private static final int[][] CARDINAL = { {1,0}, {-1,0}, {0,1}, {0,-1} };
    private static final int[][] DIAGONAL = { {1,1}, {1,-1}, {-1,1}, {-1,-1} };

    private final World world;
    private final EntityPlayer player;
    private final double maxClimbHeight;
    private final double maxDropHeight;
    private final int maxSearchNodes;
    private final boolean allowDiagonal;
    private final boolean useEuclideanHeuristic;
    private final Map<PathNode, Double> temporaryPenalties;
    private final int primaryTimeoutMs;
    private final int failureTimeoutMs;
    private final int edgeCounterThreshold;

    private final double heuristicWeight;
    private final SimpleBlockCache blockCache;
    private final NeighborGenerator neighborGenerator;
    private static final int MAX_FORCED_DIG_DEPTH = 8;

    public PathFinder(World world,
                      EntityPlayer player,
                      double maxClimbHeight,
                      double maxDropHeight,
                      int maxSearchNodes,
                      boolean allowDiagonal,
                      boolean useEuclideanHeuristic,
                      int primaryTimeoutMs,
                      int failureTimeoutMs,
                      int edgeCounterThreshold,
                      double heuristicWeight,
                      Map<PathNode, Double> temporaryPenalties) {
        this.world = world;
        this.player = player;
        this.maxClimbHeight = maxClimbHeight;
        this.maxDropHeight = maxDropHeight;
        this.maxSearchNodes = maxSearchNodes;
        this.allowDiagonal = allowDiagonal;
        this.useEuclideanHeuristic = useEuclideanHeuristic;
        this.primaryTimeoutMs = primaryTimeoutMs;
        this.failureTimeoutMs = failureTimeoutMs;
        this.edgeCounterThreshold = edgeCounterThreshold;
        this.heuristicWeight = heuristicWeight;
        this.blockCache = new SimpleBlockCache(world);
        this.temporaryPenalties = temporaryPenalties;
        this.neighborGenerator = new NeighborGenerator(world, player, allowDiagonal, maxDropHeight);
    }

    public World getWorld() { return world; }
    public double getMaxClimbHeight() { return maxClimbHeight; }
    public double getMaxDropHeight() { return maxDropHeight; }
    public int getMaxSearchNodes() { return maxSearchNodes; }

    public PathResult findPath(double startX, double startY, double startZ,
                               double targetX, double targetY, double targetZ) {

        return findPath(startX, startY, startZ, targetX, targetY, targetZ, () -> false);
    }

    public PathResult findPath(PathNode startNode, PathNode targetNode) {
        return findPath(startNode, targetNode, () -> false);
    }

    public PathResult findPath(double startX, double startY, double startZ,
                               double targetX, double targetY, double targetZ,
                               Supplier<Boolean> cancellationChecker)  {
        if (world == null) return PathResult.failure("РњРёСЂ РЅРµ Р·Р°РіСЂСѓР¶РµРЅ", 0);

        // ========== РќРђР§РђР›Рћ РРЎРџР РђР’Р›Р•РќРР™ ==========

        // 1. РћРїСЂРµРґРµР»СЏРµРј СЃС‚Р°СЂС‚РѕРІС‹Р№ РѕРїРѕСЂРЅС‹Р№ Р±Р»РѕРє РџР РћРЎРўРћ РїРѕРґ РЅРѕРіР°РјРё РёРіСЂРѕРєР°.
        int sx = MathHelper.floor_double(startX);
        double startFeetY;
        if (player != null && player.boundingBox != null) {
            startFeetY = player.boundingBox.minY;
        } else {
            startFeetY = startY - 1.625;
        }
        int sy = MathHelper.floor_double(startFeetY - 0.01);
        int sz = MathHelper.floor_double(startZ);

        // РџСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ РїРѕРґ РЅРѕРіР°РјРё РІРѕРѕР±С‰Рµ РµСЃС‚СЊ РЅР° С‡РµРј СЃС‚РѕСЏС‚СЊ. Р•СЃР»Рё РЅРµС‚, РёС‰РµРј Р±Р»РёР¶Р°Р№С€СѓСЋ РѕРїРѕСЂСѓ РІРЅРёР·Сѓ.
        // Р­С‚Рѕ РЅСѓР¶РЅРѕ РґР»СЏ СЃР»СѓС‡Р°РµРІ, РєРѕРіРґР° РёРіСЂРѕРє РЅР°С…РѕРґРёС‚СЃСЏ РІ РїРѕР»РµС‚Рµ/РїР°РґРµРЅРёРё.
        while(!PathfindingUtils.canStandOn(world, sx, sy, sz) && sy > 0) {
            sy--;
        }

        // Р•СЃР»Рё СѓРїР°Р»Рё РґРѕ СЃР°РјРѕРіРѕ РЅРёР·Р° Рё РЅРёС‡РµРіРѕ РЅРµ РЅР°С€Р»Рё, Р·РЅР°С‡РёС‚ СЃС‚Р°СЂС‚РѕРІР°С‚СЊ РЅРµРіРґРµ.
        if (sy <= 0 && !PathfindingUtils.canStandOn(world, sx, 0, sz)) {
            return PathResult.failure("РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°Р№С‚Рё РѕРїРѕСЂСѓ РїРѕРґ СЃС‚Р°СЂС‚РѕРІРѕР№ С‚РѕС‡РєРѕР№", 0);
        }

        // 2. Р”Р»СЏ Р¦Р•Р›Р РёСЃРїРѕР»СЊР·СѓРµРј Р±РѕР»РµРµ СЃР»РѕР¶РЅСѓСЋ Р»РѕРіРёРєСѓ, С‚Р°Рє РєР°Рє РѕРЅР° РјРѕР¶РµС‚ Р±С‹С‚СЊ РІ РІРѕР·РґСѓС…Рµ.
        int tx = MathHelper.floor_double(targetX);
        int ty = MathHelper.floor_double(targetY);
        int tz = MathHelper.floor_double(targetZ);

        // РС‰РµРј РІР°Р»РёРґРЅСѓСЋ С‚РѕС‡РєСѓ РґР»СЏ СЃС‚РѕСЏРЅРёСЏ Р’ Р РђР™РћРќР• С†РµР»Рё, Р° РЅРµ С‚РѕС‡РЅРѕ РІ РЅРµР№.
        Integer bestTargetY = PathfindingUtils.findStandingYAllowingDown(world, tx, ty, tz, maxClimbHeight, maxDropHeight);
        if (bestTargetY == null) {
            // Р•СЃР»Рё С‚РѕС‡РЅРѕ РІ С†РµР»Рё СЃС‚РѕСЏС‚СЊ РЅРµР»СЊР·СЏ, РјРѕР¶РЅРѕ РїРѕРїСЂРѕР±РѕРІР°С‚СЊ РЅР°Р№С‚Рё Р±Р»РёР¶Р°Р№С€СѓСЋ С‚РѕС‡РєСѓ (СЌС‚Р° Р»РѕРіРёРєР° Сѓ С‚РµР±СЏ СѓР¶Рµ РµСЃС‚СЊ РІ PathPlanner)
            return PathResult.failure("Р¦РµР»РµРІР°СЏ С‚РѕС‡РєР° РЅРµРґРѕСЃС‚РёР¶РёРјР°", 0);
        }
        int desiredTargetY = ty;
        ty = bestTargetY;
        if (desiredTargetY < bestTargetY && canDigDownToTarget(tx, tz, bestTargetY, desiredTargetY)) {
            ty = desiredTargetY;
            System.out.println("[PathFinder] Target column blocked. Allowing dig-down path to Y=" + YMath.feetFromGround(ty));
        }

        // 3. РЎРѕР·РґР°РµРј СѓР·Р»С‹ СЃ РєРѕСЂСЂРµРєС‚РЅРѕР№ 'y'
        PathNode startPathNode = new PathNode(sx, sy, sz);
        startPathNode.moveType = MovementType.TRAVERSE;
        PathNode targetPathNode = new PathNode(tx, ty, tz);
        targetPathNode.moveType = MovementType.TRAVERSE;

        System.out.println("[PathFinder] Corrected Start Node: (" + sx + ", " + YMath.feetFromGround(sy) + ", " + sz + ")"); // Р›РѕРіРёСЂСѓРµРј Y РЅРѕРі
        System.out.println("[PathFinder] Corrected Target Node: (" + tx + ", " + YMath.feetFromGround(ty) + ", " + tz + ")"); // Р›РѕРіРёСЂСѓРµРј Y РЅРѕРі

        return findPath(startPathNode, targetPathNode, cancellationChecker);
        // ========== РљРћРќР•Р¦ РРЎРџР РђР’Р›Р•РќРР™ ==========
    }

    private PathResult findPath(PathNode startNode, PathNode targetNode,
                                Supplier<Boolean> cancellationChecker) {
        if (world == null) return PathResult.failure("Р В РЎС™Р В РЎвЂР РЋР вЂљ Р В Р вЂ¦Р В Р’Вµ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦", 0);
        if (startNode.equals(targetNode)) {
            ArrayList<PathNode> path = new ArrayList<PathNode>();
            path.add(startNode);
            return PathResult.success(path, 1);
        }

        final long startTime = System.nanoTime();
        final long primaryDeadline = startTime + (long)primaryTimeoutMs * 1_000_000L;
        final long failureDeadline = startTime + (long)failureTimeoutMs * 1_000_000L;

        PriorityQueue<PathNode> openSet = new PriorityQueue<PathNode>();
        HashSet<PathNode> closedSet = new HashSet<PathNode>();
        HashMap<PathNode, PathNode> nodeMap = new HashMap<PathNode, PathNode>();

        final double[] backoffCoeffs = new double[] {1.0, 1.25, 1.5, 2.0, 3.0};
        final PathNode[] bestByCoeff = new PathNode[backoffCoeffs.length];

        startNode.gCost = 0.0;
        startNode.hCost = admissibleHeuristic(startNode, targetNode) * 1.001;
        startNode.updateFCost();
        startNode.steps = 0;
        startNode.parent = null;
        openSet.add(startNode);
        nodeMap.put(startNode, startNode);

        int nodesExplored = 0;
        int edgeCounter = 0;

        while (!openSet.isEmpty() && nodesExplored < maxSearchNodes) {

            if (cancellationChecker.get()) {
                return PathResult.failure("Р В РЎСџР В РЎвЂўР В РЎвЂР РЋР С“Р В РЎвЂќ Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’ВµР В Р вЂ¦", nodesExplored);
            }

            final long now = System.nanoTime();
            if (now >= failureDeadline) {
                // ВРЕМЯ ВЫШЛО: Возвращаем лучший путь, который успели найти
                List<PathNode> partial = bestPartial(bestByCoeff);
                return (partial != null && !partial.isEmpty())
                        ? PathResult.partial(partial, nodesExplored) // <-- ВОЗВРАЩАЕМ PARTIAL
                        : PathResult.failure("Time limit exceeded", nodesExplored);
            }

            PathNode current = openSet.poll();
            if (current == null) break;

            if (isAtLoadedChunkEdge(current)) {
                edgeCounter++;
                if (edgeCounter > edgeCounterThreshold) {
                    List<PathNode> partial = bestPartial(bestByCoeff);
                    if (partial != null && !partial.isEmpty()) {
                        return PathResult.partial(partial, nodesExplored); // <-- Вернуть то, что есть
                    }

                    return PathResult.failure("Path not found (exhausted)", nodesExplored);
                }
            }

            if (current.equals(targetNode)) {
                List<PathNode> path = reconstruct(current);
                return PathResult.success(path, nodesExplored);
            }

            closedSet.add(current);
            nodesExplored++;

            if (current.steps >= PathingConfig.MIN_SEGMENT_BLOCKS) {
                for (int i = 0; i < backoffCoeffs.length; i++) {
                    double coeff = backoffCoeffs[i];
                    double score = current.gCost + coeff * admissibleHeuristic(current, targetNode);
                    PathNode best = bestByCoeff[i];
                    if (best == null) {
                        bestByCoeff[i] = current;
                    } else {
                        double bestScore = best.gCost + coeff * admissibleHeuristic(best, targetNode);
                        if (score < bestScore) {
                            bestByCoeff[i] = current;
                        }
                    }
                }
            }

            for (PathNode neighbor : generateNeighbors(current)) {
                if (closedSet.contains(neighbor)) continue;
                PathNode existing = nodeMap.get(neighbor);
                double moveCost = MovementCost.compute(world, player, blockCache, temporaryPenalties, current, neighbor);
                double tentativeG = current.gCost + moveCost;

                if (existing == null) {
                    neighbor.gCost = tentativeG;
                    neighbor.hCost = admissibleHeuristic(neighbor, targetNode);
                    neighbor.updateFCost();
                    neighbor.parent = current;
                    neighbor.steps = current.steps + 1;
                    openSet.add(neighbor);
                    nodeMap.put(neighbor, neighbor);
                } else if (existing.gCost - tentativeG > PathingConfig.MIN_IMPROVEMENT_EPS) {
                    openSet.remove(existing);
                    existing.gCost = tentativeG;
                    existing.hCost = admissibleHeuristic(existing, targetNode);
                    existing.updateFCost();
                    existing.parent = current;
                    existing.steps = current.steps + 1;
                    openSet.add(existing);
                }
            }

            if (now >= primaryDeadline) {
                List<PathNode> partial = bestPartial(bestByCoeff);
                if (partial != null && !partial.isEmpty()) {
                    return PathResult.partial(partial, nodesExplored);
                }
            }
        }

        return PathResult.failure("Р В РЎСџР РЋРЎвЂњР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В РІвЂћвЂ“Р В РўвЂР В Р’ВµР В Р вЂ¦ (Р В Р’В»Р В РЎвЂР В РЎВР В РЎвЂР РЋРІР‚С™ Р РЋРЎвЂњР В Р’В·Р В Р’В»Р В РЎвЂўР В Р вЂ /Р РЋРІР‚С™Р В Р’В°Р В РІвЂћвЂ“Р В РЎВР В Р’В°Р РЋРЎвЂњР РЋРІР‚С™/Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РЎвЂ”Р РЋР РЏР РЋРІР‚С™Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂР РЋР РЏ)", nodesExplored);
    }

    // Angle helpers and limits moved to NeighborGenerator

    private List<PathNode> generateNeighbors(PathNode node) {
        return neighborGenerator.generateNeighbors(node);
    }

    // Angle helpers retained for legacy private methods still present below.
    private static double yawDeg(int dx, int dz) {
        if (dx == 0 && dz == 0) return Double.NaN;
        return Math.toDegrees(Math.atan2(dz, dx));
    }

    private static double pitchDeg(int dx, int dy, int dz) {
        double horiz = Math.sqrt(dx * dx + dz * dz);
        return Math.toDegrees(Math.atan2(dy, horiz));
    }

    private static double yawDiffDeg(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) return 0.0;
        double d = a - b;
        d = ((d + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return Math.abs(d);
    }

    private boolean withinAngleLimits(PathNode current, PathNode next) {
        if (!PathingConfig.LIMIT_TURN_ANGLE) return true;
        PathNode prev = current.parent;
        if (prev == null) return true;
        int pdx = current.x - prev.x;
        int pdy = current.y - prev.y;
        int pdz = current.z - prev.z;

        int ndx = next.x - current.x;
        int ndy = next.y - current.y;
        int ndz = next.z - current.z;

        double prevYaw = yawDeg(pdx, pdz);
        double nextYaw = yawDeg(ndx, ndz);
        double yawDelta = yawDiffDeg(prevYaw, nextYaw);
        if (yawDelta > PathingConfig.MAX_YAW_CHANGE_DEG) return false;

        double prevPitch = pitchDeg(pdx, pdy, pdz);
        double nextPitch = pitchDeg(ndx, ndy, ndz);
        double pitchDelta = Math.abs(prevPitch - nextPitch);
        if (pitchDelta > PathingConfig.MAX_PITCH_CHANGE_DEG) return false;
        return true;
    }

    private void addNeighborsParkour(PathNode cur, List<PathNode> out) {
        final int baseY = cur.y;

        for (int[] d : CARDINAL) {
            int overX = cur.x + d[0];
            int overZ = cur.z + d[1];
            int landX = cur.x + 2 * d[0];
            int landZ = cur.z + 2 * d[1];
            if (!world.blockExists(landX, baseY, landZ)) continue;
            if (!PathfindingUtils.isBlockPassable(world, overX, baseY, overZ)) continue;
            if (!PathfindingUtils.isBlockPassable(world, overX, YMath.feetFromGround(baseY), overZ)) continue;
            if (blockCache.get(overX, ru.fuctorial.fuctorize.utils.pathfinding.YMath.groundBelow(baseY), overZ) != PathfindingUtils.BlockClass.AIR) continue; if (!isStandSafe(landX, baseY, landZ)) continue;

            PathNode next = new PathNode(landX, baseY, landZ);
            next.moveType = MovementType.PARKOUR;
            next.needsJump = true;
            if (!withinAngleLimits(cur, next)) continue;
            double stepPitch = pitchDeg(next.x - cur.x, next.y - cur.y, next.z - cur.z);
            if (Math.abs(stepPitch) > PathingConfig.MAX_ABS_PITCH_DEG) continue;
            out.add(next);
        }

        for (int[] d : CARDINAL) {
            int over1X = cur.x + d[0];
            int over1Z = cur.z + d[1];
            int over2X = cur.x + 2 * d[0];
            int over2Z = cur.z + 2 * d[1];
            int landX = cur.x + 3 * d[0];
            int landZ = cur.z + 3 * d[1];
            if (!world.blockExists(landX, baseY, landZ)) continue;
            if (!PathfindingUtils.isBlockPassable(world, over1X, baseY, over1Z)) continue;
            if (!PathfindingUtils.isBlockPassable(world, over1X, YMath.feetFromGround(baseY), over1Z)) continue;
            if (!PathfindingUtils.isBlockPassable(world, over2X, baseY, over2Z)) continue;
            if (!PathfindingUtils.isBlockPassable(world, over2X, YMath.feetFromGround(baseY), over2Z)) continue;
            if (blockCache.get(over1X, ru.fuctorial.fuctorize.utils.pathfinding.YMath.groundBelow(baseY), over1Z) != PathfindingUtils.BlockClass.AIR) continue;
            if (blockCache.get(over2X, ru.fuctorial.fuctorize.utils.pathfinding.YMath.groundBelow(baseY), over2Z) != PathfindingUtils.BlockClass.AIR) continue;
            if (!isStandSafe(landX, baseY, landZ)) continue;
            PathNode next = new PathNode(landX, baseY, landZ);
            next.moveType = MovementType.PARKOUR;
            next.needsJump = true;
            if (!withinAngleLimits(cur, next)) continue;
            double stepPitch = pitchDeg(next.x - cur.x, next.y - cur.y, next.z - cur.z);
            if (Math.abs(stepPitch) > PathingConfig.MAX_ABS_PITCH_DEG) continue;
            out.add(next);
        }

        for (int[] d : CARDINAL) {

            {
                int overX = cur.x + d[0];
                int overZ = cur.z + d[1];
                int landX = cur.x + 2 * d[0];
                int landZ = cur.z + 2 * d[1];
                int landY = ru.fuctorial.fuctorize.utils.pathfinding.YMath.groundAbove(baseY);
                if (world.blockExists(landX, landY, landZ)
                        && PathfindingUtils.isBlockPassable(world, overX, baseY, overZ)
                        && PathfindingUtils.isBlockPassable(world, overX, YMath.feetFromGround(baseY), overZ)
                        && PathfindingUtils.isBlockPassable(world, landX, YMath.headFromGround(landY), landZ)
                        && isStandSafe(landX, landY, landZ)) {
                    PathNode next = new PathNode(landX, landY, landZ);
                    next.moveType = MovementType.PARKOUR;
                    next.needsJump = true;
                    if (withinAngleLimits(cur, next)) out.add(next);
                }
            }

            {
                int overX = cur.x + d[0];
                int overZ = cur.z + d[1];
                int landX = cur.x + 2 * d[0];
                int landZ = cur.z + 2 * d[1];
                int landY = ru.fuctorial.fuctorize.utils.pathfinding.YMath.groundBelow(baseY);
                if (landY >= 1 && world.blockExists(landX, landY, landZ)
                        && PathfindingUtils.isBlockPassable(world, overX, baseY, overZ)
                        && PathfindingUtils.isBlockPassable(world, overX, YMath.feetFromGround(baseY), overZ)
                        && isStandSafe(landX, landY, landZ)) {
                    PathNode next = new PathNode(landX, landY, landZ);
                    next.moveType = MovementType.PARKOUR;
                    next.needsJump = true;
                    if (withinAngleLimits(cur, next)) out.add(next);
                }
            }
        }

        for (int[] d : DIAGONAL) {
            int overX = cur.x + d[0];
            int overZ = cur.z + d[1];
            int landX = cur.x + 2 * d[0];
            int landZ = cur.z + 2 * d[1];
            if (!world.blockExists(landX, baseY, landZ)) continue;
            if (!PathfindingUtils.isBlockPassable(world, overX, baseY, overZ)) continue;
            if (!PathfindingUtils.isBlockPassable(world, overX, YMath.feetFromGround(baseY), overZ)) continue;
            if (blockCache.get(overX, YMath.groundBelow(baseY), overZ) != PathfindingUtils.BlockClass.AIR) continue;if (!isStandSafe(landX, baseY, landZ)) continue;
            PathNode next = new PathNode(landX, baseY, landZ);
            next.moveType = MovementType.PARKOUR;
            next.needsJump = true;
            if (!withinAngleLimits(cur, next)) continue;
            out.add(next);
        }
    }

    private void addNeighborsCardinal(PathNode cur, List<PathNode> out) {
        for (int[] d : CARDINAL) {
            PathNode n = attemptMove(cur, d[0], d[1]);
            if (n != null) out.add(n);
        }
    }

    private void addNeighborsDiagonal(PathNode cur, List<PathNode> out) {
        for (int[] d : DIAGONAL) {
            PathNode n = attemptMove(cur, d[0], d[1]);
            if (n != null) out.add(n);
        }
    }

    private void addNeighborsSwimming(PathNode cur, List<PathNode> out) {
        final int x = cur.x;
        final int y = cur.y;
        final int z = cur.z;

        if (!PathfindingUtils.isInWater(world, x, y, z)) {
            return;
        }

        Integer surfaceY = PathfindingUtils.findWaterSurface(world, x, y, z);
        int sy = (surfaceY != null) ? surfaceY : y;

        for (int[] d : CARDINAL) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (!world.blockExists(nx, sy, nz)) continue;
            if (PathfindingUtils.canSwimAt(world, nx, sy, nz)) {
                PathNode next = new PathNode(nx, sy, nz);
                next.moveType = MovementType.SWIM_SURFACE;
                out.add(next);
            }
        }
        if (allowDiagonal) {
            for (int[] d : DIAGONAL) {
                int nx = x + d[0];
                int nz = z + d[1];
                if (!world.blockExists(nx, sy, nz)) continue;
                if (PathfindingUtils.canSwimAt(world, nx, sy, nz)) {
                    PathNode next = new PathNode(nx, sy, nz);
                    next.moveType = MovementType.SWIM_SURFACE;
                    out.add(next);
                }
            }
        }
    }

    private void addNeighborsPillar(PathNode cur, List<PathNode> out) {
        final int x = cur.x;
        final int z = cur.z;
        final int upY = cur.y + 1;
        
        System.out.println("[Pillar] Attempting pillar from Y=" + cur.y + " to Y=" + upY);
        
        // РќР• Р‘Р›РћРљРР РЈР•Рњ Pillar! РџСЂРѕСЃС‚Рѕ СЃРѕР·РґР°С‘Рј СѓР·РµР», СЃС‚РѕРёРјРѕСЃС‚СЊ Р±СѓРґРµС‚ РІ movementCost()
        // PathFinder СЃР°Рј СЂРµС€РёС‚ РІС‹РіРѕРґРЅРѕ Р»Рё СЌС‚Рѕ
        
        if (!world.blockExists(x, upY, z)) {
            System.out.println("[Pillar] World doesn't exist at Y=" + upY);
            return;
        }

        // РќР• Р‘Р›РћРљРР РЈР•Рњ Pillar РґР°Р¶Рµ РµСЃР»Рё СѓР¶Рµ РЅР° РїРѕРІРµСЂС…РЅРѕСЃС‚Рё!
        // РџСЂРѕСЃС‚Рѕ РґРѕР±Р°РІРёРј С€С‚СЂР°С„ РІ movementCost() РµСЃР»Рё СѓР¶Рµ РґРѕСЃС‚Р°С‚РѕС‡РЅРѕ РІС‹СЃРѕРєРѕ
        // PathFinder СЃР°Рј СЂРµС€РёС‚ РїРµСЂРµРєР»СЋС‡РёС‚СЊСЃСЏ РЅР° ASCEND/TRAVERSE

        // РљР РРўРР§Р•РЎРљРћР• РРЎРџР РђР’Р›Р•РќРР•: Pillar РЎРћР—Р”РђР•Рў Р±Р»РѕРє РїРѕРґ РЅРѕРІРѕР№ РїРѕР·РёС†РёРµР№!
        // РќРµ РЅСѓР¶РЅРѕ РїСЂРѕРІРµСЂСЏС‚СЊ canStandAt(upY), РїРѕС‚РѕРјСѓ С‡С‚Рѕ:
        // 1. РњС‹ РїРѕСЃС‚Р°РІРёРј Р±Р»РѕРє РЅР° cur.y (РїРѕРґ РЅРѕРіР°РјРё С‚РµРєСѓС‰РµР№ РїРѕР·РёС†РёРё)
        // 2. РџРѕСЃР»Рµ СЌС‚РѕРіРѕ РјРѕР¶РµРј СЃС‚РѕСЏС‚СЊ РЅР° upY СЃ Р±Р»РѕРєРѕРј cur.y РїРѕРґ РЅРѕРіР°РјРё
        
        // РџСЂРѕРІРµСЂСЏРµРј С‚РѕР»СЊРєРѕ С‡С‚Рѕ РµСЃС‚СЊ РјРµСЃС‚Рѕ РґР»СЏ РЅРѕРі Рё РіРѕР»РѕРІС‹ РЅР° РЅРѕРІРѕР№ РІС‹СЃРѕС‚Рµ
        boolean feetClear = PathfindingUtils.isBlockPassable(world, x, YMath.feetFromGround(upY), z);
        boolean headClear = PathfindingUtils.isBlockPassable(world, x, YMath.headFromGround(upY), z);
        
        if (!feetClear) {
            System.out.println("[Pillar] Feet blocked at Y=" + YMath.feetFromGround(upY) + ": " + world.getBlock(x, YMath.feetFromGround(upY), z));
            return;
        }
        
        if (!headClear) {
            System.out.println("[Pillar] Head blocked at Y=" + YMath.headFromGround(upY) + ": " + world.getBlock(x, YMath.headFromGround(upY), z));
            return;
        }
        
        // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ РЅР° С‚РµРєСѓС‰РµР№ РїРѕР·РёС†РёРё (cur.y) РјРѕР¶РЅРѕ РїРѕСЃС‚Р°РІРёС‚СЊ Р±Р»РѕРє
        // РўРµРєСѓС‰Р°СЏ РїРѕР·РёС†РёСЏ РґРѕР»Р¶РЅР° Р±С‹С‚СЊ РїСЂРѕС…РѕРґРёРјРѕР№ (РІРѕР·РґСѓС… РёР»Рё replaceable)
        if (!PathfindingUtils.isBlockPassable(world, x, cur.y, z)) {
            System.out.println("[Pillar] Cannot place block at current Y=" + cur.y + ": " + world.getBlock(x, cur.y, z));
            return;
        }
        
        PathNode next = new PathNode(x, upY, z);
        next.moveType = MovementType.PILLAR;
        next.placeBlocks = 1;  // РЎС‚Р°РІРёРј Р±Р»РѕРє РЅР° cur.y
        next.breakBlocks = 0;
        next.needsJump = true;  // РќСѓР¶РЅРѕ РїСЂС‹РіР°С‚СЊ С‡С‚РѕР±С‹ РїРѕРґРЅСЏС‚СЊСЃСЏ
        out.add(next);
        System.out.println("[Pillar] CREATED from Y=" + cur.y + " to Y=" + upY);
    }

    private void addNeighborsBridge(PathNode cur, List<PathNode> out) {
        // РќР• Р‘Р›РћРљРР РЈР•Рњ Bridge! РЎС‚РѕРёРјРѕСЃС‚СЊ Р±СѓРґРµС‚ РІ movementCost()
        
        for (int[] d : CARDINAL) {
            int nx = cur.x + d[0];
            int nz = cur.z + d[1];

            int placementBlockX = cur.x;
            int placementBlockY = YMath.groundBelow(cur.y);
            int placementBlockZ = cur.z;

            int newBlockX = cur.x + d[0];
            int newBlockY = YMath.groundBelow(cur.y);
            int newBlockZ = cur.z + d[1];

            if (!world.blockExists(nx, cur.y, nz)) {
                System.out.println("[Bridge] World doesn't exist at " + nx + "," + cur.y + "," + nz);
                continue;
            }
            
            if (blockCache.get(placementBlockX, placementBlockY, placementBlockZ) == PathfindingUtils.BlockClass.AIR) {
                System.out.println("[Bridge] No block to place FROM at " + placementBlockX + "," + placementBlockY + "," + placementBlockZ);
                continue;
            }
            
            if (blockCache.get(newBlockX, newBlockY, newBlockZ) != PathfindingUtils.BlockClass.AIR) {
                System.out.println("[Bridge] Target position NOT air at " + newBlockX + "," + newBlockY + "," + newBlockZ);
                continue;
            }
            
            if (!PathfindingUtils.isSpaceFree(world, newBlockX + 0.5, YMath.feetFromGround(newBlockY) + 0.01, newBlockZ + 0.5)) {
                System.out.println("[Bridge] Space NOT free at " + newBlockX + "," + newBlockY + "," + newBlockZ);
                continue;
            }

            PathNode next = new PathNode(nx, cur.y, nz);
            next.moveType = MovementType.BRIDGE;
            next.placeBlocks = 1;
            next.breakBlocks = 0;
            out.add(next);
        }
    }

    private void addNeighborsDig(PathNode cur, List<PathNode> out) {
        // РќРћР’РђРЇ Р›РћР“РРљРђ: РљРѕРїР°РµРј Р›Р•РЎРўРќРР¦РЈ (РІР±РѕРє + РІРІРµСЂС…) РґР»СЏ РІС‹С…РѕРґР° РёР· СЏРј!
        // Р’Р°СЂРёР°РЅС‚ 1: РљРѕРїР°С‚СЊ РІРµСЂС‚РёРєР°Р»СЊРЅРѕ РІРІРµСЂС… (РµСЃР»Рё РµСЃС‚СЊ С‚РІРµСЂРґС‹Р№ РїРѕР»)
        // Р’Р°СЂРёР°РЅС‚ 2: РљРѕРїР°С‚СЊ РІР±РѕРє РЅР° СѓСЂРѕРІРµРЅСЊ РІС‹С€Рµ (Р›Р•РЎРўРќРР¦Рђ!)
        
        addDigVertical(cur, out);    // РљРѕРїР°С‚СЊ РїСЂСЏРјРѕ РІРІРµСЂС…
        addDigStaircase(cur, out);   // РљРѕРїР°С‚СЊ Р»РµСЃС‚РЅРёС†Сѓ РІ 4 РЅР°РїСЂР°РІР»РµРЅРёСЏС…
    }
    
    private void addDigVertical(PathNode cur, List<PathNode> out) {
        // РљРѕРїР°РµРј РїСЂСЏРјРѕ Р’Р’Р•Р РҐ (СЃС‚Р°СЂР°СЏ Р»РѕРіРёРєР°)
        final int x = cur.x;
        final int z = cur.z;
        final int upY = cur.y + 1;
        
        System.out.println("[Dig] Checking dig from Y=" + cur.y + " to Y=" + upY);
        
        if (!world.blockExists(x, upY, z)) {
            System.out.println("[Dig] World doesn't exist at Y=" + upY);
            return;
        }
        
        // РљР РРўРР§Р•РЎРљРћР• РРЎРџР РђР’Р›Р•РќРР•: РџСЂРё РєРѕРїР°РЅРёРё РІРІРµСЂС… РїСЂРѕРІРµСЂСЏРµРј РЅРµ canStandAt РґР»СЏ upY,
        // Р° РїСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ:
        // 1. РњС‹ РјРѕР¶РµРј СЃС‚РѕСЏС‚СЊ РЅР° РўР•РљРЈР©Р•Рњ СѓСЂРѕРІРЅРµ (cur.y) 
        // 2. Р‘Р»РѕРєРё РЅР°Рґ РіРѕР»РѕРІРѕР№ (upY Рё upY+1) РјРѕР¶РЅРѕ СЃР»РѕРјР°С‚СЊ
        // 3. РџРѕСЃР»Рµ РёС… СѓРґР°Р»РµРЅРёСЏ РІ upY Рё upY+1 Р±СѓРґРµС‚ РІРѕР·РґСѓС…
        
        System.out.println("[Dig] Checking blocks: current_ground=" + world.getBlock(x, YMath.groundBelow(cur.y), z) + 
                          ", current_feet=" + world.getBlock(x, YMath.feetFromGround(cur.y), z) +
                          ", current_head=" + world.getBlock(x, YMath.headFromGround(cur.y), z) +
                          ", target_feet=" + world.getBlock(x, YMath.feetFromGround(upY), z) + 
                          ", target_head=" + world.getBlock(x, YMath.headFromGround(upY), z));
        
        // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ РјС‹ РјРѕР¶РµРј СЃС‚РѕСЏС‚СЊ РЅР° С‚РµРєСѓС‰РµРј РјРµСЃС‚Рµ
        if (!PathfindingUtils.canStandAt(world, x + 0.5, YMath.feetFromGround(cur.y) + 0.01, z + 0.5)) {
            System.out.println("[Dig] Can't stand at current position " + x + "," + cur.y + "," + z);
            return;
        }
        
        // РџРѕСЃР»Рµ РєРѕРїР°РЅРёСЏ РЅР° СѓСЂРѕРІРЅРµ upY Сѓ РЅР°СЃ Р±СѓРґРµС‚ РїРѕР» РёР· С‚РµРєСѓС‰РµР№ РїРѕР·РёС†РёРё
        // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ С‚РµРєСѓС‰Р°СЏ РїРѕР·РёС†РёСЏ СЃС‚Р°РЅРµС‚ С‚РІРµСЂРґС‹Рј РїРѕР»РѕРј (С‚.Рµ. cur.y РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РїСЂРѕС…РѕРґРёРј РёР»Рё РјС‹ РЅР° РЅРµРј СЃС‚РѕРёРј)
        // РќР° СЃР°РјРѕРј РґРµР»Рµ РїСЂРё РєРѕРїР°РЅРёРё РІРІРµСЂС… РјС‹ РїРѕРґРЅРёРјР°РµРјСЃСЏ РїСЂС‹Р¶РєРѕРј, РїРѕСЌС‚РѕРјСѓ РЅСѓР¶РµРЅ solid block РЅР° cur.y-1
        if (!PathfindingUtils.canStandOn(world, x, YMath.groundBelow(cur.y), z)) {
            System.out.println("[Dig] No solid ground at Y=" + YMath.groundBelow(cur.y) + " to support upward climb");
            return;
        }
        
        int breakCount = 0;
        boolean blocked = false;
        
        // Р‘Р»РѕРєРё РєРѕС‚РѕСЂС‹Рµ РЅСѓР¶РЅРѕ СЃР»РѕРјР°С‚СЊ: РЅР° СѓСЂРѕРІРЅРµ РЅРѕРі РЅРѕРІРѕРіРѕ Y (upY) Рё РіРѕР»РѕРІС‹ (upY+1)
        if (!PathfindingUtils.isBlockPassable(world, x, YMath.feetFromGround(upY), z)) {
            Block b = world.getBlock(x, YMath.feetFromGround(upY), z);
            if (b == null || b.getBlockHardness(world, x, YMath.feetFromGround(upY), z) < 0) {
                blocked = true; // РќРµСЂР°Р·СЂСѓС€Р°РµРјС‹Р№
            } else {
                breakCount++;
            }
        }
        
        if (!PathfindingUtils.isBlockPassable(world, x, YMath.headFromGround(upY), z)) {
            Block b2 = world.getBlock(x, YMath.headFromGround(upY), z);
            if (b2 == null || b2.getBlockHardness(world, x, YMath.headFromGround(upY), z) < 0) {
                blocked = true;
            } else {
                breakCount++;
            }
        }
        
        if (blocked) {
            System.out.println("[Dig] Blocked (unbreakable block) at " + x + "," + upY + "," + z);
            return;
        }
        
        if (breakCount == 0) {
            System.out.println("[Dig] Nothing to break at " + x + "," + upY + "," + z);
            return;
        }
        
        // РЎРѕР·РґР°РµРј СѓР·РµР» РєРѕРїР°РЅРёСЏ Р’Р’Р•Р РҐ
        PathNode next = new PathNode(x, upY, z);
        next.moveType = MovementType.DIG;
        next.breakBlocks = breakCount;
        next.placeBlocks = 0;
        next.needsJump = true; // РќСѓР¶РЅРѕ РїСЂС‹РіР°С‚СЊ С‡С‚РѕР±С‹ РїРѕРґРЅСЏС‚СЊСЃСЏ
        out.add(next);
        System.out.println("[DigVertical] CREATED at " + x + "," + upY + "," + z + " with breakCount=" + breakCount);
    }
    
    private void addDigStaircase(PathNode cur, List<PathNode> out) {
        // Р›Р•РЎРўРќРР¦Рђ: РљРѕРїР°РµРј РІ СЃС‚РѕСЂРѕРЅСѓ + РЅР° СѓСЂРѕРІРµРЅСЊ Р’Р«РЁР•
        // Р­С‚Рѕ СЂРµС€РµРЅРёРµ РґР»СЏ РІС‹С…РѕРґР° РёР· СЏРј Р±РµР· Р±Р»РѕРєРѕРІ!
        // РџСЂРёРјРµСЂ: РІ СЏРјРµ РЅР° Y=2, РєРѕРїР°РµРј Р±Р»РѕРє РЅР° Y=3 РІ РЅР°РїСЂР°РІР»РµРЅРёРё X+1
        //         РЎРѕР·РґР°С‘С‚СЃСЏ РїСЂРѕС…РѕРґ Рё РјРѕР¶РµРј ASCEND РЅР° Y=3
        
        for (int[] d : CARDINAL) {
            int nx = cur.x + d[0];
            int nz = cur.z + d[1];
            int targetY = cur.y + 1; // РќР° СѓСЂРѕРІРµРЅСЊ РІС‹С€Рµ!
            
            if (!world.blockExists(nx, targetY, nz)) {
                continue;
            }
            
            // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ С†РµР»РµРІР°СЏ РїРѕР·РёС†РёСЏ Р—РђР‘Р›РћРљРР РћР’РђРќРђ (РЅСѓР¶РЅРѕ РєРѕРїР°С‚СЊ)
            Block targetFeet = world.getBlock(nx, YMath.feetFromGround(targetY), nz);
            if (PathfindingUtils.isBlockPassable(world, nx, YMath.feetFromGround(targetY), nz)) {
                // РЈР¶Рµ РїСЂРѕС…РѕРґРёРјРѕ - РЅРµ РЅСѓР¶РЅРѕ РєРѕРїР°С‚СЊ
                continue;
            }
            
            // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ РјРѕР¶РµРј СЃР»РѕРјР°С‚СЊ
            if (targetFeet == null || targetFeet.getBlockHardness(world, nx, YMath.feetFromGround(targetY), nz) < 0) {
                continue; // РќРµСЂР°Р·СЂСѓС€Р°РµРјС‹Р№
            }
            
            int breakCount = 1; // РњРёРЅРёРјСѓРј 1 Р±Р»РѕРє (РЅРѕРіРё РЅР° targetY)
            
            // РџСЂРѕРІРµСЂСЏРµРј РіРѕР»РѕРІСѓ
            Block targetHead = world.getBlock(nx, YMath.headFromGround(targetY), nz);
            if (!PathfindingUtils.isBlockPassable(world, nx, YMath.headFromGround(targetY), nz)) {
                if (targetHead == null || targetHead.getBlockHardness(world, nx, YMath.headFromGround(targetY), nz) < 0) {
                    continue; // Р“РѕР»РѕРІР° Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅР° РЅРµСЂР°Р·СЂСѓС€Р°РµРјС‹Рј
                }
                breakCount++;
            }
            
            // РџРѕСЃР»Рµ РєРѕРїР°РЅРёСЏ РґРѕР»Р¶РЅРѕ Р±С‹С‚СЊ РјРѕР¶РЅРѕ СЃС‚РѕСЏС‚СЊ
            // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ РїРѕРґ С†РµР»РµРІРѕР№ РїРѕР·РёС†РёРµР№ Р±СѓРґРµС‚ С‚РІРµСЂРґС‹Р№ Р±Р»РѕРє
            if (!PathfindingUtils.canStandOn(world, nx, YMath.groundBelow(targetY), nz)) {
                continue;
            }
            
            // РЎРѕР·РґР°С‘Рј СѓР·РµР» - РїРѕСЃР»Рµ РєРѕРїР°РЅРёСЏ С‚Р°Рј Р±СѓРґРµС‚ ASCEND РїСѓС‚СЊ!
            PathNode next = new PathNode(nx, targetY, nz);
            next.moveType = MovementType.DIG;
            next.breakBlocks = breakCount;
            next.placeBlocks = 0;
            next.needsJump = false; // РћР±С‹С‡РЅС‹Р№ ASCEND РїРѕСЃР»Рµ РєРѕРїР°РЅРёСЏ
            out.add(next);
            System.out.println("[DigStaircase] CREATED stair to " + nx + "," + targetY + "," + nz + " breakCount=" + breakCount);
        }
    }
    
    private void addNeighborsFall(PathNode cur, List<PathNode> out) {
        // РџРђР”Р•РќРР•: Р•СЃР»Рё РІРЅРёР·Сѓ Р±РµР·РѕРїР°СЃРЅРѕ РїСЂРёР·РµРјР»РёС‚СЊСЃСЏ - СЃРѕР·РґР°С‘Рј FALL СѓР·РµР»
        // РЎС‚РѕРёРјРѕСЃС‚СЊ = СѓСЂРѕРЅ РѕС‚ РїР°РґРµРЅРёСЏ + РІСЂРµРјСЏ РїР°РґРµРЅРёСЏ
        // Р’РЎРЃ Р§Р•Р Р•Р— Р’Р•РЎРђ! Р”Р°Р¶Рµ РµСЃР»Рё СѓСЂРѕРЅ Р±РѕР»СЊС€РѕР№, PathFinder РјРѕР¶РµС‚ РІС‹Р±СЂР°С‚СЊ РїР°РґРµРЅРёРµ
        
        int maxFallCheck = 20; // РџСЂРѕРІРµСЂСЏРµРј РґРѕ 20 Р±Р»РѕРєРѕРІ РІРЅРёР·
        
        for (int fallDist = 1; fallDist <= maxFallCheck; fallDist++) {
            int landY = cur.y - fallDist;
            
            if (landY < 1) break; // Р”РЅРѕ РјРёСЂР°
            if (!world.blockExists(cur.x, landY, cur.z)) break;
            
            // РџСЂРѕРІРµСЂСЏРµРј РјРѕР¶РЅРѕ Р»Рё РїСЂРёР·РµРјР»РёС‚СЊСЃСЏ РЅР° СЌС‚РѕР№ РІС‹СЃРѕС‚Рµ
            if (!PathfindingUtils.canStandAt(world, cur.x + 0.5, YMath.feetFromGround(landY) + 0.01, cur.z + 0.5)) {
                continue; // РќРµР»СЊР·СЏ СЃС‚РѕСЏС‚СЊ, РїСЂРѕРїСѓСЃРєР°РµРј
            }
            
            // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ РєРѕР»РѕРЅРЅР° РјРµР¶РґСѓ cur.y Рё landY СЃРІРѕР±РѕРґРЅР°
            boolean pathClear = true;
            for (int checkY = cur.y; checkY > landY; checkY--) {
                if (!PathfindingUtils.isBlockPassable(world, cur.x, checkY, cur.z) ||
                    !PathfindingUtils.isBlockPassable(world, cur.x, YMath.feetFromGround(checkY), cur.z)) {
                    pathClear = false;
                    break;
                }
            }
            
            if (!pathClear) continue;
            
            // РџСЂРѕРІРµСЂСЏРµРј Р±РµР·РѕРїР°СЃРЅРѕСЃС‚СЊ РїСЂРёР·РµРјР»РµРЅРёСЏ: РѕРїР°СЃРЅРѕСЃС‚СЊ РѕС†РµРЅРёРІР°РµРј РЅР° СѓСЂРѕРІРЅРµ РЅРѕРі (landY + 1)
            if (PathfindingUtils.isDangerousToStand(world, cur.x, YMath.feetFromGround(landY), cur.z)) {
                continue; // Р›Р°РІР°/РѕРіРѕРЅСЊ/РєР°РєС‚СѓСЃ
            }
            
            // РЎРѕР·РґР°С‘Рј FALL СѓР·РµР» РґР°Р¶Рµ РµСЃР»Рё СѓСЂРѕРЅ Р±СѓРґРµС‚!
            // РЎС‚РѕРёРјРѕСЃС‚СЊ СЂР°СЃСЃС‡РёС‚Р°РµС‚СЃСЏ РІ movementCost()
            PathNode next = new PathNode(cur.x, landY, cur.z);
            next.moveType = MovementType.FALL;
            next.breakBlocks = 0;
            next.placeBlocks = 0;
            next.needsJump = false;
            out.add(next);
            
            // РќР°С€Р»Рё РїРµСЂРІРѕРµ РјРµСЃС‚Рѕ РґР»СЏ РїСЂРёР·РµРјР»РµРЅРёСЏ - РѕСЃС‚Р°Р»СЊРЅС‹Рµ РЅРµ РЅСѓР¶РЅС‹
            break;
        }
    }

    private PathNode attemptMove(PathNode current, int dx, int dz) {
        final int nx = current.x + dx;
        final int nz = current.z + dz;
        if (!world.blockExists(nx, current.y, nz)) {
            return null;
        }

        final int baseY = current.y;
        Integer chosenY = null;
        MovementType type = null;

        boolean fastTraverseOk =
                PathfindingUtils.canStandOn(world, nx, YMath.groundBelow(baseY), nz)
                        && PathfindingUtils.isSpaceFree(world, nx + 0.5, YMath.feetFromGround(baseY) + 0.01, nz + 0.5)
                        // РћРїР°СЃРЅРѕСЃС‚СЊ РїСЂРѕРІРµСЂСЏРµРј РЅР° СѓСЂРѕРІРЅРµ РЅРѕРі РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ РѕРїРѕСЂС‹
                        && !PathfindingUtils.isDangerousToStand(world, nx, YMath.feetFromGround(baseY), nz);

        if (isStandSafe(nx, baseY, nz) || fastTraverseOk) {
            chosenY = baseY;
            type = MovementType.TRAVERSE;
        } else {

            int upY = baseY + 1;
            if (world.blockExists(nx, upY, nz)
                    && PathfindingUtils.isBlockPassable(world, nx, YMath.headFromGround(upY), nz)
                    && isStandSafe(nx, upY, nz)) {
                chosenY = upY;
                type = (dx != 0 && dz != 0) ? MovementType.DIAGONAL_ASCEND : MovementType.ASCEND;
            } else {
                if (PathingConfig.DEBUG) {
                    boolean spaceUp = PathfindingUtils.isSpaceFree(world, nx + 0.5, YMath.feetFromGround(upY) + 0.01, nz + 0.5);
                    boolean spaceBase = PathfindingUtils.isSpaceFree(world, nx + 0.5, YMath.feetFromGround(baseY) + 0.01, nz + 0.5);
                    boolean standOnUp = PathfindingUtils.canStandOn(world, nx, YMath.groundBelow(upY), nz);
                    boolean standOnBase = PathfindingUtils.canStandOn(world, nx, YMath.groundBelow(baseY), nz);
                }

                int maxDrop = (int) Math.floor(this.maxDropHeight);
                for (int drop = 1; drop <= maxDrop; drop++) {
                    int candY = baseY - drop;
                    if (candY < 1) break;

                    boolean columnClear = true;
                    for (int y = baseY; y >= candY + 1; y--) {
                        if (!PathfindingUtils.isBlockPassable(world, nx, YMath.feetFromGround(y), nz)
                                || !PathfindingUtils.isBlockPassable(world, nx, YMath.headFromGround(y), nz)) {
                            columnClear = false;
                            break;
                        }
                    }
                    if (!columnClear) continue;

                    if (isStandSafe(nx, candY, nz) && PathfindingUtils.canStandOn(world, nx, YMath.groundBelow(candY), nz)) {
                        if (this.player != null) {
                            double dmg = PathfindingUtils.estimateFallDamage(drop);
                            if (dmg >= this.player.getHealth()) {
                                continue; // avoid lethal drop
                            }
                        }
                        chosenY = candY;
                        if (drop > 3) {
                            type = MovementType.FALL;
                        } else {
                            type = (dx != 0 && dz != 0) ? MovementType.DIAGONAL_DESCEND : MovementType.DESCEND;
                        }
                        break;
                    }
                }
            }
        }

        if (chosenY == null) return null;

        // Forbid cutting corners on diagonal moves (without impacting parkour).
        // Require at least one adjacent cardinal cell to be clear at feet+head level
        // for the target Y. This reduces oscillation between diagonal and parkour paths.
        if (dx != 0 && dz != 0 && type != MovementType.PARKOUR) {
            int adj1x = current.x + dx, adj1z = current.z;
            int adj2x = current.x, adj2z = current.z + dz;

            boolean adj1Feet = PathfindingUtils.isBlockPassable(world, adj1x, chosenY, adj1z);
            boolean adj1Head = PathfindingUtils.isBlockPassable(world, adj1x, YMath.headFromGround(chosenY), adj1z);
            boolean adj2Feet = PathfindingUtils.isBlockPassable(world, adj2x, chosenY, adj2z);
            boolean adj2Head = PathfindingUtils.isBlockPassable(world, adj2x, YMath.headFromGround(chosenY), adj2z);

            if (!((adj1Feet && adj1Head) || (adj2Feet && adj2Head))) {
                return null;
            }
        }

        PathNode next = new PathNode(nx, chosenY, nz);
        next.moveType = type;
        next.breakBlocks = 0;
        next.placeBlocks = 0;
        if (type == MovementType.ASCEND || type == MovementType.DIAGONAL_ASCEND) {
            next.needsJump = true;
        }

        double stepPitch = pitchDeg(next.x - current.x, next.y - current.y, next.z - current.z);
        if (Math.abs(stepPitch) > PathingConfig.MAX_ABS_PITCH_DEG) {
            return null;
        }
        if (!withinAngleLimits(current, next)) {
            return null;
        }
        return next;
    }

    private boolean isStandSafe(int x, int y, int z) {
        if (y < 1 || y > world.getHeight() + 1) return false;
        // РћРїР°СЃРЅРѕСЃС‚СЊ РїСЂРѕРІРµСЂСЏРµРј РЅР° СѓСЂРѕРІРЅРµ РЅРѕРі РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ РѕРїРѕСЂС‹
        if (PathfindingUtils.isDangerousToStand(world, x, YMath.feetFromGround(y), z)) return false;
        // РџСЂРѕРІРµСЂСЏРµРј СЃС‚РѕСЏРЅРёРµ СЃ РјРёСЂРѕРІРѕР№ РєРѕРѕСЂРґРёРЅР°С‚РѕР№ РЅРѕРі (y + 0.01),
        // С‡С‚РѕР±С‹ canStandAt РєРѕСЂСЂРµРєС‚РЅРѕ РѕРїСЂРµРґРµР»СЏР» РѕРїРѕСЂРЅС‹Р№ Р±Р»РѕРє Рё СЃРІРѕР±РѕРґРЅС‹Р№ РѕР±СЉС‘Рј.
        return PathfindingUtils.canStandAt(world, x + 0.5, YMath.feetFromGround(y) + 0.01, z + 0.5);
    }

    private double movementCost(PathNode from, PathNode to) {
        if (this.player == null) {
            return 1000.0;
        }

        final int dy = to.y - from.y;
        final boolean diagonal = (to.x != from.x) && (to.z != from.z);
        double baseTicks;

        // РћС†РµРЅРёРІР°РµРј СЃСЂРµРґСѓ РЅР° СѓСЂРѕРІРЅРµ РЅРѕРі РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ РѕРїРѕСЂС‹
        PathfindingUtils.BlockClass feet = blockCache.get(to.x, YMath.feetFromGround(to.y), to.z);
        if (to.moveType == MovementType.SWIM_SURFACE) {
            baseTicks = PathingConfig.TICKS_SWIM_SURFACE;
        } else if (to.moveType == MovementType.SWIM_UNDERWATER) {
            baseTicks = PathingConfig.TICKS_SWIM_UNDERWATER;
        } else if (feet == PathfindingUtils.BlockClass.WATER) {
            baseTicks = PathingConfig.TICKS_WATER;
        } else {
            baseTicks = PathingConfig.TICKS_WALK;
        }

        if (to.moveType == MovementType.PARKOUR) {
            baseTicks += PathingConfig.PARKOUR_PENALTY_TICKS;
        }

        if (dy > 0) baseTicks += 1.0;
        else if (dy < 0) {
            int drop = -dy;
            if (drop <= 3) {

            } else {
                double fallDamage = PathfindingUtils.estimateFallDamage(drop);
                baseTicks += drop * 6.0 + fallDamage * 15.0;
            }
        }

        if (diagonal) {
            baseTicks *= PathingConfig.DIAGONAL_COST_FACTOR;
        }

        // PILLAR С€С‚СЂР°С„С‹ С‡РµСЂРµР· Р’Р•РЎРђ
        if (to.moveType == MovementType.PILLAR) {
            // Р•СЃР»Рё СѓР¶Рµ СЂСЏРґРѕРј СЃ С‚РІРµСЂРґРѕР№ РїРѕРІРµСЂС…РЅРѕСЃС‚СЊСЋ - РѕРіСЂРѕРјРЅС‹Р№ С€С‚СЂР°С„ Р·Р° Pillar
            // PathFinder РІС‹Р±РµСЂРµС‚ ASCEND/TRAVERSE РІРјРµСЃС‚Рѕ СЌС‚РѕРіРѕ
            if (PathfindingUtils.canStandOn(world, to.x, to.y, to.z)) {
                baseTicks += 10000.0; // РЈР¶Рµ РјРѕР¶РЅРѕ РїСЂС‹РіРЅСѓС‚СЊ РЅР° РїРѕРІРµСЂС…РЅРѕСЃС‚СЊ!
            }
        }
        
        // FALL С€С‚СЂР°С„ = СѓСЂРѕРЅ РѕС‚ РїР°РґРµРЅРёСЏ
        if (to.moveType == MovementType.FALL) {
            int fallDistance = from.y - to.y;
            double fallDamage = PathfindingUtils.estimateFallDamage(fallDistance);
            
            // РџСЂРѕРІРµСЂСЏРµРј Р·РґРѕСЂРѕРІСЊРµ РёРіСЂРѕРєР°
            double playerHealth = player != null ? player.getHealth() : 20.0;
            
            if (fallDamage >= playerHealth) {
                // РЎРјРµСЂС‚РµР»СЊРЅРѕРµ РїР°РґРµРЅРёРµ - РћР“Р РћРњРќР«Р™ С€С‚СЂР°С„, РЅРѕ РЅРµ infinity
                baseTicks += 100000.0 + fallDamage * 1000.0;
            } else {
                // РЈСЂРѕРЅ РїСЂРёРµРјР»РµРјС‹Р№ - С€С‚СЂР°С„ = СѓСЂРѕРЅ * РєРѕСЌС„С„РёС†РёРµРЅС‚
                baseTicks += fallDamage * 100.0; // 1 СѓСЂРѕРЅ = 100 С‚РёРєРѕРІ С€С‚СЂР°С„Р°
                baseTicks += fallDistance * 2.0; // Р’СЂРµРјСЏ РїР°РґРµРЅРёСЏ
            }
        }
        
        if (to.placeBlocks > 0) {
            baseTicks += PathingConfig.BLOCK_PLACEMENT_PENALTY_TICKS;
            
            // РЎРРЎРўР•РњРђ Р’Р•РЎРћР’: Р•СЃР»Рё РЅСѓР¶РЅРѕ СЃС‚Р°РІРёС‚СЊ Р±Р»РѕРєРё, РЅРѕ РёС… РќР•Рў - РѕРіСЂРѕРјРЅС‹Р№ С€С‚СЂР°С„
            // РќРћ РќР• Р‘Р›РћРљРР РЈР•Рњ! PathFinder РјРѕР¶РµС‚ РІС‹Р±СЂР°С‚СЊ СЌС‚Рѕ РµСЃР»Рё РЅРµС‚ Р°Р»СЊС‚РµСЂРЅР°С‚РёРІ
            if (player != null && !hasBlocksInInventory(player)) {
                baseTicks += 50000.0; // РћРіСЂРѕРјРЅС‹Р№ С€С‚СЂР°С„, РЅРѕ РЅРµ infinity
                // PathFinder РЅР°Р№РґС‘С‚ DIG Р»РµСЃС‚РЅРёС†Сѓ РµСЃР»Рё РѕРЅР° РґРµС€РµРІР»Рµ
            }
        }
        if (to.breakBlocks > 0) {
            baseTicks += PathingConfig.BLOCK_BREAK_ADDITIONAL_PENALTY * Math.max(1, to.breakBlocks);
        }
        if (feet == PathfindingUtils.BlockClass.AVOID) {
            baseTicks += 1000.0;
        }
        if (temporaryPenalties != null && temporaryPenalties.containsKey(to)) {
            baseTicks += temporaryPenalties.get(to);
        }
        return baseTicks;
    }

    private double admissibleHeuristic(PathNode from, PathNode to) {
        double dx = Math.abs(from.x - to.x);
        double dz = Math.abs(from.z - to.z);
        double dy = Math.abs(from.y - to.y);
        double horizontalCost = Math.sqrt(dx * dx + dz * dz) * PathingConfig.TICKS_SPRINT;
        double verticalCost = dy * 0.1;
        return (horizontalCost + verticalCost) * PathingConfig.HEURISTIC_WEIGHT;
    }

    private boolean isAtLoadedChunkEdge(PathNode p) {
        for (int[] d : CARDINAL) {
            int nx = p.x + d[0], nz = p.z + d[1];
            if (!world.blockExists(nx, p.y, nz)) {
                return true;
            }
        }
        return false;
    }

    private List<PathNode> bestPartial(PathNode[] bestByCoeff) {
        for (int i = 0; i < bestByCoeff.length; i++) {
            PathNode n = bestByCoeff[i];
            if (n != null && n.steps >= PathingConfig.MIN_SEGMENT_BLOCKS) {
                return reconstruct(n);
            }
        }
        return null;
    }

    private List<PathNode> reconstruct(PathNode end) {
        ArrayList<PathNode> path = new ArrayList<PathNode>();
        PathNode cur = end;
        while (cur != null) {
            path.add(cur);
            cur = cur.parent;
        }
        Collections.reverse(path);
        return path;
    }
    
    /**
     * РџСЂРѕРІРµСЂСЏРµС‚ РЅР°Р»РёС‡РёРµ Р±Р»РѕРєРѕРІ РІ РёРЅРІРµРЅС‚Р°СЂРµ РёРіСЂРѕРєР° РґР»СЏ СЂР°Р·РјРµС‰РµРЅРёСЏ (Pillar/Bridge)
     */
    private boolean hasBlocksInInventory(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return false;
        }
        
        // РџСЂРѕРІРµСЂСЏРµРј РІСЃРµ СЃР»РѕС‚С‹ РёРЅРІРµРЅС‚Р°СЂСЏ
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            net.minecraft.item.ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof net.minecraft.item.ItemBlock) {
                // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ СЌС‚Рѕ РѕР±С‹С‡РЅС‹Р№ Р±Р»РѕРє (РЅРµ С„Р°РєРµР», РЅРµ С†РІРµС‚РѕРє Рё С‚.Рґ.)
                net.minecraft.item.ItemBlock itemBlock = (net.minecraft.item.ItemBlock) stack.getItem();
                net.minecraft.block.Block block = itemBlock.field_150939_a; // Block from ItemBlock
                
                // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ Р±Р»РѕРє С‚РІРµСЂРґС‹Р№ Рё РјРѕР¶РЅРѕ РЅР° РЅС‘Рј СЃС‚РѕСЏС‚СЊ
                if (block != null && block.isOpaqueCube() && stack.stackSize > 0) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private boolean canDigDownToTarget(int x, int z, int startGroundY, int targetGroundY) {
        if (targetGroundY < 0 || startGroundY <= targetGroundY) {
            return false;
        }
        int maxDepth = Math.max(1, Math.min(MAX_FORCED_DIG_DEPTH, (int) Math.ceil(maxDropHeight) + 3));
        if (startGroundY - targetGroundY > maxDepth) {
            return false;
        }
        for (int ground = startGroundY - 1; ground >= targetGroundY; ground--) {
            if (!world.blockExists(x, ground, z)) {
                return false;
            }
            if (!PathfindingUtils.canStandOn(world, x, ground, z)) {
                return false;
            }
            if (PathfindingUtils.isDangerousToStand(world, x, YMath.feetFromGround(ground), z)) {
                return false;
            }
            int feet = YMath.feetFromGround(ground);
            if (!isPassableOrBreakable(x, feet, z)) {
                return false;
            }
            int head = YMath.headFromGround(ground);
            if (!isPassableOrBreakable(x, head, z)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPassableOrBreakable(int x, int y, int z) {
        if (PathfindingUtils.isBlockPassable(world, x, y, z)) {
            return true;
        }
        Block block = world.getBlock(x, y, z);
        return block != null && block.getBlockHardness(world, x, y, z) >= 0;
    }

}

