 
package ru.fuctorial.fuctorize.client.gui.spammer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.impl.SmartSpammer;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import ru.fuctorial.fuctorize.utils.Lang;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

public class GuiSmartSpammer extends GuiScreen {

    private final SmartSpammer module;
    private int panelX, panelY, panelWidth, panelHeight;

    private GuiTextField messageField;
    private GuiTextField intervalField;
    private GuiTextField durationField;

    private StyledButton startStopButton;

    public GuiSmartSpammer(SmartSpammer module) {
        this.module = module;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.panelWidth = 380;
        this.panelHeight = 200;  
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.buttonList.clear();

        int fieldWidth = panelWidth - 20;

         
        messageField = new GuiTextField(panelX + 10, panelY + 55, fieldWidth, 20, false);
        messageField.setText(module.message.text);
        messageField.setPlaceholder(Lang.get("smartspammer.placeholder.message"));
        messageField.setMaxStringLength(1000);
        messageField.setFocused(true);
         
        messageField.setSyntaxHighlighting(true);

         
        int smallFieldWidth = (fieldWidth / 2) - 5;
        intervalField = new GuiTextField(panelX + 10, panelY + 95, smallFieldWidth, 20, false);
        intervalField.setText(String.valueOf((int)module.interval.value));
        intervalField.setMaxStringLength(5);

        durationField = new GuiTextField(panelX + 10 + smallFieldWidth + 10, panelY + 95, smallFieldWidth, 20, false);
        durationField.setText(String.valueOf((int)module.duration.value));
        durationField.setMaxStringLength(5);

         
        int midButtonY = panelY + 125;
        int midButtonWidth = (fieldWidth / 2) - 5;
        this.buttonList.add(new StyledButton(2, panelX + 10, midButtonY, midButtonWidth, 20, Lang.get("smartspammer.button.placeholder_settings")));
        this.buttonList.add(new StyledButton(3, panelX + 10 + midButtonWidth + 10, midButtonY, midButtonWidth, 20, Lang.get("smartspammer.button.info")));

         
        int footerY = panelY + panelHeight - 35;
        int footerButtonWidth = (fieldWidth / 2) - 5;
        startStopButton = new StyledButton(0, panelX + 10, footerY, footerButtonWidth, 20, module.isSpamming() ? Lang.get("smartspammer.button.stop") : Lang.get("smartspammer.button.start"));
        this.buttonList.add(startStopButton);
        this.buttonList.add(new StyledButton(1, panelX + 10 + footerButtonWidth + 10, footerY, footerButtonWidth, 20, Lang.get("smartspammer.button.close")));
    }

    private void saveSettingsToModule() {
        module.message.text = messageField.getText();
        try { module.interval.value = Integer.parseInt(intervalField.getText()); } catch (Exception ignored) {}
        try { module.duration.value = Integer.parseInt(durationField.getText()); } catch (Exception ignored) {}
    }

    @Override
    public void onGuiClosed() {
        saveSettingsToModule();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {  
            saveSettingsToModule();
            if (module.isSpamming()) {
                module.stopSpam();
            } else {
                module.startSpam();
            }
            updateButtonState();
        } else if (button.id == 1) {  
            mc.displayGuiScreen(null);
        } else if (button.id == 2) {  
            saveSettingsToModule();
            mc.displayGuiScreen(new GuiPlaceholderSettings(this, module));
        } else if (button.id == 3) {  
            saveSettingsToModule();
            mc.displayGuiScreen(new GuiPlaceholderInfo(this));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        CustomFontRenderer boldFont = FuctorizeClient.INSTANCE.fontManager.bold_22;

        String title = Lang.get("smartspammer.title");
        boldFont.drawString(title, panelX + (panelWidth - boldFont.getStringWidth(title)) / 2f, panelY + 15, -1);

        font.drawString(Lang.get("smartspammer.field.message"), panelX + 10, panelY + 40, Theme.TEXT_WHITE.getRGB());
        messageField.drawTextBox();

        font.drawString(Lang.get("smartspammer.field.interval"), panelX + 10, panelY + 80, Theme.TEXT_WHITE.getRGB());
        intervalField.drawTextBox();

        font.drawString(Lang.get("smartspammer.field.duration"), panelX + 10 + panelWidth / 2, panelY + 80, Theme.TEXT_WHITE.getRGB());
        durationField.drawTextBox();

        String statusText = module.isSpamming() ? EnumChatFormatting.GREEN + Lang.get("smartspammer.status.active") : EnumChatFormatting.GRAY + Lang.get("smartspammer.status.idle");
        font.drawString(statusText, panelX + 10, panelY + 155, -1);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        messageField.updateCursorCounter();
        intervalField.updateCursorCounter();
        durationField.updateCursorCounter();
        updateButtonState();
    }

    private void updateButtonState() {
        if (startStopButton != null) {
            startStopButton.displayString = module.isSpamming() ? Lang.get("smartspammer.button.stop") : Lang.get("smartspammer.button.start");
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            if (module.isSpamming()) {
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(Lang.get("notification.smartspammer.active"), Lang.get("notification.smartspammer.background"), Notification.NotificationType.INFO, 2500L));
            }
            return;
        }
        messageField.textboxKeyTyped(typedChar, keyCode);
        intervalField.textboxKeyTyped(typedChar, keyCode);
        durationField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        messageField.mouseClicked(mouseX, mouseY, mouseButton);
        intervalField.mouseClicked(mouseX, mouseY, mouseButton);
        durationField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
