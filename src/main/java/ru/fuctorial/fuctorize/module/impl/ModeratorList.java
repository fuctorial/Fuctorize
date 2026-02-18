package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.misc.GuiModeratorEditor;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SeparatorSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.manager.ModeratorManager;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.RenderUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModeratorList extends Module {
    private SliderSetting xPos, yPos, scale;
    private BooleanSetting background;
    private BooleanSetting autoAddChat;
    private BooleanSetting autoAddTab;
    private BooleanSetting openEditorBtn;

    private final List<String> onlineMods = new ArrayList<>();
    private long lastCheck = 0;

    private static final Pattern MOD_REGEX = Pattern.compile("(?i)(?:\\b|\\[)Moderator(?:\\b|\\])\\s*[^a-zA-Z0-9_]*\\s*([a-zA-Z0-9_]{3,16})");
    private static final String[] TAB_KEYWORDS = {
            "admin", "owner", "founder", "mod", "moder", "helper", "support", "staff", "kurator", "curator", "gm", "gamemaster"
    };

    public ModeratorList(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("moderatorlist", Lang.get("module.moderatorlist.name"), Category.RENDER);
        xPos = new SliderSetting("X", 2.0, 0.0, 1000.0, 1.0);
        yPos = new SliderSetting("Y", 100.0, 0.0, 1000.0, 1.0);
        scale = new SliderSetting(Lang.get("module.moderatorlist.setting.scale"), 1.0, 0.5, 2.0, 0.1);
        background = new BooleanSetting(Lang.get("module.moderatorlist.setting.bg"), true);

        autoAddChat = new BooleanSetting("Auto Add (Chat)", true);
        autoAddTab = new BooleanSetting("Auto Add (Tab Prefix)", true);

        openEditorBtn = new BooleanSetting("Редактировать список...", false);

        addSetting(xPos);
        addSetting(yPos);
        addSetting(scale);
        addSetting(background);
        addSetting(new SeparatorSetting());
        addSetting(autoAddChat);
        addSetting(autoAddTab);
        addSetting(openEditorBtn);

        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.moderatorlist.desc");
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @Override
    public void onUpdate() {
        if (openEditorBtn.enabled) {
            openEditorBtn.enabled = false;
            mc.displayGuiScreen(new GuiModeratorEditor(mc.currentScreen));
        }

        if (System.currentTimeMillis() - lastCheck > 1000) {
            onlineMods.clear();
            if (mc.getNetHandler() != null && mc.getNetHandler().playerInfoList != null) {
                Scoreboard scoreboard = mc.theWorld != null ? mc.theWorld.getScoreboard() : null;
                for (Object o : mc.getNetHandler().playerInfoList) {
                    GuiPlayerInfo i = (GuiPlayerInfo) o;
                    String name = i.name;
                    if (name == null) continue;
                    if (ModeratorManager.isModerator(name)) {
                        onlineMods.add(name);
                    } else if (autoAddTab.enabled && scoreboard != null && !name.equalsIgnoreCase(mc.thePlayer.getCommandSenderName())) {
                        checkAndAddFromScoreboard(name, scoreboard);
                    }
                }
            }
            lastCheck = System.currentTimeMillis();
        }
    }

    private void checkAndAddFromScoreboard(String username, Scoreboard scoreboard) {
        ScorePlayerTeam team = scoreboard.getPlayersTeam(username);
        if (team != null) {
            String rawPrefix = team.getColorPrefix();
            String cleanPrefix = EnumChatFormatting.getTextWithoutFormattingCodes(rawPrefix).toLowerCase();
            for (String keyword : TAB_KEYWORDS) {
                if (cleanPrefix.contains(keyword)) {
                    ModeratorManager.add(username);
                    onlineMods.add(username);
                    client.notificationManager.show(new Notification("Staff Detected (Tab)", "Added " + EnumChatFormatting.RED + username + EnumChatFormatting.RESET + " (" + keyword + ")", Notification.NotificationType.WARNING, 4000L));
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!autoAddChat.enabled || mc.thePlayer == null) return;
        String message = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        Matcher matcher = MOD_REGEX.matcher(message);
        if (matcher.find()) {
            String nickname = matcher.group(1);
            if (nickname != null && !nickname.isEmpty() && !nickname.equalsIgnoreCase("list") && !nickname.equalsIgnoreCase(mc.thePlayer.getCommandSenderName())) {
                if (!ModeratorManager.isModerator(nickname)) {
                    ModeratorManager.add(nickname);
                    client.notificationManager.show(new Notification("Staff Detected (Chat)", "Added " + EnumChatFormatting.RED + nickname + EnumChatFormatting.RESET + " to list.", Notification.NotificationType.WARNING, 3000L));
                    lastCheck = 0;
                }
            }
        }
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (!FuctorizeClient.INSTANCE.fontManager.isReady()) return;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        GL11.glPushMatrix();
        GL11.glTranslated(xPos.value, yPos.value, 0);
        GL11.glScaled(scale.value, scale.value, 1);

        float w = 80;
        float titleW = font.getStringWidth(Lang.get("module.moderatorlist.name"));
        w = Math.max(w, titleW + 8);  

        for (String m : onlineMods) {
            w = Math.max(w, font.getStringWidth(m) + 8);
        }

         
        int headerHeight = 16;
        int rowHeight = 12;
        int bottomPadding = 4;  

         
        float h = onlineMods.isEmpty() ? (headerHeight + rowHeight + bottomPadding) : (headerHeight + (onlineMods.size() * rowHeight) + bottomPadding);

        if (background.enabled) {
            RenderUtils.drawRect(0, 0, w, h, new Color(0, 0, 0, 150).getRGB());
        }

         
        float titleX = (w - titleW) / 2f;
        float titleY = (headerHeight - font.getHeight()) / 2f;
        font.drawString(Lang.get("module.moderatorlist.name"), titleX, titleY, Theme.ORANGE.getRGB());

        int currentY = headerHeight;

        if (onlineMods.isEmpty()) {
            String none = "None";
            float noneX = (w - font.getStringWidth(none)) / 2f;
             
            float centeredY = currentY + (rowHeight - font.getHeight()) / 2f;
            font.drawString(EnumChatFormatting.GRAY + none, noneX, centeredY, -1);
        } else {
            for (String m : onlineMods) {
                float nameW = font.getStringWidth(m);
                float nameX = (w - nameW) / 2f;
                 
                float centeredY = currentY + (rowHeight - font.getHeight()) / 2f;

                font.drawString(EnumChatFormatting.RED + m, nameX, centeredY, -1);
                currentY += rowHeight;
            }
        }
        GL11.glPopMatrix();
    }
}