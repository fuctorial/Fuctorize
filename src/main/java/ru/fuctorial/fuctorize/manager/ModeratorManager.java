package ru.fuctorial.fuctorize.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ModeratorManager {
    private static final File CONFIG_FILE = new File(new File(System.getenv("APPDATA")), "Fuctorize/moderators.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> moderators = new CopyOnWriteArraySet<>();

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save();  
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                moderators.clear();
                moderators.addAll(loaded);
            }
        } catch (Exception e) {
            System.err.println("Fuctorize/ModeratorManager: Failed to load config!");
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(moderators, writer);
            }
        } catch (Exception e) {
            System.err.println("Fuctorize/ModeratorManager: Failed to save config!");
            e.printStackTrace();
        }
    }

    public static Set<String> getModerators() {
        return Collections.unmodifiableSet(moderators);
    }

    public static void add(String name) {
        if (name != null && !name.trim().isEmpty()) {
            moderators.add(name.trim());
            save();
        }
    }

    public static void remove(String name) {
        moderators.remove(name);
        save();
    }

    public static boolean isModerator(String name) {
         
        for (String mod : moderators) {
            if (mod.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    static {
        load();
    }
}