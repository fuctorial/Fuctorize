// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\Suicide.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import ru.fuctorial.fuctorize.utils.NetUtils;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.lwjgl.input.Keyboard;

public class Suicide extends Module {

    public Suicide(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("suicide", Lang.get("module.suicide.name"), Category.PLAYER, ActivationType.SINGLE);
        addSetting(new BindSetting(Lang.get("module.suicide.setting.bind"), Keyboard.KEY_NONE));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.suicide.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            toggle();
            return;
        }

        new Thread(() -> {
            final double startX = mc.thePlayer.posX;
            final double startY = mc.thePlayer.posY;
            final double startZ = mc.thePlayer.posZ;
            final double groundY = -5.0;
            final double highY = startY + 256;

            NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(startX, highY, highY, startZ, false));
            NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(startX, groundY, groundY, startZ, true));
        }, "SuicideThread").start();

        toggle();
    }
}
