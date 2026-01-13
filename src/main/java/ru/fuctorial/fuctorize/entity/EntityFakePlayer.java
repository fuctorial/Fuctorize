package ru.fuctorial.fuctorize.entity;

import com.mojang.authlib.GameProfile;
import ru.fuctorial.fuctorize.utils.Wrapper;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Продвинутая фейковая сущность игрока для FreeCam и Blink.
 * Имеет собственный полет, неуязвимость и NoClip.
 */
public class EntityFakePlayer extends EntityOtherPlayerMP {

    public EntityFakePlayer(World world, GameProfile gameProfile) {
        super(world, gameProfile);
        // --- ИСПРАВЛЕНИЕ: Убираем смещение модели ---
        // Это опустит модель на землю, убрав эффект "парения"
        this.yOffset = 0.0F;
    }

    // --- ИСПРАВЛЕНИЕ: Восстанавливаем высоту глаз ---
    // Так как мы обнулили yOffset, камера FreeCam упала бы в ноги.
    // Возвращаем её обратно на высоту 1.62.
    @Override
    public float getEyeHeight() {
        return 1.62F;
    }

    // Переопределяем, чтобы сделать его неуязвимым к любому урону
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    // Главный метод движения нашей "камеры"
    public void updateMovement(float speed) {
        // Отключаем гравитацию
        this.motionY = 0;

        // Движение вверх/вниз
        if (Wrapper.INSTANCE.getGameSettings().keyBindJump.getIsKeyPressed()) {
            this.motionY = speed;
        }
        if (Wrapper.INSTANCE.getGameSettings().keyBindSneak.getIsKeyPressed()) {
            this.motionY = -speed;
        }

        // Движение вперед/назад/вбок с учетом угла камеры
        float forward = Wrapper.INSTANCE.getPlayer().moveForward;
        float strafe = Wrapper.INSTANCE.getPlayer().moveStrafing;
        float yaw = Wrapper.INSTANCE.getPlayer().rotationYaw;

        if (forward == 0.0F && strafe == 0.0F) {
            this.motionX = 0.0D;
            this.motionZ = 0.0D;
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
            this.motionX = forward * speed * Math.cos(Math.toRadians(yaw + 90.0F))
                    + strafe * speed * Math.sin(Math.toRadians(yaw + 90.0F));
            this.motionZ = forward * speed * Math.sin(Math.toRadians(yaw + 90.0F))
                    - strafe * speed * Math.cos(Math.toRadians(yaw + 90.0F));
        }

        // Применяем движение к позиции
        this.setPosition(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
    }

    // --- ИСПРАВЛЕНИЕ: Отключаем интерполяцию ---
    // EntityOtherPlayerMP пытается сглаживать движение на основе пакетов сервера.
    // Для локального фейка это не нужно и может вызывать дергание.
    @Override
    public void onUpdate() {
        // Сбрасываем логику интерполяции, приравнивая "предыдущую" позицию к текущей
        this.lastTickPosX = this.posX;
        this.lastTickPosY = this.posY;
        this.lastTickPosZ = this.posZ;

        // Выполняем базовое обновление (анимации, тики), но пропускаем логику OtherPlayerMP
        super.onUpdate();

        // Принудительно синхронизируем boundingBox с позицией
        this.setPosition(this.posX, this.posY, this.posZ);
    }

    // Мы не хотим, чтобы стандартная логика движения работала,
    // поэтому переопределяем и оставляем пустым.
    @Override
    public void onLivingUpdate() {
        // Пусто, чтобы отключить гравитацию и коллизии ванильного клиента
    }
}