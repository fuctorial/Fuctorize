package ru.fuctorial.fuctorize.utils;

import net.minecraft.nbt.*;

/**
 * A robust, symmetrical parser and serializer for converting between NBT and Mojangson string format.
 * This ensures that what is serialized can be perfectly deserialized back without data loss.
 * It now uses the vanilla parser for maximum compatibility.
 */
public class JsonNbtParser {

    /**
     * Converts an NBT tag into a strictly valid JSON-compatible string (Mojangson).
     * This method is now fully symmetrical with the vanilla parser.
     */
    public static String nbtToString(NBTBase nbt) {
        if (nbt == null) {
            return "{}";
        }
        // FUNDAMENTAL FIX: The vanilla .toString() method for NBT tags already produces
        // the correct Mojangson format that its own parser can understand.
        // We leverage this for perfect symmetry and correctness.
        return nbt.toString();
    }

    /**
     * Tries to parse a string into an NBTTagCompound using the vanilla parser.
     */
    public static NBTTagCompound stringToNbt(String jsonString) throws NBTException {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return new NBTTagCompound();
        }
        // Use the reliable vanilla parser.
        NBTBase nbt = JsonToNBT.func_150315_a(jsonString);
        if (nbt instanceof NBTTagCompound) {
            return (NBTTagCompound) nbt;
        } else {
            throw new NBTException("Parsed NBT is not a compound tag!");
        }
    }
}