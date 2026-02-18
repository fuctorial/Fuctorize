 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.sniffer.GuiFavoriteScreens;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import org.lwjgl.input.Keyboard;

public class FavoriteScreen extends Module {
    public FavoriteScreen(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("favoritescreen", Lang.get("module.favoritescreen.name"), Category.MISC, ActivationType.SINGLE);
        addSetting(new BindSetting(Lang.get("module.favoritescreen.setting.open_favorites"), Keyboard.KEY_NONE));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.favoritescreen.desc");
    }

    @Override
    public void onEnable() {
        mc.displayGuiScreen(new GuiFavoriteScreens());
        toggle();
    }
}