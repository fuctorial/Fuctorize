package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.settings.TextSetting;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;


 




public class TextInputComponent extends Component {
    private final TextSetting setting;
    public boolean isFocused = false;

    public TextInputComponent(TextSetting setting, AbstractFrame parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.setting = setting;

         
         
        if (getFont() != null) {
            this.height = getFont().getHeight() + VERTICAL_PADDING * 2;
        } else {
            this.height = 16;  
        }
         
    }

    @Override
    public void drawComponent(int mouseX, int mouseY, double animFactor) {
        super.drawComponent(mouseX, mouseY, animFactor);
        if (getFont() == null) return;

        int animatedBgColor = animateColor(Theme.SETTING_BG.getRGB(), animFactor);
         

        String displayText = setting.name + ": " + setting.text;

        if (isFocused && System.currentTimeMillis() % 1000 < 500 && animFactor > 0.9) {
            displayText += "_";
        }

        int animatedTextColor = animateColor(Theme.TEXT_WHITE.getRGB(), animFactor);
        getFont().drawString(displayText, parent.x + x + 5, getCenterY(), animatedTextColor);
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (!this.isFocused) return;

        if (keyCode == Keyboard.KEY_BACK) {
            if (setting.text.length() > 0) {
                setting.text = setting.text.substring(0, setting.text.length() - 1);
            }
        } else if (net.minecraft.util.ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
             
            setting.text += typedChar;
        }
    }

    private float getCenterY() {
        return (parent.y + this.y) + (this.height / 2f) - (getFont().getHeight() / 2f);
    }

    @Override
    public TextSetting getSetting() {
        return setting;
    }
}