// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\ExcaliburBypass.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import io.netty.buffer.ByteBuf;
import net.minecraft.network.Packet;
import org.lwjgl.input.Keyboard;

public class ExcaliburBypass extends Module {

    private BooleanSetting notify;
    private BooleanSetting fixTimer;

    private static final String ANTICHEAT_CHANNEL = "OptifineAPI";
    private long lastHandshakeRequestTime = 0;

    public ExcaliburBypass(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("excaliburbypass", Lang.get("module.excaliburbypass.name"), Category.EXPLOIT);
        notify = new BooleanSetting(Lang.get("module.excaliburbypass.setting.notify"), true);
        fixTimer = new BooleanSetting(Lang.get("module.excaliburbypass.setting.fix_timer"), true);
        addSetting(notify);
        addSetting(fixTimer);
        addSetting(new BindSetting(Lang.get("module.excaliburbypass.setting.bind"), Keyboard.KEY_NONE));
        this.setEnabledFromConfig(true);
        this.setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.excaliburbypass.desc");
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!fixTimer.enabled || !(event.getPacket() instanceof FMLProxyPacket)) {
            return;
        }
        FMLProxyPacket proxyPacket = (FMLProxyPacket) event.getPacket();
        if (!proxyPacket.channel().equals(ANTICHEAT_CHANNEL)) {
            return;
        }
        ByteBuf payload = proxyPacket.payload();
        if (payload.readableBytes() > 0 && payload.getByte(0) == 3) {
            lastHandshakeRequestTime = System.currentTimeMillis();
        }
    }

    @Override
    public boolean onPacketSendPre(Packet packet) {
        if (!(packet instanceof FMLProxyPacket)) {
            return false;
        }

        FMLProxyPacket proxyPacket = (FMLProxyPacket) packet;
        if (!proxyPacket.channel().equals(ANTICHEAT_CHANNEL)) {
            return false;
        }

        ByteBuf payload = proxyPacket.payload();
        if (payload == null || payload.readableBytes() < 1) {
            return false;
        }

        int messageId = payload.getByte(0);
        String violationType = null;
        boolean cancel = false;

        if (messageId == 0 || messageId == 2) {
            violationType = (messageId == 0) ? Lang.get("notification.excaliburbypass.type.violation_packet") : Lang.get("notification.excaliburbypass.type.out_of_bounds_packet");
            cancel = true; // Mark for cancellation
        } else if (fixTimer.enabled && messageId == 3 && lastHandshakeRequestTime != 0) {
            long timeSinceRequest = System.currentTimeMillis() - lastHandshakeRequestTime;
            if (timeSinceRequest < 950) {
                long delay = 1000 - timeSinceRequest;
                cancel = true; // Cancel the immediate send

                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        client.scheduleTask(() -> {
                            if (mc.getNetHandler() != null) {
                                ru.fuctorial.fuctorize.utils.NetUtils.sendPacket(packet);
                            }
                        });
                    }
                }, delay);

                if (notify.enabled) {
                    client.notificationManager.showReplacing(new Notification(Lang.get("notification.excaliburbypass.title.timer_fix"), Lang.get("notification.excaliburbypass.message.delay") + delay + "ms", Notification.NotificationType.SUCCESS, 2000L));
                }
            }
            lastHandshakeRequestTime = 0;
        }

        if (cancel && violationType != null && notify.enabled) {
            client.notificationManager.showReplacing(new Notification(Lang.get("notification.excaliburbypass.title.ac_bypass"), Lang.get("notification.excaliburbypass.message.packet_blocked") + violationType, Notification.NotificationType.INFO, 2000L));
        }

        return cancel; // Return true to cancel, false to allow
    }

    // onPacketSend is now empty because cancellation logic is in onPacketSendPre
    @Override
    public void onPacketSend(PacketEvent.Send event) {
    }
}