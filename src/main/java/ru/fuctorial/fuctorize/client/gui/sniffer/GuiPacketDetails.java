// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\client\gui\sniffer\GuiPacketDetails.java
package ru.fuctorial.fuctorize.client.gui.sniffer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiNBTEdit;
import ru.fuctorial.fuctorize.client.gui.widgets.GuiTextInput;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.manager.FavoritePacketManager;
import ru.fuctorial.fuctorize.manager.PacketPersistence;
import ru.fuctorial.fuctorize.module.impl.PacketSniffer;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.PacketInfo;
import ru.fuctorial.fuctorize.utils.PacketSerializer;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GuiPacketDetails extends GuiScreen {

    private final GuiScreen parentScreen;
    private final PacketInfo packetInfo;

    private volatile List<String> wrappedLines = new ArrayList<>();
    private volatile boolean isLoading = true;

    private int scrollOffset = 0;
    private float totalContentHeight = 0;
    private int viewableTextHeight = 0;

    private int panelX, panelY, panelWidth, panelHeight;
    private int contentX;

    private int footerHeight = 35;

    public GuiPacketDetails(PacketInfo packetInfo, GuiScreen parentScreen) {
        this.packetInfo = packetInfo;
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        // --- АДАПТИВНАЯ ВЕРСТКА КНОПОК ---
        int padding = 5;
        int bottomMargin = 10;
        int buttonHeight = 20;
        int availableWidth = this.width - 20;

        String txtNbt = Lang.get("details.button.edit_payload_nbt");
        String txtEdit = Lang.get("details.button.edit_fields");
        String txtBack = Lang.get("details.button.back");
        String txtFav = "★ Save Favorite";

        // --- ИЗМЕНЕНИЕ: Текст для кнопки бана ---
        boolean isBlacklisted = false;
        if (packetInfo.rawPacket instanceof FMLProxyPacket) {
            isBlacklisted = PacketSniffer.getBlacklistedFmlChannels().contains(((FMLProxyPacket) packetInfo.rawPacket).channel());
        } else {
            isBlacklisted = PacketSniffer.getBlacklistedClassNames().contains(packetInfo.rawPacket.getClass().getName());
        }
        String txtBan = isBlacklisted ? EnumChatFormatting.GREEN + "Unban Packet" : EnumChatFormatting.RED + "Ban Packet";
        // ---------------------------------------

        int minBtnW = 90;

        // 5 кнопок: Back, Edit, Ban, NBT, Fav
        int cols = 5;
        if (availableWidth < (minBtnW * 5 + padding * 4)) cols = 3;
        if (availableWidth < (minBtnW * 3 + padding * 2)) cols = 2;
        if (availableWidth < (minBtnW * 2 + padding)) cols = 1;

        int rows = (int) Math.ceil(5.0 / cols);
        int buttonW = (availableWidth - (padding * (cols - 1))) / cols;

        this.footerHeight = (rows * buttonHeight) + ((rows - 1) * padding) + bottomMargin * 2;
        int startY = this.height - footerHeight + bottomMargin;
        int startX = (this.width - availableWidth) / 2;

        // IDs: 0=Back, 1=Edit, 2=Ban, 3=NBT, 4=Favorites
        addResponsiveButton(4, 0, cols, startX, startY, buttonW, buttonHeight, padding, txtFav);
        addResponsiveButton(3, 1, cols, startX, startY, buttonW, buttonHeight, padding, txtNbt);
        addResponsiveButton(2, 2, cols, startX, startY, buttonW, buttonHeight, padding, txtBan); // Центральная (или около того)
        addResponsiveButton(1, 3, cols, startX, startY, buttonW, buttonHeight, padding, txtEdit);
        addResponsiveButton(0, 4, cols, startX, startY, buttonW, buttonHeight, padding, txtBack);

        // ... расчет размеров панели (без изменений) ...
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            return;
        }

        int maxPanelWidth = (int) (this.width * 0.90f);
        panelWidth = maxPanelWidth;
        panelHeight = this.height - 40 - footerHeight;
        panelX = (this.width - panelWidth) / 2;
        panelY = 40;
        contentX = panelX + 10;

        startAsyncLoading(maxPanelWidth - 20);
    }

    private void addResponsiveButton(int id, int index, int cols, int startX, int startY, int w, int h, int pad, String text) {
        int row = index / cols;
        int col = index % cols;
        int x = startX + col * (w + pad);
        int y = startY + row * (h + pad);
        this.buttonList.add(new StyledButton(id, x, y, w, h, text));
    }

    private void startAsyncLoading(int maxPanelWidth) {
        this.isLoading = true;
        new Thread(() -> {
            try {
                CustomFontRenderer textFont = FuctorizeClient.INSTANCE.fontManager.regular_18;
                if (textFont == null) return;

                String textToDisplay = packetInfo.getSerializedData();
                String hex = packetInfo.getPayloadHex();

                if (hex != null && !hex.isEmpty()) {
                    textToDisplay += "\n\n§6--- RAW PAYLOAD (HEX) ---\n§e" + hex;
                }
                textToDisplay = textToDisplay.replace("--- RAW PAYLOAD (HEX) ---", Lang.get("details.payload_hex_heading"));

                List<String> calculatedLines = textFont.wrapText(textToDisplay, maxPanelWidth);
                synchronized (this) {
                    this.wrappedLines = calculatedLines;
                    this.isLoading = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                synchronized (this) {
                    this.wrappedLines = new ArrayList<>();
                    this.wrappedLines.add("§cError loading packet data: " + e.getMessage());
                    this.isLoading = false;
                }
            }
        }).start();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) this.mc.displayGuiScreen(this.parentScreen);
        if (button.id == 1) this.mc.displayGuiScreen(new GuiPacketEditor(this.packetInfo, this));

        // --- ИЗМЕНЕНИЕ: Логика кнопки BAN ---
        if (button.id == 2) {
            if (packetInfo.rawPacket instanceof FMLProxyPacket) {
                String channel = ((FMLProxyPacket) packetInfo.rawPacket).channel();
                if (PacketSniffer.getBlacklistedFmlChannels().contains(channel)) {
                    PacketSniffer.removeBlacklistedFmlChannel(channel);
                } else {
                    PacketSniffer.addBlacklistedFmlChannel(channel);
                }
            } else {
                String clazz = packetInfo.rawPacket.getClass().getName();
                if (PacketSniffer.getBlacklistedClassNames().contains(clazz)) {
                    PacketSniffer.removeBlacklistedClassName(clazz);
                } else {
                    PacketSniffer.addBlacklistedClassName(clazz);
                }
            }
            this.initGui(); // Обновить текст кнопки
        }
        // ------------------------------------

        if (button.id == 3) scanAndEditNbt();

        if (button.id == 4) {
            String defaultName = packetInfo.cleanName;
            mc.displayGuiScreen(new GuiTextInput(this, "Save Packet As...", defaultName, (name) -> {
                PacketPersistence.SavedPacketData data = PacketPersistence.capture(packetInfo.rawPacket, name, packetInfo.direction);
                if (data != null) {
                    FavoritePacketManager.add(data);
                    FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Success", "Packet saved to favorites!", Notification.NotificationType.SUCCESS, 2000L));
                } else {
                    FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Error", "Could not serialize packet!", Notification.NotificationType.ERROR, 3000L));
                }
            }));
        }
    }

    // ... (остальные методы без изменений: scanAndEditNbt, drawScreen, etc.) ...

    private void scanAndEditNbt() {
        // (Код scanAndEditNbt из предыдущего ответа)
        if (!(packetInfo.rawPacket instanceof C17PacketCustomPayload)) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("details.error.nbt_edit_title"),
                    Lang.get("details.error.nbt_edit_only_c17"),
                    Notification.NotificationType.ERROR, 3000L));
            return;
        }

        C17PacketCustomPayload packet = (C17PacketCustomPayload) packetInfo.rawPacket;
        ByteBuf payload = null;
        try {
            Field dataField = C17PacketCustomPayload.class.getDeclaredField("field_149561_c");
            dataField.setAccessible(true);
            payload = (ByteBuf) dataField.get(packet);
        } catch (Exception e) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("details.error.reflection_title"),
                    Lang.get("details.error.reflection_payload_access"),
                    Notification.NotificationType.ERROR, 3000L));
            return;
        }

        if (payload == null) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("details.error.nbt_edit_title"),
                    Lang.get("details.error.payload_null"),
                    Notification.NotificationType.ERROR, 3000L));
            return;
        }

        NBTTagCompound foundNbt = PacketSerializer.tryReadNBT(payload);

        if (foundNbt == null) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("details.error.nbt_edit_title"),
                    Lang.get("details.error.nbt_edit_no_valid"),
                    Notification.NotificationType.ERROR, 3000L));
            return;
        }

        mc.displayGuiScreen(new GuiNBTEdit(foundNbt, editedNbt -> {
            try {
                ByteBuf newPayload = PacketSerializer.writeNBTToBuf(editedNbt);
                if (newPayload == null) throw new Exception("Failed to serialize NBT back to ByteBuf.");

                C17PacketCustomPayload newPacket = new C17PacketCustomPayload(packet.func_149559_c(), newPayload);
                NetUtils.sendPacket(newPacket);

                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                        Lang.get("details.notify.sent.title"),
                        Lang.get("details.notify.sent.message"),
                        Notification.NotificationType.SUCCESS, 2500L));
                mc.displayGuiScreen(this);
            } catch (Exception e) {
                e.printStackTrace();
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                        Lang.get("details.notify.error.title"),
                        Lang.get("details.notify.error.message"),
                        Notification.NotificationType.ERROR, 4000L));
                mc.displayGuiScreen(this);
            }
        }));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) { if (keyCode == Keyboard.KEY_ESCAPE) this.mc.displayGuiScreen(this.parentScreen); }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (totalContentHeight > viewableTextHeight) {
                this.scrollOffset += (dWheel > 0) ? 15 : -15;
                if (this.scrollOffset > 0) this.scrollOffset = 0;
                float maxScroll = totalContentHeight - viewableTextHeight;
                if (maxScroll < 0) maxScroll = 0;
                if (this.scrollOffset < -maxScroll) this.scrollOffset = (int) -maxScroll;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // (Код отрисовки с кнопками и текстом, как в предыдущем ответе, только кнопки теперь включают кнопку Ban)
        // ... drawBackground, drawRects ...

        this.drawDefaultBackground();
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer textFont = FuctorizeClient.INSTANCE.fontManager.regular_18;

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        String title = String.format("[%s] %s", packetInfo.direction, packetInfo.cleanName);
        float titleX = panelX + (panelWidth - titleFont.getStringWidth(title)) / 2.0f;
        titleFont.drawString(title, titleX, panelY + 8, Theme.ORANGE.getRGB());

        int separatorY = panelY + 25;
        RenderUtils.drawRect(panelX + 5, separatorY, panelX + panelWidth - 5, separatorY + 1, Theme.DIVIDER.getRGB());

        if (isLoading) {
            String loadText = "Loading large packet data...";
            float lx = panelX + (panelWidth - textFont.getStringWidth(loadText)) / 2.0f;
            float ly = panelY + panelHeight / 2.0f;
            long time = System.currentTimeMillis();
            int dots = (int)((time / 500) % 4);
            String dotsStr = "";
            for(int i=0; i<dots; i++) dotsStr += ".";
            textFont.drawString(loadText + dotsStr, lx, ly, -1);

            for (Object button : this.buttonList) {
                ((GuiButton) button).drawButton(this.mc, mouseX, mouseY);
            }
            return;
        }

        int topTextY = separatorY + 8;
        int bottomTextY = panelY + panelHeight - 8;
        this.viewableTextHeight = bottomTextY - topTextY;

        this.totalContentHeight = (textFont.getHeight() + 2) * wrappedLines.size();

        RenderUtils.startScissor(panelX, topTextY, panelWidth, viewableTextHeight);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, scrollOffset, 0);
        int currentY = topTextY;

        for (String line : wrappedLines) {
            if (currentY + scrollOffset > bottomTextY) break;
            if (currentY + scrollOffset + textFont.getHeight() > topTextY) {
                textFont.drawString(line, contentX, currentY, -1);
            }
            currentY += textFont.getHeight() + 2;
        }

        GL11.glPopMatrix();
        RenderUtils.stopScissor();

        if (totalContentHeight > viewableTextHeight) {
            int scrollbarX = panelX + panelWidth - 8;
            RenderUtils.drawRect(scrollbarX, topTextY, scrollbarX + 4, bottomTextY, 0x55000000);
            float scrollPercent = (float) -scrollOffset / (totalContentHeight - viewableTextHeight);
            int handleHeight = (int) ((float) viewableTextHeight / totalContentHeight * viewableTextHeight);
            handleHeight = Math.max(handleHeight, 20);
            int handleY = topTextY + (int) (scrollPercent * (viewableTextHeight - handleHeight));
            RenderUtils.drawRect(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, Theme.ORANGE.getRGB());
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}