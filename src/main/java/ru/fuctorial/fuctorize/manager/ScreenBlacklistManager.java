 
package ru.fuctorial.fuctorize.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.GuiScreen;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ScreenBlacklistManager {
    private static final File CONFIG_FILE = new File(new File(System.getenv("APPDATA")), "Fuctorize/screen_blacklist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> blacklist = new CopyOnWriteArraySet<>();

    public static void load() {
        if (!CONFIG_FILE.exists()) {
             
            blacklist.add("net.minecraft.client.gui.GuiChat");
            blacklist.add("ru.fuctorial.fuctorize.client.gui.sniffer.GuiScreenHistory");
            blacklist.add("ru.fuctorial.fuctorize.client.gui.clickgui.ClickGuiScreen");
            save();
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                blacklist.clear();
                blacklist.addAll(loaded);
            }
        } catch (Exception e) {
            System.err.println("Fuctorize/ScreenBlacklist: Failed to load blacklist config!");
            e.printStackTrace();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(blacklist, writer);
        } catch (Exception e) {
            System.err.println("Fuctorize/ScreenBlacklist: Failed to save blacklist config!");
            e.printStackTrace();
        }
    }

    public static Set<String> getBlacklistedClassNames() {
        return Collections.unmodifiableSet(blacklist);
    }

    public static void add(String className) {
        if (className != null && !className.isEmpty()) {
            blacklist.add(className);
            save();
        }
    }

    public static void add(GuiScreen screen) {
        if (screen != null) {
            add(screen.getClass().getName());
        }
    }

    public static void remove(String className) {
        blacklist.remove(className);
        save();
    }

    public static boolean isBlacklisted(GuiScreen screen) {
        if (screen == null) {
            return false;  
        }
        return blacklist.contains(screen.getClass().getName());
    }
}