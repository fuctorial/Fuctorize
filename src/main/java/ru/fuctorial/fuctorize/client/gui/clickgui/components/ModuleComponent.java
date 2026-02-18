 
package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import java.awt.Color;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

public class ModuleComponent extends Component {
    private final Module module;

    public ModuleComponent(Module module, AbstractFrame parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.module = module;

         
         
         
        if (getFont() != null) {
            this.height = getFont().getHeight() + VERTICAL_PADDING * 2;
        } else {
            this.height = 18;  
        }
         
    }

    @Override
    public void drawComponent(int mouseX, int mouseY, double animFactor) {
        super.drawComponent(mouseX, mouseY, animFactor);
        if (getFont() == null) return;

        if (isMouseOver(mouseX, mouseY)) {
            int hoverColor = fadeColor(Theme.COMPONENT_BG_HOVER.getRGB(), animFactor);
            Gui.drawRect(parent.x + x, parent.y + y, parent.x + x + width, parent.y + y + height, hoverColor);
        }
        float textY = (parent.y + this.y) + (this.height / 2f) - (getFont().getHeight() / 2f);
        int animatedTextColor = fadeColor(Theme.TEXT_WHITE.getRGB(), animFactor);
        float textX;

         
         
         
         

        if (module.isConfigOnly()) {
            textX = parent.x + x + 5;  
        } else {
            textX = parent.x + x + 18;  
             
            Color indicatorColor = module.isEnabled() ? Theme.ENABLED_INDICATOR : Theme.DISABLED_INDICATOR;
            int animatedIndicatorColor = fadeColor(indicatorColor.getRGB(), animFactor);
            Gui.drawRect(parent.x + x + 4, parent.y + y + (height / 2) - 4, parent.x + x + 12, parent.y + y + (height / 2) + 4, animatedIndicatorColor);
        }
         

        boolean hasVisibleSettings = false;
        for (ru.fuctorial.fuctorize.module.settings.Setting setting : module.getSettings()) {
            if (!(setting instanceof BindSetting)) {
                hasVisibleSettings = true;
                break;
            }
        }

        getFont().drawString(module.getName(), textX, textY, animatedTextColor);
        int moduleNameWidth = getFont().getStringWidth(module.getName());

        String openText = "";
        int openTextWidth = 0;
        if (hasVisibleSettings) {
            openText = module.extended ? "[-]" : "[+]";
            openTextWidth = getFont().getStringWidth(openText);
            int animatedGrayColor = fadeColor(Theme.TEXT_GRAY.getRGB(), animFactor);
            getFont().drawString(openText, parent.x + x + width - openTextWidth - 4, textY, animatedGrayColor);
        }

        if (!module.isConfigOnly()) {
            BindSetting bind = BindSetting.getBindSetting(module);
            if (bind != null || parent.getBindingComponent() == this) {
                String keyName = (bind != null) ? bind.getKeyName() : "";
                String bindText = "";
                int bindColor;

                if (parent.getBindingComponent() == this) {
                    bindText = "[ ... ]";
                    bindColor = fadeColor(Theme.ORANGE.getRGB(), animFactor);
                } else if (keyName != null && !keyName.isEmpty()) {
                    bindText = "[" + keyName + "]";
                    bindColor = fadeColor(Theme.TEXT_GRAY.getRGB(), animFactor);
                } else {
                    bindColor = 0;
                }

                if (!bindText.isEmpty()) {
                    int bindTextWidth = getFont().getStringWidth(bindText);
                    int availableSpace = width - ((int)textX - (parent.x + x) + moduleNameWidth + 4 + openTextWidth + 4);

                    if (bindTextWidth < availableSpace) {
                        int bindX = parent.x + x + width - bindTextWidth - 4;
                        if(hasVisibleSettings) {
                            bindX -= (openTextWidth + 4);
                        }
                        getFont().drawString(bindText, bindX, textY, bindColor);
                    }
                }
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isMouseOver(mouseX, mouseY)) {
            if (mouseButton == 0) {
                if (!module.isConfigOnly()) {
                    module.toggle();
                    mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
                }
            } else if (mouseButton == 1) {
                if (!module.getSettings().isEmpty()) {
                    boolean hasSettings = false;
                    for(ru.fuctorial.fuctorize.module.settings.Setting s : module.getSettings()){
                        if(!(s instanceof BindSetting)){
                            hasSettings = true;
                            break;
                        }
                    }
                    if(hasSettings){
                        mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 0.8F));
                        module.extended = !module.extended;
                        module.animation.setDirection(module.extended);
                    }
                }
            } else if (mouseButton == 2) {
                if (!module.isConfigOnly()) {
                    mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 0.9F));
                    parent.setBindingComponent(this);
                }
            }
        }
    }

    public Module getModule() { return module; }
}
