package ru.fuctorial.fuctorize.client.gui.misc;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.manager.ModeratorManager;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GuiModeratorEditor extends GuiScreen {
    private final GuiScreen parent;
    private int panelX, panelY, panelWidth, panelHeight;
    private GuiTextField inputField;

     
    private int scrollOffset = 0;
    private int totalContentHeight = 0;
    private int viewableHeight = 0;

    private final List<String> cachedList = new ArrayList<>();

    public GuiModeratorEditor(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        this.panelWidth = 320;
        this.panelHeight = Math.min(400, this.height - 40);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

         
        int fieldY = panelY + 35;
         
        int inputWidth = panelWidth - 20 - 65;

        this.inputField = new GuiTextField(panelX + 10, fieldY, inputWidth, 20, false);
        this.inputField.setFocused(true);
        this.inputField.setMaxStringLength(32);

        this.buttonList.clear();

         
        this.buttonList.add(new StyledButton(1, panelX + 10 + inputWidth + 5, fieldY, 60, 20, "Add"));

         
        this.buttonList.add(new StyledButton(0, panelX + (panelWidth - 100) / 2, panelY + panelHeight - 30, 100, 20, Lang.get("generic.button.back")));

        refreshList();
    }

    private void refreshList() {
        cachedList.clear();
        cachedList.addAll(ModeratorManager.getModerators());
        Collections.sort(cachedList);
         
        totalContentHeight = cachedList.size() * 18;
         
        viewableHeight = panelHeight - 35 - 25 - 35;
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 0) {
            mc.displayGuiScreen(parent);
        }
        if (b.id == 1) {
            addModerator();
        }
    }

    private void addModerator() {
        String name = inputField.getText().trim();
        if (!name.isEmpty()) {
            ModeratorManager.add(name);
            inputField.setText("");
            refreshList();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (!FuctorizeClient.INSTANCE.fontManager.isReady()) return;

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

         
        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        drawBorder(panelX, panelY, panelWidth, panelHeight, Theme.BORDER.getRGB());

         
        String title = Lang.get("moderator.editor.title");
        titleFont.drawString(title, panelX + (panelWidth - titleFont.getStringWidth(title)) / 2f, panelY + 10, Theme.ORANGE.getRGB());

         
        inputField.drawTextBox();

         
        int listTop = panelY + 65;
        int listBottom = panelY + panelHeight - 35;

        RenderUtils.startScissor(panelX + 5, listTop, panelWidth - 10, viewableHeight);

        int currentY = listTop - scrollOffset;

        for (String mod : cachedList) {
            if (currentY + 18 > listTop && currentY < listBottom) {
                 
                boolean hover = isMouseOver(mouseX, mouseY, panelX + 10, currentY, panelWidth - 20, 18);
                int color = hover ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB();
                RenderUtils.drawRect(panelX + 10, currentY, panelX + panelWidth - 10, currentY + 18, color);

                 
                font.drawString(mod, panelX + 15, currentY + 5, Theme.TEXT_WHITE.getRGB());

                 
                int delX = panelX + panelWidth - 30;
                boolean delHover = isMouseOver(mouseX, mouseY, delX, currentY + 2, 16, 14);
                int delColor = delHover ? new Color(200, 50, 50, 200).getRGB() : new Color(200, 50, 50, 100).getRGB();

                RenderUtils.drawRect(delX, currentY + 2, delX + 16, currentY + 16, delColor);
                font.drawString("-", delX + 5, currentY + 4, -1);
            }
            currentY += 18;
        }

        RenderUtils.stopScissor();

         
        if (totalContentHeight > viewableHeight) {
            int scrollbarX = panelX + panelWidth - 6;
            RenderUtils.drawRect(scrollbarX, listTop, scrollbarX + 4, listBottom, 0x55000000);

            float scrollPercent = (float) scrollOffset / (float) (totalContentHeight - viewableHeight);
            int handleHeight = (int) ((float) viewableHeight / totalContentHeight * viewableHeight);
            handleHeight = Math.max(handleHeight, 20);

            int handleY = listTop + (int) (scrollPercent * (viewableHeight - handleHeight));
            RenderUtils.drawRect(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, Theme.ORANGE.getRGB());
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawBorder(int x, int y, int w, int h, int color) {
        RenderUtils.drawRect(x - 1, y - 1, x + w + 1, y, color);
        RenderUtils.drawRect(x - 1, y, x, y + h, color);
        RenderUtils.drawRect(x + w, y, x + w + 1, y + h, color);
        RenderUtils.drawRect(x - 1, y + h, x + w + 1, y + h + 1, color);
    }

    @Override
    protected void keyTyped(char c, int k) {
        if (inputField.isFocused()) {
            inputField.textboxKeyTyped(c, k);
            if (k == Keyboard.KEY_RETURN) {
                addModerator();
            }
        }
        if (k == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        inputField.mouseClicked(mouseX, mouseY, button);

        int listTop = panelY + 65;
        int listBottom = panelY + panelHeight - 35;

         
        if (mouseX >= panelX + 5 && mouseX <= panelX + panelWidth - 5 && mouseY >= listTop && mouseY <= listBottom) {
            int relativeY = mouseY - listTop + scrollOffset;
            int idx = relativeY / 18;

            if (idx >= 0 && idx < cachedList.size()) {
                 
                int currentY = listTop + (idx * 18) - scrollOffset;
                int delX = panelX + panelWidth - 30;

                if (isMouseOver(mouseX, mouseY, delX, currentY + 2, 16, 14)) {
                    String toRemove = cachedList.get(idx);
                    ModeratorManager.remove(toRemove);
                    refreshList();
                     
                    if (scrollOffset > Math.max(0, totalContentHeight - viewableHeight)) {
                        scrollOffset = Math.max(0, totalContentHeight - viewableHeight);
                    }
                }
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (totalContentHeight > viewableHeight) {
                scrollOffset -= (dWheel > 0 ? 1 : -1) * 18;
                if (scrollOffset < 0) scrollOffset = 0;
                int maxScroll = totalContentHeight - viewableHeight;
                if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            }
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private boolean isMouseOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}