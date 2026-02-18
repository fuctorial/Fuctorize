package ru.fuctorial.fuctorize.handlers;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import ru.fuctorial.fuctorize.Fuctorize;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.clickgui.ClickGuiScreen;
import ru.fuctorial.fuctorize.client.gui.menu.GuiCustomMainMenu;
import ru.fuctorial.fuctorize.client.gui.sniffer.GuiFavoriteScreens;
import ru.fuctorial.fuctorize.manager.RenderManager;
import ru.fuctorial.fuctorize.manager.ScreenBlacklistManager;
import ru.fuctorial.fuctorize.event.MouseEvent;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.impl.PacketSniffer;
import ru.fuctorial.fuctorize.module.impl.ScreenHistory;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.lwjgl.input.Keyboard;

import java.util.*;

public class EventHandler {

    private final Minecraft mc = FMLClientHandler.instance().getClient();
    private final FuctorizeClient client;

    public static final Deque<GuiScreen> screenHistory = new LinkedList<>();
    public static boolean isRestoringScreen = false;

    private final Map<Integer, Boolean> keyStates = new HashMap<>();
    private boolean modulesActivatedForSession = false;

    private static GuiCustomMainMenu cachedCustomMainMenu = null;

    private float initialFOV = -1f;

    public EventHandler(FuctorizeClient client) {
        this.client = client;
    }

    public float getInitialFOV() {
        if (initialFOV == -1f) {
            initialFOV = mc.gameSettings.fovSetting;
        }
        return initialFOV;
    }

    public static List<GuiScreen> getScreenHistory(boolean skipCurrent) {
        synchronized (screenHistory) {
            List<GuiScreen> result = new ArrayList<>();
            Iterator<GuiScreen> iterator = screenHistory.iterator();
            if (skipCurrent && iterator.hasNext()) {
                iterator.next();
            }
            iterator.forEachRemaining(result::add);
            return result;
        }
    }

    public static void pushScreenSnapshot(GuiScreen screen) {
        if (screen == null || ScreenBlacklistManager.isBlacklisted(screen) || screen instanceof GuiFavoriteScreens) {
            return;
        }

         
        if (screen == cachedCustomMainMenu) {
            return;
        }

        synchronized(screenHistory) {
             
            screenHistory.removeIf(existingScreen -> existingScreen == screen);

             
            screenHistory.addFirst(screen);

             
            while (screenHistory.size() > ScreenHistory.getHistoryLimit()) {
                screenHistory.removeLast();
            }
        }
    }

    public static void clearHistory() {
        synchronized (screenHistory) {
            screenHistory.clear();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGuiOpen(GuiOpenEvent event) {
         
         
        if (cachedCustomMainMenu == null) {
            cachedCustomMainMenu = new GuiCustomMainMenu();
        }

         
        if (event.gui == null && mc.theWorld == null) {
            event.gui = cachedCustomMainMenu;
        } else if (event.gui instanceof GuiMainMenu && !(event.gui instanceof GuiCustomMainMenu)) {
            event.gui = cachedCustomMainMenu;
        }

         
         
        if (!isRestoringScreen && mc.currentScreen != null && mc.currentScreen != event.gui) {
            pushScreenSnapshot(mc.currentScreen);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (client != null) client.runScheduledTasks();

         
        if (client != null && client.botNavigator != null) {
            try {
                client.botNavigator.tick();
            } catch (Exception e) {
                System.err.println("Fuctorize: Error in BotNavigator tick!");
                e.printStackTrace();
            }
        }
         

         
        if (mc.theWorld == null && mc.currentScreen instanceof GuiMainMenu && !(mc.currentScreen instanceof GuiCustomMainMenu)) {
            if (cachedCustomMainMenu == null) {
                cachedCustomMainMenu = new GuiCustomMainMenu();
            }
            mc.displayGuiScreen(cachedCustomMainMenu);
        }

        if (mc.thePlayer == null || client.moduleManager == null) {
            if (modulesActivatedForSession) modulesActivatedForSession = false;
            initialFOV = -1f;
            return;
        }

        if (initialFOV == -1f) initialFOV = mc.gameSettings.fovSetting;

        if (!modulesActivatedForSession) {
            for (Module module : client.moduleManager.getModules()) {
                if (module.wasEnabledOnStartup() && module.isEnabled() && !module.isConfigOnly()) {
                    try {
                        module.onEnable();
                    } catch (Throwable t) {
                        System.err.println("Fuctorize: onEnable exception for " + module.getClass().getName());
                        t.printStackTrace();
                    }
                    client.renderManager.register(module);
                }
            }
            modulesActivatedForSession = true;
        }

        handleKeybinds();

        for (Module module : client.moduleManager.getModules()) {
            if (module.isEnabled()) {
                module.onUpdate();
            }
        }
    }

    private void handleKeybinds() {
        if (mc.currentScreen != null) {
            if (PlayerUtils.isAnyTextFieldFocused()) {
                return;
            }
            if (mc.currentScreen instanceof ClickGuiScreen) {
                if (((ClickGuiScreen) mc.currentScreen).isAJTextFieldFocused()) {
                    return;
                }
            }
        }
        handleKey(Keyboard.KEY_RSHIFT, () -> {
            if (mc.currentScreen instanceof ru.fuctorial.fuctorize.client.gui.clickgui.ClickGuiScreen) {
                mc.displayGuiScreen(null);
            } else {
                mc.displayGuiScreen(client.clickGui);
            }
        });
        for (Module module : client.moduleManager.getModules()) {
            BindSetting bind = BindSetting.getBindSetting(module);
            if (bind != null && bind.keyCode != Keyboard.KEY_NONE) {
                handleKey(bind.keyCode, module::toggle);
            }
        }
    }

    private void handleKey(int keyCode, Runnable action) {
        boolean isPressed = Keyboard.isKeyDown(keyCode);
        boolean wasPressed = keyStates.getOrDefault(keyCode, false);
        if (isPressed && !wasPressed) {
            action.run();
        }
        keyStates.put(keyCode, isPressed);
    }

    @SubscribeEvent
    public void onConnect(ClientConnectedToServerEvent event) {
        cachedCustomMainMenu = null;

        if (event.manager != null) PacketHandler.inject(event.manager);
        PacketSniffer.setPaused(true);
        clearHistory();
        modulesActivatedForSession = false;
        initialFOV = -1f;
        if (client.moduleManager != null) {
            for (Module module : client.moduleManager.getModules()) {
                module.onConnect();
            }
        }
        RenderManager.allowRendering = true;
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectionFromServerEvent event) {
        if (cachedCustomMainMenu != null) {
            cachedCustomMainMenu.resetAnimation();
        }

        PacketHandler.uninject();
        PacketSniffer.setPaused(false);
        clearHistory();
        modulesActivatedForSession = false;
        if (client.moduleManager != null) {
            for (Module module : client.moduleManager.getModules()) {
                if (module.isEnabled()) module.onDisconnect();
            }
        }
        RenderManager.allowRendering = false;
    }

    @SubscribeEvent
    public void onAnyEvent(cpw.mods.fml.common.eventhandler.Event event) {
        if (event instanceof PacketEvent.Send) onPacketSend((PacketEvent.Send) event);
        else if (event instanceof PacketEvent.Receive) onPacketReceive((PacketEvent.Receive) event);
        else if (event instanceof MouseEvent.Click) onMouseInput((MouseEvent.Click) event);
    }

    private void onMouseInput(MouseEvent.Click event) {
        Fuctorize.FUC_EVENT_BUS.post(event);
    }

    private void onPacketSend(PacketEvent.Send event) {
        PacketSniffer.onPacketSendStatic(event);
        if (client.moduleManager == null) return;
        for (Module module : client.moduleManager.getModules()) {
            if (!module.isEnabled()) continue;
            try {
                module.onPacketSend(event);
            } catch (Throwable t) {
                System.err.println("Fuctorize: module " + module.getClass().getName() +
                        " threw while handling PacketEvent.Send:");
                t.printStackTrace();
            }
        }
    }

    private void onPacketReceive(PacketEvent.Receive event) {
        PacketSniffer.onPacketReceiveStatic(event);
        if (client.moduleManager == null) return;
        for (Module module : client.moduleManager.getModules()) {
            if (!module.isEnabled()) continue;
            try {
                module.onPacketReceive(event);
            } catch (Throwable t) {
                System.err.println("Fuctorize: module " + module.getClass().getName() +
                        " threw while handling PacketEvent.Receive:");
                t.printStackTrace();
            }
        }
    }
}
