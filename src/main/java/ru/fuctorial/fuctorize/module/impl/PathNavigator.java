package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.pathnav.GuiPathNavigator;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import org.lwjgl.input.Keyboard;

public class PathNavigator extends Module {

    public PathNavigator(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("pathnavigator", "Path Navigator", Category.MOVEMENT, ActivationType.SINGLE);
        addSetting(new BindSetting("Open GUI", Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return "Opens the Pathfinding GUI.";
    }

    @Override
    public void onEnable() {
        mc.displayGuiScreen(new GuiPathNavigator(mc.currentScreen));
        toggle();
    }
}