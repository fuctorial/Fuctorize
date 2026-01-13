// 30. C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\client\gui\sniffer\GuiBlacklistEditor.java
package ru.fuctorial.fuctorize.client.gui.sniffer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.impl.PacketSniffer;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import ru.fuctorial.fuctorize.utils.Lang;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiBlacklistEditor extends GuiScreen {

    private final GuiScreen parentScreen;
    private int x, y, panelWidth, panelHeight;
    private List<String> classEntries = new ArrayList<>();
    private List<String> channelEntries = new ArrayList<>();
    private List<ButtonRow> classRows = new ArrayList<>();
    private List<ButtonRow> channelRows = new ArrayList<>();
    private int scrollOffset = 0;
    private int totalContentHeight = 0;

    private enum RowType { CLASS, CHANNEL }

    public GuiBlacklistEditor(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        this.panelWidth = Math.min(400, this.width - 40);
        this.panelHeight = Math.min(350, this.height - 60);
        this.x = (this.width - this.panelWidth) / 2;
        this.y = (this.height - this.panelHeight) / 2;

        this.classEntries = new ArrayList<>(PacketSniffer.getBlacklistedClassNames());
        this.classEntries.sort(String::compareTo);
        this.channelEntries = new ArrayList<>(PacketSniffer.getBlacklistedFmlChannels());
        this.channelEntries.sort(String::compareTo);

        this.buttonList.clear();

        // Back button (ID 0)
        int btnWidth = Math.min(150, panelWidth - 20);
        this.buttonList.add(new StyledButton(0, x + (panelWidth - btnWidth) / 2, y + panelHeight - 30, btnWidth, 20, Lang.get("blacklist.button.back")));

        rebuildRows();
    }

    private void rebuildRows() {
        this.classRows.clear();
        this.channelRows.clear();
        int rowHeight = 15;
        int headerHeight = 12;

        for (String entry : classEntries) {
            classRows.add(new ButtonRow(entry, x + 10, 0, panelWidth - 20, rowHeight, RowType.CLASS));
        }
        for (String entry : channelEntries) {
            channelRows.add(new ButtonRow(entry, x + 10, 0, panelWidth - 20, rowHeight, RowType.CHANNEL));
        }

        totalContentHeight = 0;
        if (!classRows.isEmpty()) totalContentHeight += classRows.size() * rowHeight + headerHeight;
        if (!channelRows.isEmpty()) totalContentHeight += channelRows.size() * rowHeight + headerHeight;
        totalContentHeight += rowHeight; // for add button
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) this.mc.displayGuiScreen(parentScreen);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // FIX: Call super first to handle button clicks (Back button)
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Check if we clicked a button in super.mouseClicked. If so, return immediately.
        // (Since we cleared the button list and added only one, we check if any button was pressed)
        for(Object b : this.buttonList) {
            if (b instanceof GuiButton && ((GuiButton)b).mousePressed(mc, mouseX, mouseY)) {
                return;
            }
        }

        int topBounds = y + 28;
        int viewableHeight = panelHeight - 65;
        // If click is outside the list content area, ignore
        if (mouseX < x + 5 || mouseX > x + panelWidth - 5 || mouseY < topBounds || mouseY > topBounds + viewableHeight) return;

        int currentY = topBounds - scrollOffset;

        if (!classRows.isEmpty()) {
            currentY += 12;
            for (ButtonRow row : classRows) {
                if (mouseButton == 0 && row.isMinusClicked(mouseX, mouseY, currentY)) {
                    PacketSniffer.removeBlacklistedClassName(row.key);
                    this.initGui();
                    return;
                }
                currentY += row.height;
            }
        }

        if (!channelRows.isEmpty()) {
            currentY += 12;
            for (ButtonRow row : channelRows) {
                if (mouseButton == 0 && row.isMinusClicked(mouseX, mouseY, currentY)) {
                    PacketSniffer.removeBlacklistedFmlChannel(row.key);
                    this.initGui();
                    return;
                }
                currentY += row.height;
            }
        }

        if (isMouseOver(mouseX, mouseY, x + 10, currentY, panelWidth - 20, 15) && mouseButton == 0) {
            GuiPacketSniffer snifferGui = new GuiPacketSniffer(this);
            snifferGui.setSelectionMode(this);
            this.mc.displayGuiScreen(snifferGui);
        }
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void onGuiClosed() { PacketSniffer.saveBlacklistConfig(); }
    @Override
    protected void keyTyped(char typedChar, int keyCode) { if (keyCode == Keyboard.KEY_ESCAPE) this.mc.displayGuiScreen(parentScreen); }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            int viewableHeight = panelHeight - 65;
            if (totalContentHeight > viewableHeight) {
                scrollOffset -= (dWheel > 0 ? 1 : -1) * 15;
                if (scrollOffset < 0) scrollOffset = 0;
                int maxScroll = Math.max(0, totalContentHeight - viewableHeight);
                if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            } else {
                scrollOffset = 0;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            this.drawDefaultBackground();
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        this.drawDefaultBackground();
        RenderUtils.drawRect(x, y, x + panelWidth, y + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(x - 1, y - 1, x + panelWidth + 1, y, Theme.BORDER.getRGB());
        RenderUtils.drawRect(x - 1, y - 1, x, y + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(x + panelWidth, y - 1, x + panelWidth + 1, y + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(x - 1, y + panelHeight, x + panelWidth + 1, y + panelHeight + 1, Theme.BORDER.getRGB());
        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer regularFont = FuctorizeClient.INSTANCE.fontManager.regular_18;
        String title = Lang.get("blacklist.title");
        titleFont.drawString(title, x + (panelWidth - titleFont.getStringWidth(title)) / 2f, y + 8, -1);

        int topBounds = y + 28;
        int bottomBounds = y + panelHeight - 35;
        RenderUtils.startScissor(x + 5, topBounds, panelWidth - 10, bottomBounds - topBounds);

        int currentY = topBounds - scrollOffset;

        if (!classRows.isEmpty()) {
            regularFont.drawString(Lang.get("blacklist.header.classes"), x + 15, currentY, Theme.TEXT_GRAY.getRGB());
            currentY += 12;
            for (ButtonRow row : classRows) {
                row.draw(mouseX, mouseY, currentY);
                currentY += row.height;
            }
        }

        if (!channelRows.isEmpty()) {
            regularFont.drawString(Lang.get("blacklist.header.channels"), x + 15, currentY, Theme.TEXT_GRAY.getRGB());
            currentY += 12;
            for (ButtonRow row : channelRows) {
                row.draw(mouseX, mouseY, currentY);
                currentY += row.height;
            }
        }

        boolean addHover = isMouseOver(mouseX, mouseY, x + 10, currentY, panelWidth - 20, 15);
        RenderUtils.drawRect(x + 10, currentY, x + panelWidth - 10, currentY + 15, addHover ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());
        String addText = Lang.get("blacklist.add_button");
        regularFont.drawString(addText, x + 15, currentY + (15 - regularFont.getHeight()) / 2f + 1, -1);

        RenderUtils.stopScissor();
        drawScrollBar(topBounds, bottomBounds - topBounds);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScrollBar(int topY, int viewableHeight) {
        int maxScroll = Math.max(0, totalContentHeight - viewableHeight);
        if (maxScroll > 0) {
            int scrollbarX = x + panelWidth - 8;
            RenderUtils.drawRect(scrollbarX, topY, scrollbarX + 4, topY + viewableHeight, 0x55000000);
            float scrollPercent = (float)scrollOffset / maxScroll;
            int handleHeight = (int) ((float) viewableHeight / totalContentHeight * viewableHeight);
            handleHeight = Math.max(handleHeight, 20);
            int handleY = topY + (int) (scrollPercent * (viewableHeight - handleHeight));
            RenderUtils.drawRect(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, Theme.ORANGE.getRGB());
        }
    }

    private class ButtonRow {
        final String key, displayName;
        int x, width, height;

        ButtonRow(String key, int x, int y, int w, int h, RowType type) {
            this.key = key;
            if (type == RowType.CLASS && key.contains(".")) {
                this.displayName = key.substring(key.lastIndexOf('.') + 1).replace('$', '.');
            } else {
                this.displayName = key;
            }
            this.x = x;
            this.width = w;
            this.height = h;
        }

        void draw(int mouseX, int mouseY, int currentY) {
            CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
            boolean isRowHovered = isMouseOver(mouseX, mouseY, x, currentY, width, height);
            RenderUtils.drawRect(x, currentY, x + width, currentY + height, isRowHovered ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());
            font.drawString(displayName, x + 5, currentY + (height - font.getHeight()) / 2f + 1, Theme.TEXT_WHITE.getRGB());
            int minusX = x + width - 20;
            boolean minusHovered = isMouseOver(mouseX, mouseY, minusX, currentY, 20, height);
            RenderUtils.drawRect(minusX, currentY, minusX + 20, currentY + height, minusHovered ? new Color(255, 80, 80, 150).getRGB() : new Color(255, 80, 80, 80).getRGB());
            font.drawString("[-]", minusX + 6, currentY + (height - font.getHeight()) / 2f + 1, -1);
        }

        boolean isMinusClicked(int mouseX, int mouseY, int currentY) {
            int minusX = x + width - 20;
            return isMouseOver(mouseX, mouseY, minusX, currentY, 20, height);
        }
    }
}