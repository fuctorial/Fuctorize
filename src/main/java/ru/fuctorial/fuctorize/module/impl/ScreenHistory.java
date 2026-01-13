// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\ScreenHistory.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.sniffer.GuiScreenHistory;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import org.lwjgl.input.Keyboard;

public class ScreenHistory extends Module {

    private static SliderSetting historyLimit;

    public ScreenHistory(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("screenhistory", Lang.get("module.screenhistory.name"), Category.MISC, ActivationType.SINGLE);

        historyLimit = new SliderSetting(Lang.get("module.screenhistory.setting.history_limit"), 100.0, 10.0, 1000.0, 1.0);
        addSetting(historyLimit);

        addSetting(new BindSetting(Lang.get("module.screenhistory.setting.open_history"), Keyboard.KEY_NONE));

        setShowInHud(false);
    }

    public static int getHistoryLimit() {
        if (historyLimit != null) {
            return (int) historyLimit.value;
        }
        return 100;
    }

    @Override
    public String getDescription() {
        return Lang.get("module.screenhistory.desc");
    }

    @Override
    public void onEnable() {
        mc.displayGuiScreen(new GuiScreenHistory());
        toggle();
    }
}
