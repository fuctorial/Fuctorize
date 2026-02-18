 
package ru.fuctorial.fuctorize.client.gui.smartmoving;

import ru.fuctorial.fuctorize.client.gui.clickgui.components.CategoryComponent;
import ru.fuctorial.fuctorize.client.gui.smartmoving.components.SmartMovingPropertyComponent;
import ru.fuctorial.fuctorize.utils.SmartMovingConfigScanner;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiSmartMovingEditor extends GuiScreen {
    private static int savedContentScrollY = 0;
    private static int savedCategoryScrollY = 0;
    private static String savedCategoryName = null;

    private final Object smOptionsInstance;
    private EditorFrame mainFrame;
    private Map<String, List<Object>> categorizedProperties;

    public GuiSmartMovingEditor(Object smOptionsInstance) {
        this.smOptionsInstance = smOptionsInstance;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.categorizedProperties = SmartMovingConfigScanner.scan(smOptionsInstance);
        this.mainFrame = new EditorFrame("Smart Moving Editor", this.width / 2 - 300, this.height / 2 - 150, 600, 300);

        this.mainFrame.scrollY = savedContentScrollY;
        this.mainFrame.setCategoryScrollY(savedCategoryScrollY);
        this.mainFrame.setCurrentCategory(savedCategoryName);

        mainFrame.categoryButtons.clear();
        int catY = 0;
        for (String categoryName : categorizedProperties.keySet()) {
            mainFrame.categoryButtons.add(new CategoryComponent(categoryName, mainFrame, 0, 0, mainFrame.categoryPanelWidth, 20));
            catY += 20;
        }
        mainFrame.setTotalCategoryHeight(catY);

        if (mainFrame.getCurrentCategory() == null && !categorizedProperties.isEmpty()) {
            setCurrentCategory(categorizedProperties.keySet().iterator().next());
        } else if (mainFrame.getCurrentCategory() != null) {
            setupComponentsForCategory((String) mainFrame.getCurrentCategory());
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (this.mainFrame != null) {
            savedContentScrollY = this.mainFrame.scrollY;
            savedCategoryScrollY = this.mainFrame.getCategoryScrollY();
            savedCategoryName = (String) this.mainFrame.getCurrentCategory();
        }
    }

    public void setCurrentCategory(String categoryName) {
        if (!Objects.equals(mainFrame.getCurrentCategory(), categoryName)) {
            mainFrame.setCurrentCategory(categoryName);
            mainFrame.scrollY = 0;
            savedContentScrollY = 0;
            setupComponentsForCategory(categoryName);
        }
    }

    public void setupComponentsForCategory(String categoryName) {
        mainFrame.components.clear();
        List<Object> properties = categorizedProperties.get(categoryName);
        if (properties == null) return;

        int compX = mainFrame.categoryPanelWidth + 10;
        int compWidth = mainFrame.width - mainFrame.categoryPanelWidth - 20;

        for (Object propObject : properties) {
            mainFrame.components.add(new SmartMovingPropertyComponent(propObject, mainFrame, compX, 0, compWidth, 20));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (this.mainFrame != null) {
            this.mainFrame.drawScreen(mouseX, mouseY);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.mainFrame != null) {
             
            if (mouseX >= mainFrame.x && mouseX < mainFrame.x + mainFrame.categoryPanelWidth &&
                    mouseY >= mainFrame.y + mainFrame.titleBarHeight && mouseY < mainFrame.y + mainFrame.height) {

                 
                int adjustedMouseY = mouseY - mainFrame.getCategoryScrollY();
                for (CategoryComponent c : mainFrame.categoryButtons) {
                    if (c.isMouseOver(mouseX, adjustedMouseY)) {
                         
                        setCurrentCategory((String) c.getCategory());
                        return;  
                    }
                }
            }

             
            this.mainFrame.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0 && this.mainFrame != null) {
            ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;

            if (mouseX >= mainFrame.x && mouseX < mainFrame.x + mainFrame.categoryPanelWidth) {
                mainFrame.handleCategoryScroll(dWheel > 0 ? 1 : -1);
            } else {
                mainFrame.mouseScrolled(dWheel);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.mainFrame != null) {
            mainFrame.keyTyped(typedChar, keyCode);
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}