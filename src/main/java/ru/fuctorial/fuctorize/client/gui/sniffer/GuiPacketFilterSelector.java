package ru.fuctorial.fuctorize.client.gui.sniffer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.module.impl.PacketSniffer;
import ru.fuctorial.fuctorize.utils.PacketInfo;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GuiPacketFilterSelector extends GuiScreen {

    private final GuiScreen parentScreen;
    private int panelX, panelY, panelWidth, panelHeight;
    private int scrollOffset = 0;
    private int totalContentHeight = 0;
    private int viewableHeight = 0;

    // Список всех уникальных имен, найденных в истории
    private List<String> uniquePacketNames = new ArrayList<>();

    public GuiPacketFilterSelector(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        this.panelWidth = 350;
        this.panelHeight = Math.min(450, this.height - 60);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        // Сканируем историю на уникальные имена
        Set<String> names = PacketSniffer.packetLog.stream()
                .map(info -> info.cleanName)
                .collect(Collectors.toSet());

        // Также добавляем те, которые уже скрыты (даже если их сейчас нет в логе, чтобы можно было включить обратно)
        names.addAll(PacketSniffer.hiddenPackets);

        this.uniquePacketNames = new ArrayList<>(names);
        Collections.sort(this.uniquePacketNames);

        this.totalContentHeight = uniquePacketNames.size() * 18;
        this.viewableHeight = panelHeight - 40 - 35;

        this.buttonList.clear();

        // Кнопки управления
        int btnY = panelY + panelHeight - 30;
        int btnW = (panelWidth - 30) / 3;

        this.buttonList.add(new StyledButton(0, panelX + 10, btnY, btnW, 20, "Select All"));
        this.buttonList.add(new StyledButton(1, panelX + 15 + btnW, btnY, btnW, 20, "Deselect All"));
        this.buttonList.add(new StyledButton(2, panelX + 20 + btnW * 2, btnY, btnW, 20, "Close"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            PacketSniffer.clearExcelFilter(); // Очистить скрытые = показать все
        } else if (button.id == 1) {
            PacketSniffer.hiddenPackets.addAll(uniquePacketNames); // Скрыть все известные
        } else if (button.id == 2) {
            mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        int top = panelY + 35;
        int bottom = panelY + panelHeight - 35;

        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= top && mouseY <= bottom) {
            int adjustedY = mouseY - top + scrollOffset;
            int index = adjustedY / 18;

            if (index >= 0 && index < uniquePacketNames.size()) {
                String name = uniquePacketNames.get(index);
                // Переключаем состояние: если скрыт -> показать, если показан -> скрыть
                boolean isHidden = PacketSniffer.isPacketHidden(name);
                PacketSniffer.setPacketHidden(name, !isHidden);
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (totalContentHeight > viewableHeight) {
                scrollOffset -= (dWheel > 0 ? 1 : -1) * 18;
                if (scrollOffset < 0) scrollOffset = 0;
                int maxScroll = totalContentHeight - viewableHeight;
                if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        String title = "Packet Filter (Excel Mode)";
        titleFont.drawString(title, panelX + (panelWidth - titleFont.getStringWidth(title)) / 2f, panelY + 10, Theme.ORANGE.getRGB());

        int top = panelY + 35;
        RenderUtils.startScissor(panelX, top, panelWidth, viewableHeight);

        int currentY = top - scrollOffset;

        for (String name : uniquePacketNames) {
            // Оптимизация рендера
            if (currentY + 18 > top && currentY < top + viewableHeight) {
                boolean isHidden = PacketSniffer.isPacketHidden(name);
                boolean isVisible = !isHidden;

                boolean isHovered = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 20
                        && mouseY >= currentY && mouseY < currentY + 18;

                // Фон строки
                if (isHovered) {
                    RenderUtils.drawRect(panelX + 10, currentY, panelX + panelWidth - 10, currentY + 18, Theme.COMPONENT_BG_HOVER.getRGB());
                }

                // Checkbox box
                int boxSize = 10;
                int boxX = panelX + 15;
                int boxY = currentY + 4;

                RenderUtils.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xFF000000); // outline
                RenderUtils.drawRect(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, 0xFF303030); // bg

                if (isVisible) {
                    // Галочка (зеленый квадрат внутри)
                    RenderUtils.drawRect(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, Theme.ORANGE.getRGB());
                }

                // Текст
                int textColor = isVisible ? -1 : Theme.TEXT_GRAY.getRGB();
                font.drawString(name, boxX + 15, currentY + 5, textColor);
            }
            currentY += 18;
        }

        RenderUtils.stopScissor();

        // Скроллбар
        if (totalContentHeight > viewableHeight) {
            int scrollbarX = panelX + panelWidth - 6;
            RenderUtils.drawRect(scrollbarX, top, scrollbarX + 4, top + viewableHeight, 0x55000000);
            float scrollPercent = (float) scrollOffset / (totalContentHeight - viewableHeight);
            int handleHeight = (int) ((float) viewableHeight / totalContentHeight * viewableHeight);
            handleHeight = Math.max(handleHeight, 20);
            int handleY = top + (int) (scrollPercent * (viewableHeight - handleHeight));
            RenderUtils.drawRect(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, Theme.ORANGE.getRGB());
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parentScreen);
    }
}