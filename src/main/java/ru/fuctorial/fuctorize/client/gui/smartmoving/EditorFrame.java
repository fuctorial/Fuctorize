// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\gui\smartmoving\EditorFrame.java
package ru.fuctorial.fuctorize.client.gui.smartmoving;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.clickgui.components.CategoryComponent;
import ru.fuctorial.fuctorize.client.gui.clickgui.components.Component;
import ru.fuctorial.fuctorize.client.gui.clickgui.components.ModuleComponent;
import ru.fuctorial.fuctorize.client.gui.smartmoving.components.SmartMovingPropertyComponent;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

public class EditorFrame extends AbstractFrame {

    private int categoryScrollY = 0;
    private int totalCategoryHeight = 0;

    public EditorFrame(String title, int x, int y, int width, int height) {
        super(title, x, y, width, height);
    }

    @Override
    public void setup() {
        // Setup is handled dynamically by the parent GuiScreen
    }


    @Override
    public void drawScreen(int mouseX, int mouseY) {
        if (isDragging) {
            x = mouseX - dragX;
            y = mouseY - dragY;
        }

        Gui.drawRect(x, y, x + width, y + height, Theme.CATEGORY_BG.getRGB());
        Gui.drawRect(x + categoryPanelWidth + 1, y + titleBarHeight, x + width, y + height, Theme.MAIN_BG.getRGB());
        float titleY = this.y + (titleBarHeight / 2f) - (FuctorizeClient.INSTANCE.fontManager.bold_22.getHeight() / 2f);
        FuctorizeClient.INSTANCE.fontManager.bold_22.drawString(this.title, x + 5, titleY + 1, Theme.TEXT_WHITE.getRGB());

        drawCategoriesWithScroll(mouseX, mouseY);
        drawCategoryScrollbar();

        Gui.drawRect(x + categoryPanelWidth, y + titleBarHeight, x + categoryPanelWidth + 1, y + height, Theme.DIVIDER.getRGB());

        GL11.glPushMatrix();
        setupContentScissorBox(); // This also enables scissor test
        GL11.glTranslatef(0, scrollY, 0);

        int currentY = titleBarHeight + 4;
        for (Component component : components) {
            component.y = currentY;
            component.drawComponent(mouseX, mouseY - scrollY);
            currentY += component.height + 2;
        }

        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        Gui.drawRect(x - 1, y - 1, x + width + 1, y, Theme.BORDER.getRGB());
        Gui.drawRect(x - 1, y, x, y + height + 1, Theme.BORDER.getRGB());
        Gui.drawRect(x + width, y, x + width + 1, y + height + 1, Theme.BORDER.getRGB());
        Gui.drawRect(x - 1, y + height, x + width + 1, y + height + 1, Theme.BORDER.getRGB());
    }
    @Override
    public boolean isAnyTextFieldFocused() {
        // The PlayerUtils.isAnyTextFieldFocused() will handle the GuiTextFields
        // inside SmartMovingPropertyComponent via reflection, so we can return false here.
        return false;
    }

    private void drawCategoriesWithScroll(int mouseX, int mouseY) {
        RenderUtils.startScissor(x, y + titleBarHeight, categoryPanelWidth, height - titleBarHeight);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, categoryScrollY, 0);

        int currentCatY = titleBarHeight;
        for (CategoryComponent c : categoryButtons) {
            c.y = currentCatY;
            c.drawComponent(mouseX, mouseY - categoryScrollY);
            currentCatY += c.height;
        }

        GL11.glPopMatrix();
        RenderUtils.stopScissor();
    }

    private void drawCategoryScrollbar() {
        int viewableHeight = height - titleBarHeight;
        if (totalCategoryHeight > viewableHeight) {
            int scrollbarX = x + categoryPanelWidth - 6;
            int scrollbarY = y + titleBarHeight;
            RenderUtils.drawRect(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + viewableHeight, 0x55000000);
            float scrollPercent = (float)-categoryScrollY / (totalCategoryHeight - viewableHeight);
            int handleHeight = (int) ((float)viewableHeight / totalCategoryHeight * viewableHeight);
            handleHeight = Math.max(handleHeight, 20);
            int handleY = scrollbarY + (int)(scrollPercent * (viewableHeight - handleHeight));
            RenderUtils.drawRect(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, Theme.ORANGE.getRGB());
        }
    }

    public void handleCategoryScroll(int direction) {
        int viewableHeight = height - titleBarHeight;
        if (totalCategoryHeight > viewableHeight) {
            int scrollAmount = 15;
            if (direction > 0) categoryScrollY += scrollAmount;
            else categoryScrollY -= scrollAmount;

            if (categoryScrollY > 0) categoryScrollY = 0;
            int maxScroll = totalCategoryHeight - viewableHeight;
            if (categoryScrollY < -maxScroll) categoryScrollY = -maxScroll;
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isMouseOverTitle(mouseX, mouseY) && mouseButton == 0) {
            isDragging = true;
            dragX = mouseX - x;
            dragY = mouseY - y;
            // Если начали тащить окно, тоже сбрасываем фокусы
            for (Component c : components) {
                if (c instanceof SmartMovingPropertyComponent) {
                    ((SmartMovingPropertyComponent) c).clearFocus();
                }
            }
            return;
        }

        if (mouseX > x + categoryPanelWidth) {
            int adjustedMouseY = mouseY - scrollY;

            // --- ФИКС: Сначала сбрасываем фокус у ВСЕХ полей ---
            // Это гарантирует, что если мы кликнули в пустоту или по другому полю, старое погаснет.
            for (Component c : components) {
                if (c instanceof SmartMovingPropertyComponent) {
                    ((SmartMovingPropertyComponent) c).clearFocus();
                }
            }

            // --- Теперь обрабатываем клик ---
            for (Component c : components) {
                if (c.isMouseOver(mouseX, adjustedMouseY)) {
                    // Компонент сам включит фокус обратно внутри своего mouseClicked,
                    // если клик пришелся по его текстовому полю (благодаря фиксу №1)
                    c.mouseClicked(mouseX, adjustedMouseY, mouseButton);
                    break;
                }
            }
        }
    }


    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        isDragging = false;
        int adjustedMouseY = mouseY - scrollY;
        for (Component c : components) {
            c.mouseReleased(mouseX, adjustedMouseY, state);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        for (Component c : components) {
            if (c instanceof SmartMovingPropertyComponent) {
                ((SmartMovingPropertyComponent) c).keyTyped(typedChar, keyCode);
            }
        }
    }

    @Override
    public void mouseScrolled(int direction) {
        int scrollAmount = 15;
        if (direction > 0) scrollY += scrollAmount;
        else scrollY -= scrollAmount;
        int contentHeight = height - titleBarHeight;
        int totalComponentHeight = getTotalContentHeight();
        if (scrollY > 0) scrollY = 0;
        int maxScroll = totalComponentHeight - contentHeight + 8;
        if (maxScroll < 0) maxScroll = 0;
        if (scrollY < -maxScroll) scrollY = -maxScroll;
    }

    @Override
    public void setCurrentCategory(Object category) {
        this.currentCategory = category;
    }

    @Override
    public ModuleComponent getBindingComponent() { return null; }

    @Override
    public void setBindingComponent(ModuleComponent component) { }

    private int getTotalContentHeight() {
        int h = 0;
        for (Component c : components) {
            h += c.height + 2;
        }
        return h;
    }

    private void setupContentScissorBox() {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scaleFactor = sr.getScaleFactor();
        int scissorX = (x + categoryPanelWidth + 1) * scaleFactor;
        int scissorY = (mc.displayHeight) - ((y + height) * scaleFactor);
        int scissorWidth = (width - categoryPanelWidth - 1) * scaleFactor;
        int scissorHeight = (height - titleBarHeight) * scaleFactor;
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }

    public void setTotalCategoryHeight(int height) {
        this.totalCategoryHeight = height;
    }

    public int getCategoryScrollY() {
        return this.categoryScrollY;
    }

    public void setCategoryScrollY(int scrollY) {
        this.categoryScrollY = scrollY;
    }

    private boolean isMouseOverTitle(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + titleBarHeight;
    }
}