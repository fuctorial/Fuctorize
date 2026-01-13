package ru.fuctorial.fuctorize.client.gui.sniffer;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.module.impl.PacketBlocker;
import ru.fuctorial.fuctorize.utils.ChatUtils;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class GuiPacketBlocker extends GuiScreen {

    private final GuiScreen parentScreen;
    private int x, y, panelWidth, panelHeight;
    private List<Row> rows = new ArrayList<>();
    private int scrollOffset = 0;
    private int totalContentHeight = 0;

    public GuiPacketBlocker(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        this.panelWidth = Math.min(500, this.width - 40); // Чуть шире, чтобы влез хекс
        this.panelHeight = Math.min(350, this.height - 60);
        this.x = (this.width - this.panelWidth) / 2;
        this.y = (this.height - this.panelHeight) / 2;

        this.buttonList.clear();
        int btnWidth = Math.min(150, panelWidth - 20);
        this.buttonList.add(new StyledButton(0, x + (panelWidth - btnWidth) / 2, y + panelHeight - 30, btnWidth, 20, "Назад"));

        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        // Добавляем классы
        for (String cls : PacketBlocker.blockedClasses) {
            rows.add(new Row(cls, null));
        }
        // Добавляем правила (каналы)
        for (PacketBlocker.BlockedPacketRule rule : PacketBlocker.blockedRules) {
            rows.add(new Row(rule.channelName, rule.payloadHex));
        }
        // Сортировка: сначала классы, потом каналы
        rows.sort((r1, r2) -> {
            boolean r1IsCh = r1.isChannel();
            boolean r2IsCh = r2.isChannel();
            if (r1IsCh != r2IsCh) return Boolean.compare(r1IsCh, r2IsCh);
            return r1.displayName.compareToIgnoreCase(r2.displayName);
        });
        totalContentHeight = rows.size() * 18 + 25; // + место под кнопку Add
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) this.mc.displayGuiScreen(parentScreen);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        int topBounds = y + 30;
        int bottomBounds = y + panelHeight - 35;
        if (mouseX < x || mouseX > x + panelWidth || mouseY < topBounds || mouseY > bottomBounds) return;

        int currentY = topBounds - scrollOffset;

        // --- КНОПКА ДОБАВЛЕНИЯ ---
        if (isMouseOver(mouseX, mouseY, x + 10, currentY, panelWidth - 20, 15)) {
            if (mouseButton == 0) {
                // ОТКРЫВАЕМ SNIFFER В РЕЖИМЕ ВЫБОРА
                GuiPacketSniffer sniffer = new GuiPacketSniffer(this);

                // Передаем лямбду: что делать с выбранным пакетом
                sniffer.setSelectionMode(this, (info) -> {
                    if (info.rawPacket instanceof FMLProxyPacket) {
                        FMLProxyPacket fml = (FMLProxyPacket) info.rawPacket;
                        String channel = fml.channel();
                        
                        // Проверяем Shift для блокировки всего канала
                        if (isShiftKeyDown()) {
                            PacketBlocker.addRule(channel, null);
                            ChatUtils.printMessage(EnumChatFormatting.RED + "[Blocker] Blocked entire channel: " + channel);
                        } else {
                            // Иначе блочим конкретный пакет
                            String payload = PacketBlocker.getPayloadHex(fml);
                            PacketBlocker.addRule(channel, payload);
                            ChatUtils.printMessage(EnumChatFormatting.RED + "[Blocker] Blocked specific packet on channel: " + channel);
                        }
                    } else {
                        // Иначе блокируем класс
                        PacketBlocker.addClassBlock(info.rawPacket.getClass().getName());
                        ChatUtils.printMessage(EnumChatFormatting.RED + "[Blocker] Blocked class: " + info.rawPacket.getClass().getSimpleName());
                    }
                });

                mc.displayGuiScreen(sniffer);
            }
            return;
        }
        currentY += 20; // Отступ после кнопки

        // --- КНОПКИ УДАЛЕНИЯ ---
        for (Row row : rows) {
            if (row.isMinusClicked(mouseX, mouseY, currentY)) {
                if (mouseButton == 0) {
                    if (row.isChannel()) {
                        PacketBlocker.removeRule(row.key, row.payloadHex);
                    } else {
                        PacketBlocker.removeClassBlock(row.key);
                    }
                    initGui(); // Перерисовать список
                    return;
                }
            }
            currentY += 18;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            int viewableHeight = panelHeight - 65;
            if (totalContentHeight > viewableHeight) {
                scrollOffset -= (dWheel > 0 ? 1 : -1) * 18;
                if (scrollOffset < 0) scrollOffset = 0;
                int maxScroll = Math.max(0, totalContentHeight - viewableHeight);
                if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;

        // Отрисовка фона
        RenderUtils.drawRect(x, y, x + panelWidth, y + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(x - 1, y - 1, x + panelWidth + 1, y, Theme.BORDER.getRGB());
        RenderUtils.drawRect(x - 1, y - 1, x, y + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(x + panelWidth, y - 1, x + panelWidth + 1, y + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(x - 1, y + panelHeight, x + panelWidth + 1, y + panelHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        String title = "Packet Blocker";
        titleFont.drawString(title, x + (panelWidth - titleFont.getStringWidth(title)) / 2f, y + 8, Theme.ORANGE.getRGB());

        int topBounds = y + 30;
        int bottomBounds = y + panelHeight - 35;
        RenderUtils.startScissor(x + 5, topBounds, panelWidth - 10, bottomBounds - topBounds);

        int currentY = topBounds - scrollOffset;

        // Рисуем кнопку добавления
        boolean addHover = isMouseOver(mouseX, mouseY, x + 10, currentY, panelWidth - 20, 15);
        RenderUtils.drawRect(x + 10, currentY, x + panelWidth - 10, currentY + 15, addHover ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());
        String addText = "[ + ] Добавить (Shift = весь канал)";
        font.drawString(addText, x + (panelWidth - font.getStringWidth(addText)) / 2f, currentY + 4, Theme.ORANGE.getRGB());
        currentY += 20;

        // Рисуем список
        for (Row row : rows) {
            if (currentY + 18 > topBounds && currentY < bottomBounds) {
                row.draw(mouseX, mouseY, currentY, x, panelWidth);
            }
            currentY += 18;
        }

        RenderUtils.stopScissor();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parentScreen);
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // Вспомогательный класс для строки
    private class Row {
        String key;
        String payloadHex; // null if class or full channel
        String displayName;

        Row(String key, String payloadHex) {
            this.key = key;
            this.payloadHex = payloadHex;
            
            if (payloadHex == null) {
                // Это либо класс, либо полный канал
                // Определим по наличию в blockedClasses, но здесь мы просто передали key
                // Если это класс, он обычно содержит точки и CamelCase
                if (PacketBlocker.blockedClasses.contains(key)) {
                    if (key.contains(".")) {
                        this.displayName = key.substring(key.lastIndexOf('.') + 1).replace('$', '.');
                    } else {
                        this.displayName = key;
                    }
                } else {
                    this.displayName = "[ALL] " + key;
                }
            } else {
                // Это конкретный пакет канала
                String shortHex = payloadHex.length() > 20 ? payloadHex.substring(0, 20) + "..." : payloadHex;
                this.displayName = "[" + shortHex + "] " + key;
            }
        }
        
        boolean isChannel() {
             return !PacketBlocker.blockedClasses.contains(key);
        }

        void draw(int mouseX, int mouseY, int yPos, int panelX, int panelWidth) {
            int rowX = panelX + 10;
            int rowW = panelWidth - 20;
            int rowH = 16;

            boolean hover = isMouseOver(mouseX, mouseY, rowX, yPos, rowW, rowH);
            RenderUtils.drawRect(rowX, yPos, rowX + rowW, yPos + rowH, hover ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());

            CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
            font.drawString(displayName, rowX + 4, yPos + 4, -1);

            // Кнопка удаления
            int minusX = rowX + rowW - 16;
            boolean minusHover = isMouseOver(mouseX, mouseY, minusX, yPos, 16, rowH);
            int delColor = minusHover ? new Color(255, 50, 50, 200).getRGB() : new Color(200, 50, 50, 150).getRGB();
            RenderUtils.drawRect(minusX, yPos, minusX + 16, yPos + rowH, delColor);
            font.drawString("-", minusX + 5, yPos + 4, -1);
        }

        boolean isMinusClicked(int mouseX, int mouseY, int yPos) {
            int rowX = x + 10;
            int rowW = panelWidth - 20;
            int minusX = rowX + rowW - 16;
            return isMouseOver(mouseX, mouseY, minusX, yPos, 16, 16);
        }
    }
}