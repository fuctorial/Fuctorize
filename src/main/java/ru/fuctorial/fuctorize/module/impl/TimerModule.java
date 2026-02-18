 
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.relauncher.ReflectionHelper;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

public class TimerModule extends Module {

    private static Field mcTimerField;
    private static Field timerSpeedField;

    private SliderSetting speedSetting;

    public TimerModule(FuctorizeClient client) {
        super(client);
        initializeReflection();
    }

    @Override
    public void init() {
        setMetadata("timer", Lang.get("module.timer.name"), Category.MOVEMENT);

        speedSetting = new SliderSetting(Lang.get("module.timer.setting.speed"), 2.5, 0.1, 100.0, 0.1);
        addSetting(speedSetting);
        addSetting(new BindSetting(Lang.get("module.timer.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.timer.desc");
    }

    private void initializeReflection() {
        if (mcTimerField != null && timerSpeedField != null) return;
        try {
            mcTimerField = ReflectionHelper.findField(Minecraft.class, "field_71428_T", "timer");
            mcTimerField.setAccessible(true);
            System.out.println("Fuctorize/Timer: Successfully reflected Minecraft.timer field!");

            timerSpeedField = ReflectionHelper.findField(Timer.class, "field_74278_d", "timerSpeed");
            timerSpeedField.setAccessible(true);
            System.out.println("Fuctorize/Timer: Successfully reflected Timer.timerSpeed field!");

        } catch (Exception e) {
            System.err.println("Fuctorize/Timer: CRITICAL - Could not find required fields via reflection. Module will be disabled.");
            e.printStackTrace();
            mcTimerField = null;
            timerSpeedField = null;
        }
    }

    @Override
    public void onUpdate() {
        setTimerSpeed((float) speedSetting.value);
    }

    @Override
    public void onEnable() {
        setTimerSpeed((float) speedSetting.value);
    }

    @Override
    public void onDisable() {
        setTimerSpeed(1.0F);
    }

    private void setTimerSpeed(float speed) {
        if (mcTimerField == null || timerSpeedField == null) return;
        try {
            Timer mcTimer = (Timer) mcTimerField.get(mc);
            timerSpeedField.setFloat(mcTimer, speed);
        } catch (IllegalAccessException e) {
            System.err.println("Fuctorize/Timer: Failed to set timer speed due to access exception!");
            e.printStackTrace();
        }
    }
}
