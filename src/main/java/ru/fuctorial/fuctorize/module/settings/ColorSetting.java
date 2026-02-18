package ru.fuctorial.fuctorize.module.settings;

import ru.fuctorial.fuctorize.utils.AnimationUtils;  
import java.awt.Color;


 





public class ColorSetting extends Setting {
    public int color;
    public boolean expanded = false;

     
    public final AnimationUtils animation;

    public ColorSetting(String name, Color defaultColor) {
        this.name = name;
        this.color = defaultColor.getRGB();
         
        this.animation = new AnimationUtils(200);
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }
}