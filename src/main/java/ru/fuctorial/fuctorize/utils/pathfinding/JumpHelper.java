package ru.fuctorial.fuctorize.utils.pathfinding;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Small helper to keep jump decision logic out of BotNavigator.
 */
public final class JumpHelper {

    private JumpHelper() {}

    public static void applyParkourJump(Minecraft mc, PathNode activeNode, PathNode prevNode) {
        if (mc == null || mc.thePlayer == null || activeNode == null || prevNode == null) return;

        int dirX = Integer.signum(activeNode.x - prevNode.x);
        int dirZ = Integer.signum(activeNode.z - prevNode.z);
        if (dirX == 0 && dirZ == 0) return;

        int launchBlockX = prevNode.x + dirX;
        int launchBlockZ = prevNode.z + dirZ;
        double launchX = launchBlockX + 0.5 - dirX * 0.35;
        double launchZ = launchBlockZ + 0.5 - dirZ * 0.35;
        double edgeX = launchBlockX + 0.5 - dirX * 0.05;
        double edgeZ = launchBlockZ + 0.5 - dirZ * 0.05;

        if (mc.thePlayer.fallDistance > 0.9) {
            return;
        }

        double vx = mc.thePlayer.motionX, vz = mc.thePlayer.motionZ;
        double speed = Math.sqrt(vx * vx + vz * vz);
        double distToLaunch = Math.hypot(mc.thePlayer.posX - launchX, mc.thePlayer.posZ - launchZ);
        double timeToEdgeSec = (speed > 1.0e-3) ? (distToLaunch / speed) / 20.0 : 1.0;

        int stride = Math.max(Math.abs(activeNode.x - prevNode.x), Math.abs(activeNode.z - prevNode.z));
        boolean shortJump = stride <= 2;

        double atEdgeThresh = shortJump ? 0.18 : 0.10;
        double timeWindowThresh = shortJump ? 0.28 : 0.18;
        double minSpeed = shortJump ? 0.06 : 0.12;

        double forwardDist = (dirX != 0)
                ? (edgeX - mc.thePlayer.posX) * dirX
                : (edgeZ - mc.thePlayer.posZ) * dirZ;
        boolean atEdge = forwardDist <= atEdgeThresh;
        boolean timingWindow = timeToEdgeSec <= timeWindowThresh;

        if (mc.thePlayer.onGround && ((atEdge && (shortJump || speed >= minSpeed)) || (timingWindow && speed >= minSpeed))) {
            ru.fuctorial.fuctorize.utils.movement.MovementUtils.jump();
        }
    }

    public static void applyAscendJump(Minecraft mc, PathNode activeNode) {
        if (mc == null || mc.thePlayer == null || activeNode == null) return;

        if (!mc.thePlayer.onGround) return;

        boolean ascendMove = (activeNode.moveType == MovementType.ASCEND || activeNode.moveType == MovementType.DIAGONAL_ASCEND);
        boolean shouldJumpForNode = activeNode.needsJump || ascendMove;
        if (!shouldJumpForNode) return;

        int pbx = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posX);
        int pbz = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posZ);
        int dirX = Integer.signum(activeNode.x - pbx);
        int dirZ = Integer.signum(activeNode.z - pbz);

        double desiredFeetY = activeNode.getFeetY();
        double dyToTarget = desiredFeetY - mc.thePlayer.boundingBox.minY;
        if (dyToTarget <= 0.05) return;

        double vx = mc.thePlayer.motionX, vz = mc.thePlayer.motionZ;
        double speed = Math.sqrt(vx * vx + vz * vz);
        double atEdgeThresh = 0.18;
        double minSpeed = 0.06;

        boolean jumped = false;
        if (dirX != 0 || dirZ != 0) {
            double edgeX = pbx + 0.5 + (dirX != 0 ? 0.5 * dirX : 0.0) - (dirX != 0 ? 0.05 * dirX : 0.0);
            double edgeZ = pbz + 0.5 + (dirZ != 0 ? 0.5 * dirZ : 0.0) - (dirZ != 0 ? 0.05 * dirZ : 0.0);
            double forwardDist = (dirX != 0) ? (edgeX - mc.thePlayer.posX) * dirX : (edgeZ - mc.thePlayer.posZ) * dirZ;
            if (forwardDist <= atEdgeThresh && speed >= minSpeed) {
                ru.fuctorial.fuctorize.utils.movement.MovementUtils.jump();
                jumped = true;
            }
        }
        if (!jumped) {
            double horizDist = Math.hypot(mc.thePlayer.posX - (activeNode.x + 0.5), mc.thePlayer.posZ - (activeNode.z + 0.5));
            if (horizDist < 1.2 && speed >= minSpeed) {
                ru.fuctorial.fuctorize.utils.movement.MovementUtils.jump();
            }
        }
    }
}


