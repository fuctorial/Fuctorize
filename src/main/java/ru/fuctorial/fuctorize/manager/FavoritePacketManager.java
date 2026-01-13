package ru.fuctorial.fuctorize.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FavoritePacketManager {
    private static final File CONFIG_FILE = new File(new File(System.getenv("APPDATA")), "Fuctorize/favorite_packets.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<PacketPersistence.SavedPacketData> favorites = new ArrayList<>();

    public static void load() {
        if (!CONFIG_FILE.exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<List<PacketPersistence.SavedPacketData>>() {}.getType();
            List<PacketPersistence.SavedPacketData> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                favorites.clear();
                favorites.addAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(favorites, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<PacketPersistence.SavedPacketData> getFavorites() {
        return favorites;
    }

    public static void add(PacketPersistence.SavedPacketData data) {
        favorites.add(data);
        save();
    }

    public static void remove(int index) {
        if (index >= 0 && index < favorites.size()) {
            favorites.remove(index);
            save();
        }
    }

    static {
        load();
    }
}