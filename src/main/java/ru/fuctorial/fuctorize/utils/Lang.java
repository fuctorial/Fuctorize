package ru.fuctorial.fuctorize.utils;

import net.minecraft.client.Minecraft;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Lang {

    private static final Properties defaultTranslations = new Properties();  
    private static final Properties translations = new Properties();  
    private static boolean isLoaded = false;
    private static final String FALLBACK_LANG = "en_US";

     
    private static String lastLoadedLangCode = "";

    public static void load() {
         
        String currentLang = getMinecraftLanguage();

         
        if (defaultTranslations.isEmpty()) {
            loadLangFile(FALLBACK_LANG, defaultTranslations);
        }

         
        translations.clear();
        translations.putAll(defaultTranslations);

         
        if (!FALLBACK_LANG.equalsIgnoreCase(currentLang)) {
            loadLangFile(currentLang, translations);
        }

        isLoaded = true;
        lastLoadedLangCode = currentLang;
        System.out.println("Fuctorize/Lang: Language updated to " + currentLang);
    }

     
     
    public static void update() {
        String currentMcLang = getMinecraftLanguage();
         
        if (!currentMcLang.equals(lastLoadedLangCode)) {
            load();
        }
    }

    private static String getMinecraftLanguage() {
        try {
            return Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
        } catch (Exception e) {
            return FALLBACK_LANG;
        }
    }

    private static void loadLangFile(String langCode, Properties props) {
        String path = "/assets/fuctorize/lang/" + langCode + ".lang";
        InputStream inputStream = Lang.class.getResourceAsStream(path);

        if (inputStream == null) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                inputStream = contextClassLoader.getResourceAsStream(path.startsWith("/") ? path.substring(1) : path);
            }
        }

        if (inputStream == null) return;

        try (InputStream is = inputStream;
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String get(String key) {
        if (!isLoaded) return key;
        return translations.getProperty(key, defaultTranslations.getProperty(key, key));
    }

    public static String format(String key, Object... args) {
        return String.format(get(key), args);
    }

    public static void reset() {
        isLoaded = false;
        defaultTranslations.clear();
        translations.clear();
        lastLoadedLangCode = "";
    }
}