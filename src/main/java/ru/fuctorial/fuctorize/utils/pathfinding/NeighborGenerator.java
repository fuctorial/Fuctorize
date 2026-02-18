package ru.fuctorial.fuctorize.utils.pathfinding;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;
import ru.fuctorial.fuctorize.utils.pathfinding.YMath;

 
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
             
            if (!isPassable(current.x + dir[0], feetY, current.z) && !isPassable(current.x, feetY, current.z + dir[1])) {
                continue;
            }
            attemptMove(current, dir[0], dir[1], neighbors);
        }
    }

    private void attemptMove(PathNode current, int dx, int dz, List<PathNode> neighbors) {
        int nx = current.x + dx;
        int nz = current.z + dz;
        int baseY = current.y;  

        boolean diagonal = (dx != 0 && dz != 0);

         
        int ascendY = baseY + 1;
        if (isStandSafe(nx, ascendY, nz)) {
             
            if (isPassable(nx, YMath.headFromGround(baseY), nz)) {
                 
                if (isPassable(nx, YMath.headFromGround(ascendY), nz)) {
                    addNode(nx, ascendY, nz, diagonal ? MovementType.DIAGONAL_ASCEND : MovementType.ASCEND, neighbors, true);
                }
            }
        }

         
        boolean addedTraverse = false;
         
         
        boolean headClear = isPassable(nx, YMath.headFromGround(baseY), nz);
        boolean feetClear = isPassable(nx, YMath.feetFromGround(baseY), nz);

        if (isStandSafe(nx, baseY, nz) && feetClear && headClear) {
            addNode(nx, baseY, nz, MovementType.TRAVERSE, neighbors);
            addedTraverse = true;
        }

         
         
        if (!addedTraverse && isSolid(nx, baseY, nz)) {  
             
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

         
        boolean canTraverseOrAscend = false;
        for(PathNode node : neighbors) {
            if (node.x == nx && node.z == nz && node.y >= baseY) {  
                canTraverseOrAscend = true;
                break;
            }
        }

        if (!canTraverseOrAscend) {
             
             
             
            if (!isPassable(nx, YMath.headFromGround(baseY), nz)) {
                 
                 
                if (isBreakable(nx, YMath.headFromGround(baseY), nz)) {
                     
                     
                     
                     
                     

                     
                     
                    return;
                } else {
                    return;  
                }
            }

            for (int drop = 1; drop <= maxDropHeight; drop++) {
                int descendY = baseY - drop;
                if (descendY < 0) break;

                 
                boolean pathClear = true;
                for (int y = baseY; y > descendY; y--) {
                     
                    if (!isPassable(nx, YMath.feetFromGround(y), nz)) {
                        pathClear = false; break;
                    }
                     
                    if (!isPassable(nx, YMath.headFromGround(y), nz)) {
                        pathClear = false; break;
                    }
                }

                if (pathClear && isStandSafe(nx, descendY, nz)) {
                    if (player != null && PathfindingUtils.estimateFallDamage(drop) >= player.getHealth()) {
                        continue;
                    }
                    addNode(nx, descendY, nz, diagonal ? MovementType.DIAGONAL_DESCEND : MovementType.DESCEND, neighbors);
                    break;  
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

         
        addDownwardDig(current, neighbors);

         
        for (int[] dir : CARDINAL) {
            int nx = current.x + dir[0];
            int nz = current.z + dir[1];
            int baseY = current.y;

             
            int headY = YMath.headFromGround(baseY);
            if (!isPassable(nx, headY, nz)) {
                if (isBreakable(nx, headY, nz)) {
                     
                     
                     
                     

                     
                     
                     
                    if (PathfindingUtils.canStandOn(world, nx, baseY, nz)) {
                         
                    } else {
                         
                         
                         
                         

                         
                        for (int drop = 1; drop <= maxDropHeight; drop++) {
                            int descendY = baseY - drop;
                            if (isStandSafe(nx, descendY, nz)) {
                                 
                                 
                                PathNode dropNode = new PathNode(nx, descendY, nz);
                                dropNode.moveType = MovementType.DIG;  
                                dropNode.breakBlocks = 1;  
                                 
                                 
                                 
                                 
                                 
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
        int feetY = YMath.feetFromGround(targetY);  

         
         
         
         
         
         

        if (!isPassable(current.x, feetY, current.z)) {
            if (isBreakable(current.x, feetY, current.z)) breakCount++; else return;
        }
         
        int headY = YMath.headFromGround(targetY);
        if (!isPassable(current.x, headY, current.z)) {
            if (isBreakable(current.x, headY, current.z)) breakCount++; else return;
        }

        if (breakCount > 0 && !PathfindingUtils.isDangerousToStand(world, current.x, feetY, current.z)) {
            addNode(current.x, targetY, current.z, MovementType.DIG, neighbors, 0, breakCount, false);
        }
    }

     

    private boolean isPassable(int x, int y, int z) {
        return PathfindingUtils.isBlockPassable(world, x, y, z);
    }

    private boolean isSolid(int x, int y, int z) {  
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