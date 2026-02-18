package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CheckVanish extends Module {

    private BooleanSetting playSound;
    private BooleanSetting showOnHud;
    private SliderSetting checkInterval;
    private BooleanSetting useMcsrvApi;

     
    private final Set<String> vanishedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile String hudInfo = Lang.get("hud.checkvanish.status.starting");
    private volatile Thread checkThread;

     
    private int potentialVanishCount = 0;
    private long lastDetectionTime = 0;

    public CheckVanish(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("checkvanish", Lang.get("module.checkvanish.name"), Category.MISC);

        playSound = new BooleanSetting(Lang.get("module.checkvanish.setting.play_sound"), true);
        showOnHud = new BooleanSetting(Lang.get("module.checkvanish.setting.show_on_hud"), true);
        useMcsrvApi = new BooleanSetting(Lang.get("module.checkvanish.setting.use_mcsrv_api"), false);  
        checkInterval = new SliderSetting(Lang.get("module.checkvanish.setting.check_interval"), 5.0, 2.0, 60.0, 1.0);

        addSetting(playSound);
        addSetting(showOnHud);
        addSetting(useMcsrvApi);
        addSetting(checkInterval);
        addSetting(new BindSetting(Lang.get("module.checkvanish.setting.bind"), Keyboard.KEY_NONE));

        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.checkvanish.desc");
    }

    @Override
    public void onEnable() {
        vanishedPlayers.clear();
        potentialVanishCount = 0;
        if (checkThread == null || !checkThread.isAlive()) {
            checkThread = new Thread(this::runCheckLoop);
            checkThread.setName("Fuctorize-VanishCheck");
            checkThread.setDaemon(true);
            checkThread.start();
        }
    }

    @Override
    public void onDisable() {
        if (checkThread != null) {
            checkThread.interrupt();
            checkThread = null;
        }
        vanishedPlayers.clear();
        hudInfo = "";
    }

    private void runCheckLoop() {
        while (this.isEnabled() && !Thread.currentThread().isInterrupted()) {
            try {
                if (mc.thePlayer == null || mc.theWorld == null) {
                    hudInfo = "§7Waiting for game...";
                    Thread.sleep(2000);
                    continue;
                }

                ServerData serverData = mc.func_147104_D();
                if (serverData == null || serverData.serverIP == null) {
                    hudInfo = "§7Singleplayer";
                    Thread.sleep(5000);
                    continue;
                }

                 
                Set<String> tabPlayers = getTabPlayerNames();
                int tabCount = tabPlayers.size();

                 
                 
                boolean forceDetail = useMcsrvApi.enabled || potentialVanishCount > 0;
                NetUtils.VanishResult result = NetUtils.performMegaCheck(serverData.serverIP, forceDetail);

                if (result.isSuccess()) {
                    processLogic(result, tabPlayers, tabCount);
                } else {
                    hudInfo = "§cCheck Failed";
                }

                Thread.sleep((long) (checkInterval.value * 1000));

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processLogic(NetUtils.VanishResult result, Set<String> tabPlayers, int tabCount) {
        int serverCount = result.getOnlineCount();

         
        if (result.hasExactNames()) {
            Set<String> remoteNames = new HashSet<>(result.getPlayerNames());
            List<String> detected = new ArrayList<>();

            for (String name : remoteNames) {
                 
                if (!tabPlayers.contains(name)) {
                    detected.add(name);
                }
            }

            updateVanishList(detected);
            updateHud(detected.size(), true);
            return;
        }

         
         
         
        int diff = serverCount - tabCount;

        if (diff > 0) {
             
             
            long now = System.currentTimeMillis();

             
            if (potentialVanishCount == diff && (now - lastDetectionTime > 2000)) {
                vanishedPlayers.clear();  
                updateHud(diff, false);
                if (diff != potentialVanishCount) {  
                    playAlertSound();
                }
            } else if (potentialVanishCount != diff) {
                 
                potentialVanishCount = diff;
                lastDetectionTime = now;
                hudInfo = "§eVerifying...";
            }
        } else {
            potentialVanishCount = 0;
            if (!vanishedPlayers.isEmpty()) {
                vanishedPlayers.clear();
                hudInfo = "§aClean";
            } else {
                hudInfo = "§aClean";
            }
        }
    }

    private void updateVanishList(List<String> currentDetected) {
        boolean newVanishFound = false;

         
        vanishedPlayers.removeIf(name -> !currentDetected.contains(name));

         
        for (String name : currentDetected) {
            if (vanishedPlayers.add(name)) {
                newVanishFound = true;
            }
        }

        if (newVanishFound) {
            playAlertSound();
        }
    }

    private void playAlertSound() {
        if (playSound.enabled && mc.thePlayer != null) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("mob.endermen.portal"), 1.0F));
        }
    }

    private void updateHud(int count, boolean hasNames) {
        if (count <= 0) {
            hudInfo = "§aClean";
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§cIn Vanish: ").append(count);

        if (hasNames && !vanishedPlayers.isEmpty()) {
            sb.append(" §7[").append(String.join(", ", vanishedPlayers)).append("]");
        } else {
            sb.append(" §f(Unknown)");
        }
        hudInfo = sb.toString();
    }

    private Set<String> getTabPlayerNames() {
        Set<String> names = new HashSet<>();
        if (mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
             
            try {
                List<GuiPlayerInfo> list = new ArrayList<>(mc.thePlayer.sendQueue.playerInfoList);
                for (GuiPlayerInfo info : list) {
                    if (info != null && info.name != null) names.add(info.name);
                }
            } catch (Exception e) {
                 
            }
        }
        return names;
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (shouldShowOnHud() && client.fontManager != null) {
            CustomFontRenderer font = client.fontManager.bold_22;
            if (hudInfo == null) return;

            float hudWidth = font.getStringWidth(hudInfo);
            float hudX = (event.resolution.getScaledWidth() - hudWidth) / 2.0f;
            float hudY = 10;  

            font.drawString(hudInfo, hudX + 1, hudY + 1, 0x50000000);
            font.drawString(hudInfo, hudX, hudY, -1);
        }
    }

    public boolean shouldShowOnHud() {
        return this.isEnabled() && this.showOnHud.enabled;
    }
}