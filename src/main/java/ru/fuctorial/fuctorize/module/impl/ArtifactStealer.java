// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\ArtifactStealer.java
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.registry.GameData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ArtifactStealer extends Module {

    private static Class<?> tileEntityArtifactClass;
    private static Method isEmptyMethod;
    private static Class<?> itemDetectorClass;
    private static Class<?> artifactItemClass; // Used to confirm successful steals by inventory check
    private static boolean reflectionSuccessful = false;

    private SliderSetting range;

    private final Queue<Packet> storedPackets = new ConcurrentLinkedQueue<>();
    private volatile boolean isStealing = false;
    private int baselineArtifactCount = -1;

    public ArtifactStealer(FuctorizeClient client) {
        super(client);
        initializeReflection();
    }

    @Override
    public void init() {
        setMetadata("artifactstealer", Lang.get("module.artifactstealer.name"), Category.PLAYER, ActivationType.TOGGLE);
        range = new SliderSetting(Lang.get("module.artifactstealer.setting.range"), 50.0, 4.0, 256.0, 1.0);
        addSetting(range);
        addSetting(new BindSetting(Lang.get("module.artifactstealer.setting.bind"), Keyboard.KEY_Y));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.artifactstealer.desc");
    }

    private void initializeReflection() {
        if (reflectionSuccessful) return;
        try {
            tileEntityArtifactClass = Class.forName("org.rhino.stalker.core.common.block.tileentity.TileEntityArtifact");
            isEmptyMethod = tileEntityArtifactClass.getMethod("isEmpty");
            itemDetectorClass = Class.forName("org.rhino.stalker.core.common.item.ItemDetector");
            try {
                artifactItemClass = Class.forName("org.rhino.stalker.core.common.item.ItemArtifact");
            } catch (ClassNotFoundException ignored) {
                artifactItemClass = null;
            }
            reflectionSuccessful = true;
        } catch (Exception e) {
            reflectionSuccessful = false;
        }
    }

    @Override
    public void onEnable() {
        if (isStealing) {
            notifyWarning(Lang.get("notification.artifactstealer.message.process_already_running"));
            return;
        }

        if (!reflectionSuccessful) {
            notifyError(Lang.get("notification.artifactstealer.message.stalker_mod_not_found"));
            toggle();
            return;
        }

        if (mc.thePlayer == null || mc.theWorld == null) {
            toggle();
            return;
        }

        TileEntity target = findClosestArtifact();
        if (target == null) {
            notifyInfo(Lang.get("notification.artifactstealer.message.no_artifacts_in_range"));
            toggle();
            return;
        }

        int detectorSlot = findBestDetectorSlot();
        if (detectorSlot == -1) {
            notifyError(Lang.get("notification.artifactstealer.message.detector_required"));
            toggle();
            return;
        }

        startStealProcess(target, detectorSlot);
    }

    @Override
    public void onUpdate() {
        if (isStealing && (mc.thePlayer == null || mc.thePlayer.isDead)) {
            finishStealProcess(false, Lang.get("notification.artifactstealer.message.interrupted_by_death"));
        }
    }

    @Override
    public void onDisable() {
        if (isStealing) {
            finishStealProcess(false, Lang.get("notification.artifactstealer.message.interrupted_by_user"));
        }
    }

    private void startStealProcess(TileEntity target, int detectorSlot) {
        isStealing = true;
        storedPackets.clear();

        notifyInfo(Lang.get("notification.artifactstealer.message.starting_steal"));

        // Capture baseline artifact count to confirm success AFTER flush
        baselineArtifactCount = countArtifactsInInventory();

        new Thread(() -> {
            try {
                double startX = mc.thePlayer.posX;
                double startY = mc.thePlayer.posY;
                double startZ = mc.thePlayer.posZ;

                double targetX = target.xCoord + 0.5;
                double targetY = target.yCoord;
                double targetZ = target.zCoord + 0.5;

                sendTeleportPackets(startX, startY, startZ, targetX, targetY, targetZ);

                int originalSlot = mc.thePlayer.inventory.currentItem;
                ItemStack detectorStack = mc.thePlayer.inventory.getStackInSlot(detectorSlot);

                NetUtils.sendPacket(new C09PacketHeldItemChange(detectorSlot));
                NetUtils.sendPacket(new C08PacketPlayerBlockPlacement(target.xCoord, target.yCoord, target.zCoord, 1, detectorStack, 0.5f, 1.0f, 0.5f));
                NetUtils.sendPacket(new C09PacketHeldItemChange(originalSlot));

                sendTeleportPackets(targetX, targetY, targetZ, startX, startY, startZ);

                // Flush first, then confirm after flush by checking inventory
                client.scheduleTask(() -> {
                    finishStealProcess(false, null);
                    confirmAfterFlush();
                });

            } catch (Exception e) {
                e.printStackTrace();
                client.scheduleTask(() -> finishStealProcess(false, Lang.get("notification.artifactstealer.message.process_error")));
            }
        }, "ArtifactStealerThread").start();
    }

    private void sendTeleportPackets(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        // Improved path simulation with obstacle-aware stepping
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
        double stepLen = 0.6; // small steps to better mimic walking
        int steps = Math.max((int) Math.ceil(distance / stepLen), 10);

        double curX = fromX;
        double curY = fromY;
        double curZ = fromZ;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double targetX = fromX + dx * t;
            double targetZ = fromZ + dz * t;

            double nextY = adjustYForObstacles(targetX, curY, targetZ);
            curX = targetX;
            curY = nextY;
            curZ = targetZ;

            boolean onGround = isOnGround(curX, curY, curZ);
            NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(curX, curY, curY + mc.thePlayer.getEyeHeight(), curZ, onGround));
        }
        boolean finalOnGround = isOnGround(toX, toY, toZ);
        NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(toX, toY, toY + mc.thePlayer.getEyeHeight(), toZ, finalOnGround));
    }

    private double adjustYForObstacles(double x, double currentY, double z) {
        // Try currentY first
        if (isSpaceFree(x, currentY, z)) return currentY;

        // Try stepping up up to 1.5 blocks
        for (int i = 1; i <= 3; i++) {
            double y = currentY + i * 0.5;
            if (isSpaceFree(x, y, z)) return y;
        }

        // Try stepping down up to 2.0 blocks
        for (int i = 1; i <= 4; i++) {
            double y = currentY - i * 0.5;
            if (isSpaceFree(x, y, z)) return y;
        }
        // As a fallback, return currentY
        return currentY;
    }

    private boolean isSpaceFree(double x, double y, double z) {
        if (mc == null || mc.theWorld == null) return true;
        // Check 4 corners within player width, for feet and head space
        double w = 0.3;
        return isAirColumn(x - w, y, z - w) &&
               isAirColumn(x - w, y, z + w) &&
               isAirColumn(x + w, y, z - w) &&
               isAirColumn(x + w, y, z + w);
    }

    private boolean isAirColumn(double x, double y, double z) {
        int bx = MathHelper.floor_double(x);
        int by = MathHelper.floor_double(y);
        int bz = MathHelper.floor_double(z);
        // Require two blocks of headroom
        return mc.theWorld.isAirBlock(bx, by, bz) && mc.theWorld.isAirBlock(bx, by + 1, bz);
    }

    private boolean isOnGround(double x, double y, double z) {
        if (mc == null || mc.theWorld == null) return false;
        int bx = MathHelper.floor_double(x);
        int by = MathHelper.floor_double(y - 0.01);
        int bz = MathHelper.floor_double(z);
        // If block below is not air, consider on ground
        return !mc.theWorld.isAirBlock(bx, by - 1, bz);
    }


    private void finishStealProcess(boolean success, String reason) {
        if (!isStealing) return;
        isStealing = false;

        if (mc.getNetHandler() != null) {
            Packet packet;
            while ((packet = storedPackets.poll()) != null) {
                mc.getNetHandler().addToSendQueue(packet);
            }
        }

        if (reason != null) {
            Notification.NotificationType type = success ? Notification.NotificationType.SUCCESS : Notification.NotificationType.WARNING;
            client.notificationManager.show(new Notification(Lang.get("notification.artifactstealer.title.info"), reason, type, 3000L));
        }

        if (this.isEnabled()) {
            toggle();
        }
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        if (isStealing) {
            Packet packet = event.getPacket();
            if (packet instanceof C03PacketPlayer || packet instanceof C0BPacketEntityAction ||
                    packet instanceof C0APacketAnimation || packet instanceof C02PacketUseEntity ||
                    packet instanceof C08PacketPlayerBlockPlacement || packet instanceof C09PacketHeldItemChange) {
                storedPackets.add(packet);
                event.setCanceled(true);
            }
        }
    }

    private TileEntity findClosestArtifact() {
        List<TileEntity> validArtifacts = new ArrayList<>();
        for (Object o : mc.theWorld.loadedTileEntityList) {
            if (tileEntityArtifactClass.isInstance(o)) {
                TileEntity te = (TileEntity) o;
                if (mc.thePlayer.getDistanceSq(te.xCoord + 0.5, te.yCoord + 0.5, te.zCoord + 0.5) <= range.value * range.value) {
                    try {
                        if (!(Boolean) isEmptyMethod.invoke(te)) {
                            validArtifacts.add(te);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (validArtifacts.isEmpty()) return null;

        validArtifacts.sort(Comparator.comparingDouble(te -> mc.thePlayer.getDistanceSq(te.xCoord + 0.5, te.yCoord + 0.5, te.zCoord + 0.5)));
        return validArtifacts.get(0);
    }

    private int findBestDetectorSlot() {
        int bestSlot = -1;
        int bestMeta = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && itemDetectorClass.isInstance(stack.getItem())) {
                if (stack.getItemDamage() > bestMeta) {
                    bestMeta = stack.getItemDamage();
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private void notifyError(String message) {
        client.notificationManager.show(new Notification(Lang.get("notification.artifactstealer.title.error"), message, Notification.NotificationType.ERROR, 3000L));
    }

    private void notifyInfo(String message) {
        client.notificationManager.show(new Notification(Lang.get("notification.artifactstealer.title.info"), message, Notification.NotificationType.INFO, 3000L));
    }
    private void notifyWarning(String message) {
        client.notificationManager.show(new Notification(Lang.get("notification.artifactstealer.title.warning"), message, Notification.NotificationType.WARNING, 2000L));
    }

    private int countArtifactsInInventory() {
        if (artifactItemClass == null || mc.thePlayer == null) return -1;
        int count = 0;
        for (int i = 0; i < mc.thePlayer.inventory.mainInventory.length; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && artifactItemClass.isInstance(s.getItem())) {
                count += s.stackSize;
            }
        }
        return count;
    }

    private void confirmAfterFlush() {
        if (artifactItemClass == null) {
            // Cannot confirm without knowing artifact class
            client.notificationManager.show(new Notification(
                    Lang.get("notification.artifactstealer.title.info"),
                    Lang.get("notification.artifactstealer.message.confirmation_unavailable"),
                    Notification.NotificationType.INFO, 3000L));
            return;
        }
        final int baseline = Math.max(0, baselineArtifactCount);
        new Thread(() -> {
            for (int i = 0; i < 30; i++) { // up to ~4.5s
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                int now = countArtifactsInInventory();
                if (now > baseline) {
                    client.scheduleTask(() -> client.notificationManager.show(new Notification(
                            Lang.get("notification.artifactstealer.title.info"),
                            Lang.get("notification.artifactstealer.message.attempt_successful"),
                            Notification.NotificationType.SUCCESS, 3000L)));
                    return;
                }
            }
            // Not confirmed within timeout
            client.scheduleTask(() -> client.notificationManager.show(new Notification(
                    Lang.get("notification.artifactstealer.title.info"),
                    Lang.get("notification.artifactstealer.message.not_confirmed"),
                    Notification.NotificationType.WARNING, 3000L)));
        }, "ArtifactStealerConfirm").start();
    }
}
