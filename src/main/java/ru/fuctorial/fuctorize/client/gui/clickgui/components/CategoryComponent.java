 
package ru.fuctorial.fuctorize.client.gui.clickgui.components;

import ru.fuctorial.fuctorize.client.gui.clickgui.AbstractFrame;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.Category;
import java.awt.Color;
import net.minecraft.client.gui.Gui;

public class CategoryComponent extends Component {
    private final Object categoryObject;
    private final String categoryName;

    public CategoryComponent(Category category, AbstractFrame parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.categoryObject = category;
         
        this.categoryName = category.getDisplayName();
    }

    public CategoryComponent(String categoryName, AbstractFrame parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.categoryObject = categoryName;
         
        this.categoryName = categoryName.substring(0, 1).toUpperCase() + categoryName.substring(1).toLowerCase();
    }

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        super.drawComponent(mouseX, mouseY);
        if (getFont() == null) return;

        boolean isSelected = this.categoryObject.equals(parent.getCurrentCategory());
        Color bgColor = isSelected ? Theme.ORANGE : new Color(0, 0, 0, 0);

        Gui.drawRect(parent.x + x, parent.y + y, parent.x + x + this.width, parent.y + y + height, bgColor.getRGB());

         
        float textWidth = getFont().getStringWidth(this.categoryName);
        float textX = (parent.x + this.x) + (this.width / 2f) - (textWidth / 2f);
        float textY = (parent.y + this.y) + (this.height / 2f) - (getFont().getHeight() / 2f);

        getFont().drawString(this.categoryName, textX, textY, Theme.TEXT_WHITE.getRGB());
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isMouseOver(mouseX, mouseY) && mouseButton == 0) {
            parent.setCurrentCategory(this.categoryObject);
        }
    }

    public Object getCategory() { return categoryObject; }
}
