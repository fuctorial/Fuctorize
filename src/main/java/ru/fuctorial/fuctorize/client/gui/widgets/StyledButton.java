package ru.fuctorial.fuctorize.client.gui.widgets;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.utils.AnimationUtils;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

public class StyledButton extends GuiButton {

    private final AnimationUtils hoverAnimation;
    private final long creationTime;
    private static final long HOVER_COOLDOWN_MS = 200;

    public StyledButton(int buttonId, int x, int y, int width, int height, String buttonText) {
        super(buttonId, x, y, width, height, buttonText);
        this.hoverAnimation = new AnimationUtils(150, AnimationUtils.Easing.EASE_OUT_QUAD);
        this.hoverAnimation.setDirection(false);
        this.creationTime = System.currentTimeMillis();
    }

    public void drawButton(Minecraft mc, int mouseX, int mouseY, double animationFactor) {
        if (!this.visible) {
            return;
        }
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            super.drawButton(mc, mouseX, mouseY);
            return;
        }

        boolean isAnimationComplete = animationFactor >= 1.0;
        this.field_146123_n = isAnimationComplete && mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        hoverAnimation.setDirection(this.field_146123_n);
        double hoverFactor = hoverAnimation.getAnimationFactor();

        Color bgColor = interpolateColor(Theme.COMPONENT_BG, Theme.COMPONENT_BG_HOVER, hoverFactor);

        if (!this.enabled) {
            bgColor = Theme.DISABLED_INDICATOR;
        }

        Gui.drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, bgColor.getRGB());

        if (this.enabled && hoverFactor > 0) {
            int borderColor = Theme.ORANGE.getRGB();
            int alpha = (int)(255 * hoverFactor);
            int animatedBorderColor = (alpha << 24) | (borderColor & 0x00FFFFFF);

            Gui.drawRect(this.xPosition, this.yPosition, this.xPosition + 1, this.yPosition + this.height, animatedBorderColor);
            Gui.drawRect(this.xPosition + this.width - 1, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, animatedBorderColor);
            Gui.drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + 1, animatedBorderColor);
            Gui.drawRect(this.xPosition, this.yPosition + this.height - 1, this.xPosition + this.width, this.yPosition + this.height, animatedBorderColor);
        }

        this.mouseDragged(mc, mouseX, mouseY);
        int textColor = this.enabled ? Theme.TEXT_WHITE.getRGB() : Theme.TEXT_GRAY.getRGB();

        if (this.displayString.equals("Выход") || this.displayString.equals("Отмена")) {
            textColor = 0xFFFF5555;
        }

        float textX = this.xPosition + (this.width - FuctorizeClient.INSTANCE.fontManager.regular_18.getStringWidth(this.displayString)) / 2f;

        // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
        // Убрали "+ 1" в конце. Теперь текст строго по центру.
        float textY = this.yPosition + (this.height - FuctorizeClient.INSTANCE.fontManager.regular_18.getHeight()) / 2.0f;
        // -------------------------

        FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(this.displayString, textX, textY, textColor);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        boolean allowHoverInteraction = System.currentTimeMillis() - this.creationTime > HOVER_COOLDOWN_MS;
        double animationFactor = allowHoverInteraction ? 1.0 : 0.0;
        this.drawButton(mc, mouseX, mouseY, animationFactor);
    }

    private Color interpolateColor(Color startColor, Color endColor, double progress) {
        int r = (int) (startColor.getRed() + (endColor.getRed() - startColor.getRed()) * progress);
        int g = (int) (startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * progress);
        int b = (int) (startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * progress);
        int a = (int) (startColor.getAlpha() + (endColor.getAlpha() - startColor.getAlpha()) * progress);
        return new Color(r, g, b, a);
    }
}