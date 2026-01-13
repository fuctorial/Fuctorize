package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import cpw.mods.fml.client.FMLClientHandler;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.module.settings.Setting;
import net.minecraft.client.Minecraft;

// Файл: C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\gui\clickgui\components\Component.java



public abstract class Component {
    protected Minecraft mc = FMLClientHandler.instance().getClient();
    public AbstractFrame parent;
    public int x, y, width, height;
    protected final int VERTICAL_PADDING = 4;

    public Component(AbstractFrame parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    protected CustomFontRenderer getFont() {
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null) return null;
        return FuctorizeClient.INSTANCE.fontManager.regular_18;
    }

    protected CustomFontRenderer getBoldFont() {
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null) return null;
        return FuctorizeClient.INSTANCE.fontManager.bold_22;
    }

    public void drawComponent(int mouseX, int mouseY) {
        if (getFont() == null || getBoldFont() == null) {
            return;
        }
    }

    public void drawComponent(int mouseX, int mouseY, double animFactor) {
        if (getFont() == null || getBoldFont() == null) {
            return;
        }
        drawComponent(mouseX, mouseY);
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {}
    public void mouseReleased(int mouseX, int mouseY, int state) {}

    public boolean isMouseOver(int mouseX, int mouseY) {
        int frameX = parent.x;
        int frameY = parent.y;
        return mouseX >= frameX + x && mouseX <= frameX + x + width &&
                mouseY >= frameY + y && mouseY <= frameY + y + height;
    }

    public Setting getSetting() {
        return null;
    }

    /**
     * Fades a color's alpha based on a factor, ignoring the color's original alpha.
     * Useful for fade-in effects of opaque elements.
     * @param color The color to fade.
     * @param alphaFactor The animation factor (0.0 to 1.0).
     * @return The color with the new alpha.
     */
    protected int fadeColor(int color, double alphaFactor) {
        int alpha = (int) (255 * alphaFactor);
        if (alpha > 255) alpha = 255;
        if (alpha < 0) alpha = 0;
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Animates a color's alpha based on a factor, preserving its relative transparency.
     * Useful for fading elements that might have transparency set in the theme.
     * @param color The color to animate.
     * @param alphaFactor The animation factor (0.0 to 1.0).
     * @return The color with the animated alpha.
     */
    protected int animateColor(int color, double alphaFactor) {
        int originalAlpha = (color >> 24) & 0xFF;
        // If the color was defined without an alpha component (e.g., 0xFFFFFF), assume it's fully opaque.
        if (originalAlpha == 0 && (color & 0x00FFFFFF) != 0) originalAlpha = 255;

        int alpha = (int) (originalAlpha * alphaFactor);
        if (alpha > 255) alpha = 255;
        if (alpha < 0) alpha = 0;

        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}