 
package ru.fuctorial.fuctorize.module.impl;

import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.clickgui.ClickGuiScreen;
import ru.fuctorial.fuctorize.client.gui.sniffer.GuiPacketSpammer;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.manager.PacketPersistence;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.ModeSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.ChatUtils;
import ru.fuctorial.fuctorize.utils.HexUtils;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PacketSpammer extends Module {

    public ModeSetting mode;
    public SliderSetting delay;
    public SliderSetting threads;
    public BooleanSetting ignoreDeath;
    public BooleanSetting loop;
    public BooleanSetting antiGui;
    public BooleanSetting debug;

    public String customChannel = "CONTROL";
    public String customHex = "";

    public final Map<String, List<String>> bannedCombinations = new HashMap<>();
     
    public final Map<String, List<String>> allowedCombinations = new HashMap<>();

    public PacketPersistence.SavedPacketData selectedFavorite = null;

    private volatile boolean isSpamming = false;
    private final List<Thread> spamThreads = new ArrayList<>();
    private final AtomicLong globalCounter = new AtomicLong(0);

    public PacketSpammer(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("packetspammer", Lang.get("module.packetspammer.name"), Category.EXPLOIT, ActivationType.SINGLE);

        mode = new ModeSetting(Lang.get("module.packetspammer.setting.mode"), "Favorites", "Favorites", "Raw Hex", "Bruteforce");
        delay = new SliderSetting(Lang.get("module.packetspammer.setting.delay"), 100.0, 0.0, 1000.0, 10.0);
        threads = new SliderSetting("Threads", 1.0, 1.0, 10.0, 1.0);

        ignoreDeath = new BooleanSetting("Ignore Death", false);
        loop = new BooleanSetting("Loop Mode", false);
        antiGui = new BooleanSetting("Anti-GUI", true);
        debug = new BooleanSetting("Show Progress", false);

        addSetting(mode);
        addSetting(delay);
        addSetting(threads);
        addSetting(ignoreDeath);
        addSetting(loop);
        addSetting(antiGui);
        addSetting(debug);

        addSetting(new BindSetting(Lang.get("module.packetspammer.setting.open_gui"), Keyboard.KEY_NONE));

        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.packetspammer.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            mc.displayGuiScreen(new GuiPacketSpammer(this));
        }
        toggle();
    }

    @Override
    public void onDisable() {
        stopSpam();
    }

    @Override
    public void onDisconnect() {
        stopSpam();
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (isSpamming && antiGui.enabled) {
            if (event.getPacket() instanceof S2DPacketOpenWindow) {
                event.setCanceled(true);
            }
        }
    }

    @Override
    public void onUpdate() {
        if (!isSpamming) return;

        if (ignoreDeath.enabled) {
            if (mc.thePlayer != null && (mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0)) {
                NetUtils.sendPacket(new C16PacketClientStatus(C16PacketClientStatus.EnumState.PERFORM_RESPAWN));
                if (mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver) {
                    mc.displayGuiScreen(null);
                    mc.setIngameFocus();
                }
            }
        }

        if (antiGui.enabled && mc.currentScreen != null) {
            if (!(mc.currentScreen instanceof GuiPacketSpammer) &&
                    !(mc.currentScreen instanceof ClickGuiScreen) &&
                    !(mc.currentScreen instanceof GuiChat) &&
                    !(mc.currentScreen instanceof GuiIngameMenu)) {

                mc.displayGuiScreen(null);
                mc.setIngameFocus();
            }
        }
    }

    public boolean isSpamming() {
        return isSpamming;
    }

    public void startSpam() {
        if (isSpamming) return;

        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            client.notificationManager.show(new Notification("Spammer", "Not connected!", Notification.NotificationType.ERROR, 2000L));
            return;
        }

        if (mode.isMode("Favorites") && selectedFavorite == null) {
            client.notificationManager.show(new Notification("Packet Spammer", "No favorite packet selected!", Notification.NotificationType.ERROR, 3000L));
            return;
        }

        if ((mode.isMode("Raw Hex") || mode.isMode("Bruteforce")) && (customChannel.isEmpty() || customHex.isEmpty())) {
            client.notificationManager.show(new Notification("Packet Spammer", "Channel or Hex data empty!", Notification.NotificationType.ERROR, 3000L));
            return;
        }

        BruteforceTask task = null;

        if (mode.isMode("Bruteforce")) {
            if (!customHex.contains("{") || !customHex.contains("}")) {
                client.notificationManager.show(new Notification("Bruteforce Error", "No brackets {} found in Hex!", Notification.NotificationType.ERROR, 3000L));
                return;
            }

            try {
                 
                task = new BruteforceTask(customHex, bannedCombinations, allowedCombinations);
            } catch (Exception e) {
                client.notificationManager.show(new Notification("Bruteforce Init Error", e.getMessage(), Notification.NotificationType.ERROR, 4000L));
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Spammer Error] " + e.getMessage()));
                }
                return;
            }
        }

        isSpamming = true;
        spamThreads.clear();
        globalCounter.set(0);

        int threadCount = (int) threads.value;
        final BruteforceTask finalTask = task;

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> runSpamLoop(finalTask));
            t.setName("Fuctorize-Spammer-Worker-" + i);
            t.setDaemon(true);
            t.start();
            spamThreads.add(t);
        }

        client.notificationManager.show(new Notification("Packet Spammer", "Started (" + threadCount + " threads)", Notification.NotificationType.SUCCESS, 2000L));
    }

    public void stopSpam() {
        if (!isSpamming) return;
        isSpamming = false;

        for (Thread t : spamThreads) {
            if (t != null && t.isAlive()) {
                t.interrupt();
            }
        }
        spamThreads.clear();

        client.scheduleTask(() ->
                client.notificationManager.show(new Notification("Packet Spammer", "Stopped.", Notification.NotificationType.INFO, 2000L))
        );
    }

    private void runSpamLoop(BruteforceTask bruteTask) {
        byte[] cachedRawData = null;
        try {
            if (mode.isMode("Raw Hex")) {
                cachedRawData = HexUtils.hexToBytes(cleanHex(customHex));
            }
        } catch (Exception e) {
            isSpamming = false;
            client.scheduleTask(() -> client.notificationManager.show(new Notification("Hex Error", "Invalid Raw Hex format!", Notification.NotificationType.ERROR, 3000L)));
            return;
        }

        while (isSpamming && !Thread.currentThread().isInterrupted()) {
            try {
                if (!ignoreDeath.enabled && mc.thePlayer == null) {
                    stopSpam();
                    break;
                }
                if (mc.getNetHandler() == null || mc.getNetHandler().getNetworkManager() == null || !mc.getNetHandler().getNetworkManager().isChannelOpen()) {
                    stopSpam();
                    break;
                }

                Packet packetToSend = null;

                if (mode.isMode("Favorites")) {
                    packetToSend = PacketPersistence.reconstruct(selectedFavorite);
                } else if (mode.isMode("Raw Hex")) {
                    packetToSend = new C17PacketCustomPayload(customChannel, Unpooled.wrappedBuffer(cachedRawData));
                } else if (mode.isMode("Bruteforce") && bruteTask != null) {
                    byte[] payload = bruteTask.getNextPayload();

                    if (payload != null && debug.enabled) {
                        long count = globalCounter.incrementAndGet();
                        String debugStr = bruteTask.getLastDebugString();
                        client.scheduleTask(() -> ChatUtils.printMessage(
                                EnumChatFormatting.GRAY + "[#" + count + "] " + EnumChatFormatting.RESET + debugStr
                        ));
                    }

                    if (payload == null) {
                        if (loop.enabled) {
                            bruteTask.reset();
                            payload = bruteTask.getNextPayload();
                        } else {
                            stopSpam();
                            client.scheduleTask(() -> client.notificationManager.show(new Notification("Bruteforce", "Cycle Completed!", Notification.NotificationType.SUCCESS, 3000L)));
                            break;
                        }
                    }
                    if (payload != null) {
                        packetToSend = new C17PacketCustomPayload(customChannel, Unpooled.wrappedBuffer(payload));
                    }
                }

                if (packetToSend != null) {
                    NetUtils.sendPacket(packetToSend);
                }

                long sleepTime = (long) delay.value;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                } else {
                    Thread.sleep(1);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
                stopSpam();
                break;
            }
        }
    }

    public static String cleanHex(String raw) {
        return raw.replaceAll("[^0-9A-Fa-f]", "");
    }

     
     
     

    private static class BruteforceTask {
         
        private static class Range {
            final long min;
            final long max;
            Range(long min, long max) { this.min = min; this.max = max; }
        }

        private static class Segment {
            boolean isDynamic;
            byte[] staticData;
            int dynamicByteCount;

            long currentValue;
            Set<String> bannedValues;

             
            List<Range> allowedRanges;
            int currentRangeIndex = 0;

            public Segment(byte[] staticData) {
                this.isDynamic = false;
                this.staticData = staticData;
            }

            public Segment(int byteCount, List<String> bans, List<String> allows) {
                this.isDynamic = true;
                this.dynamicByteCount = byteCount;
                this.currentValue = 0;

                long absoluteMax;
                if (byteCount > 8) throw new IllegalArgumentException("Dynamic part too large (>8 bytes)");
                if (byteCount == 8) absoluteMax = Long.MAX_VALUE;  
                else absoluteMax = (1L << (byteCount * 8)) - 1;

                 
                if (bans != null && !bans.isEmpty()) {
                    this.bannedValues = new HashSet<>();
                    for (String ban : bans) {
                        String clean = cleanHex(ban);
                        while (clean.length() < byteCount * 2) clean = "0" + clean;
                        if (clean.length() > byteCount * 2) clean = clean.substring(clean.length() - (byteCount * 2));
                        this.bannedValues.add(clean.toUpperCase());
                    }
                }

                 
                this.allowedRanges = new ArrayList<>();
                if (allows != null && !allows.isEmpty()) {
                    for (String allow : allows) {
                        String[] parts = allow.split("-");
                        try {
                            long start = 0;
                            long end = 0;
                            if (parts.length == 2) {
                                start = Long.parseUnsignedLong(cleanHex(parts[0]), 16);
                                end = Long.parseUnsignedLong(cleanHex(parts[1]), 16);
                            } else if (parts.length == 1) {
                                start = Long.parseUnsignedLong(cleanHex(parts[0]), 16);
                                end = start;
                            }
                             
                            if (start > absoluteMax) continue;
                            if (end > absoluteMax) end = absoluteMax;
                            if (start <= end) {
                                this.allowedRanges.add(new Range(start, end));
                            }
                        } catch (Exception ignored) {}
                    }
                     
                    this.allowedRanges.sort(Comparator.comparingLong(r -> r.min));
                } else {
                     
                    this.allowedRanges.add(new Range(0, absoluteMax));
                }

                 
                resetToStart();
            }

            void resetToStart() {
                this.currentRangeIndex = 0;
                if (!allowedRanges.isEmpty()) {
                    this.currentValue = allowedRanges.get(0).min;
                } else {
                    this.currentValue = 0;
                }
            }

            boolean isCurrentBanned() {
                if (bannedValues == null || bannedValues.isEmpty()) return false;
                String fmt = String.format("%0" + (dynamicByteCount * 2) + "X", currentValue);
                return bannedValues.contains(fmt);
            }
        }

        private final List<Segment> segments = new ArrayList<>();
        private boolean finished = false;
        private String lastDebugString = "";

        public BruteforceTask(String templateHex, Map<String, List<String>> bannedMap, Map<String, List<String>> allowedMap) {
            parseTemplate(templateHex, bannedMap, allowedMap);
        }

        private void parseTemplate(String template, Map<String, List<String>> bannedMap, Map<String, List<String>> allowedMap) {
            String raw = template;
            Pattern pattern = Pattern.compile("\\{([0-9A-Fa-f\\s]*)\\}");
            Matcher matcher = pattern.matcher(raw);

            int lastEnd = 0;

            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    String staticPart = raw.substring(lastEnd, matcher.start());
                    segments.add(new Segment(HexUtils.hexToBytes(cleanHex(staticPart))));
                }

                String content = matcher.group(1);
                String fullPlaceholder = matcher.group(0);

                String cleanContent = cleanHex(content);
                if (cleanContent.length() % 2 != 0) cleanContent = "0" + cleanContent;
                int byteCount = cleanContent.length() / 2;
                if (byteCount == 0) byteCount = 1;

                 
                Segment dynSeg = new Segment(byteCount,
                        bannedMap != null ? bannedMap.get(fullPlaceholder) : null,
                        allowedMap != null ? allowedMap.get(fullPlaceholder) : null
                );

                 
                 
                 

                segments.add(dynSeg);
                lastEnd = matcher.end();
            }

            if (lastEnd < raw.length()) {
                String staticPart = raw.substring(lastEnd);
                segments.add(new Segment(HexUtils.hexToBytes(cleanHex(staticPart))));
            }

            validateStartValues();
        }

        private void validateStartValues() {
            for (Segment s : segments) {
                if (s.isDynamic) {
                     
                     
                    while (s.isCurrentBanned()) {
                        if (advanceValue(s)) {
                             
                             
                            break;
                        }
                    }
                }
            }
        }

        public synchronized void reset() {
            for (Segment s : segments) {
                if (s.isDynamic) s.resetToStart();
            }
            validateStartValues();
            finished = false;
        }

        public synchronized String getLastDebugString() {
            return lastDebugString;
        }

        public synchronized byte[] getNextPayload() {
            if (finished) return null;

            StringBuilder debugSb = new StringBuilder();
            int totalSize = 0;

            for (Segment s : segments) {
                if (s.isDynamic) {
                    totalSize += s.dynamicByteCount;
                    String hexVal = String.format("%0" + (s.dynamicByteCount * 2) + "X", s.currentValue);
                    debugSb.append(EnumChatFormatting.RED).append(hexVal).append(EnumChatFormatting.GRAY);
                } else {
                    totalSize += s.staticData.length;
                    debugSb.append(HexUtils.bytesToHex(s.staticData).replace(" ", ""));
                }
            }
            this.lastDebugString = debugSb.toString();

            byte[] payload = new byte[totalSize];
            int offset = 0;
            for (Segment s : segments) {
                if (s.isDynamic) {
                    long val = s.currentValue;
                    for (int i = s.dynamicByteCount - 1; i >= 0; i--) {
                        payload[offset + i] = (byte) (val & 0xFF);
                        val >>= 8;
                    }
                    offset += s.dynamicByteCount;
                } else {
                    System.arraycopy(s.staticData, 0, payload, offset, s.staticData.length);
                    offset += s.staticData.length;
                }
            }

            increment();
            return payload;
        }

         
        private boolean advanceValue(Segment s) {
            s.currentValue++;
            Range currentRange = s.allowedRanges.get(s.currentRangeIndex);

             
            if (s.currentValue > currentRange.max) {
                s.currentRangeIndex++;
                 
                if (s.currentRangeIndex >= s.allowedRanges.size()) {
                    s.resetToStart();  
                    return true;  
                } else {
                     
                    s.currentValue = s.allowedRanges.get(s.currentRangeIndex).min;
                }
            }
            return false;
        }

        private void increment() {
            int i = segments.size() - 1;
            while (i >= 0) {
                Segment s = segments.get(i);
                if (s.isDynamic) {
                    boolean overflow;
                    do {
                        overflow = advanceValue(s);
                         
                         
                         
                        if (overflow) break;
                    } while (s.isCurrentBanned());

                    if (!overflow) {
                         
                        return;
                    }
                     
                     
                     
                    while (s.isCurrentBanned()) {
                        if (advanceValue(s)) break;  
                    }
                }
                i--;
            }
             
            finished = true;
        }
    }
}