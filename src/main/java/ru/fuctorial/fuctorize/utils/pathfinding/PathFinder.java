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

         

         
        int sx = MathHelper.floor_double(startX);
        double startFeetY;
        if (player != null && player.boundingBox != null) {
            startFeetY = player.boundingBox.minY;
        } else {
            startFeetY = startY - 1.625;
        }
        int sy = MathHelper.floor_double(startFeetY - 0.01);
        int sz = MathHelper.floor_double(startZ);

         
         
        while(!PathfindingUtils.canStandOn(world, sx, sy, sz) && sy > 0) {
            sy--;
        }

         
        if (sy <= 0 && !PathfindingUtils.canStandOn(world, sx, 0, sz)) {
            return PathResult.failure("РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°Р№С‚Рё РѕРїРѕСЂСѓ РїРѕРґ СЃС‚Р°СЂС‚РѕРІРѕР№ С‚РѕС‡РєРѕР№", 0);
        }

         
        int tx = MathHelper.floor_double(targetX);
        int ty = MathHelper.floor_double(targetY);
        int tz = MathHelper.floor_double(targetZ);

         
        Integer bestTargetY = PathfindingUtils.findStandingYAllowingDown(world, tx, ty, tz, maxClimbHeight, maxDropHeight);
        if (bestTargetY == null) {
             
            return PathResult.failure("Р¦РµР»РµРІР°СЏ С‚РѕС‡РєР° РЅРµРґРѕСЃС‚РёР¶РёРјР°", 0);
        }
        int desiredTargetY = ty;
        ty = bestTargetY;
        if (desiredTargetY < bestTargetY && canDigDownToTarget(tx, tz, bestTargetY, desiredTargetY)) {
            ty = desiredTargetY;
            System.out.println("[PathFinder] Target column blocked. Allowing dig-down path to Y=" + YMath.feetFromGround(ty));
        }

         
        PathNode startPathNode = new PathNode(sx, sy, sz);
        startPathNode.moveType = MovementType.TRAVERSE;
        PathNode targetPathNode = new PathNode(tx, ty, tz);
        targetPathNode.moveType = MovementType.TRAVERSE;

        System.out.println("[PathFinder] Corrected Start Node: (" + sx + ", " + YMath.feetFromGround(sy) + ", " + sz + ")");  
        System.out.println("[PathFinder] Corrected Target Node: (" + tx + ", " + YMath.feetFromGround(ty) + ", " + tz + ")");  

        return findPath(startPathNode, targetPathNode, cancellationChecker);
         
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
                 
                List<PathNode> partial = bestPartial(bestByCoeff);
                return (partial != null && !partial.isEmpty())
                        ? PathResult.partial(partial, nodesExplored)  
                        : PathResult.failure("Time limit exceeded", nodesExplored);
            }

            PathNode current = openSet.poll();
            if (current == null) break;

            if (isAtLoadedChunkEdge(current)) {
                edgeCounter++;
                if (edgeCounter > edgeCounterThreshold) {
                    List<PathNode> partial = bestPartial(bestByCoeff);
                    if (partial != null && !partial.isEmpty()) {
                        return PathResult.partial(partial, nodesExplored);  
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

     

    private List<PathNode> generateNeighbors(PathNode node) {
        return neighborGenerator.generateNeighbors(node);
    }

     
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
        
         
         
        
        if (!world.blockExists(x, upY, z)) {
            System.out.println("[Pillar] World doesn't exist at Y=" + upY);
            return;
        }

         
         
         

         
         
         
         
        
         
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
        
         
         
        if (!PathfindingUtils.isBlockPassable(world, x, cur.y, z)) {
            System.out.println("[Pillar] Cannot place block at current Y=" + cur.y + ": " + world.getBlock(x, cur.y, z));
            return;
        }
        
        PathNode next = new PathNode(x, upY, z);
        next.moveType = MovementType.PILLAR;
        next.placeBlocks = 1;   
        next.breakBlocks = 0;
        next.needsJump = true;   
        out.add(next);
        System.out.println("[Pillar] CREATED from Y=" + cur.y + " to Y=" + upY);
    }

    private void addNeighborsBridge(PathNode cur, List<PathNode> out) {
         
        
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
         
         
         
        
        addDigVertical(cur, out);     
        addDigStaircase(cur, out);    
    }
    
    private void addDigVertical(PathNode cur, List<PathNode> out) {
         
        final int x = cur.x;
        final int z = cur.z;
        final int upY = cur.y + 1;
        
        System.out.println("[Dig] Checking dig from Y=" + cur.y + " to Y=" + upY);
        
        if (!world.blockExists(x, upY, z)) {
            System.out.println("[Dig] World doesn't exist at Y=" + upY);
            return;
        }
        
         
         
         
         
         
        
        System.out.println("[Dig] Checking blocks: current_ground=" + world.getBlock(x, YMath.groundBelow(cur.y), z) + 
                          ", current_feet=" + world.getBlock(x, YMath.feetFromGround(cur.y), z) +
                          ", current_head=" + world.getBlock(x, YMath.headFromGround(cur.y), z) +
                          ", target_feet=" + world.getBlock(x, YMath.feetFromGround(upY), z) + 
                          ", target_head=" + world.getBlock(x, YMath.headFromGround(upY), z));
        
         
        if (!PathfindingUtils.canStandAt(world, x + 0.5, YMath.feetFromGround(cur.y) + 0.01, z + 0.5)) {
            System.out.println("[Dig] Can't stand at current position " + x + "," + cur.y + "," + z);
            return;
        }
        
         
         
         
        if (!PathfindingUtils.canStandOn(world, x, YMath.groundBelow(cur.y), z)) {
            System.out.println("[Dig] No solid ground at Y=" + YMath.groundBelow(cur.y) + " to support upward climb");
            return;
        }
        
        int breakCount = 0;
        boolean blocked = false;
        
         
        if (!PathfindingUtils.isBlockPassable(world, x, YMath.feetFromGround(upY), z)) {
            Block b = world.getBlock(x, YMath.feetFromGround(upY), z);
            if (b == null || b.getBlockHardness(world, x, YMath.feetFromGround(upY), z) < 0) {
                blocked = true;  
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
        
         
        PathNode next = new PathNode(x, upY, z);
        next.moveType = MovementType.DIG;
        next.breakBlocks = breakCount;
        next.placeBlocks = 0;
        next.needsJump = true;  
        out.add(next);
        System.out.println("[DigVertical] CREATED at " + x + "," + upY + "," + z + " with breakCount=" + breakCount);
    }
    
    private void addDigStaircase(PathNode cur, List<PathNode> out) {
         
         
         
         
        
        for (int[] d : CARDINAL) {
            int nx = cur.x + d[0];
            int nz = cur.z + d[1];
            int targetY = cur.y + 1;  
            
            if (!world.blockExists(nx, targetY, nz)) {
                continue;
            }
            
             
            Block targetFeet = world.getBlock(nx, YMath.feetFromGround(targetY), nz);
            if (PathfindingUtils.isBlockPassable(world, nx, YMath.feetFromGround(targetY), nz)) {
                 
                continue;
            }
            
             
            if (targetFeet == null || targetFeet.getBlockHardness(world, nx, YMath.feetFromGround(targetY), nz) < 0) {
                continue;  
            }
            
            int breakCount = 1;  
            
             
            Block targetHead = world.getBlock(nx, YMath.headFromGround(targetY), nz);
            if (!PathfindingUtils.isBlockPassable(world, nx, YMath.headFromGround(targetY), nz)) {
                if (targetHead == null || targetHead.getBlockHardness(world, nx, YMath.headFromGround(targetY), nz) < 0) {
                    continue;  
                }
                breakCount++;
            }
            
             
             
            if (!PathfindingUtils.canStandOn(world, nx, YMath.groundBelow(targetY), nz)) {
                continue;
            }
            
             
            PathNode next = new PathNode(nx, targetY, nz);
            next.moveType = MovementType.DIG;
            next.breakBlocks = breakCount;
            next.placeBlocks = 0;
            next.needsJump = false;  
            out.add(next);
            System.out.println("[DigStaircase] CREATED stair to " + nx + "," + targetY + "," + nz + " breakCount=" + breakCount);
        }
    }
    
    private void addNeighborsFall(PathNode cur, List<PathNode> out) {
         
         
         
        
        int maxFallCheck = 20;  
        
        for (int fallDist = 1; fallDist <= maxFallCheck; fallDist++) {
            int landY = cur.y - fallDist;
            
            if (landY < 1) break;  
            if (!world.blockExists(cur.x, landY, cur.z)) break;
            
             
            if (!PathfindingUtils.canStandAt(world, cur.x + 0.5, YMath.feetFromGround(landY) + 0.01, cur.z + 0.5)) {
                continue;  
            }
            
             
            boolean pathClear = true;
            for (int checkY = cur.y; checkY > landY; checkY--) {
                if (!PathfindingUtils.isBlockPassable(world, cur.x, checkY, cur.z) ||
                    !PathfindingUtils.isBlockPassable(world, cur.x, YMath.feetFromGround(checkY), cur.z)) {
                    pathClear = false;
                    break;
                }
            }
            
            if (!pathClear) continue;
            
             
            if (PathfindingUtils.isDangerousToStand(world, cur.x, YMath.feetFromGround(landY), cur.z)) {
                continue;  
            }
            
             
             
            PathNode next = new PathNode(cur.x, landY, cur.z);
            next.moveType = MovementType.FALL;
            next.breakBlocks = 0;
            next.placeBlocks = 0;
            next.needsJump = false;
            out.add(next);
            
             
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
                                continue;  
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
         
        if (PathfindingUtils.isDangerousToStand(world, x, YMath.feetFromGround(y), z)) return false;
         
         
        return PathfindingUtils.canStandAt(world, x + 0.5, YMath.feetFromGround(y) + 0.01, z + 0.5);
    }

    private double movementCost(PathNode from, PathNode to) {
        if (this.player == null) {
            return 1000.0;
        }

        final int dy = to.y - from.y;
        final boolean diagonal = (to.x != from.x) && (to.z != from.z);
        double baseTicks;

         
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

         
        if (to.moveType == MovementType.PILLAR) {
             
             
            if (PathfindingUtils.canStandOn(world, to.x, to.y, to.z)) {
                baseTicks += 10000.0;  
            }
        }
        
         
        if (to.moveType == MovementType.FALL) {
            int fallDistance = from.y - to.y;
            double fallDamage = PathfindingUtils.estimateFallDamage(fallDistance);
            
             
            double playerHealth = player != null ? player.getHealth() : 20.0;
            
            if (fallDamage >= playerHealth) {
                 
                baseTicks += 100000.0 + fallDamage * 1000.0;
            } else {
                 
                baseTicks += fallDamage * 100.0;  
                baseTicks += fallDistance * 2.0;  
            }
        }
        
        if (to.placeBlocks > 0) {
            baseTicks += PathingConfig.BLOCK_PLACEMENT_PENALTY_TICKS;
            
             
             
            if (player != null && !hasBlocksInInventory(player)) {
                baseTicks += 50000.0;  
                 
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
    
     
    private boolean hasBlocksInInventory(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return false;
        }
        
         
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            net.minecraft.item.ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof net.minecraft.item.ItemBlock) {
                 
                net.minecraft.item.ItemBlock itemBlock = (net.minecraft.item.ItemBlock) stack.getItem();
                net.minecraft.block.Block block = itemBlock.field_150939_a;  
                
                 
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

