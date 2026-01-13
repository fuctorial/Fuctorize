package ru.fuctorial.fuctorize.utils;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

public class NBTStringHelper {

    public static NBTBase newTag(byte type) {
        switch (type) {
            case 1:  return new NBTTagByte((byte) 0);
            case 2:  return new NBTTagShort((short) 0);
            case 3:  return new NBTTagInt(0);
            case 4:  return new NBTTagLong(0L);
            case 5:  return new NBTTagFloat(0.0f);
            case 6:  return new NBTTagDouble(0.0);
            case 7:  return new NBTTagByteArray(new byte[0]);
            case 8:  return new NBTTagString("");
            case 9:  return new NBTTagList();
            case 10: return new NBTTagCompound();
            case 11: return new NBTTagIntArray(new int[0]);
        }
        return null;
    }

    /**
     * Generates a clean, user-friendly string representation of an NBT tag's value,
     * formatted exactly as it should appear in the GUI tree for display purposes.
     * This is the single source of truth for value representation in the tree view.
     * @param base The NBT tag.
     * @return A string representing the value, with quotes for strings and suffixes for numbers.
     */
    public static String toString(NBTBase base) {
        if (base == null) return "null";
        byte id = base.getId();
        switch (id) {
            case 10: // Compound
                return "(TagCompound)";
            case 1: // Byte
                return ((NBTTagByte) base).func_150290_f() + "b";
            case 2: // Short
                return ((NBTTagShort) base).func_150289_e() + "s";
            case 3: // Int
                return "" + ((NBTTagInt) base).func_150287_d();
            case 4: // Long
                return ((NBTTagLong) base).func_150291_c() + "L";
            case 5: // Float
                return ((NBTTagFloat) base).func_150288_h() + "f";
            case 6: // Double
                return ((NBTTagDouble) base).func_150286_g() + "d";
            case 8: // String
                // Add quotes here, and ONLY here, for display in the tree.
                return "\"" + ((NBTTagString) base).func_150285_a_() + "\"";
            case 9: // List
                return "(TagList)";
            case 7: // Byte Array
                return "[B;" + ((NBTTagByteArray) base).func_150292_c().length + " bytes]";
            case 11: // Int Array
                return "[I;" + ((NBTTagIntArray) base).func_150302_c().length + " ints]";
        }
        return "?";
    }

    public static String getButtonName(byte id) {
        switch (id) {
            case 1:  return "Byte";
            case 2:  return "Short";
            case 3:  return "Int";
            case 4:  return "Long";
            case 5:  return "Float";
            case 6:  return "Double";
            case 7:  return "ByteArray";
            case 8:  return "String";
            case 9:  return "List";
            case 10: return "Compound";
            case 11: return "IntArray";
            case 12: return "Edit";
            case 13: return "Delete";
            case 14: return "Copy";
            case 15: return "Cut";
            case 16: return "Paste";
        }
        return "Unknown";
    }
}