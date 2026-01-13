// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\SmartMovingConfigModule.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.Loader;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.smartmoving.GuiSmartMovingEditor;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.ChatUtils;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

public class SmartMovingConfigModule extends Module {

    private static Object smartMovingOptionsInstance;
    private static boolean isSmartMovingLoaded = false;

    public SmartMovingConfigModule(FuctorizeClient client) {
        super(client);
        isSmartMovingLoaded = Loader.isModLoaded("SmartMoving");
    }

    @Override
    public void init() {
        setMetadata("smartmovingconfig", Lang.get("module.smartmovingconfig.name"), Category.MISC, ActivationType.SINGLE);
        addSetting(new BindSetting(Lang.get("module.smartmovingconfig.setting.bind"), Keyboard.KEY_NONE));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.smartmovingconfig.desc");
    }

    public static Object getOptionsInstance() {
        return smartMovingOptionsInstance;
    }

    @Override
    public void onEnable() {
        if (!isSmartMovingLoaded) {
            ChatUtils.printMessage(EnumChatFormatting.RED + Lang.get("chat.smartmovingconfig.mod_not_found"));
            toggle();
            return;
        }

        if (smartMovingOptionsInstance == null) {
            try {
                Class<?> smContextClass = Class.forName("net.smart.moving.SmartMovingContext");
                Field optionsField = smContextClass.getDeclaredField("Options");
                optionsField.setAccessible(true);
                smartMovingOptionsInstance = optionsField.get(null);
            } catch (Exception e) {
                ChatUtils.printMessage(EnumChatFormatting.RED + Lang.get("chat.smartmovingconfig.reflection_error"));
                e.printStackTrace();
                toggle();
                return;
            }
        }

        mc.displayGuiScreen(new GuiSmartMovingEditor(smartMovingOptionsInstance));
        toggle();
    }
}
