// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\FreeCam.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.entity.EntityFakePlayer;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import org.lwjgl.input.Keyboard;

public class FreeCam extends Module {

    // --- Поля для настроек ---
    private SliderSetting speedSetting;

    // --- Внутренние переменные ---
    private EntityFakePlayer freecamEntity;

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

        // Создаем нашу фейковую сущность-камеру на месте игрока
        freecamEntity = new EntityFakePlayer(mc.theWorld, mc.thePlayer.getGameProfile());
        freecamEntity.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        freecamEntity.inventory.copyInventory(mc.thePlayer.inventory);
        freecamEntity.setSneaking(mc.thePlayer.isSneaking());
        mc.theWorld.addEntityToWorld(-100, freecamEntity); // Используем уникальный ID, чтобы избежать конфликтов

        // Самое главное: говорим Minecraft рендерить мир от лица нашей камеры
        mc.renderViewEntity = freecamEntity;
    }

    @Override
    public void onDisable() {
        // Возвращаем рендер на реального игрока
        if (mc.thePlayer != null) {
            mc.renderViewEntity = mc.thePlayer;
        }

        // Удаляем фейковую сущность из мира
        if (freecamEntity != null) {
            mc.theWorld.removeEntityFromWorld(freecamEntity.getEntityId());
            freecamEntity = null;
        }
    }

    @Override
    public void onUpdate() {
        if (freecamEntity == null) return;

        // Движение камеры больше не затрагивает реального игрока
        freecamEntity.noClip = true;
        freecamEntity.fallDistance = 0;
        freecamEntity.onGround = false;

        // Используем нашу кастомную логику движения из EntityFakePlayer,
        // передавая ей скорость из настроек.
        freecamEntity.updateMovement((float) speedSetting.value);

        // Копируем повороты головы с реального игрока на фейкового для плавности
        freecamEntity.rotationYaw = mc.thePlayer.rotationYaw;
        freecamEntity.rotationPitch = mc.thePlayer.rotationPitch;
        freecamEntity.rotationYawHead = mc.thePlayer.rotationYawHead;
    }
}