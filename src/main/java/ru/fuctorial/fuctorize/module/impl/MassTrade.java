// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\MassTrade.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import net.minecraft.client.gui.GuiPlayerInfo;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MassTrade extends Module {

    private SliderSetting delay;
    private SliderSetting cycleTimeout;

    private volatile Thread workerThread;

    private static boolean reflectionAttempted = false;
    private static boolean reflectionSuccessful = false;
    private static Constructor<?> messageConstructor;
    private static Object tradeChannel;
    private static Method sendToServerMethod;

    public MassTrade(FuctorizeClient client) {
        super(client);
        initializeReflection();
    }

    @Override
    public void init() {
        setMetadata("masstrade", Lang.get("module.masstrade.name"), Category.EXPLOIT);

        delay = new SliderSetting(Lang.get("module.masstrade.setting.packet_delay"), 50.0, 1.0, 1000.0, 1.0);
        cycleTimeout = new SliderSetting(Lang.get("module.masstrade.setting.cycle_pause"), 5.0, 1.0, 60.0, 1.0);

        addSetting(delay);
        addSetting(cycleTimeout);
        addSetting(new BindSetting(Lang.get("module.masstrade.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.masstrade.desc");
    }

    @Override
    public void onEnable() {
        if (workerThread != null && workerThread.isAlive()) {
            return;
        }

        workerThread = new Thread(this::runTradeLoop);
        workerThread.setName("Fuctorize-MassTrade-Thread");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void onDisable() {
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    private void runTradeLoop() {
        if (!reflectionSuccessful) {
            client.scheduleTask(() -> {
                client.notificationManager.show(new Notification(Lang.get("notification.masstrade.title.error"), Lang.get("notification.masstrade.message.mod_not_found"), Notification.NotificationType.ERROR, 3000L));
                this.toggle();
            });
            return;
        }

        client.scheduleTask(() -> client.notificationManager.show(new Notification(
                Lang.get("notification.masstrade.title.info"), Lang.get("notification.masstrade.message.starting"),
                Notification.NotificationType.INFO, 3000L
        )));

        while (this.isEnabled() && !Thread.currentThread().isInterrupted()) {
            try {
                List<String> playerNames = getOnlinePlayerNicks();
                if (playerNames.isEmpty()) {
                    Thread.sleep(2000);
                    continue;
                }

                for (String nick : playerNames) {
                    if (!this.isEnabled() || Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }

                    Object requestPacket = messageConstructor.newInstance(nick);
                    sendToServerMethod.invoke(tradeChannel, requestPacket);
                    Thread.sleep((long) delay.value);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
                try { Thread.sleep(5000); } catch (InterruptedException ie) { break; }
            }
        }
        client.scheduleTask(() -> client.notificationManager.show(new Notification(Lang.get("notification.masstrade.title.info"), Lang.get("notification.masstrade.message.stopped"), Notification.NotificationType.INFO, 2000L)));
    }

    private List<String> getOnlinePlayerNicks() {
        List<String> nicks = new ArrayList<>();
        if (mc.getNetHandler() == null || mc.getNetHandler().playerInfoList == null) return nicks;
        for (Object playerInfoObject : mc.getNetHandler().playerInfoList) {
            if (playerInfoObject instanceof GuiPlayerInfo) {
                GuiPlayerInfo playerInfo = (GuiPlayerInfo) playerInfoObject;
                String nick = playerInfo.name;
                if (nick != null && !nick.equals(mc.thePlayer.getCommandSenderName())) {
                    nicks.add(nick);
                }
            }
        }
        return nicks;
    }

    private void initializeReflection() {
        if (reflectionAttempted) return;
        reflectionAttempted = true;
        try {
            Class<?> tradeModClass = Class.forName("com.prototype.trade.Trade");
            Method getChannelMethod = tradeModClass.getMethod("getChannel");
            tradeChannel = getChannelMethod.invoke(null);
            if (tradeChannel == null) throw new RuntimeException("Trade.getChannel() вернул null.");

            Class<?> messageTradeRequestClass = Class.forName("com.prototype.trade.common.network.message.MessageTradeRequest");
            messageConstructor = messageTradeRequestClass.getConstructor(String.class);

            Class<?> iMessageClass = Class.forName("cpw.mods.fml.common.network.simpleimpl.IMessage");
            sendToServerMethod = tradeChannel.getClass().getMethod("sendToServer", iMessageClass);

            reflectionSuccessful = true;
            System.out.println("Fuctorize/MassTrade: Успешно подключился к моду 'Trade' через рефлексию.");
        } catch (Exception e) {
            System.err.println("Fuctorize/MassTrade: Не удалось подключиться к моду 'Trade'.");
            reflectionSuccessful = false;
        }
    }
}