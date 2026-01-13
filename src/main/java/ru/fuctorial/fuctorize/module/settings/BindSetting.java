package ru.fuctorial.fuctorize.module.settings;

import ru.fuctorial.fuctorize.module.Module;
import org.lwjgl.input.Keyboard;


// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\module\settings\BindSetting.java




public class BindSetting extends Setting {
    public int keyCode;
    private boolean wasPressed = false;

    public BindSetting(String name, int defaultKeyCode) {
        this.name = name;
        this.keyCode = defaultKeyCode;
    }

    public String getKeyName() {
        // --- ИЗМЕНЕНИЕ ЗДЕСЬ ---
        // Если keyCode равен 0 (KEY_NONE), возвращаем пустую строку.
        // В противном случае, возвращаем имя клавиши.
        return this.keyCode == Keyboard.KEY_NONE ? "" : Keyboard.getKeyName(this.keyCode);
    }

    public boolean isPressedOnce() {
        if (this.keyCode == Keyboard.KEY_NONE) return false;
        boolean isKeyDown = Keyboard.isKeyDown(this.keyCode);
        if (isKeyDown && !wasPressed) {
            wasPressed = true;
            return true;
        }
        if (!isKeyDown) {
            wasPressed = false;
        }
        return false;
    }

    public static BindSetting getBindSetting(Module module) {
        for (Setting setting : module.getSettings()) {
            if (setting instanceof BindSetting) {
                return (BindSetting) setting;
            }
        }
        return null;
    }
}