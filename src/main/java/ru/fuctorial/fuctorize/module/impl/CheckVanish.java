// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\module\impl\CheckVanish.java
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class CheckVanish extends Module {

    private BooleanSetting playSound;
    private BooleanSetting showOnHud;
    private SliderSetting checkInterval;
    private BooleanSetting useMcsrvApi;

    private final List<String> vanishedPlayers = new CopyOnWriteArrayList<>();
    private volatile String hudInfo = Lang.get("hud.checkvanish.status.starting");
    private volatile Thread checkThread;

    public CheckVanish(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("checkvanish", Lang.get("module.checkvanish.name"), Category.MISC);

        playSound = new BooleanSetting(Lang.get("module.checkvanish.setting.play_sound"), true);
        showOnHud = new BooleanSetting(Lang.get("module.checkvanish.setting.show_on_hud"), true);
        useMcsrvApi = new BooleanSetting(Lang.get("module.checkvanish.setting.use_mcsrv_api"), true);
        checkInterval = new SliderSetting(Lang.get("module.checkvanish.setting.check_interval"), 10.0, 5.0, 60.0, 1.0);

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
        if (checkThread == null || !checkThread.isAlive()) {
            checkThread = new Thread(this::runCheckLoop);
            checkThread.setName("Fuctorize-VanishCheck-Thread");
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
        hudInfo = Lang.get("hud.checkvanish.status.off");
    }

    private void runCheckLoop() {
        while (this.isEnabled() && !Thread.currentThread().isInterrupted()) {
            try {
                if (mc.thePlayer == null || mc.theWorld == null) {
                    hudInfo = Lang.get("hud.checkvanish.status.waiting");
                    Thread.sleep(2000);
                    continue;
                }

                ServerData serverData = mc.func_147104_D();
                if (serverData == null || serverData.serverIP == null) {
                    hudInfo = Lang.get("hud.checkvanish.status.singleplayer");
                    Thread.sleep(10000);
                    continue;
                }

                NetUtils.VanishResult result = NetUtils.performMegaCheck(serverData.serverIP, useMcsrvApi.enabled);

                if (result.isSuccess()) {
                    processVanishResult(result);
                }

                Thread.sleep((long) (checkInterval.value * 1000));

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
            }
        }
    }

    private void processVanishResult(NetUtils.VanishResult result) {
        Set<String> playersInTab = getTabPlayerNames();
        List<String> newVanishedList = new ArrayList<>();
        int serverCount = result.getOnlineCount();
        int tabCount = playersInTab.size();

        if (result.hasExactNames()) {
            Set<String> pingedNames = new HashSet<>(result.getPlayerNames());
            for (String pingedName : pingedNames) {
                if (!playersInTab.contains(pingedName)) {
                    newVanishedList.add(pingedName);
                }
            }
            updateAndPlaySounds(newVanishedList);
            updateHudInfoWithNames();
        } else {
            int vanishedCount = serverCount - tabCount;
            if (vanishedCount > 0) {
                updateHudInfoWithoutNames(vanishedCount);
                this.vanishedPlayers.clear();
            } else {
                updateAndPlaySounds(new ArrayList<>());
                updateHudInfoWithNames();
            }
        }
    }

    private void updateAndPlaySounds(List<String> newVanishedList) {
        if (playSound.enabled) {
            for (String name : newVanishedList) {
                if (!this.vanishedPlayers.contains(name)) {
                    mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("mob.endermen.portal"), 1.0F));
                }
            }
            for (String name : this.vanishedPlayers) {
                if (!newVanishedList.contains(name)) {
                    mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("random.orb"), 0.5F));
                }
            }
        }
        this.vanishedPlayers.clear();
        this.vanishedPlayers.addAll(newVanishedList);
    }

    private Set<String> getTabPlayerNames() {
        Set<String> names = new HashSet<>();
        if (mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
            for (Object infoObj : mc.thePlayer.sendQueue.playerInfoList) {
                if (infoObj instanceof GuiPlayerInfo) {
                    GuiPlayerInfo info = (GuiPlayerInfo) infoObj;
                    if (info.name != null) names.add(info.name);
                }
            }
        }
        return names;
    }

    private void updateHudInfoWithNames() {
        int vanishCount = vanishedPlayers.size();
        String namesString = "";
        if (vanishCount > 0) {
            namesString = " §7[" + String.join(", ", vanishedPlayers) + "]";
        }
        hudInfo = (vanishCount > 0 ? "§c" : "§a") + Lang.get("hud.checkvanish.status.in_vanish") + vanishCount + namesString;
    }

    private void updateHudInfoWithoutNames(int count) {
        hudInfo = "§c" + Lang.get("hud.checkvanish.status.in_vanish") + count + " §f" + Lang.get("hud.checkvanish.status.unknown_names");
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (shouldShowOnHud() && client.fontManager != null && client.fontManager.isReady()) {
            CustomFontRenderer font = client.fontManager.bold_22;
            float hudWidth = font.getStringWidth(hudInfo) + 4;
            float hudX = (event.resolution.getScaledWidth() - hudWidth) / 2.0f;
            float hudY = 5;
            font.drawString(hudInfo, hudX + 1, hudY + 1, 0x50000000);
            font.drawString(hudInfo, hudX, hudY, -1);
        }
    }

    public boolean shouldShowOnHud() {
        return this.isEnabled() && this.showOnHud.enabled;
    }
}