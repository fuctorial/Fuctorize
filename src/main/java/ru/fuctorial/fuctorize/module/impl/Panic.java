 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.Fuctorize;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import org.lwjgl.input.Keyboard;

public class Panic extends Module {

    public Panic(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("panic", Lang.get("module.panic.name"), Category.MISC, ActivationType.SINGLE);
        addSetting(new BindSetting(Lang.get("module.panic.setting.bind"), Keyboard.KEY_NONE));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.panic.desc");
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
        }
        Fuctorize.stop();
    }
}