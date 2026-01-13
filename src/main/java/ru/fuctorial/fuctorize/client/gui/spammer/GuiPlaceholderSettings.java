// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\gui\spammer\GuiPlaceholderSettings.java
package ru.fuctorial.fuctorize.client.gui.spammer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.module.impl.SmartSpammer;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public class GuiPlaceholderSettings extends GuiScreen {
    private final GuiScreen parentScreen;
    private final SmartSpammer module;

    private GuiTextField randCharsetField;
    private GuiTextField sequenceListField;

    private int panelX, panelY, panelWidth, panelHeight;

    public GuiPlaceholderSettings(GuiScreen parent, SmartSpammer module) {
        this.parentScreen = parent;
        this.module = module;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.panelWidth = 350;
        this.panelHeight = 185; // Increased height for the new button
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.buttonList.clear();

        int fieldWidth = panelWidth - 20;

        randCharsetField = new GuiTextField(panelX + 10, panelY + 50, fieldWidth, 20, true);
        randCharsetField.setText(module.randCharset.text);
        randCharsetField.setMaxStringLength(256);
        randCharsetField.setFocused(true);

        sequenceListField = new GuiTextField(panelX + 10, panelY + 95, fieldWidth, 20, true);
        sequenceListField.setText(module.sequenceList.text);
        sequenceListField.setMaxStringLength(1000);

        // --- NEW "Check Files" button ---
        this.buttonList.add(new StyledButton(2, panelX + 10, panelY + 125, fieldWidth, 20, "Проверить файлы"));

        int buttonWidth = (panelWidth - 25) / 2;
        this.buttonList.add(new StyledButton(0, panelX + 10, panelY + panelHeight - 30, buttonWidth, 20, "Сохранить"));
        this.buttonList.add(new StyledButton(1, panelX + 15 + buttonWidth, panelY + panelHeight - 30, buttonWidth, 20, "Назад"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) { // Save
            module.randCharset.text = randCharsetField.getText();
            module.sequenceList.text = sequenceListField.getText();
            mc.displayGuiScreen(this.parentScreen);
        } else if (button.id == 1) { // Back
            mc.displayGuiScreen(this.parentScreen);
        } else if (button.id == 2) { // Check Files
            // This is safe to call from the GUI thread
            // FIX: Call the method directly on the module instance we already have.
            this.module.checkFilesAndNotify();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        randCharsetField.textboxKeyTyped(typedChar, keyCode);
        sequenceListField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(this.parentScreen);
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            actionPerformed((GuiButton) this.buttonList.get(0));
        }
    }

    @Override
    public void updateScreen() {
        randCharsetField.updateCursorCounter();
        sequenceListField.updateCursorCounter();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        randCharsetField.mouseClicked(mouseX, mouseY, mouseButton);
        sequenceListField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // --- VISUAL BUG FIX: Draw a neutral background instead of the parent GUI ---
        this.drawDefaultBackground();
        RenderUtils.drawRect(0, 0, this.width, this.height, 0x90000000); // Darken

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer boldFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        String title = "Настройки плейсхолдеров";
        boldFont.drawString(title, panelX + (panelWidth - boldFont.getStringWidth(title)) / 2f, panelY + 10, -1);

        font.drawString("Символы для {rand}:", panelX + 10, panelY + 35, Theme.TEXT_WHITE.getRGB());
        randCharsetField.drawTextBox();

        font.drawString("Список для {seq} (через запятую):", panelX + 10, panelY + 80, Theme.TEXT_WHITE.getRGB());
        sequenceListField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}