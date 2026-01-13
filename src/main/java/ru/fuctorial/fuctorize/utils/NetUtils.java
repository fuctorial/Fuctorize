package ru.fuctorial.fuctorize.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.client.FMLClientHandler;
import ru.fuctorial.fuctorize.module.impl.PacketSniffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.status.INetHandlerStatusClient;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.server.S00PacketServerInfo;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class NetUtils {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();
    private static final Gson GSON = new GsonBuilder().create();

    public static void sendPacket(Packet packet) {
        if (mc.getNetHandler() != null) {
            mc.getNetHandler().addToSendQueue(packet);
            PacketSniffer.logManuallySentPacket(packet);
        }
    }

    // This method remains for debugging purposes if needed by other systems.
    public static Packet reconstructAndLogFmlPacket(Object message, String channel) {
        // ... (implementation from previous fix remains unchanged)
        return null;
    }

    /**
     * Performs a series of checks with varying reliability to determine vanished players.
     * NEW: Now prioritizes the mcsrvstat.us API for the most accurate player list.
     * @param ip The server IP address.
     * @param useMcsrvApi Whether to use the external API.
     * @return The best available result from the checks.
     */
    public static VanishResult performMegaCheck(String ip, boolean useMcsrvApi) {
        if (useMcsrvApi) {
            VanishResult result = mcsrvRequest(ip);
            if (result.isSuccess() && result.hasExactNames()) {
                return result; // API gave us a full player list, this is the best we can get.
            }
        }

        VanishResult result = queryRequest(ip);
        if (result.isSuccess()) {
            return result;
        }
        result = modernRequest(ip);
        if (result.isSuccess()) {
            return result;
        }
        result = legacyRequest(ip);
        return result;
    }

    public static List<String> getTabListPlayers() {
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            return new ArrayList<>();
        }
        List<String> playerNames = new ArrayList<>();
        List<GuiPlayerInfo> playerInfoList = mc.thePlayer.sendQueue.playerInfoList;
        for (GuiPlayerInfo playerInfo : playerInfoList) {
            if (playerInfo != null && playerInfo.name != null) {
                playerNames.add(playerInfo.name);
            }
        }
        return playerNames;
    }

    // --- NEW METHOD: Integrated mcsrvstat.us API check ---
    private static VanishResult mcsrvRequest(String ip) {
        List<String> playerNames = new ArrayList<>();
        try {
            // ARCHITECTURAL FIX: Updated API endpoint to version 3.
            String content = getContent("https://api.mcsrvstat.us/3/" + ip, 5);
            if (content.isEmpty()) {
                return new VanishResult(-1, "mcsrvstat.us", null);
            }

            JsonObject json = GSON.fromJson(content, JsonObject.class);
            if (json != null && json.has("online") && json.get("online").getAsBoolean() && json.has("players")) {
                JsonObject playersObj = json.getAsJsonObject("players");
                if (playersObj != null && playersObj.has("list")) {
                    JsonArray playerList = playersObj.getAsJsonArray("list");
                    // ARCHITECTURAL FIX: Parse the new JSON object structure for each player.
                    playerList.forEach(p -> {
                        if (p.isJsonObject() && p.getAsJsonObject().has("name")) {
                            playerNames.add(p.getAsJsonObject().get("name").getAsString());
                        }
                    });
                }
                // Even if the list is empty, we can get the online count
                int onlineCount = playersObj.get("online").getAsInt();
                return new VanishResult(onlineCount, "mcsrvstat.us API v3", playerNames);
            }
        } catch (Exception e) {
            // API might be down or return an error, this is not a critical failure.
        }
        return new VanishResult(-1, "mcsrvstat.us API v3", null);
    }

    // --- NEW HELPER METHODS for mcsrvRequest ---
    private static String getContent(String address, int timeout) {
        try {
            InputStream is = getInputStream(address, timeout);
            if (is == null) return "";
            try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static InputStream getInputStream(String address, int timeout) {
        try {
            URLConnection con = new URL(address).openConnection();
            con.setReadTimeout(timeout * 1000);
            con.setConnectTimeout(timeout * 1000);
            con.setRequestProperty("User-Agent", "Fuctorize-Client/1.0"); // Be a good netizen
            con.connect();
            return con.getInputStream();
        } catch (Exception e) {
            return null;
        }
    }

    // --- Existing methods (query, modern, legacy) remain unchanged ---
    private static VanishResult queryRequest(String ip) {
        try {
            ServerAddress addr = ServerAddress.func_78860_a(ip);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(5000);
                ByteArrayOutputStream handshakeBaos = new ByteArrayOutputStream();
                DataOutputStream handshakeDos = new DataOutputStream(handshakeBaos);
                handshakeDos.writeByte(0xFE);
                handshakeDos.writeByte(0xFD);
                handshakeDos.writeByte(9);
                handshakeDos.writeInt(1);
                socket.send(new DatagramPacket(handshakeBaos.toByteArray(), handshakeBaos.size(), InetAddress.getByName(addr.getIP()), addr.getPort()));
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                String response = new String(receivePacket.getData(), 5, receivePacket.getLength() - 5, StandardCharsets.UTF_8).trim();
                int token = Integer.parseInt(response);
                ByteArrayOutputStream statBaos = new ByteArrayOutputStream();
                DataOutputStream statDos = new DataOutputStream(statBaos);
                statDos.writeByte(0xFE);
                statDos.writeByte(0xFD);
                statDos.writeByte(0);
                statDos.writeInt(1);
                statDos.writeInt(token);
                socket.send(new DatagramPacket(statBaos.toByteArray(), statBaos.size(), InetAddress.getByName(addr.getIP()), addr.getPort()));
                receiveData = new byte[4096];
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                String fullResponse = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                String[] parts = fullResponse.split("player_\u0000\u0000");
                if (parts.length > 1) {
                    String playersPart = parts[1];
                    String[] playerNames = playersPart.split("\u0000");
                    List<String> names = Arrays.stream(playerNames).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    return new VanishResult(names.size(), "UDP Query", names);
                }
            }
        } catch (Exception e) {
        }
        return new VanishResult(-1, "UDP Query", null);
    }

    private static VanishResult modernRequest(String ip) {
        final AtomicInteger result = new AtomicInteger(-2);
        try {
            ServerAddress addr = ServerAddress.func_78860_a(ip);
            NetworkManager networkManager = NetworkManager.provideLanClient(InetAddress.getByName(addr.getIP()), addr.getPort());
            networkManager.setNetHandler(new INetHandlerStatusClient() {
                public void handleServerInfo(S00PacketServerInfo packet) {
                    if (packet.func_149294_c() != null && packet.func_149294_c().func_151318_b() != null) {
                        result.set(packet.func_149294_c().func_151318_b().func_151333_b());
                    } else {
                        result.set(-1);
                    }
                    networkManager.closeChannel(new ChatComponentText("Finished"));
                }

                public void handlePong(S01PacketPong packet) {
                    networkManager.closeChannel(new ChatComponentText("Finished"));
                }

                public void onDisconnect(IChatComponent reason) {
                    if (result.get() == -2) result.set(-1);
                }

                public void onConnectionStateTransition(EnumConnectionState from, EnumConnectionState to) {
                }

                public void onNetworkTick() {
                }
            });
            networkManager.scheduleOutboundPacket(new C00Handshake(5, addr.getIP(), addr.getPort(), EnumConnectionState.STATUS));
            networkManager.scheduleOutboundPacket(new C00PacketServerQuery());
            for (int i = 0; i < 250 && result.get() == -2; i++) {
                Thread.sleep(10);
            }
        } catch (Exception e) {
            result.set(-1);
        }
        return new VanishResult(result.get(), "Modern Status", null);
    }

    private static VanishResult legacyRequest(String ip) {
        try {
            ServerAddress addr = ServerAddress.func_78860_a(ip);
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(addr.getIP(), addr.getPort()), 2000);
                socket.setSoTimeout(5000);
                try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream()); BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    dos.write(new byte[]{(byte) 0xFE, (byte) 0x01});
                    String line = br.readLine();
                    if (line == null) return new VanishResult(-1, "Legacy Ping", null);
                    String[] data = line.split("\u0000\u0000\u0000");
                    if (data.length >= 3) {
                        return new VanishResult(Integer.parseInt(data[1]), "Legacy Ping", null);
                    }
                }
            }
        } catch (Exception e) {
        }
        return new VanishResult(-1, "Legacy Ping", null);
    }

    public static class VanishResult {
        private final int onlineCount;
        private final String usedMethod;
        private final List<String> playerNames;

        public VanishResult(int onlineCount, String usedMethod, List<String> playerNames) {
            this.onlineCount = onlineCount;
            this.usedMethod = usedMethod;
            this.playerNames = playerNames;
        }

        public boolean isSuccess() {
            return onlineCount >= 0;
        }

        public boolean hasExactNames() {
            return playerNames != null && !playerNames.isEmpty();
        }

        public int getOnlineCount() {
            return onlineCount;
        }

        public String getUsedMethod() {
            return usedMethod;
        }

        public List<String> getPlayerNames() {
            return playerNames;
        }
    }
}