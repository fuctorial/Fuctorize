package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.settings.ColorSetting;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;


 




public class ColorComponent extends Component {
    private final ColorSetting setting;

    public ColorComponent(ColorSetting setting, AbstractFrame parent, int x, int y, int width, int height) {
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

        int bgColor = animateColor(Theme.SETTING_BG.getRGB(), animFactor);
         

        float textY = (parent.y + this.y) + (this.height / 2f) - (getFont().getHeight() / 2f);
        int textColor = animateColor(Theme.TEXT_WHITE.getRGB(), animFactor);
        getFont().drawString(setting.name, parent.x + x + 5, textY, textColor);

        int colorBoxX = parent.x + x + width - 15;
        int colorBoxY = parent.y + y + (height / 2) - 5;
        int boxColor = animateColor(setting.getColor(), animFactor);
        Gui.drawRect(colorBoxX, colorBoxY, colorBoxX + 10, colorBoxY + 10, boxColor);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isMouseOver(mouseX, mouseY) && mouseButton == 0) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 0.9F));
            setting.expanded = !setting.expanded;
            setting.animation.setDirection(setting.expanded);
        }
    }

    @Override
    public ColorSetting getSetting() {
        return setting;
    }
}