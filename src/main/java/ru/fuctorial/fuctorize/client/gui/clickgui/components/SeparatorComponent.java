package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.settings.SeparatorSetting;
import net.minecraft.client.gui.Gui;


 




public class SeparatorComponent extends Component {

    private final SeparatorSetting setting;

    public SeparatorComponent(SeparatorSetting setting, AbstractFrame parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.setting = setting;

         
        this.height = 8;
    }

    @Override
    public void drawComponent(int mouseX, int mouseY, double animFactor) {
        int color = fadeColor(Theme.DIVIDER.getRGB(), animFactor);
        Gui.drawRect(parent.x + x + 2, parent.y + y + height / 2, parent.x + x + width - 2, parent.y + y + height / 2 + 1, color);
    }

    @Override
    public SeparatorSetting getSetting() {
        return this.setting;
    }
}