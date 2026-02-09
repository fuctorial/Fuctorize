package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import org.lwjgl.input.Keyboard;

public class VClip extends Module {
    private SliderSetting height;

    public VClip(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("vclip", Lang.get("module.vclip.name"), Category.EXPLOIT, ActivationType.SINGLE);
        height = new SliderSetting(Lang.get("module.vclip.setting.height"), 3.0, -9.0, 9.0, 1.0);
        addSetting(height);
        addSetting(new BindSetting(Lang.get("module.vclip.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.vclip.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            // Только перемещение сущности. NoFall подхватит изменения в пакете движения.
            mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + height.value, mc.thePlayer.posZ);
        }
        toggle();
    }
}