// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\handlers\PacketHandler.java
package ru.fuctorial.fuctorize.handlers;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.ReflectionHelper;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.event.PrePacketReceiveEvent;
import ru.fuctorial.fuctorize.module.impl.ScreenBypass;
import ru.fuctorial.fuctorize.utils.SessionManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraftforge.common.MinecraftForge;

public class PacketHandler extends ChannelDuplexHandler {

    private static final String HANDLER_NAME = "fuctorize_packet_handler";
    private static Field channelField;

    public PacketHandler(NetworkManager networkManager) {
    }

    public static void inject(NetworkManager networkManager) {
        try {
            Channel channel = getChannel(networkManager);
            if (channel == null) {
                System.err.println(">>> FUCTORIZE/PacketHandler: Channel is null, cannot inject.");
                return;
            }
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                return;
            }
            try {
                channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new PacketHandler(networkManager));
                System.out.println(">>> FUCTORIZE/PacketHandler: Injected before 'packet_handler'.");
            } catch (java.util.NoSuchElementException nse) {
                channel.pipeline().addLast(HANDLER_NAME, new PacketHandler(networkManager));
                System.out.println(">>> FUCTORIZE/PacketHandler: 'packet_handler' not found, injected as last handler.");
            }
        } catch (Throwable e) {
            System.err.println(">>> FUCTORIZE/PacketHandler: FAILED TO INJECT!");
            e.printStackTrace();
        }
    }

    public static void uninject() {
        try {
            if (cpw.mods.fml.client.FMLClientHandler.instance().getClient().thePlayer == null || cpw.mods.fml.client.FMLClientHandler.instance().getClient().thePlayer.sendQueue == null) return;
            NetworkManager networkManager = cpw.mods.fml.client.FMLClientHandler.instance().getClient().thePlayer.sendQueue.getNetworkManager();
            Channel channel = getChannel(networkManager);
            if (channel == null) return;
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                try {
                    channel.pipeline().remove(HANDLER_NAME);
                    System.out.println(">>> FUCTORIZE/PacketHandler: Uninjected successfully.");
                } catch (java.util.NoSuchElementException ignored) {}
            }
        } catch (Throwable e) {
            System.err.println(">>> FUCTORIZE/PacketHandler: FAILED TO UNINJECT!");
            e.printStackTrace();
        }
    }

    private static Channel getChannel(NetworkManager networkManager) {
        try {
            if (channelField == null) {
                channelField = ReflectionHelper.findField(NetworkManager.class, "channel", "field_150746_k");
                channelField.setAccessible(true);
            }
            return (Channel) channelField.get(networkManager);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Packet) {
            Packet packet = (Packet) msg;

            // ARCHITECTURAL FIX: Perform pre-cancellation check via direct dispatch.
            if (FuctorizeClient.INSTANCE.dispatchPacketSendPre(packet)) {
                return; // If a module cancelled the packet, stop processing it here.
            }

            // If not cancelled, post to the event bus for listeners (read-only).
            PacketEvent.Send event = new PacketEvent.Send(packet);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                // This check is now redundant but kept for safety in case other mods use it.
                // Our own modules should not cancel here anymore.
                return;
            }
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Packet) {
            if (msg instanceof FMLProxyPacket) {
                FMLProxyPacket proxyPacket = (FMLProxyPacket) msg;
                tryScanPayloadForSessionUUID(proxyPacket.channel(), proxyPacket.payload());
            }

            PrePacketReceiveEvent preEvent = new PrePacketReceiveEvent((Packet) msg, ctx);
            if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.dispatchPrePacketReceive(preEvent)) {
                return;
            }

            PacketEvent.Receive event = new PacketEvent.Receive((Packet) msg);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                return;
            }
        }
        super.channelRead(ctx, msg);
    }

    private void tryScanPayloadForSessionUUID(String channel, ByteBuf payload) {
        if (ScreenBypass.sessionChannelsSetting == null || payload == null || payload.readableBytes() < 16) {
            return;
        }
        List<String> channelsToScan = Arrays.asList(ScreenBypass.sessionChannelsSetting.text.replace(" ", "").split(","));
        if (!channelsToScan.contains(channel)) {
            return;
        }
        payload.markReaderIndex();
        try {
            for (int i = 0; i <= payload.readableBytes() - 16; i++) {
                long mostSigBits = payload.getLong(payload.readerIndex() + i);
                long leastSigBits = payload.getLong(payload.readerIndex() + i + 8);
                UUID potentialUUID = new UUID(mostSigBits, leastSigBits);
                if (potentialUUID.version() == 4) {
                    SessionManager.updateSession(potentialUUID);
                    break;
                }
            }
        } finally {
            payload.resetReaderIndex();
        }
    }
}