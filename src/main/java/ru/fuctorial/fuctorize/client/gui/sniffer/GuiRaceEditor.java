 
package ru.fuctorial.fuctorize.client.gui.sniffer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;  
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.manager.FavoritePacketManager;
import ru.fuctorial.fuctorize.manager.PacketPersistence;
import ru.fuctorial.fuctorize.module.impl.RaceTest;
import ru.fuctorial.fuctorize.utils.RenderUtils;

import java.awt.Color;
import java.util.List;

public class GuiRaceEditor extends GuiScreen {

    private final RaceTest module;
    private int panelX, panelY, panelWidth, panelHeight;

     
    private int listWidth;
    private int listHeight;

     
    private int leftScroll = 0;
    private int rightScroll = 0;
    private int maxLeftScroll = 0;
    private int maxRightScroll = 0;

    public GuiRaceEditor(RaceTest module) {
        this.module = module;
    }

    @Override
    public void initGui() {
         
        this.panelWidth = 600;
        this.panelHeight = 350;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

         
        this.listWidth = (int) ((panelWidth - 40) * 0.5);
        this.listHeight = panelHeight - 80;  

        this.buttonList.clear();

        int btnY = panelY + panelHeight - 30;
        int btnW = 120;

         
        this.buttonList.add(new StyledButton(0, panelX + 20, btnY, btnW, 20, getStartBtnText()));
        this.buttonList.add(new StyledButton(1, panelX + panelWidth / 2 - btnW / 2, btnY, btnW, 20, "Clear Sequence"));
        this.buttonList.add(new StyledButton(2, panelX + panelWidth - 20 - btnW, btnY, btnW, 20, "Close"));
    }

    private String getStartBtnText() {
        return module.isRunning() ? EnumChatFormatting.RED + "STOP RACE" : EnumChatFormatting.GREEN + "START RACE";
    }

    @Override
    public void updateScreen() {
        if (!this.buttonList.isEmpty()) {
            ((GuiButton)this.buttonList.get(0)).displayString = getStartBtnText();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (module.isRunning()) module.stopRace();
            else module.startRace();
        } else if (button.id == 1) {
            module.clearSequence();
        } else if (button.id == 2) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        int listTop = panelY + 40;  
        int leftListX = panelX + 15;
        int rightListX = panelX + panelWidth - 15 - listWidth;

         
        if (mouseX >= leftListX && mouseX <= leftListX + listWidth && mouseY >= listTop && mouseY <= listTop + listHeight) {
            int idx = (mouseY - listTop + leftScroll) / 18;
            List<PacketPersistence.SavedPacketData> favs = FavoritePacketManager.getFavorites();
            if (idx >= 0 && idx < favs.size()) {
                module.addToSequence(favs.get(idx));
            }
        }

         
        if (mouseX >= rightListX && mouseX <= rightListX + listWidth && mouseY >= listTop && mouseY <= listTop + listHeight) {
            int idx = (mouseY - listTop + rightScroll) / 18;
            if (idx >= 0 && idx < module.getSequence().size()) {
                module.removeFromSequence(idx);
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / mc.displayWidth;
             
            if (mouseX < panelX + panelWidth / 2) {
                leftScroll -= (dWheel > 0 ? 1 : -1) * 18;
                leftScroll = Math.max(0, Math.min(leftScroll, maxLeftScroll));
            } else {
                rightScroll -= (dWheel > 0 ? 1 : -1) * 18;
                rightScroll = Math.max(0, Math.min(rightScroll, maxRightScroll));
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        CustomFontRenderer boldFont = FuctorizeClient.INSTANCE.fontManager.bold_22;

         
        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        drawBorder(panelX, panelY, panelWidth, panelHeight, Theme.BORDER.getRGB());

         
        String title = "Race Condition Tester";
        boldFont.drawString(title, panelX + (panelWidth - boldFont.getStringWidth(title)) / 2f, panelY + 8, Theme.ORANGE.getRGB());

         
        int listTop = panelY + 40;
        int leftListX = panelX + 15;
        int rightListX = panelX + panelWidth - 15 - listWidth;

         
        font.drawString("Saved Packets (Click to Add)", leftListX, listTop - 12, Theme.TEXT_GRAY.getRGB());

         
        RenderUtils.drawRect(leftListX, listTop, leftListX + listWidth, listTop + listHeight, 0xFF151515);
        drawBorder(leftListX - 1, listTop - 1, listWidth + 2, listHeight + 2, 0xFF404040);

        List<PacketPersistence.SavedPacketData> favs = FavoritePacketManager.getFavorites();
         
        maxLeftScroll = Math.max(0, (favs.size() * 18) - listHeight);

        RenderUtils.startScissor(leftListX, listTop, listWidth, listHeight);
        int yL = listTop - leftScroll;

        for (PacketPersistence.SavedPacketData data : favs) {
            if (yL + 18 > listTop && yL < listTop + listHeight) {
                boolean hover = mouseX >= leftListX && mouseX <= leftListX + listWidth && mouseY >= yL && mouseY < yL + 18;
                if (hover) RenderUtils.drawRect(leftListX, yL, leftListX + listWidth, yL + 18, Theme.COMPONENT_BG_HOVER.getRGB());

                 
                String name = font.trimStringToWidth(data.name, listWidth - 60);  
                font.drawString(name, leftListX + 4, yL + 5, -1);

                 
                String typeRaw = data.className.substring(data.className.lastIndexOf('.') + 1);
                String type = font.trimStringToWidth(typeRaw, 55);
                font.drawString(type, leftListX + listWidth - font.getStringWidth(type) - 4, yL + 5, Theme.TEXT_GRAY.getRGB());
            }
            yL += 18;
        }
        RenderUtils.stopScissor();

         
        font.drawString("Sequence (Click to Remove)", rightListX, listTop - 12, Theme.TEXT_GRAY.getRGB());

         
        RenderUtils.drawRect(rightListX, listTop, rightListX + listWidth, listTop + listHeight, 0xFF151515);
        drawBorder(rightListX - 1, listTop - 1, listWidth + 2, listHeight + 2, 0xFF404040);

        List<PacketPersistence.SavedPacketData> sequence = module.getSequence();
        maxRightScroll = Math.max(0, (sequence.size() * 18) - listHeight);

        RenderUtils.startScissor(rightListX, listTop, listWidth, listHeight);
        int yR = listTop - rightScroll;
        int index = 1;

        for (PacketPersistence.SavedPacketData data : sequence) {
            if (yR + 18 > listTop && yR < listTop + listHeight) {
                boolean hover = mouseX >= rightListX && mouseX <= rightListX + listWidth && mouseY >= yR && mouseY < yR + 18;
                 
                if (hover) RenderUtils.drawRect(rightListX, yR, rightListX + listWidth, yR + 18, 0x40FF0000);

                String line = index + ". " + data.name;
                 
                line = font.trimStringToWidth(line, listWidth - 10);

                font.drawString(line, rightListX + 4, yR + 5, -1);
            }
            yR += 18;
            index++;
        }
        RenderUtils.stopScissor();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawBorder(int x, int y, int w, int h, int color) {
        RenderUtils.drawRect(x, y, x + w, y + 1, color);  
        RenderUtils.drawRect(x, y + h - 1, x + w, y + h, color);  
        RenderUtils.drawRect(x, y, x + 1, y + h, color);  
        RenderUtils.drawRect(x + w - 1, y, x + w, y + h, color);  
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(null);
    }
}