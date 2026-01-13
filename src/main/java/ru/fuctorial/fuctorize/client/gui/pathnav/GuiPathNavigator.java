package ru.fuctorial.fuctorize.client.gui.pathnav;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.utils.RenderUtils;

import java.awt.Color;

public class GuiPathNavigator extends GuiScreen {

    private final GuiScreen previousScreen;

    private GuiTextField xField, yField, zField;

    // Static fields to persist coordinates between GUI opens
    private static String savedX = "0";
    private static String savedY = "64";
    private static String savedZ = "0";

    // Уменьшили высоту панели, чтобы убрать пустоту снизу
    private int panelWidth = 240;
    private int panelHeight = 175;
    private int panelX, panelY;

    public GuiPathNavigator(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        this.panelX = (this.width - panelWidth) / 2;
        this.panelY = (this.height - panelHeight) / 2;

        int fieldX = panelX + 30;
        int fieldWidth = panelWidth - 40;
        int fieldHeight = 18;

        // Подняли startY чуть выше
        int startY = panelY + 35;
        int gap = 24; // Чуть уменьшили отступ между полями

        this.xField = new GuiTextField(fieldX, startY, fieldWidth, fieldHeight, false);
        this.yField = new GuiTextField(fieldX, startY + gap, fieldWidth, fieldHeight, false);
        this.zField = new GuiTextField(fieldX, startY + gap * 2, fieldWidth, fieldHeight, false);

        this.xField.setText(savedX);
        this.yField.setText(savedY);
        this.zField.setText(savedZ);

        this.xField.setFocused(true);

        int buttonWidth = 100;
        int buttonGap = 10;

        // Рассчитываем позицию кнопок.
        // Поле Z заканчивается на (startY + gap*2 + fieldHeight).
        // Оставляем место под статус (примерно 15px) и рисуем кнопки.
        int row1Y = startY + gap * 2 + fieldHeight + 20;
        int row2Y = row1Y + 24; // Отступ между рядами кнопок

        // Row 1 (Current Pos, Clipboard)
        this.buttonList.add(new StyledButton(3, panelX + 15, row1Y, buttonWidth, 20, "Current Pos"));
        this.buttonList.add(new StyledButton(4, panelX + 15 + buttonWidth + buttonGap, row1Y, buttonWidth, 20, "Clipboard"));

        // Row 2 (Start/Stop, Close)
        this.buttonList.add(new StyledButton(1, panelX + 15, row2Y, buttonWidth, 20, getStartStopButtonText()));
        this.buttonList.add(new StyledButton(2, panelX + 15 + buttonWidth + buttonGap, row2Y, buttonWidth, 20, "Close"));
    }

    private String getStartStopButtonText() {
        if (FuctorizeClient.INSTANCE.botNavigator != null && FuctorizeClient.INSTANCE.botNavigator.isNavigating()) {
            return "Stop";
        }
        return "Start";
    }

    @Override
    public void updateScreen() {
        this.xField.updateCursorCounter();
        this.yField.updateCursorCounter();
        this.zField.updateCursorCounter();

        if (!this.buttonList.isEmpty()) {
            for (Object btnObj : this.buttonList) {
                GuiButton btn = (GuiButton) btnObj;
                if (btn.id == 1) {
                    btn.displayString = getStartStopButtonText();
                }
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) { // Start/Stop
            handleStartStop();
        } else if (button.id == 2) { // Close
            this.mc.displayGuiScreen(previousScreen);
        } else if (button.id == 3) { // Current Pos
            if (mc.thePlayer != null) {
                this.xField.setText(String.valueOf(MathHelper.floor_double(mc.thePlayer.posX)));
                this.yField.setText(String.valueOf(MathHelper.floor_double(mc.thePlayer.boundingBox.minY)));
                this.zField.setText(String.valueOf(MathHelper.floor_double(mc.thePlayer.posZ)));
            }
        } else if (button.id == 4) { // Clipboard
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard != null && !clipboard.isEmpty()) {
                parseClipboard(clipboard);
            }
        }
    }

    private void handleStartStop() {
        if (FuctorizeClient.INSTANCE.botNavigator == null) return;

        if (FuctorizeClient.INSTANCE.botNavigator.isNavigating()) {
            FuctorizeClient.INSTANCE.botNavigator.stop();
        } else {
            try {
                savedX = xField.getText();
                savedY = yField.getText();
                savedZ = zField.getText();

                double x = parseCoordinate(savedX, mc.thePlayer.posX);
                double y = parseCoordinate(savedY, mc.thePlayer.posY);
                double z = parseCoordinate(savedZ, mc.thePlayer.posZ);

                FuctorizeClient.INSTANCE.botNavigator.navigateTo(x, y, z);
                this.mc.displayGuiScreen(null);
            } catch (NumberFormatException e) {
                // Handle error
            }
        }
    }

    private double parseCoordinate(String text, double currentVal) throws NumberFormatException {
        text = text.trim().replace(",", ".");
        if (text.startsWith("~")) {
            if (text.length() == 1) return currentVal;
            return currentVal + Double.parseDouble(text.substring(1));
        }
        return Double.parseDouble(text);
    }

    private void parseClipboard(String text) {
        // Заменяем запятые на пробелы для универсальности и сплитим
        String[] parts = text.replace(",", " ").trim().split("\\s+");

        try {
            if (parts.length >= 3) {
                // Если 3 и более чисел - X Y Z
                Double.parseDouble(parts[0]);
                Double.parseDouble(parts[1]);
                Double.parseDouble(parts[2]);

                this.xField.setText(parts[0]);
                this.yField.setText(parts[1]);
                this.zField.setText(parts[2]);
            } else if (parts.length == 2) {
                // --- ИЗМЕНЕНИЕ 1: Если 2 числа - X и Z, а Y ставим "~"
                Double.parseDouble(parts[0]);
                Double.parseDouble(parts[1]);

                this.xField.setText(parts[0]);
                this.yField.setText("~");
                this.zField.setText(parts[1]);
            }
        } catch (NumberFormatException ignored) {
            // Если в буфере мусор - ничего не делаем
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        this.xField.textboxKeyTyped(typedChar, keyCode);
        this.yField.textboxKeyTyped(typedChar, keyCode);
        this.zField.textboxKeyTyped(typedChar, keyCode);

        if (keyCode == Keyboard.KEY_TAB) {
            if (xField.isFocused()) {
                xField.setFocused(false);
                yField.setFocused(true);
            } else if (yField.isFocused()) {
                yField.setFocused(false);
                zField.setFocused(true);
            } else if (zField.isFocused()) {
                zField.setFocused(false);
                xField.setFocused(true);
            }
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(previousScreen);
        }

        if (keyCode == Keyboard.KEY_RETURN) {
            handleStartStop();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.xField.mouseClicked(mouseX, mouseY, mouseButton);
        this.yField.mouseClicked(mouseX, mouseY, mouseButton);
        this.zField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            return;
        }

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer regFont = FuctorizeClient.INSTANCE.fontManager.regular_18;

        // Фон
        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());

        // Рамка
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        // Заголовок
        String title = "Path Navigator";
        float titleWidth = titleFont.getStringWidth(title);
        titleFont.drawString(title, panelX + (panelWidth - titleWidth) / 2f, panelY + 10, Theme.ORANGE.getRGB());

        // Разделитель
        RenderUtils.drawRect(panelX + 10, panelY + 28, panelX + panelWidth - 10, panelY + 29, Theme.DIVIDER.getRGB());

        // Labels X/Y/Z
        float labelX = panelX + 15;
        int labelColor = Theme.TEXT_GRAY.getRGB();

        regFont.drawString("X:", labelX, xField.yPos + (xField.height - regFont.getHeight()) / 2f, labelColor);
        regFont.drawString("Y:", labelX, yField.yPos + (yField.height - regFont.getHeight()) / 2f, labelColor);
        regFont.drawString("Z:", labelX, zField.yPos + (zField.height - regFont.getHeight()) / 2f, labelColor);

        // Fields
        this.xField.drawTextBox();
        this.yField.drawTextBox();
        this.zField.drawTextBox();

        // --- ИЗМЕНЕНИЕ 2: Улучшенный рендер статуса ---
        if (FuctorizeClient.INSTANCE.botNavigator != null && FuctorizeClient.INSTANCE.botNavigator.isNavigating()) {
            String statusLabel = "Status: ";
            String statusVal = "Navigating...";

            float totalW = regFont.getStringWidth(statusLabel + statusVal);
            // Размещаем ровно между полем Z и кнопками (row1)
            // Поле Z yPos ~ (panelY + 35 + 24*2) = panelY + 83. Высота 18 -> 101.
            // Кнопки row1 yPos ~ (startY + 48 + 18 + 20) = panelY + 121.
            // Середина ~ panelY + 111.
            float statusY = zField.yPos + zField.height + 6;

            float startTextX = panelX + (panelWidth - totalW) / 2f;

            // Рисуем "Status:" серым
            regFont.drawString(statusLabel, startTextX, statusY, Theme.TEXT_GRAY.getRGB());
            // Рисуем "Navigating..." зеленым (но мягким, не кислотным)
            regFont.drawString(statusVal, startTextX + regFont.getStringWidth(statusLabel), statusY, new Color(100, 220, 100).getRGB());
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        savedX = xField.getText();
        savedY = yField.getText();
        savedZ = zField.getText();
    }
}