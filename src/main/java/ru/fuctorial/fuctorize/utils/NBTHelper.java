package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.relauncher.ReflectionHelper;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class NBTHelper {

    private static Field NBTTagCompound_tagMapField;
    private static Field NBTTagList_tagListField;
    private static Field NBTTagList_tagTypeField;  

    static {
        try {
             
            NBTTagCompound_tagMapField = ReflectionHelper.findField(NBTTagCompound.class, "tagMap", "field_74784_a");
            NBTTagCompound_tagMapField.setAccessible(true);
            System.out.println("Fuctorize/NBTHelper: Successfully reflected NBTTagCompound.tagMap field!");

            NBTTagList_tagListField = ReflectionHelper.findField(NBTTagList.class, "tagList", "field_74747_a");
            NBTTagList_tagListField.setAccessible(true);
            System.out.println("Fuctorize/NBTHelper: Successfully reflected NBTTagList.tagList field!");

             
            NBTTagList_tagTypeField = ReflectionHelper.findField(NBTTagList.class, "tagType", "field_74748_b");
            NBTTagList_tagTypeField.setAccessible(true);
            System.out.println("Fuctorize/NBTHelper: Successfully reflected NBTTagList.tagType field!");

        } catch (Exception e) {
            System.err.println("Fuctorize/NBTHelper: CRITICAL - Failed to reflect NBT fields! NBTEditor will not function correctly.");
            e.printStackTrace();
        }
    }

    public static NBTTagCompound nbtRead(DataInputStream in) throws IOException {
        return CompressedStreamTools.read(in);
    }

    public static void nbtWrite(NBTTagCompound compound, DataOutput out) throws IOException {
        CompressedStreamTools.write(compound, out);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, NBTBase> getMap(NBTTagCompound tag) {
        if (NBTTagCompound_tagMapField == null) return Collections.emptyMap();
        try {
            return (Map<String, NBTBase>) NBTTagCompound_tagMapField.get(tag);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("rawtypes")
    public static NBTBase getTagAt(NBTTagList tag, int index) {
        if (NBTTagList_tagListField == null) return null;
        try {
            List list = (List) NBTTagList_tagListField.get(tag);
            if (index >= 0 && index < list.size()) {
                return (NBTBase) list.get(index);
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

     
    public static void setListTagType(NBTTagList tagList, byte type) {
        if (NBTTagList_tagTypeField == null) return;
        try {
            NBTTagList_tagTypeField.setByte(tagList, type);
        } catch (Exception e) {
            System.err.println("Fuctorize/NBTHelper: Failed to set NBTTagList type via reflection!");
            e.printStackTrace();
        }
    }
}