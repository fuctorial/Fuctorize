// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\LookTP.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.PlayerUtils;
import net.minecraft.block.BlockAir;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

public class LookTP extends Module {

    private SliderSetting maxDistance;

    private static final int MAX_PACKET_STEPS = 1000;
    private static final double PACKET_STEP_DISTANCE = 5.0;

    public LookTP(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("looktp", Lang.get("module.looktp.name"), Category.MOVEMENT, ActivationType.SINGLE);

        maxDistance = new SliderSetting(Lang.get("module.looktp.setting.max_distance"), 100.0, 1.0, 1000.0, 1.0);
        addSetting(maxDistance);
        addSetting(new BindSetting(Lang.get("module.looktp.setting.key"), Keyboard.KEY_G));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.looktp.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null && mc.theWorld != null && mc.getNetHandler() != null) {
            performTeleport();
        }
        toggle();
    }

    private void performTeleport() {
        double distance = this.maxDistance.value;
        MovingObjectPosition mop = PlayerUtils.rayTrace(distance);

        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            client.notificationManager.show(new Notification(
                    Lang.get("notification.looktp.title.fail"),
                    Lang.get("notification.looktp.message.no_block") + (int)distance + "m.",
                    Notification.NotificationType.ERROR,
                    3000L
            ));
            return;
        }

        double startX = mc.thePlayer.posX;
        double startY = mc.thePlayer.posY;
        double startZ = mc.thePlayer.posZ;

        double targetX = mop.blockX + 0.5;
        double targetZ = mop.blockZ + 0.5;

        boolean isSpaceAbove = (PlayerUtils.getBlock(mop.blockX, mop.blockY + 1, mop.blockZ) instanceof BlockAir) &&
                (PlayerUtils.getBlock(mop.blockX, mop.blockY + 2, mop.blockZ) instanceof BlockAir);

        double targetY = isSpaceAbove ? (mop.blockY + 1.0) : (mop.blockY + 1.0);

        double deltaX = targetX - startX;
        double deltaY = targetY - startY;
        double deltaZ = targetZ - startZ;
        double totalDistance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

        sendTeleportPackets(startX, startY, startZ, targetX, targetY, targetZ, totalDistance);

        mc.thePlayer.setPosition(targetX, targetY, targetZ);
        mc.thePlayer.fallDistance = 0f;

        client.notificationManager.show(new Notification(
                Lang.get("notification.looktp.title.success"),
                Lang.get("notification.looktp.message.moved_to") + String.format("%.0f, %.0f, %.0f", targetX, targetY, targetZ),
                Notification.NotificationType.SUCCESS,
                2500L
        ));
    }

    private void sendTeleportPackets(double startX, double startY, double startZ, double targetX, double targetY, double targetZ, double totalDistance) {
        if (totalDistance < PACKET_STEP_DISTANCE) {
            NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(
                    targetX, targetY, targetY + mc.thePlayer.getEyeHeight(), targetZ, true
            ));
        } else {
            int numSteps = (int) Math.min(MAX_PACKET_STEPS, Math.ceil(totalDistance / PACKET_STEP_DISTANCE));

            double stepX = (targetX - startX) / numSteps;
            double stepY = (targetY - startY) / numSteps;
            double stepZ = (targetZ - startZ) / numSteps;

            double currentX = startX, currentY = startY, currentZ = startZ;

            for (int i = 0; i < numSteps; i++) {
                currentX += stepX;
                currentY += stepY;
                currentZ += stepZ;
                NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(
                        currentX, currentY, currentY + mc.thePlayer.getEyeHeight(), currentZ, true
                ));
            }
        }
    }
}