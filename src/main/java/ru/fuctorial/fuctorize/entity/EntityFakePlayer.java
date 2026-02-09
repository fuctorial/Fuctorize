// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\entity\EntityFakePlayer.java
package ru.fuctorial.fuctorize.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public class EntityFakePlayer extends EntityOtherPlayerMP {

    private final Minecraft mc = Minecraft.getMinecraft();

    public EntityFakePlayer(World world, GameProfile gameProfile) {
        super(world, gameProfile);
        // Убираем смещение модели, чтобы камера была на правильной высоте
        this.yOffset = 0.0F;
        // Включаем noclip для свободного прохода сквозь стены
        this.noClip = true;
    }

    @Override
    public float getEyeHeight() {
        return 1.62F;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    public void updateMovement(float speed) {
        // Сбрасываем физику
        this.motionX = 0;
        this.motionY = 0;
        this.motionZ = 0;

        // 1. Считываем нажатия клавиш НАПРЯМУЮ, игнорируя состояние реального игрока
        float forward = 0.0F;
        float strafe = 0.0F;

        if (mc.gameSettings.keyBindForward.getIsKeyPressed()) forward++;
        if (mc.gameSettings.keyBindBack.getIsKeyPressed()) forward--;
        if (mc.gameSettings.keyBindLeft.getIsKeyPressed()) strafe++;
        if (mc.gameSettings.keyBindRight.getIsKeyPressed()) strafe--;

        // 2. Вертикальное движение (Space / Shift)
        if (mc.gameSettings.keyBindJump.getIsKeyPressed()) {
            this.motionY += speed;
        }
        if (mc.gameSettings.keyBindSneak.getIsKeyPressed()) {
            this.motionY -= speed;
        }

        // 3. Расчет вектора движения
        // Используем yaw самого фейка, а не игрока
        float yaw = this.rotationYaw;

        if (forward != 0.0F || strafe != 0.0F) {
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

            double cos = Math.cos(Math.toRadians(yaw + 90.0F));
            double sin = Math.sin(Math.toRadians(yaw + 90.0F));

            this.motionX = forward * speed * cos + strafe * speed * sin;
            this.motionZ = forward * speed * sin - strafe * speed * cos;
        }

        // 4. Применяем движение
        this.setPosition(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
    }

    @Override
    public void onUpdate() {
        // Отключаем ванильную интерполяцию позиции, чтобы камера не дергалась
        this.lastTickPosX = this.posX;
        this.lastTickPosY = this.posY;
        this.lastTickPosZ = this.posZ;

        super.onUpdate();
    }

    @Override
    public void onLivingUpdate() {
        // Блокируем стандартную физику EntityLivingBase (гравитацию, коллизии)
    }
}