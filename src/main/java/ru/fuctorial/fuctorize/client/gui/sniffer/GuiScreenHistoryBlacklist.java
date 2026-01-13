// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\gui\sniffer\GuiScreenHistoryBlacklist.java
package ru.fuctorial.fuctorize.client.gui.sniffer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.manager.ScreenBlacklistManager;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class GuiScreenHistoryBlacklist extends GuiScreen {
    private final GuiScreen parentScreen;
    private int panelX, panelY, panelWidth, panelHeight;
    private final List<String> blacklistEntries = new ArrayList<>();

    public GuiScreenHistoryBlacklist(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        this.panelWidth = 400;
        this.blacklistEntries.clear();
        this.blacklistEntries.addAll(ScreenBlacklistManager.getBlacklistedClassNames());
        this.blacklistEntries.sort(String::compareTo);

        this.panelHeight = 40 + (blacklistEntries.size() * 17) + 22 + 30;
        this.panelHeight = Math.min(panelHeight, this.height - 40);

        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.buttonList.clear();
        this.buttonList.add(new StyledButton(999, panelX + (panelWidth / 2) - 75, panelY + panelHeight - 25, 150, 20, "Назад к истории"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 999) this.mc.displayGuiScreen(parentScreen);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        int currentY = panelY + 30;
        int rowHeight = 17;

        for (String entry : blacklistEntries) {
            if (mouseButton == 0) {
                int minusX = panelX + panelWidth - 30;
                if (mouseX >= minusX && mouseX <= minusX + 20 && mouseY >= currentY && mouseY <= currentY + rowHeight - 2) {
                    ScreenBlacklistManager.remove(entry);
                    this.initGui();
                    return;
                }
            }
            currentY += rowHeight;
        }

        if (mouseButton == 0 && mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10 && mouseY >= currentY && mouseY <= currentY + rowHeight) {
            GuiScreenHistory historyGui = new GuiScreenHistory();
            historyGui.setSelectionMode(this, false); // false indicates selection for blacklist
            this.mc.displayGuiScreen(historyGui);
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
        CustomFontRenderer textFont = FuctorizeClient.INSTANCE.fontManager.regular_18;

        String title = "Черный список экранов";
        titleFont.drawString(title, panelX + (panelWidth - titleFont.getStringWidth(title)) / 2f, panelY + 8, -1);

        int currentY = panelY + 30;
        int rowHeight = 17;

        for (String entry : blacklistEntries) {
            String simpleName = entry.substring(entry.lastIndexOf('.') + 1);
            textFont.drawString(simpleName, panelX + 10, currentY + 4, -1);

            int minusX = panelX + panelWidth - 30;
            boolean minusHovered = mouseX >= minusX && mouseX <= minusX + 20 && mouseY >= currentY && mouseY <= currentY + rowHeight - 2;
            RenderUtils.drawRect(minusX, currentY + 2, minusX + 20, currentY + rowHeight - 2, minusHovered ? new Color(255, 80, 80, 150).getRGB() : new Color(255, 80, 80, 80).getRGB());
            textFont.drawString("[-]", minusX + 6, currentY + 4, -1);

            currentY += rowHeight;
        }

        boolean addHover = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10 && mouseY >= currentY && mouseY <= currentY + rowHeight;
        RenderUtils.drawRect(panelX + 10, currentY, panelX + panelWidth - 10, currentY + rowHeight, addHover ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());
        String addText = "[+] Добавить экран в черный список...";
        textFont.drawString(addText, panelX + 15, currentY + 4, -1);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}