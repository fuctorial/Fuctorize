// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\RaceTest.java
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.sniffer.GuiRaceEditor;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.manager.PacketPersistence;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;
import net.minecraft.network.Packet;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RaceTest extends Module {

    public SliderSetting threads;
    public SliderSetting delay; // Задержка между "забегами" (если включен повтор)
    public BooleanSetting loop; // Повторять ли тест
    public BooleanSetting debug;

    private final List<PacketPersistence.SavedPacketData> packetSequence = new ArrayList<>();
    private volatile boolean isRunning = false;
    private ExecutorService executor;

    public RaceTest(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("racetest", "Race Tester", Category.EXPLOIT, ActivationType.SINGLE);

        threads = new SliderSetting("Threads", 2.0, 1.0, 10.0, 1.0);
        delay = new SliderSetting("Loop Delay (ms)", 500.0, 50.0, 5000.0, 50.0);
        loop = new BooleanSetting("Loop Mode", false);
        debug = new BooleanSetting("Debug Chat", true);

        addSetting(threads);
        addSetting(delay);
        addSetting(loop);
        addSetting(debug);
        addSetting(new BindSetting("Open GUI", Keyboard.KEY_NONE));

        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return "Sends packets simultaneously on multiple threads to test race conditions.";
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            mc.displayGuiScreen(new GuiRaceEditor(this));
        }
        toggle(); // Модуль выключается, так как работает через GUI
    }

    // Вызывается из GUI или по бинду старта
    public void startRace() {
        if (packetSequence.isEmpty()) {
            client.notificationManager.show(new Notification("RaceTest", "Sequence is empty!", Notification.NotificationType.ERROR, 2000L));
            return;
        }
        if (isRunning) {
            stopRace();
            return;
        }

        isRunning = true;
        int threadCount = (int) threads.value;
        executor = Executors.newFixedThreadPool(threadCount);

        client.notificationManager.show(new Notification("RaceTest", "Started race with " + threadCount + " threads.", Notification.NotificationType.SUCCESS, 2000L));

        new Thread(() -> {
            while (isRunning && (mc.thePlayer != null)) {
                try {
                    executeRaceBatch(threadCount);

                    if (!loop.enabled) {
                        break;
                    }
                    Thread.sleep((long) delay.value);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
            stopRace();
        }, "RaceTest-Loop").start();
    }

    private void executeRaceBatch(int threadCount) {
        // 1. Подготавливаем пакеты (преконструируем их в главном потоке или здесь, 
        // но важно, чтобы они были готовы к отправке)
        List<Packet> packetsToSend = new ArrayList<>();
        for (PacketPersistence.SavedPacketData data : packetSequence) {
            Packet p = PacketPersistence.reconstruct(data);
            if (p != null) packetsToSend.add(p);
        }

        if (packetsToSend.isEmpty()) return;

        // 2. Создаем барьер. +1 для управляющего потока, если нужно, 
        // но здесь мы просто запускаем N потоков, которые ждут друг друга.
        CyclicBarrier barrier = new CyclicBarrier(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Ждем, пока все потоки будут готовы
                    barrier.await();

                    // --- КРИТИЧЕСКАЯ СЕКЦИЯ: ОТПРАВКА ---
                    for (Packet p : packetsToSend) {
                        // Клонируем пакет, если это возможно, так как один объект пакета 
                        // в нескольких потоках может вызвать ConcurrentModificationException при записи в Netty
                        // Но NetUtils.sendPacket просто кладет в очередь NetHandler'а, который синхронизирован.
                        // Для настоящего Race Condition лучше бы писать напрямую в канал Netty, 
                        // но addToSendQueue достаточно для логических рейсов.
                        NetUtils.sendPacket(p);
                    }
                    // -------------------------------------

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        if (debug.enabled && mc.thePlayer != null) {
            // ru.fuctorial.fuctorize.utils.ChatUtils.printMessage("§7[Race] Batch sent.");
        }
    }

    public void stopRace() {
        isRunning = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        client.scheduleTask(() -> {
            client.notificationManager.show(new Notification("RaceTest", "Race stopped.", Notification.NotificationType.INFO, 2000L));
        });
    }

    public boolean isRunning() {
        return isRunning;
    }

    public List<PacketPersistence.SavedPacketData> getSequence() {
        return packetSequence;
    }

    public void addToSequence(PacketPersistence.SavedPacketData data) {
        packetSequence.add(data);
    }

    public void clearSequence() {
        packetSequence.clear();
    }

    public void removeFromSequence(int index) {
        if (index >= 0 && index < packetSequence.size()) {
            packetSequence.remove(index);
        }
    }
}