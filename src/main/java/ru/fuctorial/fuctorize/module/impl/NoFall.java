// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\NoFall.java
package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.network.play.client.C03PacketPlayer;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;

public class NoFall extends Module {

    public NoFall(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("nofall", Lang.get("module.nofall.name"), Category.MOVEMENT);
        addSetting(new BindSetting(Lang.get("module.nofall.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.nofall.desc");
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.thePlayer.fallDistance <= 2.5f) {
            return;
        }
        NetUtils.sendPacket(new C03PacketPlayer(true));
    }
}
