package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import java.awt.Color;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;


 





public class CheckboxComponent extends Component {
    private final BooleanSetting setting;

    public CheckboxComponent(BooleanSetting setting, AbstractFrame parent, int x, int y, int width, int height) {
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
         

        float textY = (parent.y + this.y) + (this.height / 2f) - (getFont().getHeight() / 2f);
        int animatedTextColor = animateColor(Theme.TEXT_WHITE.getRGB(), animFactor);
        getFont().drawString(setting.name, parent.x + x + 5, textY, animatedTextColor);

        Color boxColor = setting.enabled ? Theme.ENABLED_INDICATOR : Theme.DISABLED_INDICATOR;
        int animatedBoxColor = animateColor(boxColor.getRGB(), animFactor);
        Gui.drawRect(parent.x + x + width - 15, parent.y + y + (height/2) - 5, parent.x + x + width - 5, parent.y + y + (height/2) + 5, animatedBoxColor);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isMouseOver(mouseX, mouseY) && mouseButton == 0) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
            setting.toggle();
        }
    }

    @Override
    public BooleanSetting getSetting() {
        return setting;
    }
}