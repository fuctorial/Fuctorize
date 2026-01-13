// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\WorldSelector.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiSelectWorld;
import org.lwjgl.input.Keyboard;

public class WorldSelector extends Module {

    public WorldSelector(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("worldselector", Lang.get("module.worldselector.name"), Category.MISC, ActivationType.SINGLE);
        addSetting(new BindSetting(Lang.get("module.worldselector.setting.bind"), Keyboard.KEY_NONE));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.worldselector.desc");
    }

    @Override
    public void onEnable() {
        if (mc != null) {
            mc.displayGuiScreen(new GuiSelectWorld(new GuiMainMenu()));
        }
        toggle();
    }
}
