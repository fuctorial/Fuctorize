 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.widgets.GuiObjectInspector;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import org.lwjgl.input.Keyboard;

public class GuiInspector extends Module {

    public GuiInspector(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("guiinspector", Lang.get("module.guiinspector.name"), Category.MISC, ActivationType.SINGLE);
        addSetting(new BindSetting(Lang.get("module.guiinspector.setting.activate"), Keyboard.KEY_I));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.guiinspector.desc");
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen != null) {
             
            mc.displayGuiScreen(new GuiObjectInspector(mc.currentScreen));
        } else {
             
            client.notificationManager.show(new ru.fuctorial.fuctorize.client.hud.Notification(
                    Lang.get("notification.guiinspector.title"),
                    Lang.get("notification.guiinspector.message"),
                    ru.fuctorial.fuctorize.client.hud.Notification.NotificationType.WARNING,
                    2500L
            ));
        }
         
        toggle();
    }
}