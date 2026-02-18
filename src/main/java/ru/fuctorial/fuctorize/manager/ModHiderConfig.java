package ru.fuctorial.fuctorize.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.fuctorial.fuctorize.Fuctorize;  

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;


 





public class ModHiderConfig {

    private static final File CONFIG_FILE = new File(new File(System.getenv("APPDATA")), "Fuctorize/hider.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static boolean saveOnExit = true;

    private static final String DEFAULT_MOD_ID = "OptiFine";
    private static final String DEFAULT_VERSION = "HD_U_D5";

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            System.out.println("Fuctorize Hider: No config found. Using default OptiFine disguise.");
             
            Fuctorize.modId = DEFAULT_MOD_ID;
            Fuctorize.modVersion = DEFAULT_VERSION;
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonParser parser = new JsonParser();
            JsonObject json = (JsonObject) parser.parse(reader);

            if (json.has("modid")) {
                Fuctorize.modId = json.get("modid").getAsString();
            } else {
                Fuctorize.modId = DEFAULT_MOD_ID;
            }

            if (json.has("version")) {
                Fuctorize.modVersion = json.get("version").getAsString();
            } else {
                Fuctorize.modVersion = DEFAULT_VERSION;
            }
            System.out.println("Fuctorize Hider: Loaded config. Disguised as: '" + Fuctorize.modId + "', Version: " + Fuctorize.modVersion);

        } catch (Exception e) {
            System.err.println("Fuctorize Hider: Failed to load config! Using OptiFine defaults.");
            Fuctorize.modId = DEFAULT_MOD_ID;
            Fuctorize.modVersion = DEFAULT_VERSION;
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();

            JsonObject json = new JsonObject();
             
            json.addProperty("modid", Fuctorize.modId);
            json.addProperty("version", Fuctorize.modVersion);

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                writer.write(GSON.toJson(json));
            }
            System.out.println("Fuctorize Hider: Config saved. Disguised as: '" + Fuctorize.modId + "'");
        } catch (IOException e) {
            System.err.println("Fuctorize Hider: Failed to save config!");
            e.printStackTrace();
        }
    }
}