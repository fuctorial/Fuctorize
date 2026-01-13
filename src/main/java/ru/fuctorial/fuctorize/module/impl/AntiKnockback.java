package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import org.lwjgl.input.Keyboard;

public class AntiKnockback extends Module {

    public AntiKnockback(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("antiknockback", Lang.get("module.antiknockback.name"), Category.MOVEMENT);
        addSetting(new BindSetting(Lang.get("module.antiknockback.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.antiknockback.desc");
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.thePlayer == null) return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.func_149412_c() == mc.thePlayer.getEntityId()) {
                event.setCanceled(true);
            }
        }

        if (event.getPacket() instanceof S27PacketExplosion) {
            event.setCanceled(true);
        }
    }
}