package ru.fuctorial.fuctorize.manager;

import cpw.mods.fml.client.FMLClientHandler;
import ru.fuctorial.fuctorize.utils.PacketInfo;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

public class LogManager {
    private final File logDir;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    private PrintWriter packetWriter;
    private PrintWriter chatWriter;
    private PrintWriter payloadWriter;

    // Добавляем ExecutorService для асинхронной записи
    private final ExecutorService loggerThread = Executors.newSingleThreadExecutor();

    public LogManager() {
        this.logDir = new File(FMLClientHandler.instance().getClient().mcDataDir, "fuctorize/logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        initWriters();
    }

    private void initWriters() {
        try {
            packetWriter = new PrintWriter(new FileWriter(new File(logDir, "packet_log.txt"), false));
            chatWriter = new PrintWriter(new FileWriter(new File(logDir, "chat_log.txt"), false));
            payloadWriter = new PrintWriter(new FileWriter(new File(logDir, "custom_payloads.txt"), false));
        } catch (IOException e) {
            System.err.println("Fuctorize/LogManager: Failed to initialize log writers!");
            e.printStackTrace();
        }
    }

    public void logPacket(PacketInfo info) {
        // Отправляем задачу в отдельный поток
        loggerThread.submit(() -> {
            if (packetWriter == null) return;
            String time = dateFormat.format(new Date());

            // Здесь вызываем getSerializedData(). Так как мы в отдельном потоке,
            // тяжелая рефлексия не подвесит игру.
            String data = info.getSerializedData();

            packetWriter.printf("[%s] [%s] %s -> %s%n", time, info.direction, info.name, data);
            packetWriter.flush();

            if (info.rawPacket instanceof S3FPacketCustomPayload) {
                S3FPacketCustomPayload p = (S3FPacketCustomPayload) info.rawPacket;
                logPayload("RCVD", p.func_149169_c(), p.func_149168_d());
            } else if (info.rawPacket instanceof C17PacketCustomPayload) {
                C17PacketCustomPayload p = (C17PacketCustomPayload) info.rawPacket;
                logPayload("SENT", p.func_149559_c(), p.func_149558_e());
            }
        });
    }

    public void logChat(String message) {
        loggerThread.submit(() -> {
            if (chatWriter == null) return;
            String time = dateFormat.format(new Date());
            chatWriter.printf("[%s] %s%n", time, message);
            chatWriter.flush();
        });
    }

    private void logPayload(String direction, String channel, byte[] data) {
        if (payloadWriter == null) return;
        String time = dateFormat.format(new Date());
        int size = (data != null) ? data.length : 0;
        payloadWriter.printf("[%s] [%s] Channel: '%s', Size: %d bytes%n", time, direction, channel, size);
        payloadWriter.flush();
    }

    public void close() {
        // Останавливаем поток логирования
        loggerThread.shutdown();

        if (packetWriter != null) packetWriter.close();
        if (chatWriter != null) chatWriter.close();
        if (payloadWriter != null) payloadWriter.close();
    }

    public File getLogDir() {
        return logDir;
    }
}