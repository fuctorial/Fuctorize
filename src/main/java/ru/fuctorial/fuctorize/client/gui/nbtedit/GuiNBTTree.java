package ru.fuctorial.fuctorize.client.gui.nbtedit;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.utils.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.opengl.GL11; // <-- Важный импорт

public class GuiNBTTree extends Gui {

    private final NBTTree tree;
    private final List<GuiNBTNode> nodes;

    private int y;
    private int yClick;
    private int top, bottom;
    private int width, height;
    private int contentHeight;
    private int scrollOffset;
    private Node<NamedNBT> focused;
    private GuiEditSingleNBT window;
    private boolean isTreeBuilt = false;

    private boolean isViewOnly;

    private boolean isDraggingScrollbar = false;
    private int scrollbarDragY = 0;

    private ContextMenu contextMenu;
    public static NamedNBT clipboard;

    public GuiNBTTree(NBTTree tree) {
        this(tree, false);
    }

    public GuiNBTTree(NBTTree tree, boolean isViewOnly) {
        this.tree = tree;
        this.isViewOnly = isViewOnly;
        this.yClick = -1;
        this.nodes = new ArrayList<>();
    }

    public void setViewOnly(boolean viewOnly) {
        this.isViewOnly = viewOnly;
    }

    public boolean isTreeBuilt() {
        return isTreeBuilt;
    }

    public Node<NamedNBT> getFocused() {
        return this.focused;
    }

    public NBTTree getNBTTree() {
        return this.tree;
    }

    public int getTreeWidth() {
        return this.width;
    }

    public int getTreeHeight() {
        return this.height;
    }

    private int getMaxScroll() {
        int viewableHeight = bottom - top;
        return Math.max(0, contentHeight - viewableHeight + 1);
    }

    public GuiEditSingleNBT getWindow() {
        return this.window;
    }

    public void closeWindow() {
        this.window = null;
    }

    public void initGUI(int width, int screenHeight, int bottomY) {
        this.width = width;
        this.height = screenHeight;
        this.top = 31;
        this.bottom = bottomY;
        this.yClick = -1;
        this.isTreeBuilt = false;
        this.contextMenu = null;

        if (this.window != null) {
            this.window.initGUI(width, screenHeight);
        }

        rebuildAndSetup();
    }

    public void rebuildAndSetup() {
        if (FuctorizeClient.INSTANCE == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            this.isTreeBuilt = false;
            return;
        }
        rebuildTree(true);
        this.isTreeBuilt = true;
    }

    private void rebuildTree(boolean shiftToFocused) {
        this.y = top;
        this.nodes.clear();

        if (this.tree.getRoot() != null) {
            for (Node<NamedNBT> child : this.tree.getRoot().getChildren()) {
                addNodes(child, 10);
            }
        }

        contentHeight = (y - top);

        if (this.focused != null && !checkValidFocus(this.focused)) {
            setFocused(null);
        }

        if (scrollOffset > 0) scrollOffset = 0;
        if (scrollOffset < -getMaxScroll()) scrollOffset = -getMaxScroll();

        if (shiftToFocused && this.focused != null) {
            shiftTo(this.focused);
        }
    }

    private void addNodes(Node<NamedNBT> node, int x) {
        GuiNBTNode guiNode = new GuiNBTNode(this, node, x, this.y);
        guiNode.updateDisplay();
        this.nodes.add(guiNode);
        this.y += guiNode.height;

        if (node.shouldDrawChildren()) {
            for (Node<NamedNBT> child : node.getChildren()) {
                addNodes(child, x + 10);
            }
        }
    }

    // --- Метод draw рисует ТОЛЬКО дерево ---
    public void draw(int mx, int my) {
        if (isDraggingScrollbar) {
            mouseDragged(mx, my);
        }
        if (!isTreeBuilt) return;

        // Мышиные координаты для дерева (если окно открыто, дерево не реагирует на ховер)
        int cmx = window != null ? -1 : mx;
        int cmy = window != null ? -1 : my;

        Gui.drawRect(0, top, width, bottom, Theme.MAIN_BG.getRGB());
        RenderUtils.startScissor(0, top, width, bottom - top);

        for (GuiNBTNode node : this.nodes) {
            if (node.shouldDraw(top - scrollOffset, bottom - scrollOffset)) {
                node.draw(cmx, cmy, scrollOffset);
            }
        }

        RenderUtils.stopScissor();
        drawScrollBar();
    }

    // --- НОВЫЙ МЕТОД: Рисует окна поверх всего остального ---
    public void drawOverlays(int mx, int my) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 200f); // Поднимаем Z-уровень

        if (this.contextMenu != null) {
            this.contextMenu.draw(mx, my);
        }

        if (this.window != null) {
            Gui.drawRect(0, 0, width, height, 0x80000000); // Затемнение фона
            this.window.draw(mx, my);
        }

        GL11.glPopMatrix();
    }

    public void mouseClicked(int mx, int my, int mouseButton) {
        if (this.window != null) {
            this.window.click(mx, my);
            return;
        }

        if (this.contextMenu != null) {
            this.contextMenu.mouseClicked(mx, my, mouseButton);
            this.contextMenu = null;
            return;
        }

        if (mouseButton == 0 && isScrollBarClicked(mx, my)) {
            this.isDraggingScrollbar = true;
            this.scrollbarDragY = my - getScrollBarHandleY();
            return;
        }

        if (!isTreeBuilt) return;

        if (my >= top && my <= bottom) {
            if (!isViewOnly && mouseButton == 1) {
                Node<NamedNBT> targetNode = null;
                for (GuiNBTNode node : this.nodes) {
                    if (node.clicked(mx, my, scrollOffset)) {
                        targetNode = node.getNode();
                        break;
                    }
                }
                if (targetNode != null) {
                    setFocused(targetNode);
                    this.contextMenu = new ContextMenu(mx, my, this.width, this.bottom, buildContextMenuActions());
                }
            } else if (mouseButton == 0) {
                for (GuiNBTNode node : this.nodes) {
                    if (node.hideShowClicked(mx, my, scrollOffset)) {
                        node.getNode().setDrawChildren(!node.getNode().shouldDrawChildren());
                        rebuildTree(false);
                        return;
                    }
                }
                Node<NamedNBT> newFocus = null;
                for (GuiNBTNode node : this.nodes) {
                    if (node.clicked(mx, my, scrollOffset)) {
                        newFocus = node.getNode();
                        break;
                    }
                }
                setFocused(newFocus);
            }
        }
    }

    public void mouseDragged(int mx, int my) {
        if (this.isDraggingScrollbar) {
            int viewableHeight = bottom - top;
            int maxScroll = getMaxScroll();
            if (maxScroll <= 0) return;
            int handleHeight = getScrollBarHandleHeight();
            float scrollableArea = viewableHeight - handleHeight;
            int newHandleY = my - this.scrollbarDragY;
            float scrollPercent = (newHandleY - top) / scrollableArea;
            this.scrollOffset = (int) (-scrollPercent * maxScroll);
            if (scrollOffset > 0) scrollOffset = 0;
            if (scrollOffset < -maxScroll) scrollOffset = -maxScroll;
        }
    }

    // --- НОВЫЕ МЕТОДЫ ДЛЯ ПЕРЕДАЧИ СОБЫТИЙ В ОКНО ---

    public void handleMouseInput() {
        if (this.window != null) {
            this.window.handleMouseInput();
            return; // Если окно открыто, скроллим его, а не дерево
        }
        // Иначе ничего не делаем, скролл дерева обрабатывается в GuiNBTEdit
    }

    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.window != null) {
            this.window.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            return;
        }
        // Драг скроллбара дерева обрабатывается через mouseDragged, вызываемый из draw()
        // но можно добавить и сюда для надежности
        if (isDraggingScrollbar) {
            mouseDragged(mouseX, mouseY);
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.window != null) {
            this.window.mouseReleased(mouseX, mouseY, state);
        }
        this.isDraggingScrollbar = false;
    }
    // ------------------------------------------------

    private List<ContextMenu.ContextAction> buildContextMenuActions() {
        List<ContextMenu.ContextAction> actions = new ArrayList<>();
        actions.add(new ContextMenu.ContextAction("Copy", focused != null, this::copy));
        actions.add(new ContextMenu.ContextAction("Cut", focused != null && focused.hasParent(), this::cut));
        actions.add(new ContextMenu.ContextAction("Paste", canPaste(), this::paste));
        actions.add(new ContextMenu.ContextAction.Separator());
        actions.add(new ContextMenu.ContextAction("Edit", focused != null && focused.hasParent(), this::editSelected));
        actions.add(new ContextMenu.ContextAction("Delete", focused != null && focused.hasParent(), this::deleteSelected));

        if (focused != null && (focused.getObject().getNBT() instanceof NBTTagCompound || focused.getObject().getNBT() instanceof NBTTagList)) {
            actions.add(new ContextMenu.ContextAction.Separator());
            actions.add(new ContextMenu.ContextAction("Add Compound", true, () -> handleTagCreation((byte) 10)));
            actions.add(new ContextMenu.ContextAction("Add List", true, () -> handleTagCreation((byte) 9)));
            actions.add(new ContextMenu.ContextAction.Separator());
            actions.add(new ContextMenu.ContextAction("Add String", true, () -> handleTagCreation((byte) 8)));
            actions.add(new ContextMenu.ContextAction("Add Int", true, () -> handleTagCreation((byte) 3)));
            actions.add(new ContextMenu.ContextAction("Add Byte", true, () -> handleTagCreation((byte) 1)));
            actions.add(new ContextMenu.ContextAction("Add Short", true, () -> handleTagCreation((byte) 2)));
            actions.add(new ContextMenu.ContextAction("Add Long", true, () -> handleTagCreation((byte) 4)));
            actions.add(new ContextMenu.ContextAction("Add Float", true, () -> handleTagCreation((byte) 5)));
            actions.add(new ContextMenu.ContextAction("Add Double", true, () -> handleTagCreation((byte) 6)));
            actions.add(new ContextMenu.ContextAction("Add Byte[]", true, () -> handleTagCreation((byte) 7)));
            actions.add(new ContextMenu.ContextAction("Add Int[]", true, () -> handleTagCreation((byte) 11)));
        }
        return actions;
    }

    public void updateScreen() {
        if (this.window != null) this.window.update();
    }

    private void drawScrollBar() {
        int viewableHeight = bottom - top;
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarX = width - 6;
            Gui.drawRect(scrollbarX, top, width, bottom, 0x55000000);
            int handleY = getScrollBarHandleY();
            int handleHeight = getScrollBarHandleHeight();
            Gui.drawRect(scrollbarX, handleY, width, handleY + handleHeight, Theme.ORANGE.getRGB());
        }
    }

    public void setFocused(Node<NamedNBT> toFocus) {
        this.focused = toFocus;
    }

    private boolean checkValidFocus(Node<NamedNBT> fc) {
        for (GuiNBTNode node : this.nodes) {
            if (node.getNode() == fc) {
                this.setFocused(fc);
                return true;
            }
        }
        return fc.hasParent() && checkValidFocus(fc.getParent());
    }

    public void handleTagCreation(byte typeId) {
        if (this.isViewOnly || this.focused == null) return;
        this.focused.setDrawChildren(true);
        List<Node<NamedNBT>> children = this.focused.getChildren();
        String typeName = NBTStringHelper.getButtonName(typeId);
        if (this.focused.getObject().getNBT() instanceof NBTTagList) {
            NBTBase nbt = NBTStringHelper.newTag(typeId);
            if (nbt != null) {
                Node<NamedNBT> newNode = new Node<>(this.focused, new NamedNBT("", nbt));
                children.add(newNode);
                this.setFocused(newNode);
            }
        } else {
            if (children.isEmpty()) {
                this.setFocused(this.insert(typeName + "1", typeId));
            } else {
                for (int i = 1; i <= children.size() + 1; ++i) {
                    String name = typeName + i;
                    if (validName(name, children)) {
                        this.setFocused(this.insert(name, typeId));
                        break;
                    }
                }
            }
        }
        rebuildTree(true);
    }

    private boolean validName(String name, List<Node<NamedNBT>> list) {
        for (Node<NamedNBT> node : list) {
            if (node.getObject().getName().equals(name)) {
                return false;
            }
        }
        return true;
    }

    private Node<NamedNBT> insert(String name, byte type) {
        NBTBase nbt = NBTStringHelper.newTag(type);
        if (nbt != null) {
            return this.insert(new NamedNBT(name, nbt));
        }
        return null;
    }

    private Node<NamedNBT> insert(NamedNBT nbt) {
        Node<NamedNBT> newNode = new Node<>(this.focused, nbt);
        if (this.focused.hasChildren()) {
            List<Node<NamedNBT>> children = this.focused.getChildren();
            boolean added = false;
            for (int i = 0; i < children.size(); ++i) {
                if (NBTTree.SORTER.compare(newNode, children.get(i)) < 0) {
                    children.add(i, newNode);
                    added = true;
                    break;
                }
            }
            if (!added) {
                children.add(newNode);
            }
        } else {
            this.focused.addChild(newNode);
        }
        return newNode;
    }

    public void deleteSelected() {
        if (this.isViewOnly) return;
        if (this.focused != null && this.tree.delete(this.focused)) {
            Node<NamedNBT> oldFocused = this.focused;
            this.shiftFocus(true);
            if (this.focused == oldFocused) {
                setFocused(null);
            }
            rebuildTree(false);
        }
    }

    public void editSelected() {
        if (this.isViewOnly) return;
        if (this.focused != null && this.focused.hasParent()) {
            edit();
        }
    }

    private int getScrollBarHandleHeight() {
        int viewableHeight = bottom - top;
        int handleHeight = (int) ((float) viewableHeight / (float) contentHeight * (float) viewableHeight);
        return Math.max(handleHeight, 20);
    }

    private int getScrollBarHandleY() {
        int viewableHeight = bottom - top;
        int maxScroll = getMaxScroll();
        float scrollPercent = (float) -scrollOffset / (float) maxScroll;
        int handleHeight = getScrollBarHandleHeight();
        return top + (int) (scrollPercent * (viewableHeight - handleHeight));
    }

    private boolean isScrollBarClicked(int mx, int my) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarX = width - 6;
            if (mx >= scrollbarX && mx <= width) {
                int handleY = getScrollBarHandleY();
                int handleHeight = getScrollBarHandleHeight();
                return my >= handleY && my <= handleY + handleHeight;
            }
        }
        return false;
    }

    private boolean canAddToParent(NBTBase parent, NBTBase child) {
        if (parent instanceof NBTTagCompound) {
            return true;
        }
        if (parent instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) parent;
            return list.tagCount() == 0 || list.func_150303_d() == child.getId();
        }
        return false;
    }

    public boolean canPaste() {
        if (this.isViewOnly) return false;
        return clipboard != null && this.focused != null && canAddToParent(this.focused.getObject().getNBT(), clipboard.getNBT());
    }

    public void paste() {
        if (canPaste()) {
            this.focused.setDrawChildren(true);
            NamedNBT namedNBT = clipboard.copy();
            if (this.focused.getObject().getNBT() instanceof NBTTagList) {
                namedNBT.setName("");
                Node<NamedNBT> node = new Node<>(this.focused, namedNBT);
                this.focused.addChild(node);
                this.tree.addChildrenToTree(node);
                this.tree.sort(node);
            } else {
                String name = namedNBT.getName();
                List<Node<NamedNBT>> children = this.focused.getChildren();
                if (!validName(name, children)) {
                    for (int i = 1; i <= children.size() + 1; ++i) {
                        String n = name + "(" + i + ")";
                        if (validName(n, children)) {
                            namedNBT.setName(n);
                            break;
                        }
                    }
                }
                Node<NamedNBT> node = this.insert(namedNBT);
                this.tree.addChildrenToTree(node);
                this.tree.sort(node);
            }
            rebuildTree(true);
        }
    }

    public void copy() {
        if (this.focused != null) {
            NamedNBT namedNBT = this.focused.getObject();
            if (namedNBT.getNBT() instanceof NBTTagList) {
                NBTTagList list = new NBTTagList();
                this.tree.addChildrenToList(this.focused, list);
                clipboard = new NamedNBT(namedNBT.getName(), list);
            } else if (namedNBT.getNBT() instanceof NBTTagCompound) {
                NBTTagCompound compound = new NBTTagCompound();
                this.tree.addChildrenToTag(this.focused, compound);
                clipboard = new NamedNBT(namedNBT.getName(), compound);
            } else {
                clipboard = this.focused.getObject().copy();
            }
            setFocused(this.focused);
        }
    }

    public void cut() {
        this.copy();
        this.deleteSelected();
    }

    private void edit() {
        if (focused == null || focused.getParent() == null) return;
        NBTBase base = this.focused.getObject().getNBT();
        NBTBase parent = this.focused.getParent().getObject().getNBT();
        this.window = new GuiEditSingleNBT(this, this.focused, !(parent instanceof NBTTagList), !(base instanceof NBTTagCompound) && !(base instanceof NBTTagList));
        this.window.initGUI(this.width, this.height);
    }

    public void nodeEdited(Node<NamedNBT> node) {
        Node<NamedNBT> parent = node.getParent();
        if (parent != null) {
            Collections.sort(parent.getChildren(), NBTTree.SORTER);
        }
        rebuildTree(true);
    }

    public void arrowKeyPressed(boolean up) {
        if (this.focused == null) {
            this.shift(up ? 16 : -16);
        } else {
            this.shiftFocus(up);
        }
    }

    private int indexOf(Node<NamedNBT> node) {
        for (int i = 0; i < this.nodes.size(); ++i) {
            if (this.nodes.get(i).getNode() == node) {
                return i;
            }
        }
        return -1;
    }

    private void shiftFocus(boolean up) {
        int index = this.indexOf(this.focused);
        if (index != -1) {
            int newIndex = index + (up ? -1 : 1);
            if (newIndex >= 0 && newIndex < this.nodes.size()) {
                this.setFocused(this.nodes.get(newIndex).getNode());
                GuiNBTNode guiNode = this.nodes.get(newIndex);
                int nodeTop = guiNode.y + scrollOffset;
                int nodeBottom = nodeTop + guiNode.height;
                if (nodeTop < top) {
                    scrollOffset = -(guiNode.y - top);
                } else if (nodeBottom > bottom) {
                    scrollOffset = -(guiNode.y + guiNode.height - bottom);
                }
            }
        }
    }

    private void shiftTo(Node<NamedNBT> node) {
        int index = this.indexOf(node);
        if (index != -1) {
            GuiNBTNode gui = this.nodes.get(index);
            int targetY = gui.y - (bottom - top) / 2;
            scrollOffset = -(targetY - top);
            if (scrollOffset > 0) scrollOffset = 0;
            if (scrollOffset < -getMaxScroll()) scrollOffset = -getMaxScroll();
        }
    }

    public void shift(int amount) {
        if (getMaxScroll() <= 0 || window != null) return;
        scrollOffset += amount;
        if (scrollOffset > 0) scrollOffset = 0;
        if (scrollOffset < -getMaxScroll()) scrollOffset = -getMaxScroll();
    }

    static {
        clipboard = null;
    }

    private static class ContextMenu {
        private int x, y;
        private final int width;
        private final int height;
        private final List<ContextAction> actions;
        private final CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        private final int ROW_HEIGHT = 12;

        public ContextMenu(int clickX, int clickY, int screenWidth, int screenBottom, List<ContextAction> actions) {
            this.actions = actions;

            int maxWidth = 0;
            for (ContextAction action : actions) {
                if (!(action instanceof ContextAction.Separator)) {
                    int actionWidth = font.getStringWidth(action.text);
                    if (actionWidth > maxWidth) {
                        maxWidth = actionWidth;
                    }
                }
            }
            this.width = maxWidth + 10;
            this.height = actions.size() * ROW_HEIGHT;

            this.x = clickX;
            if (this.x + this.width > screenWidth) {
                this.x = screenWidth - this.width - 2;
            }

            this.y = clickY;
            if (this.y + this.height > screenBottom) {
                this.y = screenBottom - this.height - 2;
            }
        }

        public void draw(int mouseX, int mouseY) {
            RenderUtils.drawRect(x, y, x + width, y + height, Theme.CATEGORY_BG.getRGB());

            int currentY = y;
            for (ContextAction action : actions) {
                action.draw(x, currentY, width, ROW_HEIGHT, mouseX, mouseY);
                currentY += ROW_HEIGHT;
            }
        }

        public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (mouseButton != 0) return;
            int currentY = y;
            for (ContextAction action : actions) {
                if (action.isMouseOver(x, currentY, width, ROW_HEIGHT, mouseX, mouseY)) {
                    action.execute();
                    break;
                }
                currentY += ROW_HEIGHT;
            }
        }

        private static class ContextAction {
            public final String text;
            public final boolean enabled;
            private final Runnable action;

            public ContextAction(String text, boolean enabled, Runnable action) {
                this.text = text;
                this.enabled = enabled;
                this.action = action;
            }

            public void execute() {
                if (enabled) {
                    action.run();
                }
            }

            public void draw(int x, int y, int width, int height, int mouseX, int mouseY) {
                boolean hovered = isMouseOver(x, y, width, height, mouseX, mouseY);
                if (hovered && enabled) {
                    RenderUtils.drawRect(x, y, x + width, y + height, Theme.COMPONENT_BG_HOVER.getRGB());
                }

                CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
                int textColor = enabled ? Theme.TEXT_WHITE.getRGB() : Theme.TEXT_GRAY.getRGB();
                float textY = y + (height - font.getHeight()) / 2f + 1;
                font.drawString(text, x + 5, textY, textColor);
            }

            public boolean isMouseOver(int x, int y, int width, int height, int mouseX, int mouseY) {
                return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
            }

            public static class Separator extends ContextAction {
                public Separator() {
                    super("", false, () -> {
                    });
                }

                @Override
                public void draw(int x, int y, int width, int height, int mouseX, int mouseY) {
                    int lineY = y + height / 2;
                    RenderUtils.drawRect(x + 2, lineY, x + width - 2, lineY + 1, Theme.DIVIDER.getRGB());
                }
            }
        }
    }
}