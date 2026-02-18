 
package ru.fuctorial.fuctorize.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import ru.fuctorial.fuctorize.utils.ScreenContext;
import ru.fuctorial.fuctorize.utils.ScreenContextResult;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.lang.ref.WeakReference;

public class FavoriteScreenManager {
    private static final File CONFIG_FILE = new File(new File(System.getenv("APPDATA")), "Fuctorize/favorites_screens.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> favorites = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, WeakReference<GuiScreen>> liveInstances = new ConcurrentHashMap<>();

    public static void load() {
        try {
            if (!CONFIG_FILE.exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
                save();
                return;
            }
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    favorites.clear();
                    favorites.putAll(loaded);
                }
            }
        } catch (Exception e) {
            System.err.println("Fuctorize/FavoriteScreenManager: failed to load favorites");
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
            System.err.println("Fuctorize/FavoriteScreenManager: failed to save favorites");
            e.printStackTrace();
        }
    }

     
    public static GuiScreen instantiateScreen(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (className.equals("net.minecraft.client.gui.inventory.GuiInventory")) {
            if (mc.thePlayer != null) {
                return new net.minecraft.client.gui.inventory.GuiInventory(mc.thePlayer);
            }
        }
        if (className.equals("net.minecraft.client.gui.GuiChat")) {
            return new net.minecraft.client.gui.GuiChat();
        }
        if (className.equals("net.minecraft.client.gui.GuiMainMenu")) {
            return new GuiMainMenu();
        }
        if (className.equals("net.minecraft.client.gui.GuiSelectWorld")) {
            return new GuiSelectWorld(new GuiMainMenu());
        }

         
         
        try {
            Class<?> screenClass = Class.forName(className);
            Object instance = screenClass.newInstance();
            if (instance instanceof GuiScreen) {
                return (GuiScreen) instance;
            }
        } catch (Throwable ignored) {
             
        }

        return null;
    }

    public static Map<String, String> getFavorites() {
        return Collections.unmodifiableMap(favorites);
    }

    public static Map<String, GuiScreen> getLiveInstances() {
        Map<String, GuiScreen> res = new HashMap<>();
        for (Map.Entry<String, WeakReference<GuiScreen>> e : liveInstances.entrySet()) {
            GuiScreen s = e.getValue() == null ? null : e.getValue().get();
            res.put(e.getKey(), s);
        }
        return Collections.unmodifiableMap(res);
    }

    public static void registerLiveInstance(String uniqueKey, GuiScreen screen) {
        if (uniqueKey == null || screen == null) return;
        liveInstances.put(uniqueKey, new WeakReference<>(screen));
    }

    public static GuiScreen findBestLiveInstance(String uniqueKey) {
        if (uniqueKey == null) return null;
        WeakReference<GuiScreen> ref = liveInstances.get(uniqueKey);
        if (ref != null) {
            GuiScreen s = ref.get();
            if (s != null) return s;
        }

        String className = classNameFromUnique(uniqueKey);
        if (className == null || className.isEmpty()) return null;

        long bestTs = -1;
        GuiScreen best = null;
        for (Map.Entry<String, WeakReference<GuiScreen>> e : liveInstances.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            if (className.equals(classNameFromUnique(key))) {
                long ts = tsFromUnique(key);
                GuiScreen cand = e.getValue() == null ? null : e.getValue().get();
                if (cand != null) {
                    if (ts > bestTs) {
                        bestTs = ts;
                        best = cand;
                    }
                }
            }
        }
        return best;
    }

    public static void addFavorite(String customName, GuiScreen screen) {
        if (customName == null || customName.trim().isEmpty() || screen == null) return;

        String uniqueKey = generateUniqueKey(screen);
        favorites.put(customName.trim(), uniqueKey);
        registerLiveInstance(uniqueKey, screen);
        save();
    }

    public static void removeFavorite(String customName) {
        if (customName == null) return;
        favorites.remove(customName);
        save();
    }

    public static boolean isFavorite(GuiScreen screen) {
        if (screen == null) return false;
        String className = screen.getClass().getName();

        String maybeUnique = getUniqueKeyForScreen(screen);
        if (maybeUnique != null) {
            if (favorites.containsValue(maybeUnique)) return true;
        }

        for (String v : favorites.values()) {
            if (v == null) continue;
            if (v.equals(className)) return true;
            if (v.startsWith(className + "::")) return true;
        }
        return false;
    }

    public static void removeFavoriteForScreen(GuiScreen screen) {
        if (screen == null) return;
        String className = screen.getClass().getName();
        String uniqueOfScreen = getUniqueKeyForScreen(screen);

        String keyToRemove = null;
        for (Map.Entry<String, String> e : favorites.entrySet()) {
            String value = e.getValue();
            if (value == null) continue;
            if (uniqueOfScreen != null && value.equals(uniqueOfScreen)) {
                keyToRemove = e.getKey();
                break;
            }
            if (value.equals(className) || value.startsWith(className + "::")) {
                keyToRemove = e.getKey();
                break;
            }
        }

        if (keyToRemove != null) {
            favorites.remove(keyToRemove);
            save();
        }
    }

    public static void updateInstance(GuiScreen screen) {
        if (screen == null) return;
        String uniqueKey = generateUniqueKey(screen);
        registerLiveInstance(uniqueKey, screen);
    }

    public static void removeInstance(GuiScreen screen) {
        if (screen == null) return;
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, WeakReference<GuiScreen>> e : liveInstances.entrySet()) {
            GuiScreen s = e.getValue() == null ? null : e.getValue().get();
            if (s == screen) toRemove.add(e.getKey());
        }
        for (String k : toRemove) liveInstances.remove(k);
    }

    public static String getUniqueKeyForScreen(GuiScreen screen) {
        if (screen == null) return null;
        for (Map.Entry<String, WeakReference<GuiScreen>> e : liveInstances.entrySet()) {
            GuiScreen s = e.getValue() == null ? null : e.getValue().get();
            if (s == screen) return e.getKey();
        }
        return generateUniqueKey(screen);
    }

    public static String classNameFromUnique(String uniqueKey) {
        if (uniqueKey == null) return null;
        String[] parts = uniqueKey.split("::", 3);
        return parts.length >= 1 ? parts[0] : null;
    }

    public static String hashFromUnique(String uniqueKey) {
        if (uniqueKey == null) return null;
        String[] parts = uniqueKey.split("::", 3);
        return parts.length >= 2 ? parts[1] : null;
    }

    public static long tsFromUnique(String uniqueKey) {
        if (uniqueKey == null) return 0L;
        String[] parts = uniqueKey.split("::", 3);
        if (parts.length < 3) return 0L;
        try {
            return Long.parseLong(parts[2], 16);
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static String generateUniqueKey(GuiScreen screen) {
        String className = screen.getClass().getName();
        ScreenContextResult context = ScreenContext.getScreenContext(screen);
        String ctxHash = (context != null && context.hash != null) ? context.hash : "";
        long ts = System.currentTimeMillis();
        return className + "::" + ctxHash + "::" + Long.toHexString(ts);
    }
}
