 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import ru.fuctorial.fuctorize.utils.PlayerUtils;
import org.lwjgl.input.Keyboard;

public class Sprint extends Module {

    public Sprint(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("sprint", Lang.get("module.sprint.name"), Category.MOVEMENT);
        addSetting(new BindSetting(Lang.get("module.sprint.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.sprint.desc");
    }

    @Override
    public void onUpdate() {
        if (PlayerUtils.isPlayerMoving() && PlayerUtils.canPlayerSprint()) {
            mc.thePlayer.setSprinting(true);
        }
    }
}
