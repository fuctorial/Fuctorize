 
package ru.fuctorial.fuctorize;

import cpw.mods.fml.common.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.launchwrapper.Launch;
import ru.fuctorial.fuctorize.manager.*;
import ru.fuctorial.fuctorize.client.gui.clickgui.ClickGuiScreen;
import ru.fuctorial.fuctorize.client.hud.HUD;
import ru.fuctorial.fuctorize.client.render.NameTagRenderer;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.handlers.EventHandler;
import ru.fuctorial.fuctorize.handlers.PacketHandler;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.impl.PacketSniffer;
import net.minecraft.network.Packet;
import net.minecraftforge.common.MinecraftForge;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.ModContainerHelper;
import ru.fuctorial.fuctorize.utils.UnloadCallback;
import ru.fuctorial.fuctorize.utils.http.HttpSniffer;
import ru.fuctorial.fuctorize.utils.pathfinding.BotNavigator;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FuctorizeClient {

    public static FuctorizeClient INSTANCE;

    public final ModuleManager moduleManager;
    public final ConfigManager configManager;
    private final Queue<Runnable> scheduledTasks = new ConcurrentLinkedQueue<>();
    public final FontManager fontManager;
    public final EventHandler eventHandler;
    public final RenderManager renderManager;
    public final NameTagRenderer nameTagRenderer;
    public final HUD hud;
    public final NotificationManager notificationManager;
    public final ClickGuiScreen clickGui;
    public final AccountManager accountManager;
    public final PathfindingManager pathfindingManager;
    public BotNavigator botNavigator;

    public FuctorizeClient() {
        INSTANCE = this;
        Lang.load();
        try { HttpSniffer.installOnce(); } catch (Throwable ignored) {}
        fontManager = new FontManager();
        renderManager = new RenderManager();
        notificationManager = new NotificationManager();
        nameTagRenderer = new NameTagRenderer();
        hud = new HUD();
        moduleManager = new ModuleManager(this);
        configManager = new ConfigManager(this);
        accountManager = new AccountManager();
        pathfindingManager = new PathfindingManager(this);
        clickGui = new ClickGuiScreen(this);
        eventHandler = new EventHandler(this);

         
        botNavigator = new BotNavigator();
         
    }

    public void start() {
        System.out.println(">>> FUCTORIZE CLIENT: STARTING SYSTEMS...");

        configManager.loadConfig();
        ScreenBlacklistManager.load();
        FavoriteScreenManager.load();

         
        try {
            pathfindingManager.initialize();
            System.out.println(">>> FUCTORIZE: PathfindingManager initialized.");
        } catch (Exception e) {
            System.err.println("### FUCTORIZE: Failed to initialize PathfindingManager!");
            e.printStackTrace();
        }
         

        LoadController modController = null;
        Field activeContainerField = null;
        ModContainer originalActiveContainer = null;
        DummyModContainer dummyContainer = createDummyModContainer();

        try {
            Loader loader = Loader.instance();
            modController = ReflectionHelper.getPrivateValue(Loader.class, loader, "modController");
            activeContainerField = ReflectionHelper.findField(LoadController.class, "activeContainer");
            activeContainerField.setAccessible(true);
            originalActiveContainer = (ModContainer) activeContainerField.get(modController);

            System.out.println(">>> FUCTORIZE PATCHER: Setting active container field to DummyModContainer for Fuctorize");
            activeContainerField.set(modController, dummyContainer);
            ModContainerHelper.init(modController, activeContainerField, dummyContainer);

            FMLCommonHandler.instance().bus().register(eventHandler);
            MinecraftForge.EVENT_BUS.register(eventHandler);

            FMLCommonHandler.instance().bus().register(fontManager);
            FMLCommonHandler.instance().bus().register(notificationManager);
            MinecraftForge.EVENT_BUS.register(renderManager);

            System.out.println(">>> FUCTORIZE PATCHER: Event registration successful.");

        } catch (Exception e) {
            System.err.println("### FUCTORIZE PATCHER: CRITICAL ERROR during event registration patch!");
            e.printStackTrace();
        } finally {
            if (modController != null && activeContainerField != null) {
                try {
                    activeContainerField.set(modController, originalActiveContainer);
                    System.out.println(">>> FUCTORIZE PATCHER: Active container field has been reset.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        renderManager.register(hud);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (configManager != null) configManager.saveConfig();
        }));
    }

     
    private DummyModContainer createDummyModContainer() {
        ModMetadata meta = new ModMetadata();
        meta.modId = Fuctorize.modId;
        meta.name = Fuctorize.modId;  
        meta.version = Fuctorize.modVersion;
        return new DummyModContainer(meta);
    }
     

    @SuppressWarnings("unchecked")
    private void purgeClassLoaderCache() {
        try {
             
            Field cachedClassesField = Class.forName("net.minecraft.launchwrapper.LaunchClassLoader").getDeclaredField("cachedClasses");
            cachedClassesField.setAccessible(true);
            Map<String, Class<?>> cachedClasses = (Map<String, Class<?>>) cachedClassesField.get(Launch.classLoader);

             
            int countBefore = cachedClasses.size();
            cachedClasses.keySet().removeIf(className -> className.startsWith("ru.fuctorial.fuctorize."));
            int countAfter = cachedClasses.size();

            System.out.println(">>> FUCTORIZE UNLOAD: Purged " + (countBefore - countAfter) + " classes from LaunchClassLoader cache.");

        } catch (Exception e) {
            System.err.println(">>> FUCTORIZE UNLOAD: Failed to purge LaunchClassLoader cache.");
            e.printStackTrace();
        }
    }

    public void stop() {
        System.out.println(">>> FUCTORIZE CLIENT: SHUTDOWN SEQUENCE INITIATED (SYNCHRONOUS)...");

         
        if (botNavigator != null) {
            try {
                botNavigator.stop();
                System.out.println(">>> FUCTORIZE: BotNavigator stopped.");
            } catch (Exception e) {
                System.err.println("### FUCTORIZE: Error stopping BotNavigator!");
                e.printStackTrace();
            }
        }

         
        try {
            if (pathfindingManager != null) {
                pathfindingManager.shutdown();
                System.out.println(">>> FUCTORIZE: PathfindingManager shutdown.");
            }
        } catch (Exception e) {
            System.err.println("### FUCTORIZE: Error shutting down PathfindingManager!");
            e.printStackTrace();
        }
         

        if (configManager != null) configManager.saveConfig();
        if (ModHiderConfig.saveOnExit) ModHiderConfig.save();

        try {
            if (eventHandler != null) FMLCommonHandler.instance().bus().unregister(eventHandler);
            if (eventHandler != null) MinecraftForge.EVENT_BUS.unregister(eventHandler);
            if (fontManager != null) FMLCommonHandler.instance().bus().unregister(fontManager);
            if (notificationManager != null) FMLCommonHandler.instance().bus().unregister(notificationManager);
            if (renderManager != null) MinecraftForge.EVENT_BUS.unregister(renderManager);
            if (Fuctorize.instance != null) FMLCommonHandler.instance().bus().unregister(Fuctorize.instance);
        } catch (Exception e) {
            System.err.println("Error during event unregistering: " + e.getMessage());
        }

        if(moduleManager != null) {
            for (Module module : moduleManager.getModules()) {
                if (module.isEnabled()) {
                    module.setState(false);
                }
            }
        }

        PacketHandler.uninject();

        System.out.println(">>> FUCTORIZE CLIENT: Executing final cleanup tasks...");
        UnloadCallback.reset();

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen instanceof ru.fuctorial.fuctorize.client.gui.menu.GuiCustomMainMenu) {
            this.scheduleTask(() -> minecraft.displayGuiScreen(new GuiMainMenu()));
        }

        if (fontManager != null) {
            fontManager.destroy();
        }

        if (PacketSniffer.logManager != null) {
            PacketSniffer.stopLogSession();
        }

        purgeClassLoaderCache();

        INSTANCE = null;

        System.out.println(">>> FUCTORIZE UNLOAD: Java cleanup complete. Signaling C++.");
        UnloadCallback.signalCleanupComplete();
    }


    public void registerSync(Object object) {
        Fuctorize.FUC_SYNC_EVENT_BUS.register(object);
    }

    public void unregisterSync(Object object) {
        try { Fuctorize.FUC_SYNC_EVENT_BUS.unregister(object); } catch (Throwable ignored) {}
    }

    public boolean dispatchPacketSendPre(Packet packet) {
        if (moduleManager == null) return false;
        for (Module m : moduleManager.getModules()) {
            if (m.isEnabled()) {
                if (m.onPacketSendPre(packet)) {
                    return true;
                }
            }
        }
        return false;
    }



    public void dispatchPacketSend(PacketEvent.Send event) {
        PacketSniffer.onPacketSendStatic(event);
        if (moduleManager == null) return;
        for (Module m : moduleManager.getModules()) {
            if (m.isEnabled()) {
                m.onPacketSend(event);
            }
        }
    }

    public void dispatchPacketReceive(PacketEvent.Receive event) {
        PacketSniffer.onPacketReceiveStatic(event);
        if (moduleManager == null) return;
        for (Module m : moduleManager.getModules()) {
            if (m.isEnabled()) {
                m.onPacketReceive(event);
            }
        }
    }

    public boolean dispatchPrePacketReceive(ru.fuctorial.fuctorize.event.PrePacketReceiveEvent ev) {
        if (this.moduleManager == null) return false;
        for (Module m : this.moduleManager.getModules()) {
            if (m != null && m.isEnabled()) {
                try {
                    m.onPrePacketReceive(ev);
                    if (ev.isCanceled()) return true;
                } catch (Throwable t) {
                    System.err.println("Fuctorize: module " + (m != null ? m.getClass().getName() : "null")
                            + " threw while handling pre-packet receive:");
                    t.printStackTrace();
                }
            }
        }
        return false;
    }

    public void scheduleTask(Runnable task) {
        this.scheduledTasks.add(task);
    }

    public void runScheduledTasks() {
        while (!scheduledTasks.isEmpty()) {
            Runnable task = scheduledTasks.poll();
            if (task != null) {
                try {
                    task.run();
                } catch (Exception e) {
                    System.err.println("Fuctorize: Error executing scheduled task!");
                    e.printStackTrace();
                }
            }
        }
    }
}
