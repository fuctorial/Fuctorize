package ru.fuctorial.fuctorize.module.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.sniffer.GuiPacketBlocker;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import org.lwjgl.input.Keyboard;
import net.minecraft.network.Packet;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class PacketBlocker extends Module {

     
    public static final Set<String> blockedClasses = new CopyOnWriteArraySet<>();
    public static final Set<BlockedPacketRule> blockedRules = new CopyOnWriteArraySet<>();

    private static final File CONFIG_FILE = new File(new File(System.getenv("APPDATA")), "Fuctorize/packet_blocker.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public PacketBlocker(FuctorizeClient client) {
        super(client);
        loadConfig();
    }

    @Override
    public void init() {
        setMetadata("packetblocker", "Packet Blocker", Category.EXPLOIT, ActivationType.SINGLE);
        addSetting(new BindSetting("Open GUI", Keyboard.KEY_NONE));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return "Блокирует выбранные входящие и исходящие пакеты.";
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            mc.displayGuiScreen(new GuiPacketBlocker(mc.currentScreen));
        }
        toggle();
    }

     

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        if (shouldBlock(event.getPacket())) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (shouldBlock(event.getPacket())) {
            event.setCanceled(true);
        }
    }

    private boolean shouldBlock(Packet packet) {
         
        if (packet instanceof FMLProxyPacket) {
            FMLProxyPacket fmlPacket = (FMLProxyPacket) packet;
            String channel = fmlPacket.channel();
            String payload = getPayloadHex(fmlPacket);

            for (BlockedPacketRule rule : blockedRules) {
                if (rule.channelName.equals(channel)) {
                     
                    if (rule.payloadHex == null) return true;
                     
                    if (rule.payloadHex.equals(payload)) return true;
                }
            }
        }
         
        return blockedClasses.contains(packet.getClass().getName());
    }

    public static String getPayloadHex(FMLProxyPacket packet) {
        ByteBuf buf = packet.payload().copy();
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
         
         
         
         
        
        StringBuilder sb = new StringBuilder();
        int maxBytes = Math.min(bytes.length, 256);  
         
         
         
         
        
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

     

    public static void addClassBlock(String className) {
        blockedClasses.add(className);
        saveConfig();
    }

    public static void removeClassBlock(String className) {
        blockedClasses.remove(className);
        saveConfig();
    }

    public static void addRule(String channel, String payloadHex) {
        blockedRules.add(new BlockedPacketRule(channel, payloadHex));
        saveConfig();
    }

    public static void removeRule(String channel, String payloadHex) {
        blockedRules.remove(new BlockedPacketRule(channel, payloadHex));
        saveConfig();
    }

     

    public static void saveConfig() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            JsonObject root = new JsonObject();
            root.add("classes", GSON.toJsonTree(blockedClasses));
            root.add("rules", GSON.toJsonTree(blockedRules));
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadConfig() {
        if (!CONFIG_FILE.exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject root = new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
            if (root.has("classes")) {
                blockedClasses.addAll(GSON.fromJson(root.get("classes"), new TypeToken<Set<String>>(){}.getType()));
            }
            if (root.has("rules")) {
                blockedRules.addAll(GSON.fromJson(root.get("rules"), new TypeToken<Set<BlockedPacketRule>>(){}.getType()));
            }
             
            if (root.has("channels")) {
                Set<String> oldChannels = GSON.fromJson(root.get("channels"), new TypeToken<Set<String>>(){}.getType());
                for (String ch : oldChannels) {
                    blockedRules.add(new BlockedPacketRule(ch, null));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class BlockedPacketRule {
        public String channelName;
        public String payloadHex;  

        public BlockedPacketRule(String channelName, String payloadHex) {
            this.channelName = channelName;
            this.payloadHex = payloadHex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockedPacketRule that = (BlockedPacketRule) o;
            return Objects.equals(channelName, that.channelName) &&
                   Objects.equals(payloadHex, that.payloadHex);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channelName, payloadHex);
        }
    }
}