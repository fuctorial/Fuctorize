 
package ru.fuctorial.fuctorize.client.gui.sniffer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.GuiTextInput;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.manager.FavoriteScreenManager;
import ru.fuctorial.fuctorize.manager.ScreenBlacklistManager;
import ru.fuctorial.fuctorize.handlers.EventHandler;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import ru.fuctorial.fuctorize.utils.ScreenContext;
import ru.fuctorial.fuctorize.utils.ScreenContextResult;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;

public class GuiScreenHistory extends GuiScreen {

    private int panelX, panelY, panelWidth, panelHeight;
    private final List<GuiScreen> displayedHistory = new ArrayList<>();
    private final List<String> historyLabels = new ArrayList<>();

     
    private int scrollOffset = 0;
    private int totalContentHeight = 0;
    private int viewableContentHeight = 0;

    private boolean selectionMode = false;
    private GuiScreen selectionParent;
    private boolean selectingForFavorites = false;

    public void setSelectionMode(GuiScreen parent, boolean forFavorites) {
        this.selectionMode = true;
        this.selectionParent = parent;
        this.selectingForFavorites = forFavorites;
    }

    @Override
    public void initGui() {
        this.panelWidth = 320;
         
        this.panelHeight = Math.min(400, this.height - 40);

        this.displayedHistory.clear();
        this.historyLabels.clear();

        List<GuiScreen> liveScreens = EventHandler.getScreenHistory(true);
        for(GuiScreen screen : liveScreens) {
            this.displayedHistory.add(screen);
            this.historyLabels.add(getScreenLabel(screen));
        }

        this.totalContentHeight = displayedHistory.size() * 22;
        this.viewableContentHeight = panelHeight - 40 - 30;  

        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.buttonList.clear();

         
        if (!selectionMode) {
            this.buttonList.add(new StyledButton(997, panelX + 10, panelY + panelHeight - 25, 60, 20, "Очистить"));
            this.buttonList.add(new StyledButton(998, panelX + 75, panelY + panelHeight - 25, 110, 20, "Ред. Blacklist"));
            this.buttonList.add(new StyledButton(999, panelX + panelWidth - 110, panelY + panelHeight - 25, 100, 20, "Закрыть"));
        } else {
            this.buttonList.add(new StyledButton(999, panelX + (panelWidth / 2) - 50, panelY + panelHeight - 25, 100, 20, "Отмена"));
        }
    }

    private String getScreenLabel(GuiScreen screen) {
        String simpleName = screen.getClass().getSimpleName();
        ScreenContextResult contextResult = ScreenContext.getScreenContext(screen);
        if (contextResult != null && contextResult.human != null && !contextResult.human.isEmpty()) {
            return simpleName + " " + contextResult.human;
        }
        return simpleName;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
         
        if (button.id >= 997) {
            handleFooterAction(button.id);
        }
         
    }

    private void handleFooterAction(int id) {
        if (selectionMode) {
            if (id == 999) this.mc.displayGuiScreen(this.selectionParent);
        } else {
            if (id == 999) this.mc.displayGuiScreen(null);
            else if (id == 998) this.mc.displayGuiScreen(new GuiScreenHistoryBlacklist(this));
            else if (id == 997) {
                EventHandler.clearHistory();
                EventHandler.pushScreenSnapshot(this);
                this.initGui();
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (totalContentHeight > viewableContentHeight) {
                scrollOffset += (dWheel > 0) ? -15 : 15;  
                if (scrollOffset > 0) scrollOffset = 0;

                int maxScroll = totalContentHeight - viewableContentHeight;
                if (scrollOffset < -maxScroll) {
                    scrollOffset = -maxScroll;
                }
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int buttonId) {
         
        super.mouseClicked(mouseX, mouseY, buttonId);  

        int adjustedMouseY = mouseY - (panelY + 30) - scrollOffset;

        int topBound = panelY + 30;
        int bottomBound = panelY + panelHeight - 30;

         
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= topBound && mouseY <= bottomBound) {
            int buttonIndex = adjustedMouseY / 22;

            if (buttonIndex >= 0 && buttonIndex < displayedHistory.size()) {
                 
                if (buttonId == 0 && !selectionMode) {
                    int starX = panelX + 10;
                    if (mouseX >= starX && mouseX <= starX + 18) {
                        handleStarClick(buttonIndex);
                        return;  
                    }
                }

                 
                handleHistoryButtonClick(buttonIndex);
            }
        }
    }

    private void handleStarClick(int index) {
        GuiScreen screen = displayedHistory.get(index);
        if (FavoriteScreenManager.isFavorite(screen)) {
            FavoriteScreenManager.removeFavoriteForScreen(screen);
        } else {
            String defaultName = historyLabels.get(index);
            mc.displayGuiScreen(new GuiTextInput(this, "Название для избранного", defaultName.trim(), (newName) -> {
                FavoriteScreenManager.addFavorite(newName, screen);
            }));
        }
         
    }

    private void handleHistoryButtonClick(int index) {
        if (selectionMode) {
            GuiScreen selectedScreen = this.displayedHistory.get(index);
            if (selectingForFavorites) {
                String defaultName = historyLabels.get(index);
                mc.displayGuiScreen(new GuiTextInput(this.selectionParent, "Название для избранного", defaultName.trim(), (newName) -> {
                    FavoriteScreenManager.addFavorite(newName, selectedScreen);
                }));
            } else {
                ScreenBlacklistManager.add(selectedScreen);
                this.mc.displayGuiScreen(this.selectionParent);
            }
        } else {
            GuiScreen screenToRestore = this.displayedHistory.get(index);
            EventHandler.isRestoringScreen = true;
            this.mc.displayGuiScreen(screenToRestore);
            EventHandler.isRestoringScreen = false;
        }
    }


    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(selectionMode ? selectionParent : null);
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
        String title = "История Экранов";
        if (selectionMode) {
            title = selectingForFavorites ? "Выберите для избранного" : "Выберите для Blacklist";
        }
        titleFont.drawString(title, panelX + (panelWidth - titleFont.getStringWidth(title)) / 2f, panelY + 8, -1);

         
        int topBound = panelY + 30;
        RenderUtils.startScissor(panelX, topBound, panelWidth, viewableContentHeight);

        GL11.glPushMatrix();
        GL11.glTranslatef(0, scrollOffset, 0);

        CustomFontRenderer iconFont = FuctorizeClient.INSTANCE.fontManager.regular_18;
        int buttonY = topBound;

        for (int i = 0; i < displayedHistory.size(); i++) {
            GuiScreen screen = displayedHistory.get(i);
            String buttonText = historyLabels.get(i);

            int buttonX = selectionMode ? panelX + 10 : panelX + 30;
            int buttonWidth = selectionMode ? panelWidth - 20 : panelWidth - 40;

            boolean isHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                    mouseY >= buttonY + scrollOffset && mouseY <= buttonY + scrollOffset + 20;

            RenderUtils.drawRect(buttonX, buttonY, buttonX + buttonWidth, buttonY + 20, isHovered ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());
            iconFont.drawString(buttonText, buttonX + 5, buttonY + (20 - iconFont.getHeight()) / 2f, -1);

            if (!selectionMode) {
                boolean isFavorite = FavoriteScreenManager.isFavorite(screen);
                boolean isStarHovered = mouseX >= panelX + 10 && mouseX <= panelX + 28 &&
                        mouseY >= buttonY + scrollOffset && mouseY <= buttonY + scrollOffset + 20;

                int starColor = isFavorite ? Color.YELLOW.getRGB() : (isStarHovered ? Color.WHITE.getRGB() : Color.GRAY.brighter().getRGB());
                if (isStarHovered && isFavorite) starColor = Color.RED.getRGB();
                iconFont.drawString(isFavorite ? "★" : "☆", panelX + 15, buttonY + 6, starColor);
            }
            buttonY += 22;
        }

        GL11.glPopMatrix();
        RenderUtils.stopScissor();

        drawScrollBar(topBound);

         
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScrollBar(int topY) {
        if (totalContentHeight > viewableContentHeight) {
            int scrollbarX = panelX + panelWidth - 8;
            RenderUtils.drawRect(scrollbarX, topY, scrollbarX + 4, topY + viewableContentHeight, 0x55000000);

            float scrollPercent = (float)-scrollOffset / (totalContentHeight - viewableContentHeight);
            int handleHeight = (int) ((float) viewableContentHeight / totalContentHeight * viewableContentHeight);
            handleHeight = Math.max(handleHeight, 20);  

            int handleY = topY + (int) (scrollPercent * (viewableContentHeight - handleHeight));
            RenderUtils.drawRect(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, Theme.ORANGE.getRGB());
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}