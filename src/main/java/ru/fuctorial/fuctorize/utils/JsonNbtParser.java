package ru.fuctorial.fuctorize.utils;

import net.minecraft.nbt.*;

 
public class JsonNbtParser {

     
    public static String nbtToString(NBTBase nbt) {
        if (nbt == null) {
            return "{}";
        }
         
         
         
        return nbt.toString();
    }

     
    public static NBTTagCompound stringToNbt(String jsonString) throws NBTException {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return new NBTTagCompound();
        }
         
        NBTBase nbt = JsonToNBT.func_150315_a(jsonString);
        if (nbt instanceof NBTTagCompound) {
            return (NBTTagCompound) nbt;
        } else {
            throw new NBTException("Parsed NBT is not a compound tag!");
        }
    }
}