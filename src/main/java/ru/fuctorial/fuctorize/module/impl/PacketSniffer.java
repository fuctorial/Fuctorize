package ru.fuctorial.fuctorize.module.impl;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.sniffer.GuiPacketSniffer;
import ru.fuctorial.fuctorize.manager.LogManager;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.PacketInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.StringUtils;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class PacketSniffer extends Module {

    public static final List<PacketInfo> packetLog = new CopyOnWriteArrayList<>();
    public static LogManager logManager;
    private static volatile boolean isPaused = true;
    private static boolean useBlacklistStatic = true;

    public static boolean viewFmlOnly = true;
    public static String viewFilterSpecific = null;

    public static final Set<String> blacklistClassNames = new CopyOnWriteArraySet<>();
    public static final Set<String> blacklistFmlChannels = new CopyOnWriteArraySet<>();
    private static final File BLACKLIST_CONFIG_FILE = new File(new File(System.getenv("APPDATA")), "Fuctorize/sniffer_blacklist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Set<String> hiddenPackets = new HashSet<>();

    public PacketSniffer(FuctorizeClient client) {
        super(client);
        if (logManager == null) {
            logManager = new LogManager();
        }
        loadBlacklistConfig();
    }

    @Override
    public void init() {
        setMetadata("packetsniffer", Lang.get("module.packetsniffer.name"), Category.MISC, ActivationType.SINGLE);
        addSetting(new BindSetting(Lang.get("module.packetsniffer.setting.open_gui"), Keyboard.KEY_P));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.packetsniffer.desc");
    }

    @Override
    public void onEnable() {
        mc.displayGuiScreen(new GuiPacketSniffer(mc.currentScreen));
        toggle();
    }

    public static void onPacketSendStatic(PacketEvent.Send event) {
         
        addPacket(event.getPacket(), "SENT", false);
    }

    public static void onPacketReceiveStatic(PacketEvent.Receive event) {
        if (isPaused) return;  
        if (event.getPacket() instanceof S02PacketChat && logManager != null) {
            S02PacketChat chatPacket = (S02PacketChat) event.getPacket();
            logManager.logChat(StringUtils.stripControlCodes(chatPacket.func_148915_c().getUnformattedText()));
        }
        addPacket(event.getPacket(), "RCVD", false);
    }

    public static void logManuallySentPacket(Packet packet) {
         
        addPacket(packet, "SENT", true);
    }

    private static void addPacket(Packet packet, String direction, boolean isResent) {
         
        if (isPaused) return;

        if (useBlacklistStatic && isPacketBlacklisted(packet)) {
            return;
        }
        String cleanName = getCleanPacketName(packet);

        byte[] rawBytes = null;
        if (packet instanceof FMLProxyPacket) {
            ByteBuf payload = ((FMLProxyPacket) packet).payload();
            if (payload != null && payload.readableBytes() > 0) {
                rawBytes = new byte[payload.readableBytes()];
                payload.getBytes(payload.readerIndex(), rawBytes);
            }
        }

        PacketInfo newInfo = new PacketInfo(packet, direction, getPacketColor(packet), cleanName, rawBytes, isResent);

         
        synchronized (packetLog) {  
            if (!packetLog.isEmpty()) {
                PacketInfo lastInfo = packetLog.get(0);
                 
                 
                 
                if (newInfo.isContentEqual(lastInfo)) {
                    lastInfo.incrementCount();
                    if (isResent) {
                        lastInfo.setResent(true);  
                    }
                     
                     
                    if (logManager != null) {
                        logManager.logPacket(newInfo);
                    }
                    return;  
                }
            }

            packetLog.add(0, newInfo);
            if (packetLog.size() > 500) {
                packetLog.remove(packetLog.size() - 1);
            }
        }

        if (logManager != null) {
            logManager.logPacket(newInfo);
        }
    }

    public static boolean isPacketHidden(String cleanName) {
        return hiddenPackets.contains(cleanName);
    }

    public static void setPacketHidden(String cleanName, boolean hidden) {
        if (hidden) {
            hiddenPackets.add(cleanName);
        } else {
            hiddenPackets.remove(cleanName);
        }
    }

    public static void clearExcelFilter() {
        hiddenPackets.clear();
    }

    private static boolean isPacketBlacklisted(Packet packet) {
        if (packet == null) return true;
        Class<?> packetClass = packet.getClass();

        if (packet instanceof FMLProxyPacket) {
            String channel = ((FMLProxyPacket) packet).channel();
            return isFmlChannelBlacklisted(channel);
        }

        if (packetClass == C03PacketPlayer.class) {
            C03PacketPlayer p = (C03PacketPlayer) packet;
            if (!p.func_149466_j() && !p.func_149463_k()) {
                return true;
            }
        }
        if (S14PacketEntity.class.isAssignableFrom(packetClass)) return true;
        return blacklistClassNames.contains(packet.getClass().getName());
    }

    public static String getCleanPacketName(Packet packet) {
        if (packet == null) return "NullPacket";
        if (packet instanceof FMLProxyPacket) {
            String channel = ((FMLProxyPacket) packet).channel();
            return "FMLProxyPacket [" + (channel != null ? channel : "null_channel") + "]";
        }
        String simpleName = packet.getClass().getSimpleName();
        if (simpleName.contains("$$")) return simpleName.substring(simpleName.lastIndexOf("$$") + 2);
        if (packet instanceof C03PacketPlayer.C04PacketPlayerPosition) return "C03PacketPlayer$Position";
        if (packet instanceof C03PacketPlayer.C05PacketPlayerLook) return "C03PacketPlayer$Look";
        if (packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) return "C03PacketPlayer$PosLook";
        return simpleName;
    }

    public static int getPacketColor(Packet packet) {
        if (packet == null) return Color.DARK_GRAY.getRGB();
        if (packet instanceof C03PacketPlayer) return 0x909090;
        if (packet instanceof S02PacketChat || packet instanceof C01PacketChatMessage) return 0x55FF55;
        if (packet instanceof C0EPacketClickWindow || packet instanceof C10PacketCreativeInventoryAction || packet instanceof S2FPacketSetSlot) return 0x5555FF;
        if (packet instanceof C17PacketCustomPayload || packet instanceof S3FPacketCustomPayload) {
            if (packet instanceof FMLProxyPacket) {
                String channel = ((FMLProxyPacket) packet).channel();
                if ("stalker_core".equals(channel)) return 0xFF5555;
            }
            return 0xFF55FF;
        }
        if (packet.getClass().getPackage() != null && packet.getClass().getPackage().getName().startsWith("net.minecraft.network.play.server")) {
            return new Color(100, 180, 255).getRGB();
        }
        return Theme.ORANGE.getRGB();
    }

    public static Set<String> getBlacklistedClassNames() { return blacklistClassNames; }
    public static Set<String> getBlacklistedFmlChannels() { return blacklistFmlChannels; }
    public static void addBlacklistedClassName(String className) { if (className != null && !className.isEmpty()) blacklistClassNames.add(className); }
    public static void addBlacklistedFmlChannel(String channelName) { if (channelName != null && !channelName.isEmpty()) blacklistFmlChannels.add(channelName); }
    public static void removeBlacklistedClassName(String className) { blacklistClassNames.remove(className); }
    public static void removeBlacklistedFmlChannel(String channelName) { blacklistFmlChannels.remove(channelName); }
    public static boolean isFmlChannelBlacklisted(String channelName) { return blacklistFmlChannels.contains(channelName); }

    public static void toggleBlacklist(Class<? extends Packet> packetClass) {
        String className = packetClass.getName();
        if (blacklistClassNames.contains(className)) blacklistClassNames.remove(className);
        else blacklistClassNames.add(className);
        saveBlacklistConfig();
    }
    public static boolean isBlacklisted(Class<? extends Packet> packetClass) { return blacklistClassNames.contains(packetClass.getName()); }

    public static void saveBlacklistConfig() {
        try (FileWriter writer = new FileWriter(BLACKLIST_CONFIG_FILE)) {
            JsonObject root = new JsonObject();
            root.add("classNames", GSON.toJsonTree(blacklistClassNames));
            root.add("fmlChannels", GSON.toJsonTree(blacklistFmlChannels));
            GSON.toJson(root, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadBlacklistConfig() {
        Set<String> defaultClasses = new HashSet<>(Arrays.asList("net.minecraft.network.play.server.S14PacketEntity", "net.minecraft.network.play.server.S18PacketEntityTeleport", "net.minecraft.network.play.client.C00PacketKeepAlive", "net.minecraft.network.play.server.S00PacketKeepAlive", "net.minecraft.network.play.server.S19PacketEntityHeadLook", "net.minecraft.network.play.server.S12PacketEntityVelocity", "net.minecraft.network.play.server.S1CPacketEntityMetadata", "net.minecraft.network.play.server.S20PacketEntityProperties", "net.minecraft.network.play.server.S03PacketTimeUpdate"));

        blacklistClassNames.clear();
        blacklistFmlChannels.clear();

        if (!BLACKLIST_CONFIG_FILE.exists()) {
            blacklistClassNames.addAll(defaultClasses);
            saveBlacklistConfig();
            return;
        }
        try (FileReader reader = new FileReader(BLACKLIST_CONFIG_FILE)) {
            JsonElement json = new JsonParser().parse(reader);
            if (json.isJsonObject()) {
                JsonObject root = json.getAsJsonObject();
                if (root.has("classNames")) {
                    blacklistClassNames.addAll(GSON.fromJson(root.get("classNames"), new TypeToken<Set<String>>(){}.getType()));
                }
                if (root.has("fmlChannels")) {
                    blacklistFmlChannels.addAll(GSON.fromJson(root.get("fmlChannels"), new TypeToken<Set<String>>(){}.getType()));
                }
            } else if (json.isJsonArray()) {
                blacklistClassNames.addAll(GSON.fromJson(json, new TypeToken<Set<String>>(){}.getType()));
                saveBlacklistConfig();
            }
        } catch (Exception e) {
            blacklistClassNames.addAll(defaultClasses);
        }
    }

    public static boolean isPaused() { return isPaused; }
    public static void setPaused(boolean paused) { isPaused = paused; }
    public static boolean isUsingBlacklist() { return useBlacklistStatic; }
    public static void setUseBlacklist(boolean use) { useBlacklistStatic = use; }
    public static void startNewLogSession() {
        if (logManager != null) logManager.close();
        logManager = new LogManager();
        packetLog.clear();
    }
    public static void stopLogSession() {
        if (logManager != null) { logManager.close(); logManager = null; }
    }
}