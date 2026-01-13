package ru.fuctorial.fuctorize.module.settings;

import ru.fuctorial.fuctorize.utils.AnimationUtils; // <-- ИМПОРТ
import java.awt.Color;


// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\module\settings\ColorSetting.java





public class ColorSetting extends Setting {
    public int color;
    public boolean expanded = false;

    // --- ИЗМЕНЕНИЕ ЗДЕСЬ: Добавляем поле для анимации ---
    public final AnimationUtils animation;

    public ColorSetting(String name, Color defaultColor) {
        this.name = name;
        this.color = defaultColor.getRGB();
        // --- ИЗМЕНЕНИЕ ЗДЕСЬ: Инициализируем анимацию ---
        this.animation = new AnimationUtils(200);
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }
}