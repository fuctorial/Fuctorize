 
package ru.fuctorial.fuctorize.client.gui.spammer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public class GuiPlaceholderInfo extends GuiScreen {
    private final GuiScreen parentScreen;
    private int panelX, panelY, panelWidth, panelHeight;

    public GuiPlaceholderInfo(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        this.panelWidth = 400;
         
        this.panelHeight = 235;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.buttonList.clear();
         
        this.buttonList.add(new StyledButton(0, panelX + (panelWidth / 2) - 50, panelY + panelHeight - 30, 100, 20, "Назад"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
            mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        RenderUtils.drawRect(0, 0, this.width, this.height, 0x90000000);  

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer boldFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        String title = "Информация о плейсхолдерах";
        boldFont.drawString(title, panelX + (panelWidth - boldFont.getStringWidth(title)) / 2f, panelY + 10, -1);

        String infoText =
                "§6{rand:N}§r - Генерирует случайную строку.\n" +
                        "§7  - §eN§r: Длина строки (например, {rand:10}).\n" +
                        "§7  - Символы для генерации настраиваются\n" +
                        "    в 'Настройках плейсхолдеров'.\n\n" +

                        "§6{seq}§r - Последовательная подстановка.\n" +
                        "§7  - Поочередно подставляет слова из списка.\n" +
                        "§7  - Список задается в 'Настройках плейсхолдеров'\n" +
                        "    через запятую (например: один,два,три).\n\n" +

                        "§6{file:\"путь\"}§r - Чтение из файла.\n" +
                        "§7  - Поочередно подставляет строки из файла.\n" +
                        "§7  - Путь должен быть в кавычках и указывать на\n" +
                        "    .txt или .csv файл в кодировке UTF-8.";

         
        font.drawSplitString(infoText, panelX + 15, panelY + 35, panelWidth - 30, -1);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}