// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\Fuctorize.java
package ru.fuctorial.fuctorize;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventBus;
import ru.fuctorial.fuctorize.manager.ModHiderConfig;
import ru.fuctorial.fuctorize.utils.RuntimePatcher;

@Mod(modid = "fuctorize", name = "Fuctorize", version = "1.0-reinjectable")
public class Fuctorize {

    @Instance("fuctorize")
    public static Fuctorize instance;
    public static final EventBus FUC_EVENT_BUS = new EventBus();
    public static final EventBus FUC_SYNC_EVENT_BUS = new EventBus();

    public static String modId;
    public static String modVersion;

    private static FuctorizeClient fuctorizeClient;
    private static boolean systemsInitialized = false;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Load the desired identity first.
        ModHiderConfig.load();
        // Then, patch FML's internal state. This must happen early.
        RuntimePatcher.patch(modId, modVersion);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        start();
    }

    public static void start() {
        if (systemsInitialized) {
            System.out.println(">>> FUCTORIZE: Already initialized. Ignoring start call.");
            return;
        }

        // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
        // Вызываем загрузку конфига вручную, так как preInit пропускается при инжекте.
        // Это заполнит статические поля modId и modVersion.
        ModHiderConfig.load();
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

        fuctorizeClient = new FuctorizeClient();
        fuctorizeClient.start();

        systemsInitialized = true;
    }

    public static void stop() {
        if (!systemsInitialized || fuctorizeClient == null) {
            System.out.println(">>> FUCTORIZE: Systems not running, nothing to stop.");
            return;
        }

        fuctorizeClient.stop();
        fuctorizeClient = null;

        // --- ИСПРАВЛЕНИЕ: Очищаем статическое состояние ---
        systemsInitialized = false;
        ru.fuctorial.fuctorize.utils.Lang.reset();
        // Если JavaInjector.shutdown вызывается, мы должны очистить статические ссылки
        // на загрузчики и другие ресурсы, которые могут помешать повторной загрузке.
        // Это особенно важно для статических полей в модулях.
        // Здесь можно добавить вызов метода очистки для каждого модуля, если необходимо.
        // Пример (добавьте аналогичные методы в ваши модули, если они хранят статическое состояние):
        // TheGunsDupe.resetStaticState();
        // PacketSniffer.resetStaticState();
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---
    }
}