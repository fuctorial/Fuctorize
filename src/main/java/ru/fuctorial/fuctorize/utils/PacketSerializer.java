package ru.fuctorial.fuctorize.utils;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.Chunk;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PacketSerializer {

    private static final int MAX_RECURSION_DEPTH = 20;
    private static Unsafe unsafeInstance;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafeInstance = (Unsafe) f.get(null);
        } catch (Exception e) {
            System.err.println("Fuctorize/PacketSerializer: Failed to get Unsafe instance.");
            unsafeInstance = null;
        }
    }

    public static String serialize(Packet packet) {
        if (packet == null) return "null";
        StringBuilder sb = new StringBuilder();
        recursiveFormat(packet, sb, "  ", Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        return sb.toString().trim();
    }

    private static void recursiveFormat(Object obj, StringBuilder sb, String indent, Set<Object> visited, int depth) {
        if (obj == null) {
            sb.append("null");
            return;
        }

        if (depth > MAX_RECURSION_DEPTH) {
            sb.append("... (Max Depth)");
            return;
        }
        if (!visited.add(obj)) {
            sb.append("... (Circular Ref ").append(obj.getClass().getSimpleName()).append(")");
            return;
        }

        try {
            Class<?> clazz = obj.getClass();

            if (isSimpleType(obj)) {
                sb.append(formatSimpleType(obj));
                return;
            }

            if (clazz.isArray()) {
                formatArray(obj, sb, indent, visited, depth);
                return;
            }

            if (obj instanceof Collection) {
                formatCollection((Collection<?>) obj, sb, indent, visited, depth);
                return;
            }

            if (obj instanceof Map) {
                formatMap((Map<?, ?>) obj, sb, indent, visited, depth);
                return;
            }

             
            if (formatMinecraftTypes(obj, sb)) {
                return;
            }

            if (obj instanceof ByteBuf) {
                formatByteBuf((ByteBuf) obj, sb);
                return;
            }

            if (hasCustomToString(clazz)) {
                sb.append(obj.toString());
                return;
            }

            if (!(obj instanceof Packet)) {
                sb.append(clazz.getSimpleName()).append(" {\n");
            }

            formatFieldsViaReflection(obj, sb, indent, visited, depth, clazz);

            if (!(obj instanceof Packet)) {
                sb.append(indent.substring(2)).append("}");
            }

        } finally {
            visited.remove(obj);
        }
    }

     

    private static boolean isSimpleType(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean || obj instanceof Enum || obj instanceof UUID;
    }

    private static String formatSimpleType(Object obj) {
        if (obj instanceof String) return "\"" + obj + "\"";
        if (obj instanceof Enum) return ((Enum<?>) obj).name();
        return obj.toString();
    }

    private static boolean hasCustomToString(Class<?> clazz) {
        try {
            return clazz.getMethod("toString").getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean formatMinecraftTypes(Object obj, StringBuilder sb) {
         
        if (obj instanceof FMLProxyPacket) {
            FMLProxyPacket pkt = (FMLProxyPacket) obj;
            sb.append("FMLProxyPacket {\n");
            sb.append("    channel: \"").append(pkt.channel()).append("\"\n");
            sb.append("    payload: ");
            formatByteBuf(pkt.payload(), sb);  
            sb.append("\n  }");
            return true;
        }
         

        if (obj instanceof ItemStack) {
            formatItemStack((ItemStack) obj, sb);
            return true;
        }
        if (obj instanceof NBTBase) {
            sb.append(JsonNbtParser.nbtToString((NBTBase) obj));
            return true;
        }
        if (obj instanceof GameProfile) {
            GameProfile gp = (GameProfile) obj;
            sb.append("GameProfile{name='").append(gp.getName()).append("', id=").append(gp.getId()).append("}");
            return true;
        }
        if (obj instanceof Vec3) {
            Vec3 v = (Vec3) obj;
            sb.append(String.format("Vec3(%.2f, %.2f, %.2f)", v.xCoord, v.yCoord, v.zCoord));
            return true;
        }
        if (obj instanceof ChunkCoordinates) {
            ChunkCoordinates c = (ChunkCoordinates) obj;
            sb.append(String.format("Pos(%d, %d, %d)", c.posX, c.posY, c.posZ));
            return true;
        }
        if (obj instanceof Block) {
            sb.append("Block{").append(Block.blockRegistry.getNameForObject(obj)).append("}");
            return true;
        }
        if (obj instanceof Item) {
            sb.append("Item{").append(Item.itemRegistry.getNameForObject(obj)).append("}");
            return true;
        }
        if (obj instanceof PotionEffect) {
            PotionEffect pe = (PotionEffect) obj;
            sb.append("Effect{id=").append(pe.getPotionID()).append(", amp=").append(pe.getAmplifier()).append(", dur=").append(pe.getDuration()).append("}");
            return true;
        }
        if (obj instanceof IChatComponent) {
            sb.append("ChatComp{\"").append(((IChatComponent) obj).getUnformattedText()).append("\"}");
            return true;
        }
        if (obj instanceof Chunk) {
            Chunk c = (Chunk) obj;
            sb.append("Chunk[").append(c.xPosition).append(", ").append(c.zPosition).append("]");
            return true;
        }
        return false;
    }

    private static void formatByteBuf(ByteBuf buf, StringBuilder sb) {
        int readable = buf.readableBytes();
        sb.append("ByteBuf(len=").append(readable).append(")");
        if (readable > 0) {
            String content = tryDecodeString(buf);
            if (content != null) {
                sb.append(" \"").append(content).append("\"");
            } else {
                sb.append(" [Hex: ").append(byteBufToHexString(buf, 64)).append("]");
            }
        }
    }

    private static void formatArray(Object array, StringBuilder sb, String indent, Set<Object> visited, int depth) {
        int length = Array.getLength(array);
        sb.append(array.getClass().getSimpleName().replace("[]", "")).append("[").append(length).append("] {\n");
        int max = Math.min(length, 20);
        for (int i = 0; i < max; i++) {
            sb.append(indent).append(i).append(": ");
            recursiveFormat(Array.get(array, i), sb, indent + "  ", visited, depth + 1);
            sb.append("\n");
        }
        if (length > max) sb.append(indent).append("... (").append(length - max).append(" more)\n");
        sb.append(indent.substring(2)).append("}");
    }

    private static void formatCollection(Collection<?> col, StringBuilder sb, String indent, Set<Object> visited, int depth) {
        sb.append(col.getClass().getSimpleName()).append("(size=").append(col.size()).append(") [\n");
        int i = 0;
        int max = 20;
        for (Object o : col) {
            if (i >= max) {
                sb.append(indent).append("... (").append(col.size() - max).append(" more)\n");
                break;
            }
            sb.append(indent);
            recursiveFormat(o, sb, indent + "  ", visited, depth + 1);
            sb.append("\n");
            i++;
        }
        sb.append(indent.substring(2)).append("]");
    }

    private static void formatMap(Map<?, ?> map, StringBuilder sb, String indent, Set<Object> visited, int depth) {
        sb.append(map.getClass().getSimpleName()).append("(size=").append(map.size()).append(") {\n");
        int i = 0;
        int max = 20;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (i >= max) {
                sb.append(indent).append("... (").append(map.size() - max).append(" more)\n");
                break;
            }
            sb.append(indent);
            recursiveFormat(entry.getKey(), sb, "", visited, depth + 1);
            sb.append(" -> ");
            recursiveFormat(entry.getValue(), sb, indent + "  ", visited, depth + 1);
            sb.append("\n");
            i++;
        }
        sb.append(indent.substring(2)).append("}");
    }

    private static void formatFieldsViaReflection(Object obj, StringBuilder sb, String indent, Set<Object> visited, int depth, Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != Object.class && currentClass != null) {
            if (currentClass.getName().startsWith("java.") && !(obj instanceof Packet)) break;

            for (Field field : currentClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.isSynthetic()) continue;

                field.setAccessible(true);
                try {
                    Object val = field.get(obj);
                    String name = ObfuscationMapper.getMcpName(field.getName());
                    sb.append(indent).append(name).append(": ");
                    recursiveFormat(val, sb, indent + "  ", visited, depth + 1);
                    sb.append("\n");
                } catch (Exception e) {
                    sb.append(indent).append(field.getName()).append(": <access error>\n");
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    public static void formatItemStack(ItemStack stack, StringBuilder sb) {
        if (stack == null) {
            sb.append("null");
            return;
        }
        sb.append(stack.stackSize).append("x");
        Item item = stack.getItem();
        if (item == null) {
            sb.append("<NULL>");
        } else {
            String name = Item.itemRegistry.getNameForObject(item);
            sb.append(name != null ? name : item.getClass().getSimpleName());
        }
        if (stack.getItemDamage() != 0) sb.append(":").append(stack.getItemDamage());
        if (stack.hasTagCompound()) {
            sb.append(" ").append(JsonNbtParser.nbtToString(stack.getTagCompound()));
        }
    }

    public static String byteBufToHexString(ByteBuf buf) {
        return byteBufToHexString(buf, 256);
    }

    public static String byteBufToHexString(ByteBuf buf, int limit) {
        if (buf == null || !buf.isReadable()) return "";
        ByteBuf copy = buf.copy();
        int len = Math.min(copy.readableBytes(), limit);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X ", copy.readByte()));
        }
        if (copy.readableBytes() > 0) sb.append("...");
        copy.release();
        return sb.toString();
    }

    private static String tryDecodeString(ByteBuf buf) {
        if (buf.readableBytes() > 1024) return null;
        ByteBuf copy = buf.copy();
        try {
            byte[] bytes = new byte[copy.readableBytes()];
            copy.readBytes(bytes);
            String s = new String(bytes, StandardCharsets.UTF_8);
            int weirdChars = 0;
            for (char c : s.toCharArray()) {
                if (Character.isISOControl(c) && !Character.isWhitespace(c)) weirdChars++;
            }
            if (weirdChars > s.length() * 0.2) return null;
            return s;
        } catch (Exception e) {
            return null;
        } finally {
            copy.release();
        }
    }

    public static NBTTagCompound tryReadNBT(ByteBuf buf) {
        if (buf == null || !buf.isReadable()) return null;
        buf.markReaderIndex();
        try {
            int readableBytes = buf.readableBytes();
            if (readableBytes > 2097152) return null;
            byte[] bytes = new byte[readableBytes];
            buf.readBytes(bytes);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
            return CompressedStreamTools.read(dis);
        } catch (Exception e) {
            return null;
        } finally {
            buf.resetReaderIndex();
        }
    }

    public static ByteBuf writeNBTToBuf(NBTTagCompound nbt) {
        if (nbt == null) return null;
        ByteBuf buf = Unpooled.buffer();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            CompressedStreamTools.write(nbt, dos);
            buf.writeBytes(baos.toByteArray());
            return buf;
        } catch (Exception e) {
            e.printStackTrace();
            buf.release();
            return null;
        }
    }

    public static Packet clonePacket(PacketInfo packetInfo) {
        try {
            Packet originalPacket = packetInfo.rawPacket;
            if (originalPacket == null) return null;

            if (originalPacket instanceof FMLProxyPacket) {
                if (packetInfo.rawPayloadBytes == null) return null;
                ByteBuf newPayload = Unpooled.copiedBuffer(packetInfo.rawPayloadBytes);
                FMLProxyPacket clone = new FMLProxyPacket(newPayload, ((FMLProxyPacket) originalPacket).channel());
                clone.setDispatcher(((FMLProxyPacket) originalPacket).getDispatcher());
                clone.setTarget(((FMLProxyPacket) originalPacket).getTarget());
                return clone;
            }

            Class<?> packetClass = originalPacket.getClass();
            Packet newPacket;
            try {
                newPacket = (Packet) packetClass.newInstance();
            } catch (Throwable t) {
                if (unsafeInstance != null) {
                    newPacket = (Packet) unsafeInstance.allocateInstance(packetClass);
                } else {
                    return null;
                }
            }
            copyFieldsWithDeepClone(originalPacket, newPacket, packetClass);
            return newPacket;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void copyFieldsWithDeepClone(Object source, Object target, Class<?> clazz) throws IllegalAccessException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                field.set(target, deepCloneValue(field.get(source)));
            }
            current = current.getSuperclass();
        }
    }

    @SuppressWarnings("unchecked")
    private static Object deepCloneValue(Object value) {
        if (value == null) return null;

         
         
        if (value instanceof ItemStack) {
            return ((ItemStack) value).copy();
        }
        if (value instanceof NBTBase) {
            return ((NBTBase) value).copy();
        }

         
         
        if (value instanceof List) {
            List<Object> originalList = (List<Object>) value;
            List<Object> newList = new ArrayList<>(originalList.size());
            for (Object o : originalList) {
                newList.add(deepCloneValue(o));
            }
            return newList;
        }

        if (value instanceof Map) {
            Map<Object, Object> originalMap = (Map<Object, Object>) value;
            Map<Object, Object> newMap = new HashMap<>(originalMap.size());
            for (Map.Entry<Object, Object> entry : originalMap.entrySet()) {
                newMap.put(deepCloneValue(entry.getKey()), deepCloneValue(entry.getValue()));
            }
            return newMap;
        }

        if (value instanceof Set) {
            Set<Object> originalSet = (Set<Object>) value;
            Set<Object> newSet = new HashSet<>(originalSet.size());
            for (Object o : originalSet) {
                newSet.add(deepCloneValue(o));
            }
            return newSet;
        }

         
        if (value.getClass().isArray()) {
             
            if (value instanceof Object[]) {
                Object[] originalArr = (Object[]) value;
                Object[] newArr = (Object[]) Array.newInstance(value.getClass().getComponentType(), originalArr.length);
                for (int i = 0; i < originalArr.length; i++) {
                    newArr[i] = deepCloneValue(originalArr[i]);
                }
                return newArr;
            }

             
            if (value instanceof byte[]) return ((byte[]) value).clone();
            if (value instanceof int[]) return ((int[]) value).clone();
            if (value instanceof double[]) return ((double[]) value).clone();
            if (value instanceof float[]) return ((float[]) value).clone();
            if (value instanceof long[]) return ((long[]) value).clone();
            if (value instanceof short[]) return ((short[]) value).clone();
            if (value instanceof boolean[]) return ((boolean[]) value).clone();
            if (value instanceof char[]) return ((char[]) value).clone();
        }

         
         
        return value;
    }
}