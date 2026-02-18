 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import org.lwjgl.input.Keyboard;

public class Fullbright extends Module {

     
     
    private float oldGamma = -1.0f;

    public Fullbright(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("fullbright", Lang.get("module.fullbright.name"), Category.RENDER);
        addSetting(new BindSetting(Lang.get("module.fullbright.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.fullbright.desc");
    }

    @Override
    public void onUpdate() {
         
         
        if (this.oldGamma == -1.0f) {
            if (mc.gameSettings != null) {
                this.oldGamma = mc.gameSettings.gammaSetting;
            }
        }
         
        if (mc.gameSettings != null) {
            mc.gameSettings.gammaSetting = 100f;
        }
    }

    @Override
    public void onDisable() {
         
        if (this.oldGamma != -1.0f && mc.gameSettings != null) {
            mc.gameSettings.gammaSetting = this.oldGamma;
        }
         
         
        this.oldGamma = -1.0f;
    }
}