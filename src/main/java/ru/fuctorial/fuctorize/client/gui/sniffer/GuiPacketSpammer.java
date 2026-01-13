// 38. C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\client\gui\sniffer\GuiPacketSpammer.java
package ru.fuctorial.fuctorize.client.gui.sniffer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextArea;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.manager.FavoritePacketManager;
import ru.fuctorial.fuctorize.manager.PacketPersistence;
import ru.fuctorial.fuctorize.module.impl.PacketSpammer;
import ru.fuctorial.fuctorize.utils.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuiPacketSpammer extends GuiScreen {

    private final PacketSpammer module;
    private int panelX, panelY, panelWidth, panelHeight;

    // Сохранение состояния
    private static int savedX = -1, savedY = -1, savedW = -1, savedH = -1;

    // Ресайз
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;
    private boolean isResizing = false;
    private int resizeEdge = 0; // 1=right, 2=bottom, 3=corner
    private final int resizeBorder = 6;

    // --- ДОБАВЛЕНО НОВОЕ СОСТОЯНИЕ ALLOWED_EDIT ---
    private enum State { MAIN, PLACEHOLDERS, BANNED_EDIT, ALLOWED_EDIT }
    private State currentState = State.MAIN;

    // Компоненты
    private GuiTextField channelField;
    private GuiTextArea hexArea;
    private int mainScrollOffset = 0;

    private List<String> foundPlaceholders = new ArrayList<>();
    private int placeholderScrollOffset = 0;
    private String selectedPlaceholderKey = null;

    private GuiTextArea banInputArea;
    private int bannedListScrollOffset = 0;

    // --- Компоненты для Allowed List ---
    private GuiTextArea allowInputArea;
    private int allowedListScrollOffset = 0;

    public GuiPacketSpammer(PacketSpammer module) {
        this.module = module;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        int defaultW = (int)(this.width * 0.65);
        int defaultH = (int)(this.height * 0.75);
        defaultW = Math.max(380, Math.min(600, defaultW));
        defaultH = Math.max(320, Math.min(500, defaultH));

        if (savedX == -1 || savedW == -1) {
            this.panelWidth = defaultW;
            this.panelHeight = defaultH;
            this.panelX = (this.width - this.panelWidth) / 2;
            this.panelY = (this.height - this.panelHeight) / 2;
        } else {
            this.panelWidth = savedW;
            this.panelHeight = savedH;
            this.panelX = savedX;
            this.panelY = savedY;
        }

        if (this.panelWidth > this.width - 20) this.panelWidth = this.width - 20;
        if (this.panelHeight > this.height - 20) this.panelHeight = this.height - 20;
        if (this.panelX < 0) this.panelX = 0;
        if (this.panelY < 0) this.panelY = 0;
        if (this.panelX + this.panelWidth > this.width) this.panelX = this.width - this.panelWidth;
        if (this.panelY + this.panelHeight > this.height) this.panelY = this.height - this.panelHeight;

        rebuildUiForState();
    }

    private void rebuildUiForState() {
        this.buttonList.clear();

        if (currentState == State.MAIN) {
            initMain();
        } else if (currentState == State.PLACEHOLDERS) {
            initPlaceholdersList();
        } else if (currentState == State.BANNED_EDIT) {
            initBannedEditor();
        } else if (currentState == State.ALLOWED_EDIT) {
            initAllowedEditor();
        }
    }

    private void initMain() {
        int btnW = 140;
        this.buttonList.add(new StyledButton(0, panelX + 10, panelY + 30, btnW, 20, "Mode: " + module.mode.getMode()));

        int footerY = panelY + panelHeight - 30;
        int availableW = panelWidth - 40;
        int actionBtnW = availableW / 3;

        updateStartButton(actionBtnW, footerY); // ID 2

        StyledButton logicBtn = new StyledButton(5, panelX + 20 + actionBtnW + 5, footerY, actionBtnW - 5, 20, "Logic...");
        logicBtn.enabled = module.mode.isMode("Bruteforce");
        this.buttonList.add(logicBtn);

        this.buttonList.add(new StyledButton(3, panelX + 20 + (actionBtnW * 2) + 5, footerY, actionBtnW - 5, 20, "Close"));

        if (channelField == null) channelField = new GuiTextField(panelX + 10, panelY + 75, panelWidth - 20, 18, false);
        else {
            channelField.xPos = panelX + 10;
            channelField.yPos = panelY + 75;
            channelField.width = panelWidth - 20;
        }
        channelField.setMaxStringLength(100);
        channelField.setText(module.customChannel);

        int textAreaHeight = Math.max(50, panelHeight - 115 - 40);
        if (hexArea == null) hexArea = new GuiTextArea(panelX + 10, panelY + 115, panelWidth - 20, textAreaHeight);
        else {
            hexArea.xPos = panelX + 10;
            hexArea.yPos = panelY + 115;
            hexArea.width = panelWidth - 20;
            hexArea.height = textAreaHeight;
            hexArea.setText(hexArea.getText());
        }
        hexArea.setText(module.customHex);
    }

    private void initPlaceholdersList() {
        refreshPlaceholders();
        int btnW = (panelWidth - 30) / 3;
        int footerY = panelY + panelHeight - 30;

        StyledButton bansBtn = new StyledButton(9, panelX + 10, footerY, btnW, 20, "Edit Bans");
        bansBtn.enabled = (selectedPlaceholderKey != null);
        this.buttonList.add(bansBtn);

        StyledButton allowBtn = new StyledButton(10, panelX + 15 + btnW, footerY, btnW, 20, "Edit Allowed");
        allowBtn.enabled = (selectedPlaceholderKey != null);
        this.buttonList.add(allowBtn);

        this.buttonList.add(new StyledButton(6, panelX + 20 + btnW * 2, footerY, btnW, 20, "Back to Editor"));
    }

    private void initBannedEditor() {
        int fieldHeight = 40;
        int listHeight = Math.max(50, panelHeight - 100 - fieldHeight);

        if (banInputArea == null) banInputArea = new GuiTextArea(panelX + 10, panelY + 40 + listHeight + 20, panelWidth - 20, fieldHeight);
        else {
            banInputArea.xPos = panelX + 10;
            banInputArea.yPos = panelY + 40 + listHeight + 20;
            banInputArea.width = panelWidth - 20;
            banInputArea.height = fieldHeight;
            banInputArea.setText(banInputArea.getText());
        }
        banInputArea.setText("");
        banInputArea.setFocused(true);

        int btnW = (panelWidth - 30) / 2;
        int footerY = panelY + panelHeight - 30;

        this.buttonList.add(new StyledButton(7, panelX + 10, footerY, btnW, 20, "Add Ban"));
        this.buttonList.add(new StyledButton(8, panelX + 20 + btnW, footerY, btnW, 20, "Back"));
    }

    private void initAllowedEditor() {
        int fieldHeight = 40;
        int listHeight = Math.max(50, panelHeight - 100 - fieldHeight);

        if (allowInputArea == null) allowInputArea = new GuiTextArea(panelX + 10, panelY + 40 + listHeight + 20, panelWidth - 20, fieldHeight);
        else {
            allowInputArea.xPos = panelX + 10;
            allowInputArea.yPos = panelY + 40 + listHeight + 20;
            allowInputArea.width = panelWidth - 20;
            allowInputArea.height = fieldHeight;
            allowInputArea.setText(allowInputArea.getText());
        }
        allowInputArea.setText("");
        allowInputArea.setFocused(true);

        int btnW = (panelWidth - 30) / 2;
        int footerY = panelY + panelHeight - 30;

        this.buttonList.add(new StyledButton(11, panelX + 10, footerY, btnW, 20, "Add Allow Rule"));
        this.buttonList.add(new StyledButton(8, panelX + 20 + btnW, footerY, btnW, 20, "Back"));
    }

    private void refreshPlaceholders() {
        foundPlaceholders.clear();
        String raw = module.customHex;
        Pattern p = Pattern.compile("\\{([0-9A-Fa-f\\s]*)\\}");
        Matcher m = p.matcher(raw);
        while(m.find()) {
            String ph = m.group(0);
            if (!foundPlaceholders.contains(ph)) foundPlaceholders.add(ph);
        }
    }

    private void updateStartButton(int width, int y) {
        this.buttonList.removeIf(b -> ((GuiButton)b).id == 2);
        String text = module.isSpamming() ? EnumChatFormatting.RED + "STOP SPAM" : EnumChatFormatting.GREEN + "START SPAM";
        this.buttonList.add(new StyledButton(2, panelX + 10, y, width, 20, text));
    }

    @Override
    public void updateScreen() {
        if (currentState == State.MAIN) {
            if (channelField != null) channelField.updateCursorCounter();
            if (hexArea != null) hexArea.updateCursorCounter();
            for (Object o : buttonList) {
                if (o instanceof StyledButton) {
                    StyledButton btn = (StyledButton) o;
                    if (btn.id == 5) btn.enabled = module.mode.isMode("Bruteforce");
                    if (btn.id == 0) btn.displayString = "Mode: " + module.mode.getMode();
                    if (btn.id == 2) btn.displayString = module.isSpamming() ? EnumChatFormatting.RED + "STOP SPAM" : EnumChatFormatting.GREEN + "START SPAM";
                }
            }
        } else if (currentState == State.BANNED_EDIT) {
            if (banInputArea != null) banInputArea.updateCursorCounter();
        } else if (currentState == State.ALLOWED_EDIT) {
            if (allowInputArea != null) allowInputArea.updateCursorCounter();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            module.mode.cycle();
            rebuildUiForState();
        } else if (button.id == 2) {
            module.customChannel = channelField.getText();
            module.customHex = hexArea.getText();
            if (module.isSpamming()) module.stopSpam(); else module.startSpam();
        } else if (button.id == 3) {
            mc.displayGuiScreen(null);
        } else if (button.id == 5) {
            module.customHex = hexArea.getText();
            currentState = State.PLACEHOLDERS;
            rebuildUiForState();
        } else if (button.id == 6) {
            currentState = State.MAIN;
            rebuildUiForState();
        }
        else if (button.id == 9) { // Edit Bans
            currentState = State.BANNED_EDIT;
            rebuildUiForState();
        }
        else if (button.id == 10) { // Edit Allowed
            currentState = State.ALLOWED_EDIT;
            rebuildUiForState();
        }
        else if (button.id == 7) { // Add Ban
            String toBan = banInputArea.getText().trim();
            processAddToList(toBan, module.bannedCombinations);
            banInputArea.setText("");
        }
        else if (button.id == 11) { // Add Allow
            String toAllow = allowInputArea.getText().trim();
            processAddToList(toAllow, module.allowedCombinations);
            allowInputArea.setText("");
        }
        else if (button.id == 8) { // Back from edit
            currentState = State.PLACEHOLDERS;
            rebuildUiForState();
        }
    }

    // Универсальный метод добавления (для банов и разрешений) с поддержкой диапазонов
    private void processAddToList(String input, Map<String, List<String>> map) {
        if (input.isEmpty() || selectedPlaceholderKey == null) return;

        List<String> list = map.computeIfAbsent(selectedPlaceholderKey, k -> new ArrayList<>());

        // Получаем ожидаемую длину
        String content = selectedPlaceholderKey.substring(1, selectedPlaceholderKey.length()-1).replaceAll("\\s+", "");
        int targetLength = content.length();

        if (input.contains("-")) {
            String[] parts = input.split("-");
            if (parts.length == 2) {
                String startHex = PacketSpammer.cleanHex(parts[0]);
                String endHex = PacketSpammer.cleanHex(parts[1]);
                // Форматируем диапазон для отображения в списке
                String rangeStr = formatHex(startHex, targetLength) + "-" + formatHex(endHex, targetLength);
                if (!list.contains(rangeStr)) list.add(rangeStr);
            }
        } else {
            String clean = PacketSpammer.cleanHex(input);
            String fmt = formatHex(clean, targetLength);
            if (!list.contains(fmt)) list.add(fmt);
        }
    }

    private String formatHex(String hex, int len) {
        if (hex.length() % 2 != 0) hex = "0" + hex;
        while (hex.length() < len) hex = "0" + hex;
        if (hex.length() > len) hex = hex.substring(hex.length() - len);
        return hex.toUpperCase();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            // Resize logic... (skipped for brevity, same as before)
            boolean onRight = mouseX >= panelX + panelWidth - resizeBorder && mouseX <= panelX + panelWidth + resizeBorder;
            boolean onBottom = mouseY >= panelY + panelHeight - resizeBorder && mouseY <= panelY + panelHeight + resizeBorder;
            if (onRight || onBottom) {
                isResizing = true;
                resizeEdge = (onRight && onBottom) ? 3 : (onRight ? 1 : 2);
                return;
            }
            if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + 20) {
                dragging = true;
                dragOffsetX = mouseX - panelX;
                dragOffsetY = mouseY - panelY;
                return;
            }
        }

        State startState = this.currentState;
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.currentState != startState) return;

        if (currentState == State.MAIN) {
            if (module.mode.isMode("Raw Hex") || module.mode.isMode("Bruteforce")) {
                if (channelField != null) channelField.mouseClicked(mouseX, mouseY, mouseButton);
                if (hexArea != null) hexArea.mouseClicked(mouseX, mouseY, mouseButton);
            }
            if (module.mode.isMode("Favorites")) {
                handleFavoritesClick(mouseX, mouseY);
            }
        }
        else if (currentState == State.PLACEHOLDERS) {
            handlePlaceholderClick(mouseX, mouseY);
        }
        else if (currentState == State.BANNED_EDIT) {
            if (banInputArea != null) banInputArea.mouseClicked(mouseX, mouseY, mouseButton);
            handleListClick(mouseX, mouseY, module.bannedCombinations, bannedListScrollOffset);
        }
        else if (currentState == State.ALLOWED_EDIT) {
            if (allowInputArea != null) allowInputArea.mouseClicked(mouseX, mouseY, mouseButton);
            handleListClick(mouseX, mouseY, module.allowedCombinations, allowedListScrollOffset);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) {
            dragging = false;
            isResizing = false;
        }
    }

    private void handlePlaceholderClick(int mouseX, int mouseY) {
        int top = panelY + 40;
        int bottom = panelY + panelHeight - 35;
        if (mouseY > bottom) return;

        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= top) {
            int idx = (mouseY - top + placeholderScrollOffset) / 20;
            if (idx >= 0 && idx < foundPlaceholders.size()) {
                selectedPlaceholderKey = foundPlaceholders.get(idx);
                rebuildUiForState(); // Refresh to enable buttons
            }
        }
    }

    private void handleListClick(int mouseX, int mouseY, Map<String, List<String>> map, int scrollOffset) {
        int top = panelY + 40;
        int listHeight = Math.max(50, panelHeight - 100 - 40);
        if (mouseY > top + listHeight) return;

        List<String> list = map.get(selectedPlaceholderKey);
        if (list == null) return;

        if (mouseX >= panelX + panelWidth - 30 && mouseX <= panelX + panelWidth - 10 && mouseY >= top) {
            int idx = (mouseY - top + scrollOffset) / 18;
            if (idx >= 0 && idx < list.size()) {
                list.remove(idx);
            }
        }
    }

    // ... handleFavoritesClick ... (без изменений)
    private void handleFavoritesClick(int mouseX, int mouseY) {
        int top = panelY + 60;
        int viewH = panelHeight - 100;
        if (mouseY >= top && mouseY <= top + viewH && mouseX >= panelX + 5 && mouseX <= panelX + panelWidth - 5) {
            int index = (mouseY - top + mainScrollOffset) / 22;
            List<PacketPersistence.SavedPacketData> favs = FavoritePacketManager.getFavorites();
            if (index >= 0 && index < favs.size()) {
                module.selectedFavorite = favs.get(index);
                module.customChannel = module.selectedFavorite.channel != null ? module.selectedFavorite.channel : "CONTROL";
                try {
                    byte[] b = java.util.Base64.getDecoder().decode(module.selectedFavorite.rawData);
                    StringBuilder sb = new StringBuilder();
                    for(byte by : b) sb.append(String.format("%02X ", by));
                    module.customHex = sb.toString().trim();
                } catch(Exception e){}
                if (channelField != null) channelField.setText(module.customChannel);
                if (hexArea != null) hexArea.setText(module.customHex);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (currentState == State.BANNED_EDIT || currentState == State.ALLOWED_EDIT) {
                currentState = State.PLACEHOLDERS;
                rebuildUiForState();
            } else if (currentState == State.PLACEHOLDERS) {
                currentState = State.MAIN;
                rebuildUiForState();
            } else {
                mc.displayGuiScreen(null);
            }
            return;
        }

        if (currentState == State.MAIN) {
            if (module.mode.isMode("Raw Hex") || module.mode.isMode("Bruteforce")) {
                if (channelField != null) channelField.textboxKeyTyped(typedChar, keyCode);
                if (hexArea != null) hexArea.textboxKeyTyped(typedChar, keyCode);
            }
        } else if (currentState == State.BANNED_EDIT) {
            if (banInputArea != null) banInputArea.textboxKeyTyped(typedChar, keyCode);
        } else if (currentState == State.ALLOWED_EDIT) {
            if (allowInputArea != null) allowInputArea.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            int move = dWheel > 0 ? -15 : 15;
            if (currentState == State.MAIN && module.mode.isMode("Favorites")) {
                mainScrollOffset = clampScroll(mainScrollOffset + move, FavoritePacketManager.getFavorites().size() * 22, panelHeight - 100);
            } else if (currentState == State.PLACEHOLDERS) {
                int viewH = panelHeight - 80;
                placeholderScrollOffset = clampScroll(placeholderScrollOffset + move, foundPlaceholders.size() * 20, viewH);
            } else if (currentState == State.BANNED_EDIT) {
                List<String> list = module.bannedCombinations.get(selectedPlaceholderKey);
                int size = list != null ? list.size() * 18 : 0;
                int viewH = Math.max(50, panelHeight - 100 - 40);
                bannedListScrollOffset = clampScroll(bannedListScrollOffset + move, size, viewH);
            } else if (currentState == State.ALLOWED_EDIT) {
                List<String> list = module.allowedCombinations.get(selectedPlaceholderKey);
                int size = list != null ? list.size() * 18 : 0;
                int viewH = Math.max(50, panelHeight - 100 - 40);
                allowedListScrollOffset = clampScroll(allowedListScrollOffset + move, size, viewH);
            }
        }
    }

    private int clampScroll(int val, int contentH, int viewH) {
        if (val < 0) val = 0;
        int max = Math.max(0, contentH - viewH);
        if (val > max) val = max;
        return val;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (isResizing) {
            int minW = 360;
            int minH = 320;
            if (resizeEdge == 1 || resizeEdge == 3) this.panelWidth = Math.max(minW, mouseX - this.panelX);
            if (resizeEdge == 2 || resizeEdge == 3) this.panelHeight = Math.max(minH, mouseY - this.panelY);
            rebuildUiForState();
        } else if (dragging) {
            this.panelX = mouseX - dragOffsetX;
            this.panelY = mouseY - dragOffsetY;
            rebuildUiForState();
        }

        drawDefaultBackground();
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;

        int bgColor = 0xFF202020;
        int borderColor = Theme.ORANGE.getRGB();

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, bgColor);
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, borderColor);
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, borderColor);
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, borderColor);
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, borderColor);

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        if (currentState == State.MAIN) {
            drawMain(mouseX, mouseY, titleFont, font);
        } else if (currentState == State.PLACEHOLDERS) {
            drawPlaceholders(mouseX, mouseY, titleFont, font);
        } else if (currentState == State.BANNED_EDIT) {
            drawListEditor(mouseX, mouseY, titleFont, font, "Banned", module.bannedCombinations, banInputArea, bannedListScrollOffset);
        } else if (currentState == State.ALLOWED_EDIT) {
            drawListEditor(mouseX, mouseY, titleFont, font, "Allowed", module.allowedCombinations, allowInputArea, allowedListScrollOffset);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // ... drawMain is same ...
    private void drawMain(int mouseX, int mouseY, CustomFontRenderer titleFont, CustomFontRenderer font) {
        titleFont.drawString("Packet Spammer", panelX + 10, panelY + 10, Theme.ORANGE.getRGB());

        String modeStr = module.mode.getMode();
        if (modeStr.equals("Favorites")) {
            int top = panelY + 60;
            int viewH = panelHeight - 100;
            RenderUtils.startScissor(panelX + 5, top, panelWidth - 10, viewH);
            int cy = top - mainScrollOffset;
            List<PacketPersistence.SavedPacketData> favs = FavoritePacketManager.getFavorites();
            if (favs.isEmpty()) font.drawString("No favorites saved!", panelX + 20, top + 10, Theme.TEXT_GRAY.getRGB());

            for (PacketPersistence.SavedPacketData data : favs) {
                if (cy + 20 > top && cy < top + viewH) {
                    boolean sel = (module.selectedFavorite == data);
                    int color = sel ? Theme.ORANGE.darker().getRGB() : Theme.COMPONENT_BG.getRGB();
                    RenderUtils.drawRect(panelX + 10, cy, panelX + panelWidth - 10, cy + 20, color);
                    font.drawString(data.name, panelX + 15, cy + 6, -1);
                }
                cy += 22;
            }
            RenderUtils.stopScissor();
        } else {
            font.drawString("Channel:", panelX + 10, panelY + 62, -1);
            if (channelField != null) channelField.drawTextBox();
            String label = modeStr.equals("Bruteforce") ? "Hex Template (use {XX} for dynamic):" : "Hex Payload:";
            font.drawString(label, panelX + 10, panelY + 102, -1);
            if (hexArea != null) hexArea.drawTextBox();
        }
    }

    private void drawPlaceholders(int mouseX, int mouseY, CustomFontRenderer titleFont, CustomFontRenderer font) {
        titleFont.drawString("Placeholders List", panelX + 10, panelY + 10, Theme.ORANGE.getRGB());
        font.drawString("Select a placeholder to edit rules:", panelX + 10, panelY + 25, Theme.TEXT_GRAY.getRGB());

        int top = panelY + 40;
        int viewH = panelHeight - 80;

        RenderUtils.startScissor(panelX + 10, top, panelWidth - 20, viewH);
        int cy = top - placeholderScrollOffset;

        for (int i = 0; i < foundPlaceholders.size(); i++) {
            String ph = foundPlaceholders.get(i);
            if (cy + 20 > top && cy < top + viewH) {
                boolean hover = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10 && mouseY >= cy && mouseY <= cy + 20;
                boolean selected = ph.equals(selectedPlaceholderKey);

                int bgColor = selected ? Theme.ORANGE.darker().getRGB() : (hover ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());
                RenderUtils.drawRect(panelX + 10, cy, panelX + panelWidth - 10, cy + 19, bgColor);

                font.drawString((i + 1) + ". " + ph, panelX + 15, cy + 6, -1);

                List<String> bans = module.bannedCombinations.get(ph);
                List<String> allows = module.allowedCombinations.get(ph);

                String infoStr = "";
                if (allows != null && !allows.isEmpty()) infoStr += "Allowed: " + allows.size() + " ";
                if (bans != null && !bans.isEmpty()) infoStr += "Banned: " + bans.size();

                if (!infoStr.isEmpty()) {
                    font.drawString(infoStr, panelX + panelWidth - 15 - font.getStringWidth(infoStr), cy + 6, Theme.TEXT_GRAY.getRGB());
                }
            }
            cy += 20;
        }
        RenderUtils.stopScissor();
    }

    private void drawListEditor(int mouseX, int mouseY, CustomFontRenderer titleFont, CustomFontRenderer font, String type, Map<String, List<String>> map, GuiTextArea inputArea, int scrollOffset) {
        String phName = selectedPlaceholderKey != null ? selectedPlaceholderKey : "???";
        titleFont.drawString(type + ": " + phName, panelX + 10, panelY + 10, Theme.ORANGE.getRGB());

        int top = panelY + 40;
        int listHeight = Math.max(50, panelHeight - 100 - 40);

        RenderUtils.startScissor(panelX + 10, top, panelWidth - 20, listHeight);
        List<String> list = map.get(selectedPlaceholderKey);
        if (list == null) list = new ArrayList<>();

        int cy = top - scrollOffset;

        if (list.isEmpty()) {
            font.drawString(type.equals("Allowed") ? "All values allowed (00..FF)." : "No banned combinations.", panelX + 20, top + 10, Theme.TEXT_GRAY.getRGB());
        } else {
            for (String item : list) {
                if (cy + 18 > top && cy < top + listHeight) {
                    RenderUtils.drawRect(panelX + 10, cy, panelX + panelWidth - 40, cy + 17, Theme.COMPONENT_BG.getRGB());
                    font.drawString(item, panelX + 15, cy + 5, -1);

                    int delX = panelX + panelWidth - 30;
                    boolean hoverDel = mouseX >= delX && mouseX <= delX + 20 && mouseY >= cy && mouseY <= cy + 17;
                    RenderUtils.drawRect(delX, cy, delX + 20, cy + 17, hoverDel ? 0xFFFF0000 : 0xFFAA0000);
                    font.drawString("X", delX + 7, cy + 5, -1);
                }
                cy += 18;
            }
        }
        RenderUtils.stopScissor();

        font.drawString("Add " + type.toLowerCase() + " (Single or Range e.g. 0A-0F):", panelX + 10, panelY + 40 + listHeight + 8, Theme.TEXT_GRAY.getRGB());
        if (inputArea != null) inputArea.drawTextBox();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        savedX = panelX;
        savedY = panelY;
        savedW = panelWidth;
        savedH = panelHeight;

        if (currentState == State.MAIN && channelField != null && hexArea != null) {
            module.customChannel = channelField.getText();
            module.customHex = hexArea.getText();
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}