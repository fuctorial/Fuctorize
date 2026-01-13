// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\NoWeb.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import ru.fuctorial.fuctorize.utils.ReflectionUtils;
import org.lwjgl.input.Keyboard;

public class NoWeb extends Module {

    public NoWeb(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("noweb", Lang.get("module.noweb.name"), Category.MOVEMENT);
        addSetting(new BindSetting(Lang.get("module.noweb.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.noweb.desc");
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null) {
            return;
        }
        ReflectionUtils.setInWeb(mc.thePlayer, false);
    }
}