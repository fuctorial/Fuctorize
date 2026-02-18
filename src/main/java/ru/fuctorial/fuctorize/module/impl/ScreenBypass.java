 
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.manager.RenderManager;
import ru.fuctorial.fuctorize.event.PrePacketReceiveEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.module.settings.TextSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import ru.fuctorial.fuctorize.utils.http.HttpSniffer;
import io.netty.buffer.ByteBuf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenBypass extends Module {

    private TextSetting channelsSetting;
    private SliderSetting delaySetting;
    private BooleanSetting copyResponse;
    public static TextSetting sessionChannelsSetting;

    private final AtomicBoolean isBypassing = new AtomicBoolean(false);

    public ScreenBypass(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("screenbypass", Lang.get("module.screenbypass.name"), Category.EXPLOIT);

        delaySetting = new SliderSetting(Lang.get("module.screenbypass.setting.delay"), 2.0, 1.0, 10.0, 0.5);
        copyResponse = new BooleanSetting(Lang.get("module.screenbypass.setting.copy_response"), false);
        channelsSetting = new TextSetting(Lang.get("module.screenbypass.setting.check_channels"), "ScreenMod,screener,scr_mcs,nGuard,scrs,screen,vkfeed,custompackets,V_S");
        sessionChannelsSetting = new TextSetting(Lang.get("module.screenbypass.setting.session_channels"), "stalker_core,CustomNPCs");

        addSetting(delaySetting);
        addSetting(copyResponse);
        addSetting(channelsSetting);
        addSetting(sessionChannelsSetting);
        addSetting(new BindSetting(Lang.get("module.screenbypass.setting.bind"), Keyboard.KEY_NONE));

        this.setEnabledFromConfig(true);
        this.setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.screenbypass.desc");
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {
        RenderManager.renderingForceDisabled = false;
        isBypassing.set(false);
    }

    @Override
    public void onPrePacketReceive(PrePacketReceiveEvent event) {
        if (!this.isEnabled() || !(event.packet instanceof FMLProxyPacket)) {
            return;
        }

        List<String> channelsToCheck = Arrays.asList(channelsSetting.text.replace(" ", "").split(","));
        FMLProxyPacket packet = (FMLProxyPacket) event.packet;
        final String channel = packet.channel();

        if (channelsToCheck.contains(channel)) {
            if (isBypassing.compareAndSet(false, true)) {
                mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("mob.endermen.portal"), 0.7F));

                if (copyResponse.enabled) {
                    Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
                    String httpJson = null;
                    try { httpJson = ru.fuctorial.fuctorize.utils.http.HttpSniffer.getLastCaptureJson(); } catch (Throwable ignored) {}
                    JsonObject root = new JsonObject();
                    if (httpJson != null) {
                        try { root.add("http", gson.fromJson(httpJson, JsonObject.class)); } catch (Throwable ignored) {}
                    }
                    root.add("fml", buildDetailedJsonObject(packet, event.context));
                    GuiScreen.setClipboardString(gson.toJson(root));
                    client.notificationManager.show(new Notification(
                            Lang.get("notification.screenbypass.title.response_copied"),
                            Lang.get("notification.screenbypass.message.response_copied"),
                            Notification.NotificationType.INFO,
                            3000L
                    ));
                }

                RenderManager.renderingForceDisabled = true;
                long delayMillis = (long) (delaySetting.value * 1000);

                new Thread(() -> {
                    try {
                        if (mc.currentScreen != null) {
                            client.scheduleTask(() -> mc.displayGuiScreen(null));
                        }
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        RenderManager.renderingForceDisabled = false;
                        isBypassing.set(false);
                        client.scheduleTask(() -> client.notificationManager.show(new Notification(
                                Lang.get("notification.screenbypass.title.visuals_restored"),
                                Lang.get("notification.screenbypass.message.visuals_restored"),
                                Notification.NotificationType.SUCCESS,
                                3000L
                        )));
                    }
                }, "ScreenBypass-Thread").start();
            }
        }
    }

    private JsonObject buildDetailedJsonObject(FMLProxyPacket packet, io.netty.channel.ChannelHandlerContext ctx) {
        JsonObject obj = new JsonObject();
        String channel = packet.channel();
        ByteBuf buf = packet.payload();
        obj.addProperty("channel", channel);
        try {
            if (ctx != null && ctx.channel() != null) {
                Object remote = ctx.channel().remoteAddress();
                Object local = ctx.channel().localAddress();
                if (remote != null) obj.addProperty("serverRemote", remote.toString());
                if (local != null) obj.addProperty("serverLocal", local.toString());
            }
        } catch (Exception ignored) {}

        java.util.List<String> strings = extractAllStrings(buf);
        String endpoint = strings.stream().filter(s -> s.startsWith("http://") || s.startsWith("https://")).findFirst().orElse(null);
        String method = detectMethod(strings);
        java.util.List<String> headers = detectHeaders(strings);
        String body = detectBody(strings);

        if (endpoint != null) obj.addProperty("endpoint", endpoint);
        if (method != null) obj.addProperty("method", method);

        if (!headers.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String h : headers) arr.add(h);
            obj.add("headers", arr);
        }

        if (body != null && !body.isEmpty()) {
            obj.addProperty("body", body);
        }

         
        String hex = byteBufToHexString(buf);
        if (hex != null && !hex.isEmpty()) {
            int max = Math.min(hex.length(), 4096);
            String trimmed = hex.substring(0, max);
            if (hex.length() > max) trimmed += "...";
            obj.addProperty("hex", trimmed);
        }

        return obj;
    }

    private java.util.List<String> extractAllStrings(ByteBuf buf) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (buf == null || !buf.isReadable()) return out;
        ByteBuf data = buf.copy();
        while (data.readableBytes() > 2) {
            data.markReaderIndex();
            int length;
            try {
                length = data.readUnsignedShort();
            } catch (Exception e) {
                break;
            }
            if (length > 0 && length <= 65535 && data.readableBytes() >= length) {
                byte[] bytes = new byte[length];
                data.readBytes(bytes);
                String str = new String(bytes, StandardCharsets.UTF_8).trim();
                if (isStringPrintable(str)) {
                    out.add(str);
                }
            } else {
                data.resetReaderIndex();
                try { data.readByte(); } catch (Exception ignored) { break; }
            }
        }
        return out;
    }

    private String detectMethod(java.util.List<String> strings) {
        java.util.List<String> methods = java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD");
        for (String s : strings) {
            for (String m : methods) {
                if (s.equals(m) || s.startsWith(m + " ") || s.contains("method:" + m) || s.contains("\"method\":\"" + m + "\"") || s.contains("method=" + m)) {
                    return m;
                }
            }
        }
        return null;
    }

    private java.util.List<String> detectHeaders(java.util.List<String> strings) {
        java.util.ArrayList<String> headers = new java.util.ArrayList<>();
        for (String s : strings) {
            int idx = s.indexOf(':');
            if (idx > 0 && idx < s.length() - 1) {
                String key = s.substring(0, idx).trim();
                String val = s.substring(idx + 1).trim();
                if (key.matches("[A-Za-z0-9-]+") && !val.isEmpty()) {
                    if (key.equalsIgnoreCase("Authorization") || key.equalsIgnoreCase("User-Agent") || key.equalsIgnoreCase("Content-Type") || key.equalsIgnoreCase("Content-Length") || key.equalsIgnoreCase("Accept") || key.equalsIgnoreCase("Accept-Encoding") || key.equalsIgnoreCase("Connection") || key.equalsIgnoreCase("Host") || key.startsWith("X-")) {
                        headers.add(key + ": " + val);
                    }
                }
            }
        }
        return headers;
    }

    private String detectBody(java.util.List<String> strings) {
        for (String s : strings) {
            String t = s.trim();
            if (t.startsWith("{") || t.startsWith("[")) {
                return t;
            }
            if (t.contains("=") && t.length() > 20 && !t.contains(": ")) {
                return t;
            }
        }
        return null;
    }
    private String extractResponseString(ByteBuf buf) {
        if (buf == null || !buf.isReadable()) return "[Пустой пакет]";
        ByteBuf data = buf.copy();
        while (data.readableBytes() > 2) {
            data.markReaderIndex();
            int length = data.readUnsignedShort();
            if (length > 0 && length < 2048 && data.readableBytes() >= length) {
                byte[] bytes = new byte[length];
                data.readBytes(bytes);
                String str = new String(bytes, StandardCharsets.UTF_8);
                if (isStringPrintable(str) && (str.startsWith("{") || str.contains(":"))) {
                    return str;
                }
            } else {
                data.resetReaderIndex();
                data.readByte();
            }
        }
        return "[Читаемый ответ не найден. HEX: " + byteBufToHexString(buf) + "]";
    }

    private boolean isStringPrintable(String str) {
        if (str == null || str.isEmpty()) return false;
        int printableCount = 0;
        for (char c : str.toCharArray()) {
            if (c >= ' ' && c <= '~') printableCount++;
        }
        return (double) printableCount / str.length() > 0.5;
    }

    private static String byteBufToHexString(ByteBuf buf) {
        if (buf == null) return "";
        ByteBuf copy = buf.copy();
        StringBuilder sb = new StringBuilder();
        while (copy.isReadable()) {
            sb.append(String.format("%02X ", copy.readByte()));
        }
        return sb.toString().trim();
    }
}


