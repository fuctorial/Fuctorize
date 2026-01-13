package ru.fuctorial.fuctorize.client.gui.sniffer;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.impl.PacketSniffer;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.ChatUtils;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.PacketInfo;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import java.util.function.Consumer;
import java.awt.Color;
import java.awt.Desktop;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiPacketSniffer extends GuiScreen {

    private int x, y, width, height;
    private int headerHeight = 20;
    private int footerHeight = 22;
    private int scrollOffset = 0;
    private boolean dragging = false;
    private int dragX, dragY;
    private GuiTextField filterField;
    private String filterText = "";
    private final List<Button> headerButtons = new ArrayList<>();
    private static int savedX = -1, savedY = -1, savedWidth = 0, savedHeight = 0;

    private boolean isResizing = false;
    private int resizeEdge = 0;
    private final int resizeBorder = 5;

    private boolean selectionMode = false;
    private GuiScreen selectionModeParent = null;
    // --- НОВОЕ ПОЛЕ: Callback для возврата пакета ---
    private Consumer<PacketInfo> onSelectionCallback = null;

    private final GuiScreen previousScreen;

    public GuiPacketSniffer(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    // Старый метод для совместимости (если используется где-то еще без колбэка)
    public void setSelectionMode(GuiScreen parent) {
        setSelectionMode(parent, null);
    }

    // --- НОВЫЙ МЕТОД: Принимает callback ---
    public void setSelectionMode(GuiScreen parent, Consumer<PacketInfo> callback) {
        this.selectionMode = true;
        this.selectionModeParent = parent;
        this.onSelectionCallback = callback;

        // Отключаем кнопки управления в хедере, чтобы не мешали выбору
        for (Button b : headerButtons) b.enabled = false;
        if (this.filterField != null) this.filterField.setFocused(false);
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        ScaledResolution sr = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);

        boolean invalidSaved = savedWidth == 0 || savedHeight == 0 || savedWidth < 100 || savedHeight < 100;
        boolean tooBig = (savedX + savedWidth > sr.getScaledWidth()) || (savedY + savedHeight > sr.getScaledHeight());

        if (invalidSaved || tooBig) {
            this.width = Math.min(600, sr.getScaledWidth() - 40);
            this.height = Math.min(400, sr.getScaledHeight() - 60);
            this.x = (sr.getScaledWidth() - this.width) / 2;
            this.y = (sr.getScaledHeight() - this.height) / 2;
        } else {
            this.x = savedX;
            this.y = savedY;
            this.width = savedWidth;
            this.height = savedHeight;
        }

        if (this.x < 0) this.x = 0;
        if (this.y < 0) this.y = 0;
        if (this.x + this.width > sr.getScaledWidth()) this.x = sr.getScaledWidth() - this.width;
        if (this.y + this.height > sr.getScaledHeight()) this.y = sr.getScaledHeight() - this.height;

        filterField = new GuiTextField(this.mc.fontRenderer, 0, 0, 0, 18);
        filterField.setEnableBackgroundDrawing(true);
        filterField.setMaxStringLength(100);
        filterField.setText(filterText);
        setupHeaderButtons();

        // Если мы уже в режиме выбора, отключаем кнопки сразу при инициализации
        if (selectionMode) {
            for (Button b : headerButtons) b.enabled = false;
        }
    }

    // --- ИЗМЕНЕНО: Обработка клика по пакету ---
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        filterField.mouseClicked(mouseX, mouseY, mouseButton);

        if (isMouseOver(mouseX, mouseY, x, y + headerHeight, width, this.height - headerHeight - footerHeight)) {
            int virtualMouseY = mouseY + scrollOffset;
            int currentVirtualY = y + headerHeight + 2;
            List<PacketInfo> filtered = getFilteredPackets();

            for (PacketInfo info : filtered) {
                if (virtualMouseY >= currentVirtualY && virtualMouseY <= currentVirtualY + 11) {
                    if (mouseButton == 0) {
                        if (selectionMode) {
                            // --- ВОТ ЗДЕСЬ ИСПОЛЬЗУЕМ CALLBACK ---
                            handleSelectionModeClick(info);
                        } else if (isCtrlKeyDown()) {
                            handleFilterClick(info);
                        } else {
                            if (FuctorizeClient.INSTANCE.fontManager.isReady()) {
                                this.mc.displayGuiScreen(new GuiPacketDetails(info, this));
                            }
                        }
                    } else if (mouseButton == 1 && !selectionMode) {
                        handleRightClick(info);
                    }
                    return;
                }
                currentVirtualY += 11;
            }
            return;
        }

        if (mouseButton == 0 && resizeEdge != 0) {
            isResizing = true; return;
        }

        // Кнопки хедера работают только если НЕ selectionMode
        if (!selectionMode) {
            for (Button button : headerButtons) {
                if (button.isVisible() && isMouseOver(mouseX, mouseY, button.x, button.y, button.width, button.height)) {
                    if (button.text.equals("BlackList")) {
                        if (mouseButton == 1) PacketSniffer.setUseBlacklist(!PacketSniffer.isUsingBlacklist());
                        else if (mouseButton == 0) mc.displayGuiScreen(new GuiBlacklistEditor(this));
                    } else { button.onClick(); }
                    return;
                }
            }
        }

        if (isMouseOver(mouseX, mouseY, x, y, width, headerHeight) && mouseButton == 0) {
            dragging = true; dragX = mouseX - x; dragY = mouseY - y;
        }
    }

    private void handleSelectionModeClick(PacketInfo info) {
        if (onSelectionCallback != null) {
            // Если есть колбэк (новый способ через PacketBlocker), используем его
            onSelectionCallback.accept(info);
        } else {
            // Fallback для старых вызовов (например, через BlacklistEditor), если они не передали callback
            if (info.rawPacket instanceof FMLProxyPacket) {
                PacketSniffer.addBlacklistedFmlChannel(((FMLProxyPacket) info.rawPacket).channel());
            } else {
                PacketSniffer.addBlacklistedClassName(info.rawPacket.getClass().getName());
            }
        }
        // Возвращаемся обратно
        mc.displayGuiScreen(selectionModeParent);
    }

    // ... Остальной код (setupHeaderButtons, drawScreen, и т.д.) оставляем без изменений ...

    private void setupHeaderButtons() {
        headerButtons.clear();
        headerButtons.add(new Button("Reset Filter", "Reset current packet filter", () -> {
            PacketSniffer.viewFilterSpecific = null;
            scrollOffset = 0;
        }) {
            @Override String getDisplayText() { return "Reset"; }
            @Override int getTextColor() { return 0xFFFF5555; }
            @Override boolean isVisible() { return PacketSniffer.viewFilterSpecific != null; }
        });
        headerButtons.add(new Button("FML", "Show FML Packets Only", () -> {
            PacketSniffer.viewFmlOnly = !PacketSniffer.viewFmlOnly;
            scrollOffset = 0;
        }) {
            @Override String getDisplayText() { return PacketSniffer.viewFmlOnly ? "FML Only" : "All"; }
            @Override int getTextColor() { return PacketSniffer.viewFmlOnly ? 0xFF55FF55 : Theme.TEXT_GRAY.getRGB(); }
        });
        headerButtons.add(new Button("Folder", Lang.get("sniffer.tooltip.folder"), () -> {
            if (PacketSniffer.logManager != null) {
                try { Desktop.getDesktop().open(PacketSniffer.logManager.getLogDir()); }
                catch (IOException e) { e.printStackTrace(); }
            }
        }) { @Override int getTextColor() { return 0xFF55FFFF; } });
        headerButtons.add(new Button("Clear", Lang.get("sniffer.tooltip.clear"), () -> {
            PacketSniffer.startNewLogSession();
            scrollOffset = 0;
        }) { @Override int getTextColor() { return 0xFFFF5555; } });
        headerButtons.add(new Button("Pause", Lang.get("sniffer.tooltip.pause"), () -> PacketSniffer.setPaused(!PacketSniffer.isPaused())) {
            @Override String getDisplayText() { return PacketSniffer.isPaused() ? Lang.get("sniffer.header.resume") : Lang.get("sniffer.header.pause"); }
            @Override int getTextColor() { return PacketSniffer.isPaused() ? 0xFF55FF55 : 0xFFFFFF55; }
        });
        headerButtons.add(new Button("Filter", "Advanced packet filter (Excel-like)", () -> {
            mc.displayGuiScreen(new GuiPacketFilterSelector(this));
        }) {
            @Override String getDisplayText() {
                int hiddenCount = PacketSniffer.hiddenPackets.size();
                return hiddenCount > 0 ? "Filter (" + hiddenCount + ")" : "Filter";
            }
            @Override int getTextColor() {
                return PacketSniffer.hiddenPackets.isEmpty() ? Theme.TEXT_GRAY.getRGB() : 0xFF55FFFF;
            }
        });
        headerButtons.add(new Button("Favorites", "Saved Packets", () -> {
            mc.displayGuiScreen(new GuiFavoritePackets(this));
        }) {
            @Override int getTextColor() { return 0xFFFFAA00; }
        });
        headerButtons.add(new Button("BlackList", Lang.get("sniffer.tooltip.blacklist_toggle"), () -> {}) {
            @Override String getDisplayText() {
                return Lang.get("sniffer.header.blacklist") + (PacketSniffer.isUsingBlacklist() ? ": ON" : ": OFF");
            }
            @Override int getTextColor() { return PacketSniffer.isUsingBlacklist() ? 0xFF55FF55 : 0xFFFF5555; }
        });
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        savedX = this.x;
        savedY = this.y;
        savedWidth = this.width;
        savedHeight = this.height;
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0 || state == 1) {
            dragging = false;
            isResizing = false;
        }
        if (isResizing) {
            int minWidth = 300;
            int minHeight = 200;
            if (resizeEdge == 1 || resizeEdge == 3) {
                this.width = Math.max(minWidth, mouseX - this.x);
            }
            if (resizeEdge == 2 || resizeEdge == 3) {
                this.height = Math.max(minHeight, mouseY - this.y);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        if (!dragging && !isResizing) {
            boolean onRight = mouseX >= this.x + this.width - resizeBorder && mouseX <= this.x + this.width + resizeBorder;
            boolean onBottom = mouseY >= this.y + this.height - resizeBorder && mouseY <= this.y + this.height + resizeBorder;
            if (onRight && onBottom) resizeEdge = 3;
            else if (onRight) resizeEdge = 1;
            else if (onBottom) resizeEdge = 2;
            else resizeEdge = 0;
        }
        if (dragging) {
            x = mouseX - dragX;
            y = mouseY - dragY;
        }
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        ScaledResolution sr = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        if (x + width > sr.getScaledWidth()) x = sr.getScaledWidth() - width;
        if (y + height > sr.getScaledHeight()) y = sr.getScaledHeight() - height;

        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;

        String titleText = selectionMode ? Lang.get("sniffer.title.select") : Lang.get("sniffer.title");
        if (PacketSniffer.viewFilterSpecific != null && !selectionMode) {
            titleText += " [" + PacketSniffer.viewFilterSpecific + "]";
        }

        RenderUtils.drawRect(x, y, x + width, y + this.height, new Color(20, 20, 22, 220).getRGB());
        RenderUtils.drawRect(x, y, x + width, y + headerHeight, Theme.CATEGORY_BG.getRGB());

        float titleY = y + (headerHeight > 30 ? 6 : (headerHeight - titleFont.getHeight()) / 2.0f);
        titleFont.drawString(titleText, x + 5, titleY, Theme.ORANGE.getRGB());

        if (!selectionMode) {
            updateAndDrawHeaderButtons(mouseX, mouseY, font);
        }

        filterField.xPosition = x + 2;
        filterField.yPosition = y + this.height - footerHeight + 2;
        filterField.width = width - 4;

        RenderUtils.startScissor(x, y + headerHeight, width, this.height - headerHeight - footerHeight);
        int currentPacketY = y + headerHeight + 2 - scrollOffset;
        List<PacketInfo> filteredList = getFilteredPackets();

        for (PacketInfo info : filteredList) {
            if (currentPacketY + 11 > y + headerHeight && currentPacketY < y + this.height - footerHeight) {
                if (isMouseOver(mouseX, mouseY, x + 1, currentPacketY, width - 2, 11)) {
                    RenderUtils.drawRect(x + 1, currentPacketY, x + width - 1, currentPacketY + 11, Theme.ORANGE.darker().getRGB());
                }
                StringBuilder lineBuilder = new StringBuilder();
                if (info.isResent()) lineBuilder.append(EnumChatFormatting.AQUA).append("[R] ");
                lineBuilder.append(String.format("[%s] %s", info.direction, info.cleanName));
                if (info.getCount() > 1) lineBuilder.append(EnumChatFormatting.GRAY).append(" x").append(info.getCount());
                font.drawString(lineBuilder.toString(), x + 4, currentPacketY + 1, PacketSniffer.getPacketColor(info.rawPacket));
            }
            currentPacketY += 11;
        }
        RenderUtils.stopScissor();

        RenderUtils.drawRect(x, y + this.height - footerHeight, x + width, y + this.height, Theme.CATEGORY_BG.getRGB());
        filterField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (!selectionMode) {
            drawHeaderButtonTooltips(mouseX, mouseY);
        }
    }

    private List<PacketInfo> getFilteredPackets() {
        return PacketSniffer.packetLog.stream()
                .filter(info -> {
                    if (!filterText.isEmpty() && !info.cleanName.toLowerCase().contains(filterText.toLowerCase())) return false;
                    if (PacketSniffer.viewFmlOnly && !(info.rawPacket instanceof FMLProxyPacket)) return false;
                    if (PacketSniffer.viewFilterSpecific != null) {
                        boolean match = info.cleanName.equals(PacketSniffer.viewFilterSpecific);
                        if (info.rawPacket instanceof FMLProxyPacket) {
                            String ch = ((FMLProxyPacket)info.rawPacket).channel();
                            if (info.cleanName.contains(PacketSniffer.viewFilterSpecific)) match = true;
                            if (ch != null && ch.equals(PacketSniffer.viewFilterSpecific)) match = true;
                        }
                        if (!match) return false;
                    }
                    if (PacketSniffer.isPacketHidden(info.cleanName)) return false;
                    return true;
                })
                .collect(Collectors.toList());
    }

    private void updateAndDrawHeaderButtons(int mouseX, int mouseY, CustomFontRenderer font) {
        int startX = x + width - 5;
        int startY = y + 4;
        if (headerHeight > 30) {
            startY = y + 22;
        }
        int currentButtonX = startX;
        for (Button button : headerButtons) {
            if (!button.isVisible()) continue;
            String text = button.getDisplayText();
            int textWidth = font.getStringWidth(text);
            int buttonW = textWidth + 10;
            currentButtonX -= (buttonW + 4);
            button.x = currentButtonX;
            button.y = startY;
            button.width = buttonW;
            button.height = 16;
            button.draw(mouseX, mouseY);
        }
    }

    private void handleFilterClick(PacketInfo info) {
        if (info.rawPacket instanceof FMLProxyPacket) {
            PacketSniffer.viewFilterSpecific = ((FMLProxyPacket) info.rawPacket).channel();
        } else {
            PacketSniffer.viewFilterSpecific = info.cleanName;
        }
        scrollOffset = 0;
        ChatUtils.printMessage(EnumChatFormatting.GREEN + "[Sniffer] Filter set to: " + PacketSniffer.viewFilterSpecific);
        setupHeaderButtons();
    }

    private void drawHeaderButtonTooltips(int mouseX, int mouseY) {
        for (Button button : headerButtons) {
            if (button.isVisible() && isMouseOver(mouseX, mouseY, button.x, button.y, button.width, button.height)) {
                button.drawHoveringText(button.tooltip, mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        int snifferKeyCode = -1;
        ru.fuctorial.fuctorize.module.Module snifferModule = FuctorizeClient.INSTANCE.moduleManager.getModuleByKey("packetsniffer");
        if (snifferModule != null) {
            BindSetting bind = BindSetting.getBindSetting(snifferModule);
            if (bind != null) snifferKeyCode = bind.keyCode;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.previousScreen);
            return;
        }
        if (keyCode != Keyboard.KEY_NONE && keyCode == snifferKeyCode) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (filterField.isFocused()) {
            filterField.textboxKeyTyped(typedChar, keyCode);
            filterText = filterField.getText();
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            scrollOffset -= (dWheel > 0 ? 1 : -1) * 11;
            if (scrollOffset < 0) scrollOffset = 0;

            long count = PacketSniffer.packetLog.stream().filter(info -> {
                if (!filterText.isEmpty() && !info.cleanName.toLowerCase().contains(filterText.toLowerCase())) return false;
                if (PacketSniffer.viewFmlOnly && !(info.rawPacket instanceof FMLProxyPacket)) return false;
                if (PacketSniffer.viewFilterSpecific != null) {
                    boolean match = info.cleanName.equals(PacketSniffer.viewFilterSpecific);
                    if (info.rawPacket instanceof FMLProxyPacket) {
                        String ch = ((FMLProxyPacket)info.rawPacket).channel();
                        if (info.cleanName.contains(PacketSniffer.viewFilterSpecific)) match = true;
                        if (ch != null && ch.equals(PacketSniffer.viewFilterSpecific)) match = true;
                    }
                    if (!match) return false;
                }
                if (PacketSniffer.isPacketHidden(info.cleanName)) return false;
                return true;
            }).count();

            int maxScroll = Math.max(0, ((int)count * 11) - (height - headerHeight - footerHeight - 4));
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        }
    }

    private void handleRightClick(PacketInfo info) {
        if (isShiftKeyDown()) {
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(info.rawPacket);
                PacketSniffer.logManuallySentPacket(info.rawPacket);
                ChatUtils.printMessage(EnumChatFormatting.GREEN + Lang.format("sniffer.message.resend", info.cleanName));
            }
        } else {
            String data = info.getSerializedData();
            setClipboardString(data);
            ChatUtils.printMessage(EnumChatFormatting.GREEN + Lang.format("sniffer.message.copied", info.cleanName));
        }
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private class Button {
        String text, tooltip;
        int x, y, width, height;
        Runnable action;
        boolean enabled = true;

        Button(String text, String tooltip, Runnable action) {
            this.text = text;
            this.tooltip = tooltip;
            this.action = action;
        }

        boolean isVisible() { return true; }
        String getDisplayText() { return text; }
        int getTextColor() { return enabled ? -1 : Theme.TEXT_GRAY.getRGB(); }

        void draw(int mouseX, int mouseY) {
            boolean hovered = enabled && isMouseOver(mouseX, mouseY, x, y, width, height);
            Color bgColor = hovered ? Theme.COMPONENT_BG_HOVER : Theme.COMPONENT_BG;
            if (!enabled) bgColor = Theme.DISABLED_INDICATOR;

            RenderUtils.drawRect(x, y, x + width, y + height, bgColor.getRGB());

            String displayText = getDisplayText();
            CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
            float textX = x + (width - font.getStringWidth(displayText)) / 2.0f;
            float textY = y + (height - font.getHeight()) / 2.0f;
            font.drawString(displayText, textX, textY, getTextColor());
        }

        void drawHoveringText(String text, int mouseX, int mouseY) {
            if (!enabled || text == null || text.isEmpty()) return;
            CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
            int textWidth = font.getStringWidth(text);
            int paddingX = 4, paddingY = 2;
            int tipWidth = textWidth + (paddingX * 2);
            int tipHeight = font.getHeight() + (paddingY * 2);

            ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int renderX = mouseX + 12;
            int renderY = mouseY - 12;
            if (renderX + tipWidth > sr.getScaledWidth()) renderX = mouseX - tipWidth - 8;
            if (renderY + tipHeight > sr.getScaledHeight()) renderY = sr.getScaledHeight() - tipHeight - 2;
            if (renderY < 0) renderY = 2;

            int bgColor = new Color(0, 0, 0, 210).getRGB();
            RenderUtils.drawRect(renderX, renderY, renderX + tipWidth, renderY + tipHeight, bgColor);

            int borderColor = Theme.ORANGE.getRGB();
            RenderUtils.drawRect(renderX, renderY, renderX + tipWidth, renderY + 0.5f, borderColor);
            RenderUtils.drawRect(renderX, renderY + tipHeight - 0.5f, renderX + tipWidth, renderY + tipHeight, borderColor);
            RenderUtils.drawRect(renderX, renderY, renderX + 0.5f, renderY + tipHeight, borderColor);
            RenderUtils.drawRect(renderX + tipWidth - 0.5f, renderY, renderX + tipWidth, renderY + tipHeight, borderColor);

            font.drawString(text, renderX + paddingX, renderY + paddingY, -1);
        }

        void onClick() { if (enabled) action.run(); }
    }
}
