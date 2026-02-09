package ru.fuctorial.fuctorize.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cpw.mods.fml.client.FMLClientHandler;
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
import ru.fuctorial.fuctorize.module.impl.PacketSniffer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class NetUtils {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();
    private static final Gson GSON = new GsonBuilder().create();

    // Таймауты уменьшены для отзывчивости
    private static final int API_TIMEOUT = 3000;
    private static final int PING_TIMEOUT = 3000;

    public static void sendPacket(Packet packet) {
        if (mc.getNetHandler() != null) {
            mc.getNetHandler().addToSendQueue(packet);
            PacketSniffer.logManuallySentPacket(packet);
        }
    }

    /**
     * Умная проверка.
     * @param ip IP сервера
     * @param forceApi Если true, всегда дергаем API (медленно, но точно по именам, если нет Query).
     */
    public static VanishResult performMegaCheck(String ip, boolean forceApi) {
        // 1. Сначала пробуем UDP Query - это "золотой стандарт" (дает ники без кэша)
        VanishResult queryRes = queryRequest(ip);
        if (queryRes.isSuccess() && queryRes.hasExactNames()) {
            return queryRes;
        }

        // 2. Если форсировано API или Query не сработал, пробуем API
        if (forceApi) {
            VanishResult apiRes = mcsrvRequest(ip);
            if (apiRes.isSuccess() && apiRes.hasExactNames()) {
                // Если API вернул список, возвращаем его.
                // ВАЖНО: API кэшируется на 2 минуты, поэтому это fallback.
                return apiRes;
            }
        }

        // 3. Если ничего не помогло, делаем просто пинг (узнать число)
        // Используем Modern, если не вышел Query
        VanishResult modernRes = modernRequest(ip);
        if (modernRes.isSuccess()) return modernRes;

        return legacyRequest(ip);
    }

    // --- API v3 Implementation ---
    private static VanishResult mcsrvRequest(String ip) {
        List<String> playerNames = new ArrayList<>();
        try {
            // Используем endpoint v3
            String content = getContent("https://api.mcsrvstat.us/3/" + ip, API_TIMEOUT);
            if (content.isEmpty()) return new VanishResult(-1, "API-Error", null);

            JsonObject json = GSON.fromJson(content, JsonObject.class);

            // Проверка на online: true
            if (json == null || !json.has("online") || !json.get("online").getAsBoolean()) {
                return new VanishResult(-1, "API-Offline", null);
            }

            // Получаем объект players
            if (json.has("players")) {
                JsonObject playersObj = json.getAsJsonObject("players");
                int onlineCount = playersObj.has("online") ? playersObj.get("online").getAsInt() : -1;

                // Парсим список (v3 использует массив объектов)
                if (playersObj.has("list")) {
                    JsonArray list = playersObj.getAsJsonArray("list");
                    for (JsonElement el : list) {
                        if (el.isJsonObject()) {
                            JsonObject p = el.getAsJsonObject();
                            if (p.has("name")) {
                                playerNames.add(p.get("name").getAsString());
                            }
                        }
                    }
                }
                return new VanishResult(onlineCount, "mcsrvstat.us", playerNames);
            }
        } catch (Exception e) {
            // e.printStackTrace(); // Не спамить в консоль
        }
        return new VanishResult(-1, "API-Fail", null);
    }

    private static String getContent(String address, int timeout) {
        HttpURLConnection con = null;
        try {
            URL url = new URL(address);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(timeout);
            con.setReadTimeout(timeout);
            // Обязательный заголовок User-Agent для этого API
            con.setRequestProperty("User-Agent", "Fuctorize-Client/1.0 (Minecraft Mod)");
            con.setRequestMethod("GET");

            int status = con.getResponseCode();
            if (status != 200) return "";

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "";
        } finally {
            if (con != null) con.disconnect();
        }
    }

    // --- UDP Query (Быстрый, дает ники) ---
    private static VanishResult queryRequest(String ip) {
        try {
            ServerAddress addr = ServerAddress.func_78860_a(ip);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(PING_TIMEOUT);

                // Handshake
                ByteArrayOutputStream handshakeBaos = new ByteArrayOutputStream();
                DataOutputStream handshakeDos = new DataOutputStream(handshakeBaos);
                handshakeDos.writeByte(0xFE);
                handshakeDos.writeByte(0xFD);
                handshakeDos.writeByte(9);
                handshakeDos.writeInt(1);

                socket.send(new DatagramPacket(handshakeBaos.toByteArray(), handshakeBaos.size(), InetAddress.getByName(addr.getIP()), addr.getPort()));

                byte[] buffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                // Parse Challenge Token
                String challengeStr = new String(receivePacket.getData(), 5, receivePacket.getLength() - 5, StandardCharsets.UTF_8).trim();
                int token = Integer.parseInt(challengeStr);

                // Full Stat Request
                ByteArrayOutputStream statBaos = new ByteArrayOutputStream();
                DataOutputStream statDos = new DataOutputStream(statBaos);
                statDos.writeByte(0xFE);
                statDos.writeByte(0xFD);
                statDos.writeByte(0);
                statDos.writeInt(1);
                statDos.writeInt(token);
                statDos.writeInt(0); // Padding? Обычно не нужно, но иногда помогает

                socket.send(new DatagramPacket(statBaos.toByteArray(), statBaos.size(), InetAddress.getByName(addr.getIP()), addr.getPort()));

                // Receive Data
                byte[] dataBuffer = new byte[4096]; // Увеличили буфер
                receivePacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                socket.receive(receivePacket);

                // Парсинг ответа Query (очень грязный протокол, но рабочий)
                // Ищем начало списка игроков (обычно после "player_" + null + null)
                // Простая эвристика: разбиваем по 0x00
                String fullResponse = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.ISO_8859_1);

                // Находим секцию игроков
                int playerSectionStart = fullResponse.indexOf("player_");
                if (playerSectionStart != -1) {
                    String tail = fullResponse.substring(playerSectionStart + 7); // пропускаем "player_"
                    // Пропускаем нули до первого имени
                    int firstChar = 0;
                    while (firstChar < tail.length() && tail.charAt(firstChar) == 0) firstChar++;

                    String namesPart = tail.substring(firstChar);
                    String[] rawNames = namesPart.split("\u0000");
                    List<String> names = new ArrayList<>();

                    for (String s : rawNames) {
                        if (!s.trim().isEmpty()) names.add(s.trim());
                    }
                    return new VanishResult(names.size(), "UDP Query", names);
                }
            }
        } catch (Exception e) {
            // UDP закрыт
        }
        return new VanishResult(-1, "UDP Query", null);
    }

    // --- Modern Ping (Server List Ping) ---
    private static VanishResult modernRequest(String ip) {
        final AtomicInteger result = new AtomicInteger(-2);
        try {
            ServerAddress addr = ServerAddress.func_78860_a(ip);
            // Создаем соединение
            NetworkManager networkManager = NetworkManager.provideLanClient(InetAddress.getByName(addr.getIP()), addr.getPort());

            networkManager.setNetHandler(new INetHandlerStatusClient() {
                public void handleServerInfo(S00PacketServerInfo packet) {
                    if (packet.func_149294_c() != null && packet.func_149294_c().func_151318_b() != null) {
                        result.set(packet.func_149294_c().func_151318_b().func_151333_b());
                    } else {
                        result.set(-1);
                    }
                    // Закрываем сразу после получения инфо
                    networkManager.closeChannel(new ChatComponentText("Finished"));
                }
                public void handlePong(S01PacketPong packet) {
                    networkManager.closeChannel(new ChatComponentText("Finished"));
                }
                public void onDisconnect(IChatComponent reason) {}
                public void onConnectionStateTransition(EnumConnectionState from, EnumConnectionState to) {}
                public void onNetworkTick() {}
            });

            networkManager.scheduleOutboundPacket(new C00Handshake(47, addr.getIP(), addr.getPort(), EnumConnectionState.STATUS));
            networkManager.scheduleOutboundPacket(new C00PacketServerQuery());

            // Ждем ответа (синхронное ожидание в асинхронном нетворке)
            long start = System.currentTimeMillis();
            while (result.get() == -2 && (System.currentTimeMillis() - start) < PING_TIMEOUT) {
                if (networkManager.isChannelOpen()) {
                    networkManager.processReceivedPackets(); // Важно дергать обработку пакетов
                }
                Thread.sleep(20);
            }
        } catch (Exception e) {
            result.set(-1);
        }
        return new VanishResult(result.get(), "Modern Ping", null);
    }

    private static VanishResult legacyRequest(String ip) {
        // Оставлен как самый крайний вариант
        try {
            ServerAddress addr = ServerAddress.func_78860_a(ip);
            try (Socket socket = new Socket()) {
                socket.setSoTimeout(PING_TIMEOUT);
                socket.connect(new InetSocketAddress(addr.getIP(), addr.getPort()), PING_TIMEOUT);

                try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                     BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_16BE))) {

                    dos.write(new byte[]{(byte) 0xFE, (byte) 0x01});

                    int packetId = br.read(); // читаем первый байт
                    if (packetId == -1) return new VanishResult(-1, "Legacy", null);

                    String line = br.readLine();
                    if (line != null) {
                        // Legacy формат сложный, просто ищем числа
                        String[] data = line.split("\u0000");
                        // Обычно индекс 4 или 5 содержит онлайн
                        if (data.length > 3) {
                            return new VanishResult(Integer.parseInt(data[4]), "Legacy Ping", null);
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return new VanishResult(-1, "Legacy Ping", null);
    }

    // --- Result Class ---
    public static class VanishResult {
        private final int onlineCount;
        private final String usedMethod;
        private final List<String> playerNames;

        public VanishResult(int onlineCount, String usedMethod, List<String> playerNames) {
            this.onlineCount = onlineCount;
            this.usedMethod = usedMethod;
            this.playerNames = playerNames;
        }
        public boolean isSuccess() { return onlineCount >= 0; }
        public boolean hasExactNames() { return playerNames != null && !playerNames.isEmpty(); }
        public int getOnlineCount() { return onlineCount; }
        public List<String> getPlayerNames() { return playerNames; }
    }
}