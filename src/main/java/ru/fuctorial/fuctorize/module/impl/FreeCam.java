// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\FreeCam.java
package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.entity.EntityFakePlayer;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;

public class FreeCam extends Module {

    private SliderSetting speedSetting;
    private EntityFakePlayer freecamEntity;

    // Сохраняем позицию, где игрок вошел в FreeCam
    private double startX, startY, startZ;
    private float startYaw, startPitch;

    public FreeCam(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("freecam", Lang.get("module.freecam.name"), Category.PLAYER);
        speedSetting = new SliderSetting(Lang.get("module.freecam.setting.speed"), 0.5, 0.1, 5.0, 0.1);
        addSetting(speedSetting);
        addSetting(new BindSetting(Lang.get("module.freecam.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.freecam.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            toggle();
            return;
        }

        // 1. Сохраняем реальную позицию
        startX = mc.thePlayer.posX;
        startY = mc.thePlayer.posY;
        startZ = mc.thePlayer.posZ;
        startYaw = mc.thePlayer.rotationYaw;
        startPitch = mc.thePlayer.rotationPitch;

        // 2. Создаем клона
        freecamEntity = new EntityFakePlayer(mc.theWorld, mc.thePlayer.getGameProfile());
        freecamEntity.setPositionAndRotation(startX, startY, startZ, startYaw, startPitch);
        freecamEntity.rotationYawHead = mc.thePlayer.rotationYawHead;
        freecamEntity.inventory.copyInventory(mc.thePlayer.inventory);
        freecamEntity.setSneaking(mc.thePlayer.isSneaking());

        mc.theWorld.addEntityToWorld(-100, freecamEntity);

        // 3. Переключаем рендер
        mc.renderViewEntity = freecamEntity;
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer == null) return;

        // 1. Возвращаем рендер
        mc.renderViewEntity = mc.thePlayer;

        // 2. Удаляем фейка
        if (freecamEntity != null) {
            mc.theWorld.removeEntityFromWorld(freecamEntity.getEntityId());
            freecamEntity = null;
        }

        // 3. Сбрасываем движение реального игрока, чтобы он не дернулся после выхода
        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionY = 0;
        mc.thePlayer.motionZ = 0;

        // Восстанавливаем поворот головы (опционально, если хотите вернуть прицел туда, где были)
        mc.thePlayer.rotationYaw = startYaw;
        mc.thePlayer.rotationPitch = startPitch;
    }

    @Override
    public void onUpdate() {
        if (freecamEntity == null || mc.thePlayer == null) return;

        // --- ЛОГИКА ФЕЙКА ---
        // Синхронизируем поворот камеры (мыши) с фейком.
        // Minecraft меняет mc.thePlayer.rotationYaw, когда мы двигаем мышкой.
        // Мы передаем это вращение фейку, чтобы камера крутилась.
        freecamEntity.rotationYaw = mc.thePlayer.rotationYaw;
        freecamEntity.rotationPitch = mc.thePlayer.rotationPitch;
        freecamEntity.rotationYawHead = mc.thePlayer.rotationYawHead;

        // Двигаем фейка (он читает клавиши сам)
        freecamEntity.updateMovement((float) speedSetting.value);

        // --- ЛОГИКА ОРИГИНАЛА ---
        // Жестко фиксируем позицию оригинала.
        // Мы НЕ блокируем вращение (rotationYaw), чтобы мышь работала плавно.
        // Но так как пакеты отменены (см. onPacketSend), сервер не узнает, что мы крутим головой.
        mc.thePlayer.setPosition(startX, startY, startZ);
        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionY = 0;
        mc.thePlayer.motionZ = 0;

        // Блокируем логические флаги движения у оригинала, чтобы не срабатывали анимации
        if (mc.thePlayer instanceof EntityPlayerSP) {
            EntityPlayerSP playerSP = (EntityPlayerSP) mc.thePlayer;
            playerSP.setSprinting(false);
            if (playerSP.movementInput != null) {
                playerSP.movementInput.moveForward = 0;
                playerSP.movementInput.moveStrafe = 0;
                playerSP.movementInput.jump = false;
                playerSP.movementInput.sneak = false;
            }
        }
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        // Блокируем отправку пакетов движения игрока, чтобы сервер видел нас стоящими на месте.
        // C03PacketPlayer включает в себя C04, C05, C06.
        if (event.getPacket() instanceof C03PacketPlayer) {
            event.setCanceled(true);
        }
    }
}