 
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
         
        ModHiderConfig.load();
         
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

         
         
         
        ModHiderConfig.load();
         

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

         
        systemsInitialized = false;
        ru.fuctorial.fuctorize.utils.Lang.reset();
         
         
         
         
         
         
         
         
    }
}