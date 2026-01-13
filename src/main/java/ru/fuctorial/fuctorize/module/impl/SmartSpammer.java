// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\SmartSpammer.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.spammer.GuiSmartSpammer;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.module.settings.TextSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmartSpammer extends Module {

    public TextSetting message;
    public SliderSetting interval;
    public SliderSetting duration;
    public TextSetting randCharset;
    public TextSetting sequenceList;

    private volatile Thread spamThread;
    private volatile boolean isSpamming = false;
    private final Random random = new Random();
    private int sequenceIndex = 0;

    private static final Pattern GENERIC_PLACEHOLDER_PATTERN = Pattern.compile("(\\{[^}]+})");
    private static final Pattern RAND_SYNTAX = Pattern.compile("rand:(\\d+)");
    private static final Pattern SEQ_SYNTAX = Pattern.compile("seq");
    private static final Pattern FILE_SYNTAX = Pattern.compile("file:\"([^\"]+)\"");

    private final Map<String, List<String>> fileCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> fileLineIndexes = new ConcurrentHashMap<>();

    public SmartSpammer(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("smartspammer", Lang.get("module.smartspammer.name"), Category.EXPLOIT, ActivationType.SINGLE);
        setShowInHud(false);

        message = new TextSetting(Lang.get("module.smartspammer.setting.message"), "Hello, {seq}! Random number: {rand:5}");
        interval = new SliderSetting(Lang.get("module.smartspammer.setting.interval"), 1000.0, 10.0, 5000.0, 50.0);
        duration = new SliderSetting(Lang.get("module.smartspammer.setting.duration"), 60.0, 1.0, 300.0, 1.0);
        randCharset = new TextSetting(Lang.get("module.smartspammer.setting.rand_charset"), "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890");
        sequenceList = new TextSetting(Lang.get("module.smartspammer.setting.sequence_list"), "hi,bye,start,end");

        addSetting(message);
        addSetting(interval);
        addSetting(duration);
        addSetting(randCharset);
        addSetting(sequenceList);
        addSetting(new BindSetting(Lang.get("module.smartspammer.setting.open_gui"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.smartspammer.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            mc.displayGuiScreen(new GuiSmartSpammer(this));
        }
        toggle();
    }

    @Override
    public void onDisable() {
        stopSpam();
    }

    public boolean isSpamming() {
        return isSpamming;
    }

    public void startSpam() {
        if (isSpamming) return;

        if (!preloadFiles()) {
            return;
        }

        isSpamming = true;
        sequenceIndex = 0;
        fileLineIndexes.clear();

        spamThread = new Thread(this::spamLoop);
        spamThread.setName("Fuctorize-SmartSpammer-Worker");
        spamThread.setDaemon(true);
        spamThread.start();

        client.notificationManager.show(new Notification(Lang.get("notification.smartspammer.title.started"), Lang.get("notification.smartspammer.message.started"), Notification.NotificationType.SUCCESS, 3000L));
    }

    public void stopSpam() {
        if (!isSpamming) return;
        isSpamming = false;
        if (spamThread != null) spamThread.interrupt();
        spamThread = null;
        client.notificationManager.show(new Notification(Lang.get("notification.smartspammer.title.stopped"), Lang.get("notification.smartspammer.message.stopped"), Notification.NotificationType.INFO, 2000L));
    }

    private boolean preloadFiles() {
        fileCache.clear();
        Matcher matcher = FILE_SYNTAX.matcher(message.text);
        while (matcher.find()) {
            String path = matcher.group(1);
            try {
                String normalizedPath = path.replace("\\", "/").replace("//", "/");
                List<String> lines = Files.readAllLines(Paths.get(normalizedPath), StandardCharsets.UTF_8);
                if (lines.isEmpty()) {
                    client.notificationManager.show(new Notification(Lang.get("notification.smartspammer.title.error"), Lang.get("notification.smartspammer.message.file_empty") + path, Notification.NotificationType.ERROR, 4000L));
                    return false;
                }
                fileCache.put(path, lines);
            } catch (InvalidPathException e) {
                client.notificationManager.show(new Notification(Lang.get("notification.smartspammer.title.error"), Lang.get("notification.smartspammer.message.invalid_path") + path, Notification.NotificationType.ERROR, 4000L));
                return false;
            } catch (IOException e) {
                client.notificationManager.show(new Notification(Lang.get("notification.smartspammer.title.error"), Lang.get("notification.smartspammer.message.read_error") + path, Notification.NotificationType.ERROR, 4000L));
                return false;
            }
        }
        return true;
    }

    private void spamLoop() {
        long endTime = System.currentTimeMillis() + (long) (duration.value * 1000);
        String[] sequenceItems = sequenceList.text.split(",");

        while (isSpamming && System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
            try {
                if (mc.thePlayer != null && mc.getNetHandler() != null) {
                    client.scheduleTask(() -> {
                        if (mc.thePlayer != null) {
                            String processedMessage = processPlaceholders(message.text, sequenceItems);
                            mc.thePlayer.sendChatMessage(processedMessage);
                        }
                    });
                } else {
                    break;
                }
                Thread.sleep((long) interval.value);
            } catch (InterruptedException e) {
                break;
            }
        }

        if (isSpamming) {
            client.scheduleTask(() -> client.notificationManager.show(new Notification(Lang.get("notification.smartspammer.title.finished"), Lang.get("notification.smartspammer.message.finished"), Notification.NotificationType.INFO, 2000L)));
        }
        isSpamming = false;
    }
    public void checkFilesAndNotify() {
        Map<String, List<String>> checkCache = new ConcurrentHashMap<>();
        Matcher matcher = FILE_SYNTAX.matcher(message.text);
        List<String> errors = new java.util.ArrayList<>();
        int fileCount = 0;

        while (matcher.find()) {
            fileCount++;
            String path = matcher.group(1);
            try {
                String normalizedPath = path.replace("\\", "/").replace("//", "/");
                List<String> lines = Files.readAllLines(Paths.get(normalizedPath), StandardCharsets.UTF_8);
                if (lines.isEmpty()) {
                    errors.add(Lang.get("notification.smartspammer.message.file_empty") + path);
                }
                checkCache.put(path, lines);
            } catch (InvalidPathException e) {
                errors.add(Lang.get("notification.smartspammer.message.invalid_path") + path);
            } catch (IOException e) {
                errors.add(Lang.get("notification.smartspammer.message.read_error") + path);
            }
        }

        if (fileCount == 0) {
            client.notificationManager.show(new Notification(Lang.get("notification.smartspammer.title.file_check"), Lang.get("notification.smartspammer.message.no_files"), Notification.NotificationType.INFO, 3000L));
        } else if (errors.isEmpty()) {
            client.notificationManager.show(new Notification(Lang.get("notification.smartspammer.title.file_check"), Lang.get("notification.smartspammer.message.files_ok"), Notification.NotificationType.SUCCESS, 3000L));
        } else {
            client.notificationManager.show(new Notification(Lang.get("notification.smartspammer.title.file_check"), Lang.get("notification.smartspammer.message.check_error") + errors.get(0), Notification.NotificationType.ERROR, 5000L));
        }
    }

    private String processPlaceholders(String rawMessage, String[] sequenceItems) {
        Matcher matcher = GENERIC_PLACEHOLDER_PATTERN.matcher(rawMessage);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String fullPlaceholder = matcher.group(1);
            String replacement = evaluatePlaceholder(fullPlaceholder, sequenceItems);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String evaluatePlaceholder(String fullPlaceholder, String[] sequenceItems) {
        String content = fullPlaceholder.substring(1, fullPlaceholder.length() - 1);

        Matcher randMatcher = RAND_SYNTAX.matcher(content);
        if (randMatcher.matches()) {
            int length = Integer.parseInt(randMatcher.group(1));
            return generateRandomString(length);
        }

        if (SEQ_SYNTAX.matcher(content).matches()) {
            if (sequenceItems.length > 0 && !sequenceItems[0].isEmpty()) {
                String item = sequenceItems[sequenceIndex].trim();
                sequenceIndex = (sequenceIndex + 1) % sequenceItems.length;
                return item;
            }
            return "";
        }

        Matcher fileMatcher = FILE_SYNTAX.matcher(content);
        if (fileMatcher.matches()) {
            String path = fileMatcher.group(1);
            List<String> lines = fileCache.get(path);
            if (lines != null && !lines.isEmpty()) {
                int currentIndex = fileLineIndexes.getOrDefault(path, 0);
                String line = lines.get(currentIndex);
                fileLineIndexes.put(path, (currentIndex + 1) % lines.size());
                return line;
            }
            return "";
        }

        return "";
    }

    public static boolean isValidPlaceholderSyntax(String fullPlaceholder) {
        if (fullPlaceholder == null || fullPlaceholder.length() < 3 || !fullPlaceholder.startsWith("{") || !fullPlaceholder.endsWith("}")) {
            return false;
        }
        String content = fullPlaceholder.substring(1, fullPlaceholder.length() - 1);
        return RAND_SYNTAX.matcher(content).matches()
                || SEQ_SYNTAX.matcher(content).matches()
                || FILE_SYNTAX.matcher(content).matches();
    }

    private String generateRandomString(int length) {
        String charset = randCharset.text;
        if (length <= 0 || charset.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(random.nextInt(charset.length())));
        }
        return sb.toString();
    }
}
