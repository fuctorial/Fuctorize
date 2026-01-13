package ru.fuctorial.fuctorize.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

public class RotationUtils {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * Вычисляет необходимые углы (yaw, pitch) для того, чтобы посмотреть на сущность.
     * @param entity Целевая сущность.
     * @return Массив [yaw, pitch].
     */
    public static float[] getRotations(EntityLivingBase entity) {
        if (entity == null) {
            return null;
        }

        double diffX = entity.posX - mc.thePlayer.posX;
        double diffZ = entity.posZ - mc.thePlayer.posZ;
        double diffY = entity.posY + entity.getEyeHeight() * 0.9 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());

        double dist = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(diffY, dist) * 180.0D / Math.PI);

        return new float[]{yaw, pitch};
    }

    /**
     * Плавно поворачивает игрока к цели.
     * @param target Целевая сущность.
     * @param smoothness Фактор сглаживания (чем выше, тем медленнее поворот).
     */
    public static void faceEntity(EntityLivingBase target, float smoothness) {
        float[] rotations = getRotations(target);
        if (rotations != null) {
            mc.thePlayer.rotationYaw = smoothRotation(mc.thePlayer.rotationYaw, rotations[0], smoothness);
            mc.thePlayer.rotationPitch = smoothRotation(mc.thePlayer.rotationPitch, rotations[1], smoothness);
        }
    }

    /**
     * Вспомогательный метод для плавного изменения угла.
     */
    private static float smoothRotation(float current, float target, float speed) {
        float diff = MathHelper.wrapAngleTo180_float(target - current);
        if (diff > speed) {
            diff = speed;
        }
        if (diff < -speed) {
            diff = -speed;
        }
        return current + diff;
    }
}