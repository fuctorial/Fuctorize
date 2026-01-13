// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\Fullbright.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import org.lwjgl.input.Keyboard;

public class Fullbright extends Module {

    // Используем значение, которое точно не будет у gammaSetting,
    // чтобы надежно определять, сохранили ли мы значение.
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
        // Если мы еще не сохранили оригинальную яркость, делаем это.
        // Это сработает в первый же тик после включения модуля.
        if (this.oldGamma == -1.0f) {
            if (mc.gameSettings != null) {
                this.oldGamma = mc.gameSettings.gammaSetting;
            }
        }
        // Постоянно поддерживаем яркость на нужном уровне.
        if (mc.gameSettings != null) {
            mc.gameSettings.gammaSetting = 100f;
        }
    }

    @Override
    public void onDisable() {
        // Если мы сохраняли оригинальную яркость, возвращаем ее.
        if (this.oldGamma != -1.0f && mc.gameSettings != null) {
            mc.gameSettings.gammaSetting = this.oldGamma;
        }
        // Сбрасываем сохраненное значение, чтобы при следующем включении
        // мы снова взяли актуальную яркость на тот момент.
        this.oldGamma = -1.0f;
    }
}