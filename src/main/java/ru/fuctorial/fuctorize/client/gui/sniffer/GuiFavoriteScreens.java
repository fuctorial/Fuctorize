 
package ru.fuctorial.fuctorize.client.gui.sniffer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.manager.FavoriteScreenManager;
import ru.fuctorial.fuctorize.handlers.EventHandler;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.*;

public class GuiFavoriteScreens extends GuiScreen {
    private int panelX, panelY, panelWidth, panelHeight;
    private final List<Map.Entry<String, String>> favoritesList = new ArrayList<>();
    private boolean inDeleteMode = false;

    @Override
    public void initGui() {
        this.panelWidth = 300;
        this.favoritesList.clear();
        this.favoritesList.addAll(FavoriteScreenManager.getFavorites().entrySet());
        this.favoritesList.sort(Map.Entry.comparingByKey());

        this.panelHeight = 40 + (favoritesList.size() * 22) + 22 + 30;
        this.panelHeight = Math.min(this.panelHeight, this.height - 40);

        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.buttonList.clear();

        int buttonY = panelY + 30;
        int buttonIndex = 0;
        for (Map.Entry<String, String> entry : favoritesList) {
            this.buttonList.add(new StyledButton(buttonIndex, panelX + 10, buttonY, panelWidth - 20, 20, entry.getKey()));
            buttonY += 22;
            buttonIndex++;
        }

        this.buttonList.add(new StyledButton(1000, panelX + 10, panelY + panelHeight - 25, 110, 20, "[+] Добавить"));
        String deleteButtonText = inDeleteMode ? "Отмена" : "[-] Удалить";
        this.buttonList.add(new StyledButton(1001, panelX + 125, panelY + panelHeight - 25, 60, 20, deleteButtonText));
        this.buttonList.add(new StyledButton(999, panelX + panelWidth - 90, panelY + panelHeight - 25, 80, 20, "Закрыть"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 999) {
            this.mc.displayGuiScreen(null);
        } else if (button.id == 1000) {
            GuiScreenHistory historyGui = new GuiScreenHistory();
            historyGui.setSelectionMode(this, true);
            this.mc.displayGuiScreen(historyGui);
        } else if (button.id == 1001) {
            inDeleteMode = !inDeleteMode;
            this.initGui();
        } else if (button.id >= 0 && button.id < favoritesList.size()) {
            Map.Entry<String, String> selectedEntry = favoritesList.get(button.id);
            if (inDeleteMode) {
                FavoriteScreenManager.removeFavorite(selectedEntry.getKey());
                inDeleteMode = false;
                this.initGui();
            } else {
                openFavorite(selectedEntry);
            }
        }
    }

    private void openFavorite(Map.Entry<String, String> favoriteEntry) {
        String customName = favoriteEntry.getKey();
        String uniqueKey = favoriteEntry.getValue();

         
        GuiScreen screenToOpen = null;

         
        screenToOpen = FavoriteScreenManager.findBestLiveInstance(uniqueKey);

         
        if (screenToOpen == null) {
            String className = FavoriteScreenManager.classNameFromUnique(uniqueKey);
            screenToOpen = FavoriteScreenManager.instantiateScreen(className);
        }

        if (screenToOpen != null) {
            EventHandler.isRestoringScreen = true;
            this.mc.displayGuiScreen(screenToOpen);  
            EventHandler.isRestoringScreen = false;
        } else {
            FuctorizeClient.INSTANCE.notificationManager.show(new ru.fuctorial.fuctorize.client.hud.Notification(
                    "Не удалось открыть",
                    "Этот экран требует контекста и будет удален.",
                    ru.fuctorial.fuctorize.client.hud.Notification.NotificationType.ERROR, 4000L
            ));
            FavoriteScreenManager.removeFavorite(customName);
            this.initGui();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (FuctorizeClient.INSTANCE == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        String title = inDeleteMode ? "Нажмите для удаления" : "Избранные Экраны";
        int titleColor = inDeleteMode ? Color.RED.getRGB() : -1;
        titleFont.drawString(title, panelX + (panelWidth - titleFont.getStringWidth(title)) / 2f, panelY + 8, titleColor);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}