package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.module.settings.ColorSetting;
import java.awt.Color;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

public class ColorPickerComponent extends Component {
    private final ColorSetting setting;
    private boolean hueDragging, sbDragging; // sb = saturation/brightness

    public ColorPickerComponent(ColorSetting setting, AbstractFrame parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void drawComponent(int mouseX, int mouseY, double animFactor) {
        if (sbDragging) {
            updateSB(mouseX, mouseY);
        }
        if (hueDragging) {
            updateHue(mouseY);
        }

        float[] hsb = getHSB();
        Color fullColor = Color.getHSBColor(hsb[0], 1.0f, 1.0f);

        int animatedBgColor = animateColor(0xFF202020, animFactor);
        //Gui.drawRect(parent.x + x, parent.y + y, parent.x + x + width, parent.y + y + height, animatedBgColor);

        // --- FIXED GRADIENT RENDERING ---
        drawPickerPanel(parent.x + x + 2, parent.y + y + 2, width - 18, height - 4, fullColor.getRGB(), animFactor);

        drawHueSlider(parent.x + x + width - 14, parent.y + y + 2, 10, height - 4, animFactor);

        int pickerX = (int)((parent.x + x + 2) + hsb[1] * (width - 18));
        int pickerY = (int)((parent.y + y + 2) + (1 - hsb[2]) * (height - 4));

        int shadowColor = animateColor(0x50000000, animFactor);
        int pointerColor = animateColor(0xFFFFFFFF, animFactor);

        Gui.drawRect(pickerX - 2, pickerY - 2, pickerX + 2, pickerY + 2, shadowColor); // Shadow
        Gui.drawRect(pickerX - 1, pickerY - 1, pickerX + 1, pickerY + 1, pointerColor); // Pointer

        int hueY = (int)((parent.y + y + 2) + hsb[0] * (height - 4));
        Gui.drawRect(parent.x + x + width - 16, hueY - 2, parent.x + x + width - 2, hueY + 2, shadowColor); // Shadow
        Gui.drawRect(parent.x + x + width - 15, hueY - 1, parent.x + x + width - 3, hueY + 1, pointerColor); // Pointer
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            if (isMouseOverSB(mouseX, mouseY)) {
                sbDragging = true;
            }
            if (isMouseOverHue(mouseX, mouseY)) {
                hueDragging = true;
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        sbDragging = false;
        hueDragging = false;
    }

    private void updateSB(int mouseX, int mouseY) {
        float saturation = (float)(mouseX - (parent.x + x + 2)) / (width - 18);
        float brightness = 1.0f - (float)(mouseY - (parent.y + y + 2)) / (height - 4);
        saturation = Math.max(0, Math.min(1, saturation));
        brightness = Math.max(0, Math.min(1, brightness));
        float[] hsb = getHSB();
        setting.setColor(Color.HSBtoRGB(hsb[0], saturation, brightness));
    }

    private void updateHue(int mouseY) {
        float hue = (float)(mouseY - (parent.y + y + 2)) / (height - 4);
        hue = Math.max(0, Math.min(1, hue));
        float[] hsb = getHSB();
        setting.setColor(Color.HSBtoRGB(hue, hsb[1], hsb[2]));
    }

    private boolean isMouseOverSB(int mouseX, int mouseY) {
        return mouseX >= parent.x + x + 2 && mouseX <= parent.x + x + 2 + (width - 18) &&
                mouseY >= parent.y + y + 2 && mouseY <= parent.y + y + 2 + (height - 4);
    }

    private boolean isMouseOverHue(int mouseX, int mouseY) {
        return mouseX >= parent.x + x + width - 14 && mouseX <= parent.x + x + width - 4 &&
                mouseY >= parent.y + y + 2 && mouseY <= parent.y + y + 2 + (height - 4);
    }

    private float[] getHSB() {
        return Color.RGBtoHSB(
                (setting.getColor() >> 16) & 0xFF,
                (setting.getColor() >> 8) & 0xFF,
                (setting.getColor() >> 0) & 0xFF,
                null
        );
    }

    private void drawHueSlider(int x, int y, int width, int height, double animFactor) {
        for(int i = 0; i < height; i++) {
            float hue = (float)i / (height - 1);
            int color = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            Gui.drawRect(x, y + i, x + width, y + i + 1, animateColor(color, animFactor));
        }
    }

    /**
     * Renders the Saturation/Brightness selection panel using a single quad with 4 colored vertices.
     * This is the most efficient way to render a smooth 2D gradient.
     */
    private void drawPickerPanel(int left, int top, int width, int height, int hueColor, double animFactor) {
        int right = left + width;
        int bottom = top + height;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH); // Enable smooth color blending

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        // Top-right vertex: pure color (S=1, B=1)
        tessellator.setColorOpaque_I(animateColor(hueColor, animFactor));
        tessellator.addVertex(right, top, 0);

        // Top-left vertex: white (S=0, B=1)
        tessellator.setColorOpaque_I(animateColor(0xFFFFFFFF, animFactor));
        tessellator.addVertex(left, top, 0);

        // Bottom-left vertex: black (S=0, B=0)
        tessellator.setColorOpaque_I(animateColor(0xFF000000, animFactor));
        tessellator.addVertex(left, bottom, 0);

        // Bottom-right vertex: black (S=1, B=0)
        tessellator.setColorOpaque_I(animateColor(0xFF000000, animFactor));
        tessellator.addVertex(right, bottom, 0);

        tessellator.draw();

        GL11.glShadeModel(GL11.GL_FLAT); // Reset to flat shading model
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public ColorSetting getSetting() {
        return setting;
    }
}