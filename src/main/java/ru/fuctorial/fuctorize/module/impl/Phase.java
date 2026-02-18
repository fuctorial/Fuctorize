 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import ru.fuctorial.fuctorize.utils.NetUtils;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.lwjgl.input.Keyboard;

public class Phase extends Module {

    public Phase(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("phase", Lang.get("module.phase.name"), Category.EXPLOIT);
        addSetting(new BindSetting(Lang.get("module.phase.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.phase.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            toggle();
            return;
        }
        NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY - 2, mc.thePlayer.posY, mc.thePlayer.posZ, false));
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null) return;

        mc.thePlayer.boundingBox.offset(0,0,0);
        mc.thePlayer.noClip = true;
        mc.thePlayer.motionY = 0;

        if (mc.gameSettings.keyBindJump.getIsKeyPressed()) {
            mc.thePlayer.motionY = 0.5;
        } else if (mc.gameSettings.keyBindSneak.getIsKeyPressed()) {
            mc.thePlayer.motionY = -0.5;
        }

        setMoveSpeed(0.3);
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof C03PacketPlayer) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer != null) {
            mc.thePlayer.noClip = false;
        }
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
                if (strafe > 0.0F) yaw += (forward > 0.0F ? -45 : 45);
                else if (strafe < 0.0F) yaw += (forward > 0.0F ? 45 : -45);
                strafe = 0.0F;
                if (forward > 0.0F) forward = 1.0F;
                else if (forward < 0.0F) forward = -1.0F;
            }
            mc.thePlayer.motionX = forward * speed * Math.cos(Math.toRadians(yaw + 90.0F)) + strafe * speed * Math.sin(Math.toRadians(yaw + 90.0F));
            mc.thePlayer.motionZ = forward * speed * Math.sin(Math.toRadians(yaw + 90.0F)) - strafe * speed * Math.cos(Math.toRadians(yaw + 90.0F));
        }
    }
}
