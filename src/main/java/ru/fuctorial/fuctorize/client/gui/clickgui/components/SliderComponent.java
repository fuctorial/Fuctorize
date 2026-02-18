 
package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.GuiTextInput;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.client.gui.Gui;

public class SliderComponent extends Component {
    private final SliderSetting setting;
    private boolean dragging = false;
    private double animatedValue;

    public SliderComponent(SliderSetting setting, AbstractFrame parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.setting = setting;
        this.animatedValue = setting.value;

        if (getFont() != null) {
            this.height = getFont().getHeight() + VERTICAL_PADDING * 2;
        } else {
            this.height = 16;
        }
    }

    private void updateValue(int mouseX) {
        double diff = setting.max - setting.min;
        double val = setting.min + (Math.max(0, Math.min(width, mouseX - (parent.x + x)))) / (double)width * diff;

         
        if (setting.increment > 0) {
            double remainder = val % setting.increment;
            if (remainder >= setting.increment / 2.0) {
                val = val - remainder + setting.increment;
            } else {
                val = val - remainder;
            }
        }
         

         
        setting.value = roundToPlace(val, 2);
    }

    @Override
    public void drawComponent(int mouseX, int mouseY, double animFactor) {
        super.drawComponent(mouseX, mouseY, animFactor);
        if (getFont() == null) return;

         
        animatedValue += (setting.value - animatedValue) * 0.2;  

        if (dragging) {
            updateValue(mouseX);
        }

        double percent = (animatedValue - setting.min) / (setting.max - setting.min);
        int fillWidth = (int) (width * percent);

         
         

         
        int animatedFillColor = animateColor(Theme.ORANGE.getRGB(), animFactor);
        Gui.drawRect(parent.x + x, parent.y + y, parent.x + x + fillWidth, parent.y + y + height, animatedFillColor);

         
        int handleX = parent.x + x + fillWidth;
        int handleColor = animateColor(Theme.ORANGE.brighter().getRGB(), animFactor);
        Gui.drawRect(handleX - 1, parent.y + y, handleX + 1, parent.y + y + height, handleColor);

        float textY = (parent.y + this.y) + (this.height / 2f) - (getFont().getHeight() / 2f);

         
        String valString = String.valueOf(roundToPlace(animatedValue, 2));
        if (valString.endsWith(".0")) {
            valString = valString.replace(".0", "");
        } else if (valString.endsWith(".00")) {
            valString = valString.replace(".00", "");
        }

        String displayText = setting.name + ": " + valString;
        int animatedTextColor = animateColor(Theme.TEXT_WHITE.getRGB(), animFactor);
        getFont().drawString(displayText, parent.x + x + 5, textY, animatedTextColor);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isMouseOver(mouseX, mouseY)) {
            if (mouseButton == 0) {
                this.dragging = true;
                updateValue(mouseX);
            }
            else if (mouseButton == 1) {
                String currentValue = String.valueOf(roundToPlace(setting.value, 2));
                mc.displayGuiScreen(new GuiTextInput(mc.currentScreen, "Введите значение для " + setting.name, currentValue, (newValue) -> {
                    try {
                        double parsedValue = Double.parseDouble(newValue);
                         
                        parsedValue = Math.max(setting.min, Math.min(setting.max, parsedValue));
                        setting.value = parsedValue;
                    } catch (NumberFormatException e) {
                         
                    }
                }));
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.dragging = false;
    }

    private double roundToPlace(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    @Override
    public SliderSetting getSetting() {
        return setting;
    }
}