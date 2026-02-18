 
package ru.fuctorial.fuctorize.client.gui.menu;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.manager.Account;
import ru.fuctorial.fuctorize.manager.AccountManager;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.SkinProvider;
import ru.fuctorial.fuctorize.utils.SkinUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
 
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GuiAccountManager extends GuiScreen {

    private enum ScreenMode {OFFLINE_LIST, OFFLINE_EDIT, ONLINE_EDIT}

    private ScreenMode currentMode;

    private final GuiScreen parentScreen;
    private final AccountManager accountManager;
    private List<Account> accounts;
    private Account editingAccount;

    private final Map<String, GuiTextField> editableFields = new LinkedHashMap<>();

    private int panelX, panelY, panelWidth, panelHeight;
     
     
     
     
    public static SkinProvider currentSkinProvider = SkinProvider.MOJANG;

    private long lastInitTime = 0;
    private static final long CLICK_COOLDOWN_MS = 200;

    private int onlineScrollOffset = 0;
    private int onlineContentHeight = 0;
    private int offlineScrollOffset = 0;
    private int offlineContentHeight = 0;
    private final List<StyledButton> offlineActionButtons = new ArrayList<>();

    public GuiAccountManager(GuiScreen parent) {
        this.parentScreen = parent;
        this.accountManager = FuctorizeClient.INSTANCE.accountManager;
        this.currentMode = accountManager.isLaunchedWithTokens() ? ScreenMode.ONLINE_EDIT : ScreenMode.OFFLINE_LIST;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.lastInitTime = System.currentTimeMillis();

         
         

         
        float widthPercent = 0.6f;
        float heightPercent = 0.7f;
        int minWidth = 380;
        int minHeight = 260;
        int maxWidth = 500;
        int maxHeight = 340;

        this.panelWidth = (int) (this.width * widthPercent);
        this.panelHeight = (int) (this.height * heightPercent);

        if (this.panelWidth < minWidth) this.panelWidth = minWidth;
        if (this.panelHeight < minHeight) this.panelHeight = minHeight;
        if (this.panelWidth > maxWidth) this.panelWidth = maxWidth;
        if (this.panelHeight > maxHeight) this.panelHeight = maxHeight;

        if (this.panelWidth > this.width - 20) this.panelWidth = this.width - 20;
        if (this.panelHeight > this.height - 20) this.panelHeight = this.height - 20;

        this.panelX = (this.width - panelWidth) / 2;
        this.panelY = (this.height - panelHeight) / 2;

        this.buttonList.clear();
        this.offlineActionButtons.clear();

        switch (currentMode) {
            case ONLINE_EDIT:
                setupOnlineEditGui();
                break;
            case OFFLINE_LIST:
                this.accounts = accountManager.getAccounts();
                setupOfflineListGui();
                break;
            case OFFLINE_EDIT:
                setupOfflineEditGui();
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
         
         
        RenderUtils.drawRect(0, 0, this.width, this.height, new Color(20, 20, 22).getRGB());

         
        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        switch (currentMode) {
            case ONLINE_EDIT:
                drawOnlineEditGui(mouseX, mouseY);
                break;
            case OFFLINE_LIST:
                drawOfflineListGui(mouseX, mouseY);
                break;
            case OFFLINE_EDIT:
                drawOfflineEditGui();
                break;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

     
    private void setupOnlineEditGui() {
        editableFields.clear();
        onlineContentHeight = 0;
        onlineScrollOffset = 0;
        Account current = accountManager.getCurrentAccount();
        if (current == null) return;
        Map<String, String> allTokens = new LinkedHashMap<>();
        allTokens.put("username", current.getUsername());
        allTokens.putAll(current.getTokens());
        for (Map.Entry<String, String> entry : allTokens.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            boolean isUsername = key.equalsIgnoreCase("username");
            boolean matchesHeuristic = value.length() > 16 && value.matches("[a-zA-Z0-9]+");
            if (isUsername || matchesHeuristic) {
                int fieldX = panelX + (int) (panelWidth * 0.3);
                int fieldWidth = (int) (panelWidth * 0.7) - 15;
                GuiTextField tokenField = new GuiTextField(fieldX, 0, fieldWidth, 20, false);
                tokenField.setMaxStringLength(256);
                tokenField.setText(value);
                editableFields.put(key, tokenField);
                onlineContentHeight += 30;
            }
        }
        this.buttonList.add(new StyledButton(2, panelX + 10, panelY + panelHeight - 30, (panelWidth - 25) / 2, 20, ru.fuctorial.fuctorize.utils.Lang.get("account.button.save")));
        this.buttonList.add(new StyledButton(1, panelX + 15 + (panelWidth - 25) / 2, panelY + panelHeight - 30, (panelWidth - 25) / 2, 20, ru.fuctorial.fuctorize.utils.Lang.get("account.button.back")));
    }

    private void setupOfflineListGui() {
        offlineContentHeight = accounts.size() * 32;
        boolean canDelete = accounts.size() > 1;
        for (int i = 0; i < accounts.size(); i++) {
            offlineActionButtons.add(new StyledButton(100 + i, 0, 0, 65, 20, ru.fuctorial.fuctorize.utils.Lang.get("account.button.edit")));
            StyledButton deleteButton = new StyledButton(200 + i, 0, 0, 65, 20, ru.fuctorial.fuctorize.utils.Lang.get("account.button.delete"));
            deleteButton.enabled = canDelete;
            offlineActionButtons.add(deleteButton);
        }
        this.buttonList.add(new StyledButton(0, panelX + 10, panelY + panelHeight - 30, (panelWidth - 25) / 2, 20, ru.fuctorial.fuctorize.utils.Lang.get("account.button.add")));
        this.buttonList.add(new StyledButton(1, panelX + 15 + (panelWidth - 25) / 2, panelY + panelHeight - 30, (panelWidth - 25) / 2, 20, ru.fuctorial.fuctorize.utils.Lang.get("account.button.back")));
    }

    private void setupOfflineEditGui() {
        editableFields.clear();
        int fieldY = panelY + 50;
        GuiTextField usernameField = new GuiTextField(panelX + (int) (panelWidth * 0.25), fieldY, (int) (panelWidth * 0.7) - 15, 20, false);
        usernameField.setMaxStringLength(32);
        if (editingAccount != null) {
            usernameField.setText(editingAccount.getUsername());
        } else {
            usernameField.setText("");
            usernameField.setPlaceholder(Lang.get("account.placeholder.username"));
        }
        editableFields.put("username", usernameField);
        this.buttonList.add(new StyledButton(2, panelX + 10, panelY + panelHeight - 30, (panelWidth - 25) / 2, 20, ru.fuctorial.fuctorize.utils.Lang.get("account.button.save")));
        this.buttonList.add(new StyledButton(3, panelX + 15 + (panelWidth - 25) / 2, panelY + panelHeight - 30, (panelWidth - 25) / 2, 20, ru.fuctorial.fuctorize.utils.Lang.get("account.button.cancel")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (System.currentTimeMillis() - lastInitTime < CLICK_COOLDOWN_MS) {
            return;
        }
        if (button.id >= 100) {
            if (button.id >= 200) {
                int index = button.id - 200;
                if (index < accounts.size()) accountManager.removeAccount(accounts.get(index));
                initGui();
            } else {
                int index = button.id - 100;
                if (index < accounts.size()) {
                    editingAccount = accounts.get(index);
                    currentMode = ScreenMode.OFFLINE_EDIT;
                    initGui();
                }
            }
            return;
        }
        switch (button.id) {
            case 0:
                editingAccount = null;
                currentMode = ScreenMode.OFFLINE_EDIT;
                initGui();
                break;
            case 1:
                mc.displayGuiScreen(parentScreen);
                break;
            case 2:
                handleSaveLocalized();
                break;
            case 3:
                currentMode = ScreenMode.OFFLINE_LIST;
                initGui();
                break;
        }
    }

    private void handleSave() {
        GuiTextField usernameField = editableFields.get("username");
        if (usernameField == null) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Критическая ошибка", "Поле username не найдено.", Notification.NotificationType.ERROR, 3000L));
            return;
        }
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Ошибка", "Имя аккаунта не может быть пустым.", Notification.NotificationType.ERROR, 3000L));
            return;
        }
        Account accountToSave;
        if (currentMode == ScreenMode.ONLINE_EDIT) {
            accountToSave = accountManager.getCurrentAccount();
        } else {
            accountToSave = (editingAccount != null) ? editingAccount : new Account();
        }
        if (accountToSave == null) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Ошибка", "Не удалось найти аккаунт для сохранения.", Notification.NotificationType.ERROR, 3000L));
            return;
        }
        accountToSave.setUsername(usernameField.getText());
        for (Map.Entry<String, GuiTextField> entry : editableFields.entrySet()) {
            if (entry.getKey().equals("username")) continue;
            accountToSave.setToken(entry.getKey(), entry.getValue().getText());
        }
        accountManager.addOrUpdateAccount(accountToSave);
        boolean loginSuccess = accountManager.login(accountToSave);
        if (loginSuccess) {
            String notificationTitle = (editingAccount == null && currentMode == ScreenMode.OFFLINE_EDIT) ? "Аккаунт добавлен" : "Аккаунт обновлен";
            String notificationMessage = "Аккаунт '" + username + "' сохранен.";
            if (currentMode == ScreenMode.ONLINE_EDIT) {
                notificationTitle = "Сессия обновлена";
                notificationMessage = "Перезайдите на сервер для применения.";
            }
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(notificationTitle, notificationMessage, Notification.NotificationType.SUCCESS, 2500L));
        } else {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Ошибка входа", "Не удалось применить сессию.", Notification.NotificationType.ERROR, 3000L));
        }
        this.currentMode = accountManager.isLaunchedWithTokens() ? ScreenMode.ONLINE_EDIT : ScreenMode.OFFLINE_LIST;
        initGui();
    }

     
    private void handleSaveLocalized() {
        GuiTextField usernameField = editableFields.get("username");
        if (usernameField == null) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("notification.account.error.critical_title"),
                    Lang.get("notification.account.error.username_field_missing"),
                    Notification.NotificationType.ERROR, 3000L));
            return;
        }
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("notification.account.error.title"),
                    Lang.get("notification.account.error.username_empty"),
                    Notification.NotificationType.ERROR, 3000L));
            return;
        }

        Account accountToSave;
        if (currentMode == ScreenMode.ONLINE_EDIT) {
            accountToSave = accountManager.getCurrentAccount();
        } else {
            accountToSave = (editingAccount != null) ? editingAccount : new Account();
        }
        if (accountToSave == null) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("notification.account.error.title"),
                    Lang.get("notification.account.error.account_not_found"),
                    Notification.NotificationType.ERROR, 3000L));
            return;
        }

        accountToSave.setUsername(usernameField.getText());
        for (Map.Entry<String, GuiTextField> entry : editableFields.entrySet()) {
            if (entry.getKey().equals("username")) continue;
            accountToSave.setToken(entry.getKey(), entry.getValue().getText());
        }
        accountManager.addOrUpdateAccount(accountToSave);
        boolean loginSuccess = accountManager.login(accountToSave);
        if (loginSuccess) {
            String notificationTitle;
            String notificationMessage;
            if (currentMode == ScreenMode.ONLINE_EDIT) {
                notificationTitle = Lang.get("notification.account.tokens_updated.title");
                notificationMessage = Lang.get("notification.account.tokens_updated.message");
            } else if (editingAccount == null && currentMode == ScreenMode.OFFLINE_EDIT) {
                notificationTitle = Lang.get("notification.account.saved_new.title");
                notificationMessage = Lang.format("notification.account.saved_new.message", username);
            } else {
                notificationTitle = Lang.get("notification.account.saved.title");
                notificationMessage = Lang.format("notification.account.saved.message", username);
            }
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(notificationTitle, notificationMessage, Notification.NotificationType.SUCCESS, 2500L));
        } else {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("notification.account.login_error.title"),
                    Lang.get("notification.account.login_error.apply_failed"),
                    Notification.NotificationType.ERROR, 3000L));
        }
        this.currentMode = accountManager.isLaunchedWithTokens() ? ScreenMode.ONLINE_EDIT : ScreenMode.OFFLINE_LIST;
        initGui();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (System.currentTimeMillis() - lastInitTime < CLICK_COOLDOWN_MS) {
            return;
        }
        if (currentMode == ScreenMode.OFFLINE_EDIT || currentMode == ScreenMode.ONLINE_EDIT) {
            for (GuiTextField field : editableFields.values()) field.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (currentMode == ScreenMode.OFFLINE_LIST) {
            handleOfflineListClick(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void handleOfflineListClick(int mouseX, int mouseY, int mouseButton) {
        int topBounds = panelY + 28;
        int bottomBounds = panelY + panelHeight - 60;
        int adjustedMouseY = mouseY + offlineScrollOffset;
        for (GuiButton button : this.offlineActionButtons) {
            if (button.mousePressed(mc, mouseX, adjustedMouseY)) {
                this.actionPerformed(button);
                return;
            }
        }
        if (mouseX >= panelX + 5 && mouseX <= panelX + panelWidth - 5 && mouseY >= topBounds && mouseY <= bottomBounds) {
            int currentY = panelY + 30;
            for (Account acc : accounts) {
                int listMouseY = mouseY + offlineScrollOffset;
                if (mouseX >= this.panelX + 10 && mouseX < this.panelX + this.panelWidth - 145 && listMouseY >= currentY && listMouseY < currentY + 30) {
                    if (mouseButton == 0) {
                        if (accountManager.login(acc)) cycleSkinProvider();
                    } else if (mouseButton == 1) {
                        editingAccount = acc;
                        currentMode = ScreenMode.OFFLINE_EDIT;
                        initGui();
                    }
                    return;
                }
                currentY += 32;
            }
        }
    }

    private void cycleSkinProvider() {
        int nextOrdinal = (currentSkinProvider.ordinal() + 1) % SkinProvider.values().length;
        currentSkinProvider = SkinProvider.values()[nextOrdinal];
        SkinUtils.clearSkinCache();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (currentMode == ScreenMode.OFFLINE_EDIT || currentMode == ScreenMode.ONLINE_EDIT) {
            for (GuiTextField field : editableFields.values()) field.textboxKeyTyped(typedChar, keyCode);
            if (keyCode == Keyboard.KEY_RETURN) {
                lastInitTime = 0;
                actionPerformed((GuiButton) this.buttonList.get(0));
            }
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (currentMode == ScreenMode.OFFLINE_EDIT) {
                currentMode = ScreenMode.OFFLINE_LIST;
                initGui();
            } else {
                mc.displayGuiScreen(parentScreen);
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel == 0) return;
        int scrollAmount = dWheel > 0 ? -15 : 15;
        if (currentMode == ScreenMode.ONLINE_EDIT) {
            int viewableHeight = panelHeight - 80;
            if (onlineContentHeight > viewableHeight) {
                onlineScrollOffset += scrollAmount;
                if (onlineScrollOffset > 0) onlineScrollOffset = 0;
                int maxScroll = onlineContentHeight - viewableHeight;
                if (onlineScrollOffset < -maxScroll) onlineScrollOffset = -maxScroll;
            }
        } else if (currentMode == ScreenMode.OFFLINE_LIST) {
            int viewableHeight = panelHeight - 90;
            if (offlineContentHeight > viewableHeight) {
                offlineScrollOffset += scrollAmount;
                if (offlineScrollOffset > 0) offlineScrollOffset = 0;
                int maxScroll = offlineContentHeight - viewableHeight;
                if (offlineScrollOffset < -maxScroll) offlineScrollOffset = -maxScroll;
            }
        }
    }

    private void drawOnlineEditGui(int mouseX, int mouseY) {
        String title = "Редактор сессии";
        FuctorizeClient.INSTANCE.fontManager.bold_22.drawString(title, panelX + (panelWidth - FuctorizeClient.INSTANCE.fontManager.bold_22.getStringWidth(title)) / 2f, panelY + 15, -1);
        int topBounds = panelY + 40;
        int bottomBounds = panelY + panelHeight - 35;
        int viewableHeight = bottomBounds - topBounds;
        RenderUtils.startScissor(panelX + 5, topBounds, panelWidth - 10, viewableHeight);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, onlineScrollOffset, 0);
        int fieldY = topBounds + 5;
        for (Map.Entry<String, GuiTextField> entry : editableFields.entrySet()) {
            FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(entry.getKey(), panelX + 10, fieldY + 5, -1);
            GuiTextField field = entry.getValue();
            field.yPos = fieldY;
            field.drawTextBox();
            fieldY += 30;
        }
        GL11.glPopMatrix();
        RenderUtils.stopScissor();
        if (onlineContentHeight > viewableHeight) {
            drawScrollBar(topBounds, viewableHeight, onlineContentHeight, onlineScrollOffset);
        }
    }

    private void drawOfflineListGui(int mouseX, int mouseY) {
        String title = "Менеджер аккаунтов";
        FuctorizeClient.INSTANCE.fontManager.bold_22.drawString(title, panelX + (panelWidth - FuctorizeClient.INSTANCE.fontManager.bold_22.getStringWidth(title)) / 2f, panelY + 8, -1);
        int topBounds = panelY + 28;
        int bottomBounds = panelY + panelHeight - 60;
        int viewableHeight = bottomBounds - topBounds;
        RenderUtils.startScissor(panelX + 5, topBounds, panelWidth - 10, viewableHeight);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, -offlineScrollOffset, 0);
        int currentY = topBounds;
        int index = 1;
        int buttonPairIndex = 0;
        for (Account acc : accounts) {
            boolean isCurrentlyLoggedIn = acc.getUsername().equalsIgnoreCase(mc.getSession().getUsername());
            int adjustedMouseY = mouseY + offlineScrollOffset;
            boolean isHovered = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 145 && adjustedMouseY >= currentY && adjustedMouseY < currentY + 30;
            int bgColor = isCurrentlyLoggedIn ? Theme.ORANGE.darker().getRGB() : (isHovered ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());
            RenderUtils.drawRect(panelX + 10, currentY, panelX + panelWidth - 10, currentY + 30, bgColor);
            String number = index + ".";
            FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(number, panelX + 15, currentY + 11, -1);
            SkinUtils.bindSkin(acc.getUsername());
            SkinUtils.drawHead(panelX + 35, currentY + 3, 24);
            String username = acc.getUsername();
            if (isCurrentlyLoggedIn) {
                FuctorizeClient.INSTANCE.fontManager.bold_22.drawString("§a" + username, panelX + 65, currentY + 10, -1);
            } else {
                FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(username, panelX + 65, currentY + 11, -1);
            }
            int buttonGroupX = panelX + panelWidth - 145;
            RenderUtils.drawRect(buttonGroupX - 2, currentY + 3, buttonGroupX + 130 + 5, currentY + 27, Theme.SETTING_BG.getRGB());
            StyledButton editButton = offlineActionButtons.get(buttonPairIndex++);
            editButton.xPosition = buttonGroupX;
            editButton.yPosition = currentY + 5;
            editButton.drawButton(mc, mouseX, adjustedMouseY);
            StyledButton deleteButton = offlineActionButtons.get(buttonPairIndex++);
            deleteButton.xPosition = buttonGroupX + 70;
            deleteButton.yPosition = currentY + 5;
            deleteButton.drawButton(mc, mouseX, adjustedMouseY);
            currentY += 32;
            index++;
        }
        GL11.glPopMatrix();
        RenderUtils.stopScissor();
        if (offlineContentHeight > viewableHeight) {
            drawScrollBar(topBounds, viewableHeight, offlineContentHeight, -offlineScrollOffset);
        }
        int switcherY = panelY + panelHeight - 55;
        String label = "Источник скинов:";
        String providerName = "< " + currentSkinProvider.getDisplayName() + " >";
        int totalWidth = FuctorizeClient.INSTANCE.fontManager.regular_18.getStringWidth(label + " " + providerName);
        int switcherX = panelX + (panelWidth - totalWidth) / 2;
        boolean providerHovered = mouseX >= switcherX && mouseX <= switcherX + totalWidth && mouseY >= switcherY && mouseY <= switcherY + 20;
        FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(label, switcherX, switcherY + 6, Theme.TEXT_GRAY.getRGB());
        FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(providerName, switcherX + FuctorizeClient.INSTANCE.fontManager.regular_18.getStringWidth(label) + 2, switcherY + 6, providerHovered ? Theme.ORANGE.getRGB() : -1);
    }

    private void drawScrollBar(int top, int viewHeight, int contentHeight, int scroll) {
        int scrollbarX = panelX + panelWidth - 8;
        RenderUtils.drawRect(scrollbarX, top, scrollbarX + 4, top + viewHeight, 0x55000000);
        float scrollableHeight = contentHeight - viewHeight;
        if (scrollableHeight <= 0) return;
        float scrollPercent = (float) scroll / scrollableHeight;
        int handleHeight = (int) ((float) viewHeight / contentHeight * viewHeight);
        handleHeight = Math.max(handleHeight, 20);
        int handleY = top + (int) (scrollPercent * (viewHeight - handleHeight));
        RenderUtils.drawRect(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, Theme.ORANGE.getRGB());
    }

    private void drawOfflineEditGui() {
        String title = (editingAccount == null) ? "Добавление аккаунта" : "Редактирование аккаунта";
        FuctorizeClient.INSTANCE.fontManager.bold_22.drawString(title, panelX + (panelWidth - FuctorizeClient.INSTANCE.fontManager.bold_22.getStringWidth(title)) / 2f, panelY + 15, -1);
        int fieldY = panelY + 50;
        FuctorizeClient.INSTANCE.fontManager.regular_18.drawString("Никнейм:", panelX + 10, fieldY + 5, -1);
        editableFields.get("username").drawTextBox();
    }

}
