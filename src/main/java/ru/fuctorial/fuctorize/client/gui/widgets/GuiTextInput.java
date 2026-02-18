 
package ru.fuctorial.fuctorize.client.gui.widgets;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import java.util.function.Consumer;

public class GuiTextInput extends GuiScreen {
    private final GuiScreen parentScreen;
    private final String title;
    private final String initialText;
    private final Consumer<String> callback;
    private GuiTextField inputField;
    private int panelX, panelY, panelWidth, panelHeight;

    public GuiTextInput(GuiScreen parent, String title, String initialText, Consumer<String> callback) {
        this.parentScreen = parent;
        this.title = title;
        this.initialText = initialText;
        this.callback = callback;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.panelWidth = 200;
        this.panelHeight = 100;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        int fieldX = panelX + 10;
        int fieldY = panelY + 35;
        int fieldWidth = panelWidth - 20;
        this.inputField = new GuiTextField(fieldX, fieldY, fieldWidth, 20, false);
        this.inputField.setFocused(true);
        this.inputField.setText(initialText);

        int buttonWidth = (panelWidth - 25) / 2;
        this.buttonList.add(new StyledButton(0, panelX + 10, panelY + panelHeight - 30, buttonWidth, 20, ru.fuctorial.fuctorize.utils.Lang.get("generic.button.save")));
        this.buttonList.add(new StyledButton(1, panelX + 15 + buttonWidth, panelY + panelHeight - 30, buttonWidth, 20, ru.fuctorial.fuctorize.utils.Lang.get("generic.button.cancel")));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (button.id == 0) {  
            this.callback.accept(this.inputField.getText());
            this.mc.displayGuiScreen(this.parentScreen);
        } else if (button.id == 1) {  
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        this.inputField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            actionPerformed((net.minecraft.client.gui.GuiButton) this.buttonList.get(0));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.parentScreen.drawScreen(0, 0, 0);  
        RenderUtils.drawRect(0, 0, this.width, this.height, 0x90000000);  

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        titleFont.drawString(this.title, panelX + (panelWidth - titleFont.getStringWidth(this.title)) / 2f, panelY + 10, -1);

        this.inputField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
