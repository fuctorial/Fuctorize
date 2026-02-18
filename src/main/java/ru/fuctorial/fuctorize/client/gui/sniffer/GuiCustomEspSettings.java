 
package ru.fuctorial.fuctorize.client.gui.sniffer;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.module.impl.CustomESP;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GuiCustomEspSettings extends GuiScreen {

    private final GuiScreen parentScreen;
    private final CustomESP module;

    private int panelX, panelY, panelWidth, panelHeight;
    private int scrollOffset = 0;

     
    private GuiTextField addKeyField;
    private GuiTextField addValueField;

     
    private GuiTextField searchField;

     
    private final List<FilterEntry> filters = new ArrayList<>();

    public GuiCustomEspSettings(GuiScreen parent, CustomESP module) {
        this.parentScreen = parent;
        this.module = module;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.panelWidth = 450;
        this.panelHeight = 340;  
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.buttonList.clear();

         
        if (filters.isEmpty()) {
            for (Map.Entry<String, String> entry : module.getNbtFilterMap().entrySet()) {
                filters.add(new FilterEntry(entry.getKey(), entry.getValue(), true));
            }
        }

         
         
        searchField = new GuiTextField(panelX + 10, panelY + 25, panelWidth - 20, 16, false);
        searchField.setPlaceholder("Search filter (key or value)...");

         
         
        int bottomControlsHeight = 85;
        int bottomY = panelY + panelHeight - bottomControlsHeight;

        addKeyField = new GuiTextField(panelX + 10, bottomY, 150, 18, false);
        addKeyField.setPlaceholder("Key (e.g. id)");

        addValueField = new GuiTextField(panelX + 170, bottomY, 150, 18, false);
        addValueField.setPlaceholder("Value (e.g. Diamond)");

         
        this.buttonList.add(new StyledButton(1, panelX + 330, bottomY, 110, 18, "+ Add"));

         
        int footerY = panelY + panelHeight - 30;
        int btnWidth = (panelWidth - 40) / 3;

        this.buttonList.add(new StyledButton(2, panelX + 10, footerY - 25, btnWidth, 20, "Select All"));
        this.buttonList.add(new StyledButton(3, panelX + 20 + btnWidth, footerY - 25, btnWidth, 20, "Deselect All"));
        this.buttonList.add(new StyledButton(0, panelX + 30 + btnWidth * 2, footerY - 25, btnWidth, 20, Lang.get("generic.button.save")));
    }

     
    private List<FilterEntry> getVisibleFilters() {
        String query = searchField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            return filters;
        }
        return filters.stream()
                .filter(entry -> entry.key.toLowerCase().contains(query) || entry.value.toLowerCase().contains(query))
                .collect(Collectors.toList());
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {  
            applyChanges();
            mc.displayGuiScreen(parentScreen);
        } else if (button.id == 1) {  
            String k = addKeyField.getText().trim();
            String v = addValueField.getText().trim();
            if (!k.isEmpty()) {
                filters.add(new FilterEntry(k, v, true));
                addKeyField.setText("");
                addValueField.setText("");
                 
                searchField.setText("");
            }
        } else if (button.id == 2) {  
            for (FilterEntry entry : getVisibleFilters()) {
                entry.enabled = true;
            }
        } else if (button.id == 3) {  
            for (FilterEntry entry : getVisibleFilters()) {
                entry.enabled = false;
            }
        }
    }

    private void applyChanges() {
        module.getNbtFilterMap().clear();
        for (FilterEntry entry : filters) {
            if (entry.enabled) {
                module.getNbtFilterMap().put(entry.key, entry.value);
            }
        }
        module.forceUpdate();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        addKeyField.mouseClicked(mouseX, mouseY, mouseButton);
        addValueField.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);

         
         
        int listTop = panelY + 50;
         
        int listBottom = panelY + panelHeight - 105;

        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= listTop && mouseY <= listBottom) {
            int currentY = listTop - scrollOffset;

             
            List<FilterEntry> visibleFilters = getVisibleFilters();

            for (FilterEntry entry : visibleFilters) {
                if (mouseY >= currentY && mouseY <= currentY + 18) {
                    if (mouseButton == 0) {
                        entry.enabled = !entry.enabled;
                        return;
                    }
                }
                currentY += 20;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        addKeyField.textboxKeyTyped(typedChar, keyCode);
        addValueField.textboxKeyTyped(typedChar, keyCode);

        if (searchField.isFocused()) {
            searchField.textboxKeyTyped(typedChar, keyCode);
             
            scrollOffset = 0;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parentScreen);
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            if (addKeyField.isFocused() || addValueField.isFocused()) {
                actionPerformed((GuiButton) this.buttonList.get(0));  
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            int listHeight = getVisibleFilters().size() * 20;
             
            int viewHeight = panelHeight - 155;

            if (listHeight > viewHeight) {
                scrollOffset -= Integer.signum(dWheel) * 15;
                if (scrollOffset < 0) scrollOffset = 0;
                int max = listHeight - viewHeight;
                if (scrollOffset > max) scrollOffset = max;
            } else {
                scrollOffset = 0;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (FuctorizeClient.INSTANCE.fontManager == null) return;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
         
        RenderUtils.drawRect(panelX - 1, panelY, panelX, panelY + panelHeight, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY, panelX + panelWidth + 1, panelY + panelHeight, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX, panelY - 1, panelX + panelWidth, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX, panelY + panelHeight, panelX + panelWidth, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        titleFont.drawString("Настройка фильтра CustomESP", panelX + 10, panelY + 8, Theme.ORANGE.getRGB());

         
        searchField.drawTextBox();

         
        int listTop = panelY + 50;
        int listBottom = panelY + panelHeight - 105;

        RenderUtils.startScissor(panelX, listTop, panelWidth, listBottom - listTop);

        List<FilterEntry> visibleFilters = getVisibleFilters();
        int currentY = listTop - scrollOffset;

        if (visibleFilters.isEmpty()) {
            if (filters.isEmpty()) {
                font.drawString("Фильтры не заданы. Будет подсвечиваться всё.", panelX + 20, currentY + 10, Theme.TEXT_GRAY.getRGB());
            } else {
                font.drawString("Ничего не найдено.", panelX + 20, currentY + 10, Theme.TEXT_GRAY.getRGB());
            }
        }

        for (FilterEntry entry : visibleFilters) {
             
            if (currentY + 20 > listTop && currentY < listBottom) {
                boolean isHovered = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10
                        && mouseY >= currentY && mouseY < currentY + 18;

                int bgColor = isHovered ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB();
                RenderUtils.drawRect(panelX + 10, currentY, panelX + panelWidth - 20, currentY + 18, bgColor);

                drawCheckbox(panelX + 15, currentY + 4, entry.enabled);

                int textColor = entry.enabled ? -1 : Theme.TEXT_GRAY.getRGB();
                 
                String fullText = entry.key + " = " + entry.value;
                String displayKey = font.trimStringToWidth(entry.key, 130);
                String displayVal = font.trimStringToWidth(entry.value, 200);

                if (entry.enabled) {
                     
                    font.drawString("§b" + displayKey + " §7= §f" + displayVal, panelX + 30, currentY + 5, textColor);
                } else {
                    font.drawString(displayKey + " = " + displayVal, panelX + 30, currentY + 5, textColor);
                }
            }
            currentY += 20;
        }
        RenderUtils.stopScissor();

         
         
        font.drawString("Add new filter:", panelX + 10, panelY + panelHeight - 103, Theme.TEXT_GRAY.getRGB());

        addKeyField.drawTextBox();
        addValueField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCheckbox(int x, int y, boolean checked) {
        RenderUtils.drawRect(x, y, x + 10, y + 10, 0xFF000000);
        RenderUtils.drawRect(x + 1, y + 1, x + 9, y + 9, 0xFF303030);
        if (checked) {
            RenderUtils.drawRect(x + 2, y + 2, x + 8, y + 8, Theme.ORANGE.getRGB());
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private static class FilterEntry {
        String key;
        String value;
        boolean enabled;

        FilterEntry(String k, String v, boolean enabled) {
            this.key = k;
            this.value = v;
            this.enabled = enabled;
        }
    }
}