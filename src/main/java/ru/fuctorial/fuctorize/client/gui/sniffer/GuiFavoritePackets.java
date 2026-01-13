package ru.fuctorial.fuctorize.client.gui.sniffer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.manager.FavoritePacketManager;
import ru.fuctorial.fuctorize.manager.PacketPersistence;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.ReflectionUtils; // Не забудь импорт!
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.Packet;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;

public class GuiFavoritePackets extends GuiScreen {

    private final GuiScreen parentScreen;
    private int panelX, panelY, panelWidth, panelHeight;
    private int scrollOffset = 0;
    private int totalContentHeight = 0;
    private int viewableContentHeight = 0;

    private boolean deleteMode = false;

    public GuiFavoritePackets(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        this.panelWidth = 320;
        this.panelHeight = Math.min(400, this.height - 40);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        List<PacketPersistence.SavedPacketData> list = FavoritePacketManager.getFavorites();
        this.totalContentHeight = list.size() * 22;
        this.viewableContentHeight = panelHeight - 40 - 30;

        this.buttonList.clear();

        String delBtnText = deleteMode ? EnumChatFormatting.RED + "Delete Mode: ON" : "Delete Mode: OFF";
        this.buttonList.add(new StyledButton(1, panelX + 10, panelY + panelHeight - 25, 100, 20, delBtnText));
        this.buttonList.add(new StyledButton(0, panelX + panelWidth - 90, panelY + panelHeight - 25, 80, 20, Lang.get("blacklist.button.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(parentScreen);
        } else if (button.id == 1) {
            deleteMode = !deleteMode;
            this.initGui();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        int topBound = panelY + 35;
        int bottomBound = panelY + panelHeight - 30;

        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= topBound && mouseY <= bottomBound) {
            int adjustedY = mouseY - topBound + scrollOffset;
            int index = adjustedY / 22;
            List<PacketPersistence.SavedPacketData> list = FavoritePacketManager.getFavorites();

            if (index >= 0 && index < list.size()) {
                PacketPersistence.SavedPacketData selectedPacket = list.get(index);

                if (mouseButton == 0) { // LEFT CLICK
                    if (deleteMode) {
                        FavoritePacketManager.remove(index);
                        this.totalContentHeight = FavoritePacketManager.getFavorites().size() * 22;
                    } else {
                        // Обычная отправка (как было раньше)
                        processPacketAction(selectedPacket);
                    }
                }
                else if (mouseButton == 1) { // RIGHT CLICK
                    // Открываем HEX редактор для выбранного пакета
                    // Передаем индекс, чтобы можно было сохранить изменения
                    mc.displayGuiScreen(new GuiHexEditor(this, selectedPacket, index));
                }
            }
        }
    }

    /**
     * Главный метод обработки пакета.
     * Различает входящие (для открытия GUI) и исходящие пакеты.
     */
    private void processPacketAction(PacketPersistence.SavedPacketData data) {
        Packet packet = PacketPersistence.reconstruct(data);

        if (packet == null) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Error", "Failed to reconstruct packet!", Notification.NotificationType.ERROR, 3000L));
            return;
        }

        boolean isIncoming = "RCVD".equals(data.direction);
        if (data.direction == null) {
            String simpleName = packet.getClass().getSimpleName();
            if (simpleName.startsWith("S")) isIncoming = true;
        }

        // --- FIX 4 (Применяем и здесь) ---
        if (packet instanceof cpw.mods.fml.common.network.internal.FMLProxyPacket && isIncoming) {
            try {
                ((cpw.mods.fml.common.network.internal.FMLProxyPacket) packet).setTarget(cpw.mods.fml.relauncher.Side.CLIENT);
            } catch (Exception ignored) {}
        }
        // --------------------------------

        if (isIncoming) {
            if (mc.getNetHandler() != null) {
                try {
                    ReflectionUtils.receivePacket(mc.getNetHandler().getNetworkManager(), packet);
                    FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                            "Processed Locally",
                            "Packet '" + data.name + "' processed!",
                            Notification.NotificationType.SUCCESS,
                            2000L
                    ));
                } catch (Exception e) {
                    FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Error", "Failed to process: " + e.getMessage(), Notification.NotificationType.ERROR, 3000L));
                    e.printStackTrace();
                }
            }
        } else {
            NetUtils.sendPacket(packet);
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    "Sent to Server",
                    "Packet '" + data.name + "' sent!",
                    Notification.NotificationType.SUCCESS,
                    2000L
            ));
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (totalContentHeight > viewableContentHeight) {
                scrollOffset -= (dWheel > 0 ? 1 : -1) * 15;
                if (scrollOffset < 0) scrollOffset = 0;
                int maxScroll = Math.max(0, totalContentHeight - viewableContentHeight);
                if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        titleFont.drawString("Favorite Packets", panelX + 10, panelY + 10, Theme.ORANGE.getRGB());

        // Добавляем подсказку мелким шрифтом справа
        CustomFontRenderer smallFont = FuctorizeClient.INSTANCE.fontManager.regular_18;
        String hint = "LMB: Send | RMB: Edit Hex";
        float hintW = smallFont.getStringWidth(hint);
        smallFont.drawString(hint, panelX + panelWidth - hintW - 10, panelY + 12, Theme.TEXT_GRAY.getRGB());

        int topBound = panelY + 35;
        RenderUtils.startScissor(panelX, topBound, panelWidth, viewableContentHeight);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, -scrollOffset, 0);

        int currentY = topBound;
        List<PacketPersistence.SavedPacketData> list = FavoritePacketManager.getFavorites();
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        for (PacketPersistence.SavedPacketData data : list) {
            boolean hovered = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10 &&
                    mouseY >= currentY - scrollOffset && mouseY <= currentY + 20 - scrollOffset;

            int color = hovered ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB();
            if (deleteMode) color = hovered ? new Color(100, 0, 0, 200).getRGB() : new Color(60, 0, 0, 200).getRGB();

            RenderUtils.drawRect(panelX + 10, currentY, panelX + panelWidth - 10, currentY + 20, color);

            String display = data.name;
            // Добавляем визуальную метку направления
            String dirTag = (data.direction != null) ? (data.direction.equals("SENT") ? " §a[->] " : " §b[<-] ") : " §7[?] ";
            String meta = EnumChatFormatting.GRAY + " (" + data.className.substring(data.className.lastIndexOf('.')+1) + ")";

            font.drawString(dirTag + display + meta, panelX + 15, currentY + 6, -1);

            currentY += 22;
        }

        GL11.glPopMatrix();
        RenderUtils.stopScissor();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parentScreen);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}