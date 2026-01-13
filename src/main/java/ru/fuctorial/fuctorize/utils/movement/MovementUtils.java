package ru.fuctorial.fuctorize.utils.movement;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public final class MovementUtils {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();

    private MovementUtils() {}

    /**
     * Плавно поворачивает камеру игрока на ЦЕНТР указанного блока.
     */
    public static void lookAtBlock(int x, int y, int z) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        // Целимся ровно в центр блока
        double targetX = x + 0.5D;
        double targetY = y + 0.5D;
        double targetZ = z + 0.5D;

        // --- ФИКС ЗДЕСЬ: Используем posY вместо boundingBox.minY ---
        // boundingBox может отставать или быть неточным в момент тика.
        // posY + getEyeHeight() — это стандарт для определения позиции глаз.
        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;

        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;

        // Горизонтальная дистанция
        double dist = Math.sqrt(dx * dx + dz * dz);

        // === РАСЧЕТ УГЛОВ ===
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        // Ограничение углов
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

        // Применяем поворот.
        // Убрали плавность для мгновенной и точной наводки при копании,
        // так как задержка поворота тоже может вызвать удар "не туда".
        player.rotationYaw = yaw;
        player.rotationYawHead = yaw;
        player.rotationPitch = pitch;
    }

    public static void updateMovementOnly(Vec3 moveTarget) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;
        double dx = moveTarget.xCoord - player.posX;
        double dz = moveTarget.zCoord - player.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.05) {
            double ndx = dx / dist;
            double ndz = dz / dist;
            double speed = player.isSprinting() ? 0.28 : 0.22;
            player.motionX = ndx * speed;
            player.motionZ = ndz * speed;
        }
    }

    public static void stopMovement() {
        if (mc.thePlayer != null) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }
    }

    public static void jump() {
        if (mc.thePlayer != null && mc.thePlayer.onGround) mc.thePlayer.jump();
    }

    public static void lookAt(Vec3 target) {
        if (target == null || mc.thePlayer == null) return;
        // Перенаправляем на корректный метод
        lookAtBlock(MathHelper.floor_double(target.xCoord), MathHelper.floor_double(target.yCoord), MathHelper.floor_double(target.zCoord));
    }
}