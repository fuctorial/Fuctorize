// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\module\impl\BHop.java
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.ModeSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.PlayerUtils;
import org.lwjgl.input.Keyboard;

public class BHop extends Module {

    private ModeSetting mode;
    private SliderSetting speedMultiplier;

    private double moveSpeed;
    private double lastDist;
    private int stage;

    public BHop(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("bhop", Lang.get("module.bhop.name"), Category.MOVEMENT);
        mode = new ModeSetting(Lang.get("module.bhop.setting.mode"), Lang.get("module.bhop.setting.mode.speed"), Lang.get("module.bhop.setting.mode.speed"), Lang.get("module.bhop.setting.mode.simple"));
        speedMultiplier = new SliderSetting(Lang.get("module.bhop.setting.speed_multiplier"), 1.8, 1.0, 3.0, 0.1);
        addSetting(mode);
        addSetting(speedMultiplier);
        addSetting(new BindSetting(Lang.get("module.bhop.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.bhop.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null) {
            return;
        }
        this.moveSpeed = getBaseMoveSpeed();
        this.lastDist = 0.0D;
        this.stage = 0;
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer != null) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (mode.isMode(Lang.get("module.bhop.setting.mode.speed"))) {
            handleSpeedMode();
        } else if (mode.isMode(Lang.get("module.bhop.setting.mode.simple"))) {
            handleSimpleMode();
        }
    }

    private void handleSpeedMode() {
        lastDist = Math.sqrt(Math.pow(mc.thePlayer.posX - mc.thePlayer.prevPosX, 2) + Math.pow(mc.thePlayer.posZ - mc.thePlayer.prevPosZ, 2));

        if (!PlayerUtils.isPlayerMoving()) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            moveSpeed = getBaseMoveSpeed();
            stage = 0;
            return;
        }

        if (stage == 0 && mc.thePlayer.onGround) {
            stage = 1;
        }

        if (stage == 1 && mc.thePlayer.onGround) {
            mc.thePlayer.motionY = 0.4D;
            moveSpeed *= 1.8D;
            stage = 2;
        } else if (stage == 2) {
            moveSpeed = lastDist - (0.66D * (lastDist - getBaseMoveSpeed()));
            stage = 3;
        } else {
            if (mc.thePlayer.onGround || mc.thePlayer.isCollidedHorizontally) {
                stage = 1;
            }
            moveSpeed = lastDist - lastDist / 159.0D;
        }

        moveSpeed = Math.max(moveSpeed, getBaseMoveSpeed());
        setMoveSpeed(moveSpeed);
    }

    private void handleSimpleMode() {
        if (PlayerUtils.isPlayerMoving() && mc.thePlayer.onGround) {
            mc.thePlayer.jump();
            mc.thePlayer.motionY = 0.42f;
        } else if (!mc.thePlayer.onGround) {
            mc.thePlayer.jumpMovementFactor = 0.0265f * (float)speedMultiplier.value;
        }
    }

    private double getBaseMoveSpeed() {
        if (mc.thePlayer == null) {
            return 0.2873D;
        }
        double baseSpeed = 0.2873D;
        if (mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.moveSpeed)) {
            int amplifier = mc.thePlayer.getActivePotionEffect(net.minecraft.potion.Potion.moveSpeed).getAmplifier();
            baseSpeed *= 1.0D + 0.2D * (double) (amplifier + 1);
        }
        return baseSpeed;
    }

    private void setMoveSpeed(double speed) {
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        float yaw = mc.thePlayer.rotationYaw;

        if (forward == 0.0F && strafe == 0.0F) {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
        } else {
            if (forward != 0.0F) {
                if (strafe > 0.0F) {
                    yaw += (forward > 0.0F ? -45 : 45);
                } else if (strafe < 0.0F) {
                    yaw += (forward > 0.0F ? 45 : -45);
                }
                strafe = 0.0F;
                if (forward > 0.0F) {
                    forward = 1.0F;
                } else if (forward < 0.0F) {
                    forward = -1.0F;
                }
            }
            mc.thePlayer.motionX = forward * speed * Math.cos(Math.toRadians(yaw + 90.0F)) + strafe * speed * Math.sin(Math.toRadians(yaw + 90.0F));
            mc.thePlayer.motionZ = forward * speed * Math.sin(Math.toRadians(yaw + 90.0F)) - strafe * speed * Math.cos(Math.toRadians(yaw + 90.0F));
        }
    }
}