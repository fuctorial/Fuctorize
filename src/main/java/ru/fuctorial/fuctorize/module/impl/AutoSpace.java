package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;

public class AutoSpace extends Module {

    public AutoSpace(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("autospace", "Auto Space", Category.MOVEMENT);
        addSetting(new BindSetting("Bind", Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return "Automatically holds the jump key.";
    }

    @Override
    public void onUpdate() {
        // Принудительно зажимаем пробел каждый тик.
        // Это работает даже если открыт GUI (инвентарь, торговец и т.д.)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
    }

    @Override
    public void onDisable() {
        // Обязательно отпускаем кнопку при выключении, иначе игрок продолжит прыгать
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }

    @Override
    public void onDisconnect() {
        // Выключаем модуль при выходе из мира
        if (this.isEnabled()) {
            this.toggle();
        }
    }
}