package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import net.minecraft.network.Packet;

public class PacketInfo {
    public final Packet rawPacket;
    public final String name;
    public final String cleanName;
    public final String direction;
    public final int color;
    public final byte[] rawPayloadBytes;

    // --- НОВЫЕ ПОЛЯ ---
    private int count = 1;
    private boolean isResent = false;
    // ------------------

    private String cachedSerializedData;
    private String cachedPayloadHex;

    public PacketInfo(Packet rawPacket, String direction, int color, String cleanName, byte[] rawPayloadBytes, boolean isResent) {
        this.rawPacket = rawPacket;
        this.name = rawPacket.getClass().getSimpleName();
        this.cleanName = cleanName;
        this.direction = direction;
        this.color = color;
        this.rawPayloadBytes = rawPayloadBytes;
        this.isResent = isResent;
    }

    public synchronized String getSerializedData() {
        if (cachedSerializedData == null) {
            cachedSerializedData = PacketSerializer.serialize(rawPacket);
        }
        return cachedSerializedData;
    }

    public synchronized String getPayloadHex() {
        if (cachedPayloadHex == null) {
            if (rawPacket instanceof FMLProxyPacket) {
                if (rawPayloadBytes != null && rawPayloadBytes.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    int maxBytes = Math.min(rawPayloadBytes.length, 256);
                    for (int i = 0; i < maxBytes; i++) {
                        sb.append(String.format("%02X ", rawPayloadBytes[i]));
                    }
                    if (rawPayloadBytes.length > maxBytes) {
                        sb.append("... (").append(rawPayloadBytes.length - maxBytes).append(" more bytes)");
                    }
                    cachedPayloadHex = sb.toString().trim();
                } else {
                    cachedPayloadHex = "[Empty]";
                }
            } else {
                cachedPayloadHex = "";
            }
        }
        return cachedPayloadHex;
    }

    // --- Методы для группировки ---

    public void incrementCount() {
        this.count++;
    }

    public int getCount() {
        return count;
    }

    public boolean isResent() {
        return isResent;
    }

    public void setResent(boolean resent) {
        this.isResent = resent;
    }

    /**
     * Проверяет, идентичен ли этот пакет другому по содержимому.
     * Используется для группировки.
     */
    public boolean isContentEqual(PacketInfo other) {
        if (other == null) return false;
        if (!this.direction.equals(other.direction)) return false;
        if (!this.cleanName.equals(other.cleanName)) return false;

        // Сравниваем сериализованные данные (содержимое пакета)
        // Это может быть тяжело, но PacketSerializer теперь оптимизирован.
        return this.getSerializedData().equals(other.getSerializedData());
    }
}