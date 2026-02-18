 
package ru.fuctorial.fuctorize.client.gui.clickgui;

import ru.fuctorial.fuctorize.client.gui.clickgui.components.CategoryComponent;
import ru.fuctorial.fuctorize.client.gui.clickgui.components.Component;
import ru.fuctorial.fuctorize.client.gui.clickgui.components.ModuleComponent;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;

public abstract class AbstractFrame {
    public int x, y, width, height;
    public int scrollY = 0;
    public int categoryPanelWidth = 100;
    public int titleBarHeight = 25;

    protected final String title;
    public Object currentCategory;
    public final ArrayList<CategoryComponent> categoryButtons = new ArrayList<>();
    public final ArrayList<Component> components = new ArrayList<>();

    protected boolean isDragging;
    protected int dragX, dragY;
    protected final Minecraft mc = Minecraft.getMinecraft();

    public AbstractFrame(String title, int x, int y, int width, int height) {
        this.title = title;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

     
    public abstract void setup();
    public abstract void drawScreen(int mouseX, int mouseY);
    public abstract void mouseClicked(int mouseX, int mouseY, int mouseButton);
    public abstract void mouseReleased(int mouseX, int mouseY, int state);
    public abstract void keyTyped(char typedChar, int keyCode);
    public abstract void mouseScrolled(int direction);

     
    public abstract boolean isAnyTextFieldFocused();

     
    public abstract void setCurrentCategory(Object category);

     
    public abstract ModuleComponent getBindingComponent();

     
    public abstract void setBindingComponent(ModuleComponent component);

    public Object getCurrentCategory() {
        return currentCategory;
    }
}