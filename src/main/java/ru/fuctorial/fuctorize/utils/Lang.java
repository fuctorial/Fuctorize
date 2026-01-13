package ru.fuctorial.fuctorize.utils;

import net.minecraft.client.Minecraft;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Lang {

    private static final Properties defaultTranslations = new Properties(); // en_US
    private static final Properties translations = new Properties(); // Текущий язык
    private static boolean isLoaded = false;
    private static final String FALLBACK_LANG = "en_US";

    // --- НОВОЕ ПОЛЕ: Запоминаем, какой язык мы загружали последним ---
    private static String lastLoadedLangCode = "";

    public static void load() {
        // Получаем текущий код языка из Minecraft (например, "ru_RU" или "en_US")
        String currentLang = getMinecraftLanguage();

        // 1) Всегда грузим фоллбэк (en_US), если еще не загружен или если мы делаем полную перезагрузку
        if (defaultTranslations.isEmpty()) {
            loadLangFile(FALLBACK_LANG, defaultTranslations);
        }

        // 2) Очищаем текущие переводы и кладем туда фоллбэк
        translations.clear();
        translations.putAll(defaultTranslations);

        // 3) Если текущий язык не английский, грузим его поверх
        if (!FALLBACK_LANG.equalsIgnoreCase(currentLang)) {
            loadLangFile(currentLang, translations);
        }

        isLoaded = true;
        lastLoadedLangCode = currentLang;
        System.out.println("Fuctorize/Lang: Language updated to " + currentLang);
    }

    // --- НОВЫЙ МЕТОД: Проверка и обновление ---
    // Этот метод нужно вызывать в initGui главного меню
    public static void update() {
        String currentMcLang = getMinecraftLanguage();
        // Если язык в майнкрафте изменился с момента последней загрузки - перезагружаем
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