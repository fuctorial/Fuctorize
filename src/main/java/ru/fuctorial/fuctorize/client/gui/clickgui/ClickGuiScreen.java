// Файл: C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\gui\clickgui\ClickGuiScreen.java
package ru.fuctorial.fuctorize.client.gui.clickgui;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.utils.AnimationUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class ClickGuiScreen extends GuiScreen {
    private final FuctorizeClient client;
    private final Frame mainFrame;
    private AnimationUtils openAnimation;
    private boolean hasFrameBeenSetup = false;

    public ClickGuiScreen(FuctorizeClient client) {
        this.client = client;
        this.mainFrame = new Frame("FUCTORIZE", 0, 0, 0, 0);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.hasFrameBeenSetup = false;
        int oldW = this.mainFrame.width;
        int oldH = this.mainFrame.height;

        // 1. Задаем желаемые размеры в процентах (можно сделать их чуть больше)
        float widthPercent = 0.45f; // 45% ширины экрана (было 50%)
        float heightPercent = 0.7f;  // 70% высоты экрана (оставляем)

// 2. Рассчитываем начальные размеры
        int guiWidth = (int) (this.width * widthPercent);
        int guiHeight = (int) (this.height * heightPercent);

// 3. Корректируем минимальные и максимальные размеры
        int minWidth = 420;      // Минимальная ширина (уменьшаем, было 450)
        int minHeight = 320;     // Минимальная высота (УВЕЛИЧИВАЕМ, было 280)
        int maxWidth = 650;      // Максимальная ширина (уменьшаем, было 700)
        int maxHeight = 500;     // Максимальная высота (оставляем)

// 4. Применяем ограничения (ограничиваем рассчитанный размер)
        guiWidth = Math.max(minWidth, Math.min(maxWidth, guiWidth));
        guiHeight = Math.max(minHeight, Math.min(maxHeight, guiHeight));

// 5. Финальная проверка: GUI не может быть больше самого окна (оставляем как есть)
        if (guiWidth > this.width - 40) {
            guiWidth = this.width - 40;
        }
        if (guiHeight > this.height - 40) {
            guiHeight = this.height - 40;
        }

// 6. Устанавливаем и центрируем итоговые размеры
        this.mainFrame.width = guiWidth;
        this.mainFrame.height = guiHeight;
        this.mainFrame.x = (this.width - guiWidth) / 2;
        this.mainFrame.y = (this.height - guiHeight) / 2;

        // Compensate scroll for the change in visible area when resolution changes during open GUI
        if (oldW > 0 && oldH > 0) {
            this.mainFrame.onResized(oldW, oldH, guiWidth, guiHeight);
        }

        this.openAnimation = new AnimationUtils(300, AnimationUtils.Easing.EASE_OUT_QUAD);
        this.openAnimation.setDirection(true);

        if (client.fontManager != null && client.fontManager.isReady()) {
            this.mainFrame.setup();
            this.hasFrameBeenSetup = true;
        }

    }

    // Метод onGuiClosed() больше не нужен, так как блюр рисуется только когда открыт этот GUI

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!this.hasFrameBeenSetup && client.fontManager != null && client.fontManager.isReady()) {
            this.mainFrame.setup();
            this.hasFrameBeenSetup = true;
        }

        // --- ЭТА СТРОКА УДАЛЕНА. БЛЮР ТЕПЕРЬ ВКЛЮЧАЕТСЯ АВТОМАТИЧЕСКИ ---

        if (this.mainFrame != null) {
            double animFactor = openAnimation.getAnimationFactor();
            GL11.glPushMatrix();
            ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            float ty = (float) ((1.0 - animFactor) * (sr.getScaledHeight() / 2.0f));
            GL11.glTranslatef(0, ty, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, (float) animFactor);
            mainFrame.drawScreen(mouseX, mouseY);
            GL11.glPopMatrix();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }



    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.mainFrame != null) {
            mainFrame.keyTyped(typedChar, keyCode);
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0 && this.mainFrame != null) {
            mainFrame.mouseScrolled(dWheel);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.mainFrame != null) {
            mainFrame.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (this.mainFrame != null) {
            if (state != -1) {
                mainFrame.mouseReleased(mouseX, mouseY, state);
            }
        }
    }
    public boolean isAJTextFieldFocused() {
        if (mainFrame != null) {
            return mainFrame.isAnyTextFieldFocused();
        }
        return false;
    }
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }
}
