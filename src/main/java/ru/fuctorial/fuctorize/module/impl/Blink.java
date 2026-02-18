 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.entity.EntityFakePlayer;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import org.lwjgl.input.Keyboard;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Blink extends Module {

    private final Queue<Packet> storedPackets = new ConcurrentLinkedQueue<>();
    private EntityFakePlayer fakePlayer;

    public Blink(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("blink", Lang.get("module.blink.name"), Category.EXPLOIT);
        addSetting(new BindSetting(Lang.get("module.blink.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.blink.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            toggle();
            return;
        }

        fakePlayer = new EntityFakePlayer(mc.theWorld, mc.thePlayer.getGameProfile());
        fakePlayer.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        fakePlayer.inventory.copyInventory(mc.thePlayer.inventory);
        fakePlayer.setSneaking(mc.thePlayer.isSneaking());
        mc.theWorld.addEntityToWorld(-100, fakePlayer);

        storedPackets.clear();
    }

    @Override
    public void onDisable() {
        if (fakePlayer != null) {
            mc.theWorld.removeEntityFromWorld(fakePlayer.getEntityId());
            fakePlayer = null;
        }

        if (mc.getNetHandler() != null) {
            Packet packet;
            while ((packet = storedPackets.poll()) != null) {
                mc.getNetHandler().addToSendQueue(packet);
            }
        }
        storedPackets.clear();
    }

    @Override
    public String getName() {
        if (this.isEnabled() && !storedPackets.isEmpty()) {
            return super.getName() + " §7[" + storedPackets.size() + "]";
        }
        return super.getName();
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        Packet packet = event.getPacket();
        if (packet instanceof C03PacketPlayer ||
                packet instanceof C0BPacketEntityAction ||
                packet instanceof C0APacketAnimation ||
                packet instanceof C02PacketUseEntity ||
                packet instanceof C08PacketPlayerBlockPlacement ||
                packet instanceof C09PacketHeldItemChange) {

            storedPackets.add(packet);
            event.setCanceled(true);
        }
    }
}