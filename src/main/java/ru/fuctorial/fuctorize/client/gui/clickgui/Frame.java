// Файл: C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\gui\clickgui\Frame.java
package ru.fuctorial.fuctorize.client.gui.clickgui;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.components.*;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.*;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class Frame extends AbstractFrame {

    private ModuleComponent bindingComponent = null;
    private ModuleComponent hoveredComponent = null;

    // --- ИСПРАВЛЕНИЕ: Выносим константы отступов в поля класса для удобства ---
    private static final int CONTENT_PADDING_X = 10;
    private static final int CONTENT_PADDING_Y = 4;
    private static final int COMPONENT_SPACING_Y = 2;

    public Frame(String title, int x, int y, int width, int height) {
        super(title, x, y, width, height);
    }

    @Override
    public void setup() {
        // --- ИСПРАВЛЕНИЕ: Вся логика расчета размеров и создания кнопок теперь находится ЗДЕСЬ, и выполняется ОДИН РАЗ ---
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            return; // Не можем ничего рассчитать без шрифтов
        }

        if (categoryButtons.isEmpty()) {
            CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
            CustomFontRenderer categoryFont = FuctorizeClient.INSTANCE.fontManager.regular_18;
            final int PADDING_VERTICAL = 4;
            final int PADDING_HORIZONTAL = 10;

            // 1. Рассчитываем высоту заголовка
            this.titleBarHeight = titleFont.getHeight() + PADDING_VERTICAL * 2;

            // 2. Рассчитываем ширину панели категорий
            int maxCategoryWidth = 0;
            for (Category cat : Category.values()) {
                int currentWidth = categoryFont.getStringWidth(cat.getDisplayName());
                if (currentWidth > maxCategoryWidth) {
                    maxCategoryWidth = currentWidth;
                }
            }
            this.categoryPanelWidth = maxCategoryWidth + PADDING_HORIZONTAL * 2;

            // 3. Создаем кнопки категорий с адаптивной высотой и рассчитанной шириной
            int categoryButtonHeight = categoryFont.getHeight() + PADDING_VERTICAL * 2;
            for (Category cat : Category.values()) {
                // Y-координата будет установлена динамически в drawScreen, здесь можно оставить 0
                CategoryComponent comp = new CategoryComponent(cat, this, 0, 0, categoryPanelWidth, categoryButtonHeight);
                categoryButtons.add(comp);
            }
        }

        // Эта логика остается без изменений
        if (this.currentCategory == null) {
            setCurrentCategory(Category.COMBAT);
        } else {
            if (this.currentCategory instanceof Category) {
                setupComponentsForCategory((Category) this.currentCategory);
            }
        }
    }

    @Override
    public void setCurrentCategory(Object category) {
        if (category instanceof Category) {
            this.currentCategory = category;
            // Reset scroll only when user explicitly changes category
            this.scrollY = 0;
            setupComponentsForCategory((Category) category);
        }
    }

    public void setupComponentsForCategory(Category category) {
        this.components.clear();
        // --- ИЗМЕНЕНИЕ 1: Определяем отступы как константы ---
        int modX = categoryPanelWidth + CONTENT_PADDING_X;
        int modWidth = width - categoryPanelWidth - (CONTENT_PADDING_X * 2);

        // --- ИЗМЕНЕНИЕ 2: Удаляем жестко заданные высоты ---
        // int compHeight = 16;
        // int settingHeight = 15;
        // int separatorHeight = 8;
        // int colorPickerHeight = 80;

        for (Module module : FuctorizeClient.INSTANCE.moduleManager.getModulesInCategory(category)) {
            // Передаем 0 в качестве высоты, компонент определит ее сам
            components.add(new ModuleComponent(module, this, modX, 0, modWidth, 0));
            for (Setting setting : module.getSettings()) {
                if (setting instanceof SeparatorSetting)
                    components.add(new SeparatorComponent((SeparatorSetting) setting, this, modX, 0, modWidth, 0));
                else if (setting instanceof BooleanSetting)
                    components.add(new CheckboxComponent((BooleanSetting) setting, this, modX, 0, modWidth, 0));
                else if (setting instanceof SliderSetting)
                    components.add(new SliderComponent((SliderSetting) setting, this, modX, 0, modWidth, 0));
                else if (setting instanceof ModeSetting)
                    components.add(new ModeComponent((ModeSetting) setting, this, modX, 0, modWidth, 0));
                else if (setting instanceof TextSetting)
                    components.add(new TextInputComponent((TextSetting) setting, this, modX, 0, modWidth, 0));
                else if (setting instanceof ColorSetting) {
                    ColorSetting cs = (ColorSetting) setting;
                    components.add(new ColorComponent(cs, this, modX, 0, modWidth, 0));
                    // Для сложных компонентов, как ColorPicker, можно оставить высоту или сделать ее адаптивной
                    components.add(new ColorPickerComponent(cs, this, modX, 0, modWidth, 80));
                }
            }
        }
    }

    @Override
    public ModuleComponent getBindingComponent() {
        return this.bindingComponent;
    }

    @Override
    public void setBindingComponent(ModuleComponent component) {
        this.bindingComponent = component;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        hoveredComponent = null;
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            // Базовая отрисовка, если шрифты еще не загружены
            Gui.drawRect(x, y, x + width, y + height, Theme.CATEGORY_BG.getRGB());
            Gui.drawRect(x + categoryPanelWidth + 1, y + titleBarHeight, x + width, y + height, Theme.MAIN_BG.getRGB());
            return;
        }

        if (isDragging) {
            x = mouseX - dragX;
            y = mouseY - dragY;
        }

        // Clamp scroll to new bounds in case resolution changed while GUI is open
        clampScrollToBounds();

        // --- ОТРИСОВКА ФОНОВ ---
        Gui.drawRect(x, y, x + width, y + titleBarHeight, Theme.CATEGORY_BG.getRGB());
        Gui.drawRect(x, y + titleBarHeight, x + categoryPanelWidth, y + height, Theme.CATEGORY_BG.getRGB());
        Gui.drawRect(x + categoryPanelWidth, y + titleBarHeight, x + width, y + height, Theme.MAIN_BG.getRGB());

        // --- ОТРИСОВКА ЗАГОЛОВКА ---
        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        float titleWidth = titleFont.getStringWidth(this.title);
        float titleX = this.x + (this.width - titleWidth) / 2f;
        float titleY = this.y + (titleBarHeight / 2f) - (titleFont.getHeight() / 2f);
        titleFont.drawString(this.title, titleX, titleY + 1, Theme.TEXT_WHITE.getRGB());

        // --- ИСПРАВЛЕНИЕ: Динамически устанавливаем Y для кнопок категорий и рисуем их ---
        int categoryY = titleBarHeight;
        for (CategoryComponent c : categoryButtons) {
            c.y = categoryY;
            c.drawComponent(mouseX, mouseY);
            categoryY += c.height;
        }

        // --- Рендер основной панели с модулями и настройками ---
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        setupScissorBox();
        GL11.glTranslatef(0, scrollY, 0);

        // --- ИЗМЕНЕНИЕ ЗДЕСЬ ---
        // Было: int layoutY = titleBarHeight + CONTENT_PADDING_Y;
        // Стало:
        int layoutY = titleBarHeight; // Убрали отступ, чтобы 1-й модуль был на уровне 1-й категории
        for (Component component : components) {
            component.y = layoutY;

            if (isComponentVisible(component)) {
                double animFactor = 1.0;
                if (component.getSetting() != null && !(component instanceof ModuleComponent)) {
                    Module parentModule = component.getSetting().getParent();
                    if (parentModule != null) animFactor = parentModule.animation.getAnimationFactor();
                    if (component instanceof ColorPickerComponent)
                        animFactor = ((ColorSetting) component.getSetting()).animation.getAnimationFactor();
                }

                component.drawComponent(mouseX, mouseY - scrollY, animFactor);

                if (component instanceof ModuleComponent && component.isMouseOver(mouseX, mouseY - scrollY)) {
                    hoveredComponent = (ModuleComponent) component;
                }

                // Используем адаптивную высоту компонента и константу отступа
                if (component.getSetting() != null && !(component instanceof ModuleComponent)) {
                    layoutY += (int) ((component.height + COMPONENT_SPACING_Y) * animFactor);
                } else {
                    layoutY += component.height + COMPONENT_SPACING_Y;
                }
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glPopMatrix();

        // --- Рендер рамки и подсказок ---
        Gui.drawRect(x - 1, y - 1, x + width + 1, y, Theme.BORDER.getRGB());
        Gui.drawRect(x - 1, y, x, y + height + 1, Theme.BORDER.getRGB());
        Gui.drawRect(x + width, y, x + width + 1, y + height + 1, Theme.BORDER.getRGB());
        Gui.drawRect(x - 1, y + height, x + width + 1, y + height + 1, Theme.BORDER.getRGB());
        if (hoveredComponent != null && !hoveredComponent.getModule().getDescription().isEmpty()) {
            drawDescriptionTooltip(hoveredComponent.getModule(), mouseX, mouseY);
        }
    }

    /**
     * Adjust scroll to valid range based on current content size.
     * Call this after any size/scale change.
     */
    private void clampScrollToBounds() {
        int contentHeight = height - titleBarHeight;
        int totalComponentHeight = getTotalContentHeight();
        int maxScroll = totalComponentHeight - contentHeight + 8;
        if (maxScroll < 0) maxScroll = 0;
        if (scrollY > 0) scrollY = 0;
        if (scrollY < -maxScroll) scrollY = -maxScroll;
    }

    /**
     * Called when the frame is resized (e.g., user changes resolution while GUI is open).
     * Tries to preserve the viewport by compensating scroll for the change in visible content height.
     */
    public void onResized(int oldWidth, int oldHeight, int newWidth, int newHeight) {
        int oldContent = oldHeight - titleBarHeight;
        int newContent = newHeight - titleBarHeight;
        int delta = newContent - oldContent;
        // Shift scroll by the increase in visible area to keep content aligned
        this.scrollY += delta;
        clampScrollToBounds();
    }


    @Override
    public boolean isAnyTextFieldFocused() {
        for (Component c : components) {
            if (c instanceof TextInputComponent && ((TextInputComponent) c).isFocused) {
                return true;
            }
        }
        return false;
    }
    private void drawDescriptionTooltip(Module module, int mouseX, int mouseY) {
        if (getFont() == null) return;
        List<String> lines = getFont().wrapText(module.getDescription(), 150);
        int tooltipWidth = 0;
        for (String line : lines) {
            tooltipWidth = Math.max(tooltipWidth, getFont().getStringWidth(line));
        }
        tooltipWidth += 6;
        int tooltipHeight = (getFont().getHeight() + 2) * lines.size() + 4;
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();
        int tooltipX = mouseX + 10;
        int tooltipY = mouseY;
        if (tooltipX + tooltipWidth > screenWidth) {
            tooltipX = mouseX - tooltipWidth - 10;
        }
        if (tooltipY + tooltipHeight > screenHeight) {
            tooltipY = screenHeight - tooltipHeight - 2;
        }
        Gui.drawRect(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, Theme.MAIN_BG.darker().getRGB());
        Gui.drawRect(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY, Theme.BORDER.getRGB());
        Gui.drawRect(tooltipX - 1, tooltipY - 1, tooltipX, tooltipY + tooltipHeight + 1, Theme.BORDER.getRGB());
        Gui.drawRect(tooltipX + tooltipWidth, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, Theme.BORDER.getRGB());
        Gui.drawRect(tooltipX - 1, tooltipY + tooltipHeight, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, Theme.BORDER.getRGB());
        int currentY = tooltipY + 3;
        for (String line : lines) {
            getFont().drawString(line, tooltipX + 3, currentY, Theme.TEXT_WHITE.getRGB());
            currentY += getFont().getHeight() + 2;
        }
    }

    private CustomFontRenderer getFont() {
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null) return null;
        return FuctorizeClient.INSTANCE.fontManager.regular_18;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;
        boolean isStartingBind = false;
        if (mouseButton == 2) {
            int adjustedMouseY = mouseY - scrollY;
            for (Component c : components) {
                if (isComponentVisible(c) && c.isMouseOver(mouseX, adjustedMouseY)) {
                    isStartingBind = true;
                    break;
                }
            }
        }
        if (bindingComponent != null && !isStartingBind) {
            Module targetModule = bindingComponent.getModule();
            BindSetting bind = BindSetting.getBindSetting(targetModule);
            if (bind != null) {
                bind.keyCode = Keyboard.KEY_NONE;
            }
            bindingComponent = null;
            return;
        }
        if (isMouseOverTitle(mouseX, mouseY) && mouseButton == 0) {
            isDragging = true;
            dragX = mouseX - x;
            dragY = mouseY - y;
            return;
        }
        boolean consumedClick = false;
        for (CategoryComponent c : categoryButtons) {
            if (c.isMouseOver(mouseX, mouseY)) {
                c.mouseClicked(mouseX, mouseY, mouseButton);
                consumedClick = true;
                break;
            }
        }
        if (!consumedClick && mouseX > x + categoryPanelWidth) {
            int adjustedMouseY = mouseY - scrollY;
            for (Component c : components) {
                if (isComponentVisible(c) && c.isMouseOver(mouseX, adjustedMouseY)) {
                    if (c instanceof TextInputComponent) {
                        components.stream()
                                .filter(comp -> comp instanceof TextInputComponent)
                                .forEach(comp -> ((TextInputComponent) comp).isFocused = false);
                        ((TextInputComponent) c).isFocused = true;
                    }
                    c.mouseClicked(mouseX, adjustedMouseY, mouseButton);
                    consumedClick = true;
                    break;
                }
            }
        }
        if (!consumedClick) {
            components.stream()
                    .filter(c -> c instanceof TextInputComponent)
                    .forEach(c -> ((TextInputComponent) c).isFocused = false);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;
        isDragging = false;
        int adjustedMouseY = mouseY - scrollY;
        for (Component c : components) {
            c.mouseReleased(mouseX, adjustedMouseY, state);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;
        if (bindingComponent != null) {
            Module targetModule = bindingComponent.getModule();
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
                BindSetting bind = BindSetting.getBindSetting(targetModule);
                if (bind == null) {
                    bind = new BindSetting("Keybind", Keyboard.KEY_NONE);
                    targetModule.addSetting(bind);
                }
                bind.keyCode = Keyboard.KEY_NONE;
                bindingComponent = null;
                return;
            }
            for (Module module : FuctorizeClient.INSTANCE.moduleManager.getModules()) {
                if (module == targetModule) continue;
                BindSetting otherBind = BindSetting.getBindSetting(module);
                if (otherBind != null && otherBind.keyCode == keyCode && keyCode != Keyboard.KEY_NONE) {
                    FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                            "Keybind Error",
                            "Key '" + Keyboard.getKeyName(keyCode) + "' is already used by " + module.getName(),
                            Notification.NotificationType.ERROR,
                            3000L
                    ));
                    return;
                }
            }
            BindSetting bind = BindSetting.getBindSetting(targetModule);
            if (bind == null) {
                bind = new BindSetting("Keybind", Keyboard.KEY_NONE);
                targetModule.addSetting(bind);
            }
            bind.keyCode = keyCode;
            bindingComponent = null;
            return;
        }
        for (Component c : components) {
            if (c instanceof TextInputComponent && ((TextInputComponent) c).isFocused) {
                ((TextInputComponent) c).keyTyped(typedChar, keyCode);
                return;
            }
        }
    }

    @Override
    public void mouseScrolled(int direction) {
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;
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

    private boolean isComponentVisible(Component c) {
        if (c instanceof ModuleComponent) return true;
        Setting setting = c.getSetting();
        if (setting == null) return false;
        Module parentModule = setting.getParent();
        if (parentModule == null) return false;
        if (!parentModule.extended) return false;
        if (c instanceof ColorPickerComponent) return ((ColorSetting) setting).expanded;
        return true;
    }

    private int getTotalContentHeight() {
        int h = 0;
        for (Component c : components) {
            if (isComponentVisible(c)) {
                double animFactor = 1.0;
                if (c.getSetting() != null && !(c instanceof ModuleComponent) && c.getSetting().getParent() != null) {
                    animFactor = c.getSetting().getParent().animation.getAnimationFactor();
                    if (c instanceof ColorPickerComponent)
                        animFactor = ((ColorSetting) c.getSetting()).animation.getAnimationFactor();
                }
                h += (int) ((c.height + COMPONENT_SPACING_Y) * animFactor); // Используем константу
            }
        }
        return h;
    }

    private void setupScissorBox() {
        final ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        final int scaleFactor = sr.getScaleFactor();
        int scissorX = (x + categoryPanelWidth) * scaleFactor;
        int scissorY = (mc.displayHeight) - ((y + height) * scaleFactor);
        int scissorWidth = (width - categoryPanelWidth) * scaleFactor;
        int scissorHeight = (height - titleBarHeight) * scaleFactor;
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }

    private boolean isMouseOverTitle(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + titleBarHeight;
    }
}
