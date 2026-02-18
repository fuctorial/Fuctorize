package ru.fuctorial.fuctorize.utils.pathfinding;

import net.minecraft.world.World;

 
public final class PathPlanner {

    private PathPlanner() {}

    public static PathResult findPathTowardUnloadedTarget(
            PathFinder finder,
            World world,
            double startX, double startY, double startZ,
            double targetX, double targetY, double targetZ,  
            double configuredClimbHeight,
            double lastComputedDropHeight
    ) {
        if (finder == null || world == null) {
            return PathResult.failure("World/PathFinder not ready", 0);
        }

        double dx = targetX - startX;
        double dy = targetY - startY;
        double dz = targetZ - startZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

         
        if (dist < 20.0) {
            return finder.findPath(startX, startY, startZ, targetX, targetY, targetZ);
        }

         
        double ndx = dx / dist;
        double ndz = dz / dist;

         
         
        double searchDist = Math.min(dist, 80.0);

         
        for (double d = searchDist; d > 5.0; d -= 5.0) {
            int tx = (int) Math.floor(startX + ndx * d);
            int tz = (int) Math.floor(startZ + ndz * d);

             
             
            if (!world.blockExists(tx, 64, tz)) {
                continue;
            }

             
             
            int pY = (int)startY;

             
            Integer foundY = PathfindingUtils.findStandingYAllowingDown(world, tx, pY, tz, 20.0, 50.0);

            if (foundY != null) {
                 
                 
                 
                PathResult res = finder.findPath(startX, startY, startZ, tx + 0.5, foundY, tz + 0.5);

                 
                 
                if (res.isSuccess() || (res.isPartial() && res.getPathLength() > 5)) {
                    System.out.println("[PathPlanner] Found intermediate target at dist " + d + " -> " + tx + "," + foundY + "," + tz);
                    return res;
                }
            }
        }

         
         
        return PathResult.failure("No path found toward frontier (dumb mode)", 0);
    }

     
    public static PathResult findPathToNearest(
            PathFinder finder,
            World world,
            double startX, double startY, double startZ,
            double targetX, double targetY, double targetZ,
            int searchRadius,
            double configuredClimbHeight,
            double lastComputedDropHeight
    ) {
        if (finder == null || world == null) return PathResult.failure("Not ready", 0);

         
        return finder.findPath(startX, startY, startZ, targetX, targetY, targetZ);
    }

    public static double computeSafeDropHeight(net.minecraft.entity.player.EntityPlayer player) {
        return 20.0;  
    }
}