 
package ru.fuctorial.fuctorize.client.gui.nbtedit;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import java.awt.Color;
import ru.fuctorial.fuctorize.utils.NBTStringHelper;
import ru.fuctorial.fuctorize.utils.NamedNBT;
import ru.fuctorial.fuctorize.utils.Node;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.EnumChatFormatting;

public class GuiNBTNode extends Gui {

    private final Node<NamedNBT> node;
    private final GuiNBTTree tree;
    protected int height;
    protected int x;
    public int y;

    private static final int TEXT_INDENT = 75;

    public GuiNBTNode(GuiNBTTree tree, Node<NamedNBT> node, int x, int y) {
        this.tree = tree;
        this.node = node;
        this.x = x;
        this.y = y;
    }

    public void draw(int mx, int my, int scrollOffset) {
        int currentY = this.y + scrollOffset;
        boolean selected = this.tree.getFocused() == this.node;
        boolean hover = this.clicked(mx, my, scrollOffset);

        if (selected) {
            Gui.drawRect(0, currentY, tree.getTreeWidth(), currentY + height, Theme.COMPONENT_BG_HOVER.getRGB());
        } else if (hover) {
            Gui.drawRect(0, currentY, tree.getTreeWidth(), currentY + height, Theme.COMPONENT_BG.getRGB());
        }

        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        int textY = currentY + (height - font.getHeight()) / 2;

        if (this.node.hasChildren()) {
            String expander = this.node.shouldDrawChildren() ? "[-]" : "[+]";
            int expanderColor = this.hideShowClicked(mx, my, scrollOffset) ? Theme.TEXT_WHITE.getRGB() : Theme.TEXT_GRAY.getRGB();
            font.drawString(expander, this.x - 8, textY + 1, expanderColor);
        }

        drawTagMarker(this.x + 5, currentY);

         
         
        String name = node.getObject().getName();
        String valueStr = NBTStringHelper.toString(node.getObject().getNBT());

        String displayString;
        if (name == null || name.isEmpty()) {
             
            displayString = EnumChatFormatting.WHITE + valueStr;
        } else {
             
            displayString = name + ": " + EnumChatFormatting.WHITE + valueStr;
        }

        int textColor = selected ? Theme.TEXT_WHITE.getRGB() : Theme.TEXT_GRAY.getRGB();
        font.drawString(displayString, this.x + TEXT_INDENT, textY + 1, textColor);
    }

    private void drawTagMarker(int markerX, int markerY) {
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        byte id = this.node.getObject().getNBT().getId();

        String markerText;
        Color markerColor;

        switch (id) {
            case 10: markerText = "Compound"; markerColor = Theme.NBT_COMPOUND; break;
            case 9:  markerText = "List"; markerColor = Theme.NBT_LIST; break;
            case 8:  markerText = "String"; markerColor = Theme.NBT_STRING; break;
            case 1:  markerText = "Byte"; markerColor = Theme.NBT_NUMBER; break;
            case 2:  markerText = "Short"; markerColor = Theme.NBT_NUMBER; break;
            case 3:  markerText = "Int"; markerColor = Theme.NBT_NUMBER; break;
            case 4:  markerText = "Long"; markerColor = Theme.NBT_NUMBER; break;
            case 5:  markerText = "Float"; markerColor = Theme.NBT_NUMBER; break;
            case 6:  markerText = "Double"; markerColor = Theme.NBT_NUMBER; break;
            case 7:  markerText = "Byte[]"; markerColor = Theme.NBT_ARRAY; break;
            case 11: markerText = "Int[]"; markerColor = Theme.NBT_ARRAY; break;
            default: markerText = "Unknown"; markerColor = Color.WHITE; break;
        }

        int markerTextWidth = font.getStringWidth(markerText);
        int bgWidth = markerTextWidth + 6;
        int textY = markerY + (height - font.getHeight()) / 2;

        RenderUtils.drawRect(markerX, markerY + 1, markerX + bgWidth, markerY + height - 1, markerColor.darker().getRGB());
        font.drawString(markerText, markerX + 3, textY + 1, markerColor.brighter().getRGB());
    }

    public boolean clicked(int mx, int my, int scrollOffset) {
        int currentY = this.y + scrollOffset;
        return mx >= 0 && my >= currentY && mx < tree.getTreeWidth() && my < currentY + this.height;
    }

    public boolean hideShowClicked(int mx, int my, int scrollOffset) {
        int currentY = this.y + scrollOffset;
        return node.hasChildren() && mx >= this.x - 8 && my >= currentY && mx < this.x + 2 && my < currentY + this.height;
    }

    public void updateDisplay() {
        if (FuctorizeClient.INSTANCE == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;
        this.height = FuctorizeClient.INSTANCE.fontManager.regular_18.getHeight() + 4;
    }

    public boolean shouldDraw(int top, int bottom) {
        return this.y + this.height >= top && this.y <= bottom;
    }

    public Node<NamedNBT> getNode() {
        return this.node;
    }
}