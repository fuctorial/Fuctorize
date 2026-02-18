package ru.fuctorial.fuctorize.client.gui.menu;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.utils.AnimationUtils;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiCustomMainMenu extends GuiScreen {

     
     
    private AnimationUtils masterAnimation;
    private final List<StyledButton> orderedButtons = new ArrayList<>();

    private static final double TITLE_ANIM_START = 0.0;
    private static final double TITLE_ANIM_DURATION = 0.4;
    private static final double SUBTITLE_ANIM_START = 0.1;
    private static final double SUBTITLE_ANIM_DURATION = 0.4;
    private static final double BUTTON_ANIM_START = 0.25;
    private static final double BUTTON_ANIM_DURATION = 0.5;
    private static final double BUTTON_STAGGER = 0.06;

    private float titleY, subtitleY;
    private boolean hasBeenInitialized = false;
    private boolean animationPlayed = false;
     
     

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.orderedButtons.clear();
        this.hasBeenInitialized = false;
        ru.fuctorial.fuctorize.utils.Lang.update();

        if (!this.animationPlayed) {
            this.masterAnimation = new AnimationUtils(900, AnimationUtils.Easing.EASE_OUT_QUAD);
            this.masterAnimation.setDirection(true);
        } else {
            this.masterAnimation = null;
        }

        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            return;
        }

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_32;
        CustomFontRenderer subtitleFont = FuctorizeClient.INSTANCE.fontManager.regular_18;

        if (titleFont == null || subtitleFont == null) {
            return;
        }

         

         
        String txtSingle = ru.fuctorial.fuctorize.utils.Lang.get("mainmenu.button.singleplayer");
        String txtMulti = ru.fuctorial.fuctorize.utils.Lang.get("mainmenu.button.multiplayer");
        String txtAcc = ru.fuctorial.fuctorize.utils.Lang.get("mainmenu.button.accounts");
        String txtOpt = ru.fuctorial.fuctorize.utils.Lang.get("mainmenu.button.options");
        String txtQuit = ru.fuctorial.fuctorize.utils.Lang.get("mainmenu.button.quit");

        String[] allTexts = {txtSingle, txtMulti, txtAcc, txtOpt, txtQuit};

        int maxTextWidth = 0;
        for (String txt : allTexts) {
            int w = subtitleFont.getStringWidth(txt);
            if (w > maxTextWidth) {
                maxTextWidth = w;
            }
        }


        int buttonHeight = 22;

         
         
        int totalVerticalPadding = buttonHeight - subtitleFont.getHeight();

         
        int buttonWidth = maxTextWidth + totalVerticalPadding + 20;

         
        if (buttonWidth < 120) {
            buttonWidth = 120;
        }
         

        int spacing = 5;
        int titleToButtonSpacing = 25;

        float titleBlockHeight = titleFont.getHeight() + subtitleFont.getHeight() + 2;
        int buttonBlockHeight = (buttonHeight * 5) + (spacing * 4);
        float totalContentHeight = titleBlockHeight + titleToButtonSpacing + buttonBlockHeight;

        float contentStartY = (this.height - totalContentHeight) / 2f;
        this.titleY = contentStartY;
        this.subtitleY = this.titleY + titleFont.getHeight() + 2;
        int buttonBlockStartY = (int) (this.subtitleY + subtitleFont.getHeight() + titleToButtonSpacing);

        int centerX = this.width / 2;

         
        orderedButtons.add(new StyledButton(1, centerX - buttonWidth / 2, buttonBlockStartY, buttonWidth, buttonHeight, txtMulti));
        orderedButtons.add(new StyledButton(0, centerX - buttonWidth / 2, buttonBlockStartY + (buttonHeight + spacing), buttonWidth, buttonHeight, txtSingle));
        orderedButtons.add(new StyledButton(4, centerX - buttonWidth / 2, buttonBlockStartY + (buttonHeight + spacing) * 2, buttonWidth, buttonHeight, txtAcc));
        orderedButtons.add(new StyledButton(2, centerX - buttonWidth / 2, buttonBlockStartY + (buttonHeight + spacing) * 3, buttonWidth, buttonHeight, txtOpt));
        orderedButtons.add(new StyledButton(3, centerX - buttonWidth / 2, buttonBlockStartY + (buttonHeight + spacing) * 4, buttonWidth, buttonHeight, txtQuit));

        this.buttonList.addAll(this.orderedButtons);
        this.hasBeenInitialized = true;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!this.hasBeenInitialized && FuctorizeClient.INSTANCE.fontManager != null && FuctorizeClient.INSTANCE.fontManager.isReady()) {
            this.initGui();
        }

         
        RenderUtils.drawRect(0, 0, this.width, this.height, new Color(20, 20, 22).getRGB());

        if (!this.hasBeenInitialized) {
            drawCenteredString(mc.fontRenderer, "Loading assets...", this.width / 2, this.height / 2, -1);
            return;
        }

        double masterProgress;

         
        if (!this.animationPlayed) {
            if (this.masterAnimation == null) {
                 
                this.masterAnimation = new AnimationUtils(900, AnimationUtils.Easing.EASE_OUT_QUAD);
                this.masterAnimation.setDirection(true);
            }
            masterProgress = this.masterAnimation.getAnimationFactor();
        } else {
             
            masterProgress = 1.0;
        }
        double titleProgress = getAnimationProgress(masterProgress, TITLE_ANIM_START, TITLE_ANIM_DURATION);
        if (titleProgress > 0) {
            double titleYOffset = (1.0 - titleProgress) * -20.0;
            int titleAlpha = (int) (255 * titleProgress);
            if (titleAlpha > 4) {
                String title = "FUCTORIZE";
                float titleX = (this.width - FuctorizeClient.INSTANCE.fontManager.bold_32.getStringWidth(title)) / 2f;
                FuctorizeClient.INSTANCE.fontManager.bold_32.drawString(title, titleX, (float) (this.titleY + titleYOffset), (titleAlpha << 24) | 0xFFFFFF);
            }
        }
        double subtitleProgress = getAnimationProgress(masterProgress, SUBTITLE_ANIM_START, SUBTITLE_ANIM_DURATION);
        if (subtitleProgress > 0) {
            double subtitleYOffset = (1.0 - subtitleProgress) * -15.0;
            int subtitleAlpha = (int) (255 * subtitleProgress);
            if (subtitleAlpha > 4) {
                String subtitle = "v1.0";
                float subtitleX = (this.width - FuctorizeClient.INSTANCE.fontManager.regular_18.getStringWidth(subtitle)) / 2f;
                FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(subtitle, subtitleX, (float) (this.subtitleY + subtitleYOffset), (subtitleAlpha << 24) | 0xAAAAAA);
            }
        }
        for (int i = 0; i < this.orderedButtons.size(); i++) {
            StyledButton button = this.orderedButtons.get(i);
            double buttonProgress = getAnimationProgress(masterProgress, BUTTON_ANIM_START + (i * BUTTON_STAGGER), BUTTON_ANIM_DURATION);
            if (buttonProgress > 0) {
                double buttonYOffset = (1.0 - buttonProgress) * 30.0;
                GL11.glPushMatrix();
                GL11.glTranslatef(0, (float) buttonYOffset, 0);

                GL11.glColor4f(1.0f, 1.0f, 1.0f, (float) buttonProgress);

                button.drawButton(this.mc, mouseX, (int) (mouseY - buttonYOffset), buttonProgress);

                GL11.glPopMatrix();
            }
        }
        double infoProgress = getAnimationProgress(masterProgress, BUTTON_ANIM_START, 0.7);
        if (infoProgress > 0) {
            int infoAlpha = (int) (255 * infoProgress);
            if (infoAlpha > 4) {
                String infoString = "Fuctorize Client";
                int infoColor = (infoAlpha << 24) | 0xAAAAAA;
                FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(infoString, 5, this.height - FuctorizeClient.INSTANCE.fontManager.regular_18.getHeight() - 5, infoColor);
            }
        }

        if (!this.animationPlayed && masterProgress >= 1.0) {
            this.animationPlayed = true;
            this.masterAnimation = null;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case 1:
                this.mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case 2:
                this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
                break;
            case 3:
                this.mc.shutdown();
                break;
            case 4:
                this.mc.displayGuiScreen(new GuiAccountManager(this));
                break;
        }
    }

     
    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
             
            return;
        }
         
        try {
            super.keyTyped(typedChar, keyCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

     
     
     
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
         
         
         

        if (mouseButton == 0) {
            for (int i = 0; i < this.orderedButtons.size(); i++) {
                StyledButton button = this.orderedButtons.get(i);

                 
                 
                 
                double masterProgress = (this.masterAnimation != null) ? this.masterAnimation.getAnimationFactor() : 1.0;
                 

                 
                double buttonProgress = getAnimationProgress(masterProgress, BUTTON_ANIM_START + (i * BUTTON_STAGGER), BUTTON_ANIM_DURATION);

                if (buttonProgress >= 1.0 && button.mousePressed(this.mc, mouseX, mouseY)) {
                    button.func_146113_a(this.mc.getSoundHandler());  
                    this.actionPerformed(button);
                    return;
                }
            }
        }
    }

    private double getAnimationProgress(double masterProgress, double startTime, double duration) {
        if (masterProgress < startTime) return 0.0;
        if (masterProgress > startTime + duration) return 1.0;
        double localProgress = (masterProgress - startTime) / duration;
        return AnimationUtils.Easing.EASE_OUT_QUAD.ease(localProgress);
    }

    public void resetAnimation() {
         
        this.animationPlayed = false;
         
        this.masterAnimation = null;
         
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}