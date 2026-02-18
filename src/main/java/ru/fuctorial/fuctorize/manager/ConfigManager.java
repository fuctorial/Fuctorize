 
package ru.fuctorial.fuctorize.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.smartmoving.GuiSmartMovingEditor;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.*;
import ru.fuctorial.fuctorize.module.Category;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;

public class ConfigManager {
    private final File configDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final FuctorizeClient client;

    public ConfigManager(FuctorizeClient client) {
        this.client = client;
        File appData = new File(System.getenv("APPDATA"));
        this.configDir = new File(appData, "Fuctorize");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
    }

    public void saveConfig() {
        try {
            JsonObject fuctorizeJson = new JsonObject();
            JsonObject modulesJson = new JsonObject();
            JsonObject guiJson = new JsonObject();

             
            for (Module module : client.moduleManager.getModules()) {
                JsonObject moduleJson = new JsonObject();
                moduleJson.addProperty("enabled", module.isEnabled());
                moduleJson.addProperty("extended", module.extended);  

                for (Setting setting : module.getSettings()) {
                    if (setting instanceof BooleanSetting) {
                        moduleJson.addProperty(setting.name, ((BooleanSetting) setting).enabled);
                    } else if (setting instanceof SliderSetting) {
                        moduleJson.addProperty(setting.name, ((SliderSetting) setting).value);
                    } else if (setting instanceof ModeSetting) {
                         
                        ModeSetting ms = (ModeSetting) setting;
                        moduleJson.addProperty(setting.name, ms.getMode());
                        moduleJson.addProperty(setting.name + "_index", ms.index);
                    } else if (setting instanceof TextSetting) {
                        moduleJson.addProperty(setting.name, ((TextSetting) setting).text);
                    } else if (setting instanceof ColorSetting) {
                        ColorSetting cs = (ColorSetting) setting;
                        moduleJson.addProperty(setting.name, cs.getColor());
                        moduleJson.addProperty(setting.name + "_expanded", cs.expanded);  
                    }
                }

                BindSetting bind = BindSetting.getBindSetting(module);
                if (bind != null) {
                    moduleJson.addProperty("bind", bind.keyCode);
                }

                 
                modulesJson.add(module.getKey(), moduleJson);
            }

             
            saveSmartMovingGuiState(guiJson);
             
            saveClickGuiState(guiJson);


            fuctorizeJson.add("modules", modulesJson);
            fuctorizeJson.add("gui", guiJson);

            File configFile = new File(configDir, "config.json");
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(gson.toJson(fuctorizeJson));
            }

            System.out.println("Fuctorize config saved!");

        } catch (IOException e) {
            System.err.println("Failed to save Fuctorize config!");
            e.printStackTrace();
        }
    }

    public void loadConfig() {
        File configFile = new File(configDir, "config.json");
        if (!configFile.exists()) {
            System.out.println("No Fuctorize config found, skipping load.");
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonParser parser = new JsonParser();
            JsonObject fuctorizeJson = (JsonObject) parser.parse(reader);

            if (fuctorizeJson.isJsonNull()) {
                System.err.println("Fuctorize config is empty or invalid.");
                return;
            }

             
            if (fuctorizeJson.has("modules")) {
                JsonObject modulesJson = fuctorizeJson.getAsJsonObject("modules");
                for (Module module : client.moduleManager.getModules()) {
                     
                    JsonObject moduleJson = modulesJson.getAsJsonObject(module.getKey());
                     
                    if (moduleJson == null) {
                        moduleJson = modulesJson.getAsJsonObject(module.getName());
                    }
                    if (moduleJson != null) {
                         
                        if (moduleJson.has("enabled")) {
                            module.setEnabledFromConfig(moduleJson.get("enabled").getAsBoolean());
                        }

                        if (moduleJson.has("extended")) {
                            module.extended = moduleJson.get("extended").getAsBoolean();
                        }

                        for (Setting setting : module.getSettings()) {
                            if (moduleJson.has(setting.name)) {
                                if (setting instanceof BooleanSetting) {
                                    ((BooleanSetting) setting).enabled = moduleJson.get(setting.name).getAsBoolean();
                                } else if (setting instanceof SliderSetting) {
                                    ((SliderSetting) setting).value = moduleJson.get(setting.name).getAsDouble();
                                } else if (setting instanceof ModeSetting) {
                                    ModeSetting modeSetting = (ModeSetting) setting;
                                     
                                    int idx = -1;
                                    String indexKey = setting.name + "_index";
                                    if (moduleJson.has(indexKey)) {
                                        try {
                                            idx = moduleJson.get(indexKey).getAsInt();
                                        } catch (Exception ignored) {}
                                    }
                                    if (idx >= 0 && idx < modeSetting.modes.size()) {
                                        modeSetting.index = idx;
                                    } else if (moduleJson.has(setting.name)) {
                                        try {
                                            String modeName = moduleJson.get(setting.name).getAsString();
                                            int modeIndex = modeSetting.modes.indexOf(modeName);
                                            modeSetting.index = Math.max(modeIndex, 0);
                                        } catch (Exception ignored) {
                                            modeSetting.index = Math.max(modeSetting.index, 0);
                                        }
                                    }
                                } else if (setting instanceof TextSetting) {
                                    ((TextSetting) setting).text = moduleJson.get(setting.name).getAsString();
                                } else if (setting instanceof ColorSetting) {
                                    ColorSetting cs = (ColorSetting) setting;
                                    cs.setColor(moduleJson.get(setting.name).getAsInt());
                                     
                                    if (moduleJson.has(setting.name + "_expanded")) {
                                        cs.expanded = moduleJson.get(setting.name + "_expanded").getAsBoolean();
                                    }
                                }
                            }
                        }

                        BindSetting bind = BindSetting.getBindSetting(module);
                        if (bind != null && moduleJson.has("bind")) {
                            bind.keyCode = moduleJson.get("bind").getAsInt();
                        }
                    }
                }
            }

             
            if (fuctorizeJson.has("gui")) {
                JsonObject guiJson = fuctorizeJson.getAsJsonObject("gui");
                loadSmartMovingGuiState(guiJson);
                loadClickGuiState(guiJson);
            }

            System.out.println("Fuctorize config loaded!");
        } catch (Exception e) {
            System.err.println("Failed to load Fuctorize config!");
            e.printStackTrace();
        }
    }

    private void saveSmartMovingGuiState(JsonObject guiJson) {
        try {
            Class<?> guiClass = GuiSmartMovingEditor.class;
            Field contentScrollField = guiClass.getDeclaredField("savedContentScrollY");
            Field categoryScrollField = guiClass.getDeclaredField("savedCategoryScrollY");
            Field categoryNameField = guiClass.getDeclaredField("savedCategoryName");

            contentScrollField.setAccessible(true);
            categoryScrollField.setAccessible(true);
            categoryNameField.setAccessible(true);

            guiJson.addProperty("smartMovingEditor_contentScroll", (int) contentScrollField.get(null));
            guiJson.addProperty("smartMovingEditor_categoryScroll", (int) categoryScrollField.get(null));
            String category = (String) categoryNameField.get(null);
            if (category != null) {
                guiJson.addProperty("smartMovingEditor_categoryName", category);
            }

        } catch (Exception e) {
            System.err.println("Failed to save SmartMoving Editor GUI state.");
        }
    }

    private void loadSmartMovingGuiState(JsonObject guiJson) {
        try {
            Class<?> guiClass = GuiSmartMovingEditor.class;
            Field contentScrollField = guiClass.getDeclaredField("savedContentScrollY");
            Field categoryScrollField = guiClass.getDeclaredField("savedCategoryScrollY");
            Field categoryNameField = guiClass.getDeclaredField("savedCategoryName");

            contentScrollField.setAccessible(true);
            categoryScrollField.setAccessible(true);
            categoryNameField.setAccessible(true);

            if (guiJson.has("smartMovingEditor_contentScroll")) {
                contentScrollField.set(null, guiJson.get("smartMovingEditor_contentScroll").getAsInt());
            }
            if (guiJson.has("smartMovingEditor_categoryScroll")) {
                categoryScrollField.set(null, guiJson.get("smartMovingEditor_categoryScroll").getAsInt());
            }
            if (guiJson.has("smartMovingEditor_categoryName")) {
                categoryNameField.set(null, guiJson.get("smartMovingEditor_categoryName").getAsString());
            }

        } catch (Exception e) {
            System.err.println("Failed to load SmartMoving Editor GUI state.");
        }
    }

     
    private void saveClickGuiState(JsonObject guiJson) {
        try {
             
            Class<?> screenClass = ru.fuctorial.fuctorize.client.gui.clickgui.ClickGuiScreen.class;
            Field mainFrameField = screenClass.getDeclaredField("mainFrame");
            mainFrameField.setAccessible(true);
            Object frameObj = mainFrameField.get(client.clickGui);
            if (frameObj == null) return;

            Class<?> frameClass = ru.fuctorial.fuctorize.client.gui.clickgui.Frame.class;
            Field scrollField = frameClass.getSuperclass().getDeclaredField("scrollY");  
            Field currentCategoryField = frameClass.getSuperclass().getDeclaredField("currentCategory");
            scrollField.setAccessible(true);
            currentCategoryField.setAccessible(true);

            int scrollY = scrollField.getInt(frameObj);
            Object currentCatObj = currentCategoryField.get(frameObj);

            guiJson.addProperty("clickGui_scrollY", scrollY);
            if (currentCatObj instanceof Category) {
                guiJson.addProperty("clickGui_category", ((Category) currentCatObj).name());
            } else if (currentCatObj != null) {
                guiJson.addProperty("clickGui_category", currentCatObj.toString());
            }
        } catch (Throwable t) {
            System.err.println("Failed to save ClickGUI state.");
        }
    }

    private void loadClickGuiState(JsonObject guiJson) {
        try {
            if (!guiJson.has("clickGui_scrollY") && !guiJson.has("clickGui_category")) return;

            Class<?> screenClass = ru.fuctorial.fuctorize.client.gui.clickgui.ClickGuiScreen.class;
            Field mainFrameField = screenClass.getDeclaredField("mainFrame");
            mainFrameField.setAccessible(true);
            Object frameObj = mainFrameField.get(client.clickGui);
            if (frameObj == null) return;

            Class<?> frameClass = ru.fuctorial.fuctorize.client.gui.clickgui.Frame.class;
            Field scrollField = frameClass.getSuperclass().getDeclaredField("scrollY");  
            Field currentCategoryField = frameClass.getSuperclass().getDeclaredField("currentCategory");
            scrollField.setAccessible(true);
            currentCategoryField.setAccessible(true);

            if (guiJson.has("clickGui_scrollY")) {
                int scrollY = guiJson.get("clickGui_scrollY").getAsInt();
                scrollField.setInt(frameObj, scrollY);
            }

            if (guiJson.has("clickGui_category")) {
                String catName = guiJson.get("clickGui_category").getAsString();
                try {
                    Category category = Category.valueOf(catName);
                    currentCategoryField.set(frameObj, category);
                } catch (IllegalArgumentException ignored) {
                     
                }
            }
        } catch (Throwable t) {
            System.err.println("Failed to load ClickGUI state.");
        }
    }
}
