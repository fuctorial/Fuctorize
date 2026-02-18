package ru.fuctorial.fuctorize.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Base64;

public class PacketPersistence {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Unsafe unsafeInstance;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafeInstance = (Unsafe) f.get(null);
        } catch (Exception ignored) {}
    }

    public static class SavedPacketData {
        public String name;        
        public String className;   
        public String channel;     
        public String rawData;     
        public String direction;   

        public SavedPacketData(String name, String className, String channel, String rawData, String direction) {
            this.name = name;
            this.className = className;
            this.channel = channel;
            this.rawData = rawData;
            this.direction = direction;
        }
    }

     
    public static SavedPacketData capture(Packet packet, String saveName, String direction) {
        if (packet == null) return null;

        try {
            String className = packet.getClass().getName();
            String channel = null;
            ByteBuf buffer = Unpooled.buffer();

             
            if (packet instanceof FMLProxyPacket) {
                FMLProxyPacket fml = (FMLProxyPacket) packet;
                channel = fml.channel();
                ByteBuf payload = fml.payload();
                if (payload != null) {
                     
                    ByteBuf dup = payload.duplicate();
                    byte[] allBytes = new byte[dup.capacity()];
                    dup.getBytes(0, allBytes);
                    buffer.writeBytes(allBytes);
                }
            }
             
            else {
                PacketBuffer packetBuffer = new PacketBuffer(buffer);
                packet.writePacketData(packetBuffer);
            }

            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            String base64 = Base64.getEncoder().encodeToString(bytes);

             
            return new SavedPacketData(saveName, className, channel, base64, direction);

        } catch (Exception e) {
            System.err.println("Fuctorize: Failed to SNAPSHOT packet " + packet.getClass().getSimpleName());
            e.printStackTrace();
            return null;
        }
    }

    public static Packet reconstruct(SavedPacketData data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data.rawData);
            ByteBuf buffer = Unpooled.wrappedBuffer(bytes);

            if (data.channel != null) {
                return new FMLProxyPacket(buffer, data.channel);
            }

            Class<?> clazz = Class.forName(data.className);
            Packet packet;

            if (unsafeInstance != null) {
                packet = (Packet) unsafeInstance.allocateInstance(clazz);
            } else {
                packet = (Packet) clazz.newInstance();
            }

            PacketBuffer packetBuffer = new PacketBuffer(buffer);
            packet.readPacketData(packetBuffer);

            return packet;

        } catch (Exception e) {
            System.err.println("Fuctorize: Failed to RECONSTRUCT packet " + data.name);
            e.printStackTrace();
            return null;
        }
    }
}