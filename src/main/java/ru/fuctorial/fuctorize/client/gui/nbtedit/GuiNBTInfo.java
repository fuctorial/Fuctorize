package ru.fuctorial.fuctorize.client.gui.nbtedit;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GuiNBTInfo extends GuiScreen {

    private final GuiScreen parentScreen;
    private float scrollOffset = 0;
    private float totalContentHeight = 0;
    private int viewableTextHeight = 0;

    public GuiNBTInfo(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new StyledButton(0, this.width / 2 - 75, this.height - 35, 150, 20, ru.fuctorial.fuctorize.utils.Lang.get("nbtedit.button.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            if (totalContentHeight > this.viewableTextHeight) {
                this.scrollOffset += (scroll > 0) ? 15 : -15;
                if (this.scrollOffset > 0) {
                    this.scrollOffset = 0;
                }
                float maxScroll = totalContentHeight - this.viewableTextHeight;
                if (maxScroll < 0) maxScroll = 0;
                if (this.scrollOffset < -maxScroll) {
                    this.scrollOffset = -maxScroll;
                }
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

        int windowPadding = 25;
        int windowX = windowPadding;
        int windowY = windowPadding;
        int windowWidth = this.width - windowPadding * 2;
        int windowHeight = this.height - windowPadding * 2;

        Gui.drawRect(windowX, windowY, windowX + windowWidth, windowY + windowHeight, Theme.CATEGORY_BG.getRGB());
        Gui.drawRect(windowX - 1, windowY - 1, windowX + windowWidth + 1, windowY, Theme.BORDER.getRGB());
        Gui.drawRect(windowX - 1, windowY - 1, windowX, windowY + windowHeight + 1, Theme.BORDER.getRGB());
        Gui.drawRect(windowX + windowWidth, windowY - 1, windowX + windowWidth + 1, windowY + windowHeight + 1, Theme.BORDER.getRGB());
        Gui.drawRect(windowX - 1, windowY + windowHeight, windowX + windowWidth + 1, windowY + windowHeight + 1, Theme.BORDER.getRGB());

        String title = "NBT Editor Information";
        int titleY = windowY + 10;
        FuctorizeClient.INSTANCE.fontManager.bold_22.drawString(title, this.width / 2.0f - FuctorizeClient.INSTANCE.fontManager.bold_22.getStringWidth(title) / 2.0f, titleY, Theme.ORANGE.getRGB());

        int textHorizontalPadding = 15;
        int scrollbarWidth = 4;
        int scrollbarAreaWidth = 10;

        int textX = windowX + textHorizontalPadding;
        int maxTextWidth = windowWidth - (textHorizontalPadding * 2);

        int topTextY = titleY + FuctorizeClient.INSTANCE.fontManager.bold_22.getHeight() + 10;
        int bottomTextY = windowY + windowHeight - 10;
        this.viewableTextHeight = bottomTextY - topTextY;

        String fullHelpText =
                "§l--- NBT Editor Help ---\n\n" +
                        "This tool allows you to modify the NBT data of items, entities, and blocks.\n\n" +
                        "§6§lToolbar Buttons:\n" +
                        "§eCopy/Cut/Paste/Edit/Delete:§f Standard clipboard actions for the selected tag.\n" +
                        "§eC, B, S, I, L, ...:§f Adds a new tag of the specified type inside the\n" +
                        "selected folder (Compound or List). §7(C=Compound, Str=String, BByte, etc.)\n\n" +
                        "§6§lBottom Panel:\n" +
                        "§eSave:§f Saves the structure from the visual tree and applies it to the item.\n" +
                        "§eCancel:§f Closes the editor, discarding all changes.\n" +
                        "§eTo JSON:§f Converts the visual tree into a text string in the input box.\n" +
                        "§eFrom JSON:§f Parses the text from the input box and immediately applies it,\n" +
                        "replacing any existing NBT data.\n\n" +
                        "§6§lHotkeys:\n" +
                        "§eEnter:§f Edit the selected tag or expand/collapse a folder.\n" +
                        "§eDelete:§f Delete the selected tag.\n" +
                        "§eArrow Keys:§f Navigate up and down the tree.\n" +
                        "§eMouse Wheel:§f Scroll the tree view.";


        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        List<String> wrappedLines = font.wrapText(fullHelpText, maxTextWidth - scrollbarAreaWidth);
        totalContentHeight = (font.getHeight() + 2) * wrappedLines.size();

        RenderUtils.startScissor(windowX, topTextY, windowWidth - scrollbarAreaWidth, this.viewableTextHeight);

        GL11.glPushMatrix();
        GL11.glTranslatef(0, scrollOffset, 0);

        int yPos = topTextY;
        for (String line : wrappedLines) {
            font.drawString(line, textX, yPos, Theme.TEXT_WHITE.getRGB());
            yPos += font.getHeight() + 2;
        }

        GL11.glPopMatrix();
        RenderUtils.stopScissor();

        if (totalContentHeight > this.viewableTextHeight) {
            int scrollbarX = windowX + windowWidth - scrollbarAreaWidth + 2;
            Gui.drawRect(scrollbarX, topTextY, scrollbarX + scrollbarWidth, bottomTextY, 0x55000000);

            float scrollPercent = -scrollOffset / (totalContentHeight - this.viewableTextHeight);
            int handleHeight = (int) ((float) this.viewableTextHeight / totalContentHeight * this.viewableTextHeight);
            handleHeight = Math.max(handleHeight, 20);
            int handleY = topTextY + (int) (scrollPercent * (this.viewableTextHeight - handleHeight));

            Gui.drawRect(scrollbarX, handleY, scrollbarX + scrollbarWidth, handleY + handleHeight, Theme.ORANGE.getRGB());
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
