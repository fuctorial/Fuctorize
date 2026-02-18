 
package ru.fuctorial.fuctorize.client.gui.smartmoving.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.clickgui.components.Component;

import java.awt.Color;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.Gui;

public class SmartMovingPropertyComponent extends Component {

    private final Object property;
    private final String comment;
    private final Class<?> valueType;

    private boolean isCheckbox, isSlider, isComboBox, isTextInput;
    private List<String> comboBoxOptions;
    private boolean isComboBoxExpanded = false;
    private ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField textField;

    private boolean isDraggingSlider = false;
    private float sliderMinValue, sliderMaxValue;
    private Class<?> PropertyClass;

    public SmartMovingPropertyComponent(Object property, AbstractFrame parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.property = property;

        try {
            this.PropertyClass = Class.forName("net.smart.properties.Property");
        } catch (ClassNotFoundException e) {
            System.err.println("CRITICAL: Smart Moving Property class not found!");
            this.PropertyClass = null;
        }

        this.comment = getFieldValue(property, "comment", "No description");
        Object value = getFieldValue(property, "value", null);
        this.valueType = value != null ? value.getClass() : Void.class;
        determineControlType();
    }

    private void determineControlType() {
        String key = getFieldValue(property, "currentKey", "");
        if (valueType == Boolean.class) {
            isCheckbox = true;
        } else if (key.equals("move.climb.base")) {
            isComboBox = true;
            comboBoxOptions = Arrays.asList("standard", "simple", "smart", "free");
        } else if (valueType == Float.class || valueType == Integer.class) {
            Object minVal = getFieldValue(property, "minValue", null);
            Object maxVal = getFieldValue(property, "maxValue", null);

            if (minVal != null && PropertyClass != null && PropertyClass.isInstance(minVal)) minVal = getFieldValue(minVal, "value", null);
            if (maxVal != null && PropertyClass != null && PropertyClass.isInstance(maxVal)) maxVal = getFieldValue(maxVal, "value", null);

            if (minVal instanceof Number && maxVal instanceof Number) {
                isSlider = true;
                sliderMinValue = ((Number) minVal).floatValue();
                sliderMaxValue = ((Number) maxVal).floatValue();
                if (sliderMaxValue - sliderMinValue > 500 || sliderMaxValue - sliderMinValue <= 0) {
                    isSlider = false;
                    isTextInput = true;
                }
            } else {
                isTextInput = true;
            }
        } else {
            isTextInput = true;
        }

        if (isTextInput) {
            int textWidth = 150;
             
            textField = new ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField(0, 0, textWidth, 18, false);
            textField.setText(String.valueOf(getFieldValue(property, "value", "")));
        }
    }

     
    @Override
    public void drawComponent(int mouseX, int mouseY) {
        super.drawComponent(mouseX, mouseY);
        if (getFont() == null) return;

        int controlWidth = 190;
        List<String> wrappedComment = getFont().wrapText(this.comment, width - controlWidth - 15);
        this.height = Math.max(20, (getFont().getHeight() + 1) * wrappedComment.size() + 4);

        boolean isHovered = isMouseOver(mouseX, mouseY);
        Gui.drawRect(parent.x + x, parent.y + y, parent.x + x + width, parent.y + y + height,
                isHovered ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());

        if (isSlider && isDraggingSlider) {
            int sliderX = parent.x + x + width - 195;
            double mousePercent = (double) (mouseX - sliderX) / 185.0;
            mousePercent = Math.max(0, Math.min(1, mousePercent));
            float newValue = sliderMinValue + (sliderMaxValue - sliderMinValue) * (float) mousePercent;

            if (valueType == Integer.class) {
                setLiveValue(Math.round(newValue));
            } else {
                setLiveValue((float) roundToPlace(newValue, 2));
            }
        }

        int textBlockHeight = wrappedComment.size() * (getFont().getHeight() + 1);
        int textYOffset = (this.height - textBlockHeight) / 2;
        int currentTextY = parent.y + y + textYOffset;

        for (String line : wrappedComment) {
            getFont().drawString(line, parent.x + x + 5, currentTextY, Theme.TEXT_WHITE.getRGB());
            currentTextY += getFont().getHeight() + 1;
        }

        int controlX = parent.x + x + width - controlWidth - 5;
        int controlY = parent.y + y + (height / 2) - 9;

        if (isCheckbox) drawCheckbox(mouseX, mouseY, controlX, controlY);
        else if (isSlider) drawSlider(mouseX, mouseY, controlX, controlY);
        else if (isComboBox) drawComboBox(mouseX, mouseY, controlX, controlY);
        else if (isTextInput) drawTextInput(mouseX, mouseY, controlX, controlY);
    }

    private void drawCheckbox(int mouseX, int mouseY, int cx, int cy) {
        boolean enabled = (Boolean) getFieldValue(property, "value", false);
        Color boxColor = enabled ? Theme.ENABLED_INDICATOR : Theme.DISABLED_INDICATOR;
        Gui.drawRect(cx + 175, cy + 4, cx + 185, cy + 14, boxColor.getRGB());
    }

    private void drawSlider(int mouseX, int mouseY, int cx, int cy) {
        int sliderWidth = 185;
        Gui.drawRect(cx, cy + 4, cx + sliderWidth, cy + 14, Theme.SETTING_BG.getRGB());
        float currentValue = ((Number) getFieldValue(property, "value", 0.0f)).floatValue();
        double range = sliderMaxValue - sliderMinValue;
        double percent = (range == 0) ? 0 : (currentValue - sliderMinValue) / range;
        int fillWidth = (int) (sliderWidth * percent);
        Gui.drawRect(cx, cy + 4, cx + fillWidth, cy + 14, Theme.ORANGE.getRGB());
        String valueStr = String.valueOf(roundToPlace(currentValue, 2));
        getFont().drawString(valueStr, cx + sliderWidth - getFont().getStringWidth(valueStr) - 2, cy - 6, Theme.TEXT_GRAY.getRGB());
    }

    private void drawComboBox(int mouseX, int mouseY, int cx, int cy) {
        String currentValue = (String) getFieldValue(property, "value", "");
        Gui.drawRect(cx, cy, cx + 185, cy + 18, Theme.SETTING_BG.getRGB());
        getFont().drawString(currentValue + "  v", cx + 5, cy + (18 - getFont().getHeight()) / 2f, -1);
    }

    private void drawTextInput(int mouseX, int mouseY, int cx, int cy) {
        textField.xPos = cx;
        textField.yPos = cy;
        textField.drawTextBox();
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (isTextInput && textField != null && textField.isFocused()) {
            textField.textboxKeyTyped(typedChar, keyCode);
            try {
                Object parsedValue;
                String text = textField.getText();
                if (valueType == Float.class) parsedValue = Float.parseFloat(text);
                else if (valueType == Integer.class) parsedValue = Integer.parseInt(text);
                else parsedValue = text;
                setLiveValue(parsedValue);
            } catch (NumberFormatException e) {   }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int controlXStart = parent.x + x + width - 190 - 5;
        if (mouseX > controlXStart && isMouseOver(mouseX, mouseY)) {
            if (isCheckbox) {
                setLiveValue(!(Boolean) getFieldValue(property, "value", false));
            } else if (isSlider) {
                isDraggingSlider = true;
            } else if (isTextInput && textField != null) {
                textField.mouseClicked(mouseX, mouseY, mouseButton);
            } else if (isComboBox) {
                 
            }
        } else if (isTextInput && textField != null) {
            textField.setFocused(false);
        }
    }
    public void clearFocus() {
        if (textField != null) {
            textField.setFocused(false);
        }
    }
    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        isDraggingSlider = false;
    }

    private void setLiveValue(Object newValue) {
        try {
            Field valueField = this.property.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            valueField.set(this.property, newValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private <T> T getFieldValue(Object obj, String fieldName, T defaultValue) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(obj);
        } catch (Exception e) {
            try {
                Field f = obj.getClass().getSuperclass().getDeclaredField(fieldName);
                f.setAccessible(true);
                return (T) f.get(obj);
            } catch (Exception e2) {
                return defaultValue;
            }
        }
    }

    private double roundToPlace(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}