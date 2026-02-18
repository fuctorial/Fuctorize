package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.settings.ModeSetting;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;


 




public class ModeComponent extends Component {
    private final ModeSetting setting;

    public ModeComponent(ModeSetting setting, AbstractFrame parent, int x, int y, int width, int height) {
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
        String displayText = setting.name + ": " + setting.getMode();
        int animatedTextColor = animateColor(Theme.TEXT_WHITE.getRGB(), animFactor);
        getFont().drawString(displayText, parent.x + x + 5, textY, animatedTextColor);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isMouseOver(mouseX, mouseY) && mouseButton == 0) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
            setting.cycle();
        }
    }

    @Override
    public ModeSetting getSetting() {
        return setting;
    }
}