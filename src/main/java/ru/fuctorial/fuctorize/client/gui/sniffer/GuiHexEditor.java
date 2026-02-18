package ru.fuctorial.fuctorize.client.gui.sniffer;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import org.lwjgl.input.Mouse;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextArea;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.manager.FavoritePacketManager;
import ru.fuctorial.fuctorize.manager.PacketPersistence;
import ru.fuctorial.fuctorize.utils.HexUtils;
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.ReflectionUtils;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.network.Packet;
import org.lwjgl.input.Keyboard;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class GuiHexEditor extends GuiScreen {

    private final GuiScreen parentScreen;
    private final PacketPersistence.SavedPacketData packetData;
    private final int packetIndex;

    private String currentDirection;
     
    private final List<SegmentEditor> segmentEditors = new ArrayList<>();

     
    private int panelX, panelY, panelWidth, panelHeight;
    private static int savedX = -1, savedY = -1, savedWidth = 0, savedHeight = 0;

     
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;
    private boolean isResizing = false;
    private int resizeEdge = 0;  
    private final int resizeBorder = 6;

    private int scrollOffset = 0;
    private int totalContentHeight = 0;
    private int viewableHeight = 0;

     
    private boolean isDraggingScrollbar = false;
    private int initialClickY = 0;
    private int initialScrollY = 0;
    private static final int SCROLLBAR_WIDTH = 8;

    public GuiHexEditor(GuiScreen parent, PacketPersistence.SavedPacketData data, int index) {
        this.parentScreen = parent;
        this.packetData = data;
        this.packetIndex = index;
        this.currentDirection = data.direction != null ? data.direction : "SENT";
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

         
        ScaledResolution sr = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);

        if (savedWidth == 0 || savedHeight == 0) {
            this.panelWidth = Math.min(600, sr.getScaledWidth() - 40);
            this.panelHeight = Math.min(450, sr.getScaledHeight() - 60);
            this.panelX = (sr.getScaledWidth() - this.panelWidth) / 2;
            this.panelY = (sr.getScaledHeight() - this.panelHeight) / 2;
        } else {
            this.panelX = savedX;
            this.panelY = savedY;
            this.panelWidth = savedWidth;
            this.panelHeight = savedHeight;
        }

         
        if (this.panelX < 0) this.panelX = 0;
        if (this.panelY < 0) this.panelY = 0;
        if (this.panelX + this.panelWidth > sr.getScaledWidth()) this.panelX = sr.getScaledWidth() - this.panelWidth;
        if (this.panelY + this.panelHeight > sr.getScaledHeight()) this.panelY = sr.getScaledHeight() - this.panelHeight;

         
        if (segmentEditors.isEmpty()) {
            byte[] rawBytes = new byte[0];
            try {
                rawBytes = Base64.getDecoder().decode(packetData.rawData);
            } catch (Exception ignored) {}
            rebuildSegments(rawBytes);
        } else {
             
            updateLayout();
        }
    }

    private void updateLayout() {
        this.buttonList.clear();

        int padding = 6;
        int startX = panelX + 10;
        int innerW = panelWidth - 20;
        int btnHeight = 20;

         
        int footerHeight = (btnHeight * 2) + (padding * 3);
        int row1Y = panelY + panelHeight - footerHeight + padding;
        int row2Y = row1Y + btnHeight + padding;

         
        int btnW_Row1 = (innerW - padding) / 2;
        this.buttonList.add(new StyledButton(3, startX, row1Y, btnW_Row1, btnHeight, "Back"));
        this.buttonList.add(new StyledButton(2, startX + btnW_Row1 + padding, row1Y, btnW_Row1, btnHeight, "Dir: " + currentDirection));

         
        int btnW_Row2 = (innerW - padding) / 2;
        this.buttonList.add(new StyledButton(1, startX, row2Y, btnW_Row2, btnHeight, "Save"));
        this.buttonList.add(new StyledButton(0, startX + btnW_Row2 + padding, row2Y, btnW_Row2, btnHeight, "Send"));

         
        int topHeaderHeight = 30;
        this.viewableHeight = panelHeight - topHeaderHeight - footerHeight;

        updateEditorPositions();
    }

    private void rebuildSegments(byte[] rawBytes) {
        segmentEditors.clear();
        scrollOffset = 0;

         
         
         
        int contentWidth = panelWidth - 35;

        GuiTextArea area = new GuiTextArea(0, 0, contentWidth, 0);
        area.setText(HexUtils.bytesToHex(rawBytes));

         
        segmentEditors.add(new SegmentEditor("Raw Data (" + rawBytes.length + " bytes)", area));

        updateLayout();
    }

    private byte[] assembleBytes() throws IllegalArgumentException, java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (SegmentEditor seg : segmentEditors) {
             
            byte[] chunk = HexUtils.hexToBytes(seg.textArea.getText());
            baos.write(chunk);
        }
        return baos.toByteArray();
    }

    private void updateEditorPositions() {
        int contentWidth = panelWidth - 35;  
        int startX = panelX + 15;

        for (SegmentEditor seg : segmentEditors) {
             
            int h = Math.max(50, viewableHeight - 20);

            seg.textArea.xPos = startX;
            seg.textArea.width = contentWidth;
            seg.textArea.height = h;

             
             
             
            seg.textArea.setText(seg.textArea.getText());
        }
        recalcTotalHeight();
    }

    private void recalcTotalHeight() {
        totalContentHeight = 0;
        for (SegmentEditor seg : segmentEditors) {
            totalContentHeight += 14;  
            totalContentHeight += seg.textArea.height;
            totalContentHeight += 8;  
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 3) {  
            mc.displayGuiScreen(parentScreen);
        }
        else if (button.id == 2) {  
            currentDirection = currentDirection.equals("SENT") ? "RCVD" : "SENT";
            button.displayString = "Dir: " + currentDirection;
        }
        else if (button.id == 0 || button.id == 1) {  
            try {
                byte[] newBytes = assembleBytes();
                String newBase64 = Base64.getEncoder().encodeToString(newBytes);

                PacketPersistence.SavedPacketData newData = new PacketPersistence.SavedPacketData(
                        packetData.name,
                        packetData.className,
                        packetData.channel,
                        newBase64,
                        currentDirection
                );

                if (button.id == 0) {
                    processPacket(newData);
                } else {
                    if (packetIndex >= 0 && packetIndex < FavoritePacketManager.getFavorites().size()) {
                        FavoritePacketManager.getFavorites().set(packetIndex, newData);
                        FavoritePacketManager.save();
                        FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Saved", "Packet updated.", Notification.NotificationType.SUCCESS, 2000L));
                    } else {
                        FavoritePacketManager.add(newData);
                    }
                }
            } catch (IllegalArgumentException e) {
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Format Error", "Invalid HEX! Check characters.", Notification.NotificationType.ERROR, 3000L));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processPacket(PacketPersistence.SavedPacketData data) {
        Packet packet = PacketPersistence.reconstruct(data);
        if (packet == null) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Error", "Reconstruct failed", Notification.NotificationType.ERROR, 3000L));
            return;
        }
        if (packet instanceof FMLProxyPacket && "RCVD".equals(data.direction)) {
            try { ((FMLProxyPacket) packet).setTarget(Side.CLIENT); } catch (Exception ignored) {}
        }
        if ("RCVD".equals(data.direction)) {
            if (mc.getNetHandler() != null) {
                try {
                    ReflectionUtils.receivePacket(mc.getNetHandler().getNetworkManager(), packet);
                    FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Success", "Processed locally (RCVD)", Notification.NotificationType.SUCCESS, 2000L));
                } catch (Exception e) {
                    FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Error", "Process failed", Notification.NotificationType.ERROR, 3000L));
                }
            }
        } else {
            NetUtils.sendPacket(packet);
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Success", "Sent to server (SENT)", Notification.NotificationType.SUCCESS, 2000L));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        for (SegmentEditor seg : segmentEditors) {
            if (seg.textArea.isFocused()) {
                seg.textArea.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parentScreen);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0 || state == 1) {
            dragging = false;
            isResizing = false;
            isDraggingScrollbar = false;
        }
        for (SegmentEditor seg : segmentEditors) {
            seg.textArea.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (isDraggingScrollbar && clickedMouseButton == 0) {
            int maxScroll = totalContentHeight - viewableHeight;
            if (maxScroll <= 0) return;

            float ratio = (float)totalContentHeight / (float)viewableHeight;
            int deltaY = mouseY - initialClickY;
            int scrollDelta = (int) (deltaY * ratio * -1);

            int newScroll = initialScrollY + scrollDelta;
            if (newScroll > 0) newScroll = 0;
            if (newScroll < -maxScroll) newScroll = -maxScroll;

            scrollOffset = newScroll;
        }

        for (SegmentEditor seg : segmentEditors) {
            if (seg.textArea.isFocused()) {
                seg.textArea.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

         
        if (mouseButton == 0) {
             
            boolean onRight = mouseX >= this.panelX + this.panelWidth - resizeBorder && mouseX <= this.panelX + this.panelWidth + resizeBorder;
            boolean onBottom = mouseY >= this.panelY + this.panelHeight - resizeBorder && mouseY <= this.panelY + this.panelHeight + resizeBorder;

            if (onRight || onBottom) {
                isResizing = true;
                if (onRight && onBottom) resizeEdge = 3;
                else if (onRight) resizeEdge = 1;
                else resizeEdge = 2;
                return;
            }

             
            int top = panelY + 30;
            int sbX = panelX + panelWidth - SCROLLBAR_WIDTH;
            if (mouseX >= sbX && mouseX <= sbX + SCROLLBAR_WIDTH && mouseY >= top && mouseY <= top + viewableHeight) {
                isDraggingScrollbar = true;
                initialClickY = mouseY;
                initialScrollY = scrollOffset;
                return;
            }

             
            if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + 30) {
                dragging = true;
                dragOffsetX = mouseX - panelX;
                dragOffsetY = mouseY - panelY;
                return;
            }
        }

         
        int top = panelY + 30;
        int bottom = top + viewableHeight;

        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= top && mouseY <= bottom) {
            int relativeY = mouseY - top + scrollOffset;
            int currentY = 0;
            boolean anyFocused = false;

            for (SegmentEditor seg : segmentEditors) {
                int itemH = 14 + seg.textArea.height + 8;

                if (relativeY >= currentY && relativeY <= currentY + itemH) {
                     
                    seg.textArea.mouseClicked(mouseX, mouseY, mouseButton);
                    if (seg.textArea.isFocused()) anyFocused = true;
                } else {
                    seg.textArea.setFocused(false);
                }
                currentY += itemH;
            }

            if (!anyFocused) {
                for (SegmentEditor seg : segmentEditors) seg.textArea.setFocused(false);
            }
        } else {
            for (SegmentEditor seg : segmentEditors) seg.textArea.setFocused(false);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (totalContentHeight > viewableHeight) {
                scrollOffset -= (dWheel > 0 ? 1 : -1) * 20;
                if (scrollOffset < 0) scrollOffset = 0;
                int max = totalContentHeight - viewableHeight;
                if (scrollOffset > max) scrollOffset = max;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (isResizing) {
            int minW = 320;
            int minH = 240;
            if (resizeEdge == 1 || resizeEdge == 3) {
                this.panelWidth = Math.max(minW, mouseX - this.panelX);
            }
            if (resizeEdge == 2 || resizeEdge == 3) {
                this.panelHeight = Math.max(minH, mouseY - this.panelY);
            }
            updateLayout();
        } else if (dragging) {
            this.panelX = mouseX - dragOffsetX;
            this.panelY = mouseY - dragOffsetY;
            updateLayout();
        }

        this.drawDefaultBackground();

         
        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
         
        int border = Theme.BORDER.getRGB();
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, border);
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, border);
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, border);
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, border);

        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer regFont = FuctorizeClient.INSTANCE.fontManager.regular_18;

         
        String title = "Hex Editor: " + packetData.name;
        if (font.getStringWidth(title) > panelWidth - 30) title = font.trimStringToWidth(title, panelWidth - 40) + "...";
        font.drawString(title, panelX + 15, panelY + 10, Theme.ORANGE.getRGB());

         
        int top = panelY + 30;
        RenderUtils.startScissor(panelX + 5, top, panelWidth - 10, viewableHeight);

        int currentY = top - scrollOffset;

        for (SegmentEditor seg : segmentEditors) {
            int itemHeight = 14 + seg.textArea.height + 8;

            if (currentY + itemHeight > top && currentY < top + viewableHeight) {
                int labelColor = 0xFFAAAAAA;
                regFont.drawString(seg.label, seg.textArea.xPos, currentY, labelColor);

                seg.textArea.yPos = currentY + 12;
                seg.textArea.drawTextBox();
            }
            currentY += itemHeight;
        }

        RenderUtils.stopScissor();

         
        if (totalContentHeight > viewableHeight) {
            int sbX = panelX + panelWidth - 6;
            RenderUtils.drawRect(sbX, top, sbX + 4, top + viewableHeight, 0x55000000);
            float pct = (float)scrollOffset / (totalContentHeight - viewableHeight);
            int h = (int)((float)viewableHeight / totalContentHeight * viewableHeight);
            h = Math.max(20, h);
            int y = top + (int)(pct * (viewableHeight - h));

            int sbColor = isDraggingScrollbar ? Theme.ORANGE.brighter().getRGB() : Theme.ORANGE.getRGB();
            RenderUtils.drawRect(sbX, y, sbX + 4, y + h, sbColor);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        savedX = panelX;
        savedY = panelY;
        savedWidth = panelWidth;
        savedHeight = panelHeight;
    }

    private static class SegmentEditor {
        String label;
        GuiTextArea textArea;

        SegmentEditor(String label, GuiTextArea area) {
            this.label = label;
            this.textArea = area;
        }
    }
}