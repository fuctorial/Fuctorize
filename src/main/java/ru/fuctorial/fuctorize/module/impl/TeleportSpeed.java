// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\TeleportSpeed.java
package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.PlayerUtils;

public class TeleportSpeed extends Module {

    private static final double STEP_SEGMENT = 0.6;

    private SliderSetting stepCount;
    private SliderSetting climbHeight;

    public TeleportSpeed(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("teleportspeed", Lang.get("module.teleportspeed.name"), Category.MOVEMENT);

        stepCount = new SliderSetting(Lang.get("module.teleportspeed.setting.steps"), 6.0, 1.0, 20.0, 1.0);
        addSetting(stepCount);
        climbHeight = new SliderSetting(Lang.get("module.teleportspeed.setting.climb_height"), 3.0, 1.0, 10.0, 1.0);
        addSetting(climbHeight);
        addSetting(new BindSetting(Lang.get("module.teleportspeed.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.teleportspeed.desc");
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
            return;
        }

        if (!PlayerUtils.isPlayerMoving()) {
            return;
        }

        float moveForward = mc.thePlayer.movementInput.moveForward;
        float moveStrafe = mc.thePlayer.movementInput.moveStrafe;

        if (moveForward == 0.0f && moveStrafe == 0.0f) {
            return;
        }

        float yaw = resolveMovementYaw(mc.thePlayer.rotationYaw, moveForward, moveStrafe);
        double yawRad = Math.toRadians(yaw);

        int blocks = Math.max(1, (int) Math.round(stepCount.value));
        double distance = blocks;

        double fromX = mc.thePlayer.posX;
        double fromY = mc.thePlayer.posY;
        double fromZ = mc.thePlayer.posZ;
        double toX = fromX - MathHelper.sin((float) yawRad) * distance;
        double toZ = fromZ + MathHelper.cos((float) yawRad) * distance;

        double climbLimit = Math.max(1.0, climbHeight.value);
        PathResult result = simulateWalkPath(fromX, fromY, fromZ, toX, toZ, climbLimit);

        mc.thePlayer.setPosition(result.x, result.y, result.z);
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionZ = 0.0;
    }

    private float resolveMovementYaw(float baseYaw, float moveForward, float moveStrafe) {
        float yaw = baseYaw;

        if (moveForward != 0.0f) {
            if (moveStrafe > 0.0f) {
                yaw += moveForward > 0.0f ? -45.0f : 45.0f;
            } else if (moveStrafe < 0.0f) {
                yaw += moveForward > 0.0f ? 45.0f : -45.0f;
            }
            if (moveForward < 0.0f) {
                yaw += 180.0f;
            }
        } else if (moveStrafe != 0.0f) {
            yaw += moveStrafe > 0.0f ? -90.0f : 90.0f;
        }

        return yaw;
    }

    private PathResult simulateWalkPath(double fromX, double fromY, double fromZ, double toX, double toZ, double climbLimit) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max((int) Math.ceil(distance / STEP_SEGMENT), 1);

        double currentX = fromX;
        double currentY = fromY;
        double currentZ = fromZ;

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double stepX = fromX + dx * t;
            double stepZ = fromZ + dz * t;

            AdjustResult adjust = adjustYForObstacles(stepX, currentY, stepZ, climbLimit);
            if (adjust.blocked) {
                break;
            }

            currentX = stepX;
            currentY = adjust.y;
            currentZ = stepZ;

            boolean onGround = isOnGround(currentX, currentY, currentZ);
            NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(
                    currentX,
                    currentY,
                    currentY + mc.thePlayer.getEyeHeight(),
                    currentZ,
                    onGround
            ));
        }

        return new PathResult(currentX, currentY, currentZ);
    }

    private AdjustResult adjustYForObstacles(double x, double currentY, double z, double climbLimit) {
        if (isSpaceFree(x, currentY, z)) {
            return AdjustResult.success(currentY);
        }

        for (double offset = 0.5; offset <= climbLimit; offset += 0.5) {
            double y = currentY + offset;
            if (isSpaceFree(x, y, z)) {
                return AdjustResult.success(y);
            }
        }

        for (double offset = 0.5; offset <= 2.0; offset += 0.5) {
            double y = currentY - offset;
            if (isSpaceFree(x, y, z)) {
                return AdjustResult.success(y);
            }
        }

        return AdjustResult.blocked();
    }

    private boolean isSpaceFree(double x, double y, double z) {
        if (mc == null || mc.theWorld == null) {
            return true;
        }

        double w = 0.3;
        return isAirColumn(x - w, y, z - w)
                && isAirColumn(x - w, y, z + w)
                && isAirColumn(x + w, y, z - w)
                && isAirColumn(x + w, y, z + w);
    }

    private boolean isAirColumn(double x, double y, double z) {
        int bx = MathHelper.floor_double(x);
        int by = MathHelper.floor_double(y);
        int bz = MathHelper.floor_double(z);
        return mc.theWorld.isAirBlock(bx, by, bz) && mc.theWorld.isAirBlock(bx, by + 1, bz);
    }

    private boolean isOnGround(double x, double y, double z) {
        if (mc == null || mc.theWorld == null) {
            return false;
        }

        int bx = MathHelper.floor_double(x);
        int by = MathHelper.floor_double(y - 0.01);
        int bz = MathHelper.floor_double(z);
        return !mc.theWorld.isAirBlock(bx, by - 1, bz);
    }

    private static final class PathResult {
        final double x;
        final double y;
        final double z;

        PathResult(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class AdjustResult {
        final double y;
        final boolean blocked;

        private AdjustResult(double y, boolean blocked) {
            this.y = y;
            this.blocked = blocked;
        }

        static AdjustResult success(double y) {
            return new AdjustResult(y, false);
        }

        static AdjustResult blocked() {
            return new AdjustResult(0.0, true);
        }
    }
}
