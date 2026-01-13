package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.network.play.client.C03PacketPlayer;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.ModeSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;

public class Fly extends Module {

    private static final double BASE_SPEED = 0.2873D;

    private ModeSetting mode;
    private SliderSetting speedSetting;

    private boolean previousAllowFlying;
    private boolean previousIsFlying;
    private boolean stateCaptured;
    private int tick;

    public Fly(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("fly", Lang.get("module.fly.name"), Category.MOVEMENT);
        mode = new ModeSetting(
                Lang.get("module.fly.setting.mode"),
                Lang.get("module.fly.mode.vanilla"),
                Lang.get("module.fly.mode.vanilla"),
                Lang.get("module.fly.mode.motion"),
                Lang.get("module.fly.mode.packet"),
                Lang.get("module.fly.mode.jetpack")
        );
        speedSetting = new SliderSetting(Lang.get("module.fly.setting.speed"), 1.2D, 0.2D, 100.0D, 0.05D);
        addSetting(mode);
        addSetting(speedSetting);
        addSetting(new BindSetting(Lang.get("module.fly.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.fly.desc");
    }

    @Override
    public void onEnable() {
        tick = 0;
        if (mc.thePlayer == null) {
            return;
        }
        // Only change capabilities for Vanilla mode
        if (mode.isMode(Lang.get("module.fly.mode.vanilla"))) {
            previousAllowFlying = mc.thePlayer.capabilities.allowFlying;
            previousIsFlying = mc.thePlayer.capabilities.isFlying;
            stateCaptured = true;
            mc.thePlayer.capabilities.allowFlying = true;
            mc.thePlayer.capabilities.isFlying = true;
            mc.thePlayer.motionY = 0.0D;
        } else {
            stateCaptured = false;
        }
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer != null && stateCaptured) {
            mc.thePlayer.capabilities.allowFlying = previousAllowFlying;
            mc.thePlayer.capabilities.isFlying = previousAllowFlying && previousIsFlying;
        }
        stateCaptured = false;

        if (mc.thePlayer != null) {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionY = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
            mc.thePlayer.fallDistance = 0.0F;
        }
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null) {
            return;
        }

        double h = BASE_SPEED * speedSetting.value;
        double v = 0.2D + (speedSetting.value * 0.12D);

        if (mode.isMode(Lang.get("module.fly.mode.vanilla"))) {
            mc.thePlayer.capabilities.allowFlying = true;
            mc.thePlayer.capabilities.isFlying = true;
            applyHorizontal(h);
            applyVerticalHold(v);
            mc.thePlayer.fallDistance = 0.0F;
            return;
        }

        if (mode.isMode(Lang.get("module.fly.mode.motion"))) {
            applyHorizontal(h);
            applyVerticalHold(v);
            if (tick % 8 == 0 && !mc.thePlayer.movementInput.jump) {
                mc.thePlayer.motionY -= 0.04D; // small anti-kick nudge
            }
            mc.thePlayer.fallDistance = 0.0F;
            return;
        }

        if (mode.isMode(Lang.get("module.fly.mode.packet"))) {
            applyHorizontal(h);
            handlePacketVertical(v);
            mc.thePlayer.fallDistance = 0.0F;
            return;
        }

        if (mode.isMode(Lang.get("module.fly.mode.jetpack"))) {
            applyHorizontal(h * 0.85D);
            if (mc.thePlayer.movementInput.jump) {
                handlePacketBurst(v * 0.9D);
            } else if (mc.thePlayer.movementInput.sneak) {
                mc.thePlayer.motionY = -v * 0.6D;
            } else {
                mc.thePlayer.motionY = 0.0D;
            }
            mc.thePlayer.fallDistance = 0.0F;
        }
    }

    private void applyVerticalHold(double verticalSpeed) {
        if (mc.thePlayer.movementInput.jump) {
            mc.thePlayer.motionY = verticalSpeed;
        } else if (mc.thePlayer.movementInput.sneak) {
            mc.thePlayer.motionY = -verticalSpeed;
        } else {
            mc.thePlayer.motionY = 0.0D;
        }
    }

    private void applyHorizontal(double speed) {
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        float yaw = mc.thePlayer.rotationYaw;

        if (forward == 0.0F && strafe == 0.0F) {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
            return;
        }

        if (forward != 0.0F) {
            if (strafe > 0.0F) {
                yaw += (forward > 0.0F ? -45.0F : 45.0F);
            } else if (strafe < 0.0F) {
                yaw += (forward > 0.0F ? 45.0F : -45.0F);
            }
            strafe = 0.0F;
            forward = forward > 0.0F ? 1.0F : -1.0F;
        }

        double rad = Math.toRadians(yaw + 90.0F);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        mc.thePlayer.motionX = forward * speed * cos + strafe * speed * sin;
        mc.thePlayer.motionZ = forward * speed * sin - strafe * speed * cos;
    }

    private void handlePacketVertical(double vSpeed) {
        boolean up = mc.thePlayer.movementInput.jump;
        boolean down = mc.thePlayer.movementInput.sneak;
        if (!up && !down) {
            mc.thePlayer.motionY = 0.0D;
            if (tick % 10 == 0) sendStep(0.0D, -0.03D);
            return;
        }
        double dir = up ? 1.0D : -1.0D;
        double step = Math.min(0.0625D * 3, vSpeed * 0.6D);
        for (int i = 0; i < 3; i++) sendStep(0.0D, dir * step);
        mc.thePlayer.motionY = dir * (vSpeed * 0.2D);
    }

    private void handlePacketBurst(double v) {
        double step = Math.min(0.1D, Math.max(0.05D, v * 0.5D));
        for (int i = 0; i < 2; i++) sendStep(0.0D, step);
        mc.thePlayer.motionY = step;
    }

    private void sendStep(double dx, double dy) {
        double x = mc.thePlayer.posX + dx;
        double y = mc.thePlayer.posY + dy;
        double z = mc.thePlayer.posZ;
        double stance = y + mc.thePlayer.getEyeHeight();
        NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, stance, z, false));
    }
}
