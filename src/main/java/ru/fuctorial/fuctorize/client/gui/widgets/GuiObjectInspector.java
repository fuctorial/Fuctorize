// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\gui\widgets\GuiObjectInspector.java
package ru.fuctorial.fuctorize.client.gui.widgets;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.utils.JsonNbtParser;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.ObfuscationMapper; // Updated import
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StringUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class GuiObjectInspector extends GuiScreen {

    private final GuiScreen parentScreen;
    private final Object rootObject;
    private Node rootNode;

    private int panelX, panelY, panelWidth, panelHeight;
    private int scrollOffset = 0;
    private int totalContentHeight = 0;
    private int viewableContentHeight = 0;
    private final List<Node> visibleNodes = new ArrayList<>();

    private GuiTextField filterField;
    private String filterText = "";

    public GuiObjectInspector(Object target) {
        this.parentScreen = (GuiScreen) target;
        this.rootObject = target;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.panelWidth = Math.max(600, this.width / 2);
        this.panelHeight = Math.max(400, this.height - 80);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.viewableContentHeight = this.panelHeight - 40 - 25;

        this.filterField = new GuiTextField(panelX + 10, panelY + 10, panelWidth - 20, 20, false);
        this.filterField.setText(filterText);
        this.filterField.setFocused(true);

        this.buttonList.clear();
        int footerY = panelY + panelHeight - 30;
        int buttonWidth = (panelWidth - 20) / 2 - 5;
        this.buttonList.add(new StyledButton(1, panelX + 10, footerY, buttonWidth, 20, Lang.get("inspector.button.copy_all")));
        this.buttonList.add(new StyledButton(0, panelX + 10 + buttonWidth + 10, footerY, buttonWidth, 20, Lang.get("inspector.button.back")));

        if (rootNode == null) {
            rootNode = new Node(rootObject, "root", 0, null, null, null);
            expandNode(rootNode);
        }
        updateVisibleNodes();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void updateVisibleNodes() {
        visibleNodes.clear();
        addNodesRecursive(rootNode, visibleNodes, filterText.toLowerCase());
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        totalContentHeight = visibleNodes.size() * (font.getHeight() + 2);
        if (totalContentHeight <= viewableContentHeight) scrollOffset = 0;
    }

    private boolean addNodesRecursive(Node node, List<Node> list, String filter) {
        boolean selfVisible = filter.isEmpty() || StringUtils.stripControlCodes(node.getDisplayText()).toLowerCase().contains(filter);

        List<Node> visibleChildren = new ArrayList<>();
        boolean childVisible = false;

        if (node.isExpanded()) {
            for (Node child : node.getChildren()) {
                if (addNodesRecursive(child, visibleChildren, filter)) {
                    childVisible = true;
                }
            }
        }

        if (selfVisible || childVisible) {
            list.add(node);
            list.addAll(visibleChildren);
            return true;
        }
        return false;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
        if (button.id == 1) {
            StringBuilder sb = new StringBuilder();
            recursivelyExpandAndCopy(rootNode, sb);
            setClipboardString(sb.toString());
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("inspector.title.gui"),
                    Lang.get("inspector.message.report_copied"),
                    Notification.NotificationType.SUCCESS, 2000L));
        }
    }

    private void recursivelyExpandAndCopy(Node node, StringBuilder sb) {
        sb.append(node.getCopyableText()).append("\n");
        if (node.isExpandable() && !node.isExpanded()) {
            expandNode(node);
        }
        if (node.isExpanded()) {
            for (Node child : node.getChildren()) {
                recursivelyExpandAndCopy(child, sb);
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (totalContentHeight > viewableContentHeight) {
                scrollOffset -= (dWheel > 0) ? 15 : -15;
                int maxScroll = totalContentHeight - viewableContentHeight;
                if (maxScroll < 0) maxScroll = 0;
                if (scrollOffset < 0) scrollOffset = 0;
                if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.filterField.mouseClicked(mouseX, mouseY, mouseButton);

        int topY = panelY + 35;
        int leftX = panelX + 10;
        if (mouseX < leftX || mouseX > panelX + panelWidth - 10 || mouseY < topY || mouseY > panelY + panelHeight - 35) {
            return;
        }

        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        int lineHeight = font.getHeight() + 2;
        int lineIndex = (mouseY - topY + scrollOffset) / lineHeight;

        if (lineIndex >= 0 && lineIndex < visibleNodes.size()) {
            Node clickedNode = visibleNodes.get(lineIndex);

            int expanderX = leftX + clickedNode.depth * 10;
            int expanderWidth = font.getStringWidth("[+] ");

            // Клик по иконке [+] / [-]
            if (clickedNode.isExpandable() && mouseX >= expanderX && mouseX <= expanderX + expanderWidth) {
                if (mouseButton == 0) toggleNode(clickedNode);
                return;
            }

            // Клик по самому элементу
            if (mouseButton == 0) { // Левый клик
                if (clickedNode.isEditableEnum()) {
                    clickedNode.cycleEnumValue(true); // Вперед
                } else {
                    String lineToCopy = clickedNode.getCopyableText();
                    setClipboardString(StringUtils.stripControlCodes(lineToCopy));
                    FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                            Lang.get("inspector.title.gui"),
                            Lang.get("inspector.message.line_copied"),
                            Notification.NotificationType.SUCCESS, 1500L));
                }
            } else if (mouseButton == 1) { // Правый клик
                if (clickedNode.isEditableEnum()) {
                    clickedNode.cycleEnumValue(false); // Назад
                } else if (clickedNode.isEditableByText()) {
                    mc.displayGuiScreen(new GuiTextInput(this, Lang.format("inspector.input.title", clickedNode.path), clickedNode.getCurrentValueAsString(), clickedNode::trySetValue));
                }
            }
        }
    }

    private void toggleNode(Node node) {
        if (node.isExpanded()) {
            node.setExpanded(false);
        } else {
            if (node.getChildren().isEmpty() && node.isExpandable()) {
                expandNode(node);
            }
            node.setExpanded(true);
        }
        updateVisibleNodes();
    }

    private void expandNode(Node node) {
        node.generateChildren();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());

        this.filterField.drawTextBox();

        int topY = panelY + 35;
        RenderUtils.startScissor(panelX, topY, panelWidth, viewableContentHeight);

        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        int lineHeight = font.getHeight() + 2;
        int currentY = topY - scrollOffset;

        for (Node node : visibleNodes) {
            if (currentY + lineHeight > topY && currentY < topY + viewableContentHeight) {
                boolean isHovered = mouseY >= currentY && mouseY < currentY + lineHeight && mouseX >= panelX && mouseX < panelX + panelWidth;
                if (isHovered) {
                    RenderUtils.drawRect(panelX + 1, currentY, panelX + panelWidth - 1, currentY + lineHeight, Theme.COMPONENT_BG_HOVER.getRGB());
                }
                node.draw(panelX + 10, currentY + 1, font);
            }
            currentY += lineHeight;
        }

        RenderUtils.stopScissor();
        drawScrollBar(topY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScrollBar(int topY) {
        if (totalContentHeight > viewableContentHeight) {
            int scrollbarX = panelX + panelWidth - 8;
            RenderUtils.drawRect(scrollbarX, topY, scrollbarX + 4, topY + viewableContentHeight, 0x55000000);
            float scrollableHeight = totalContentHeight - viewableContentHeight;
            float scrollPercent = (float)scrollOffset / scrollableHeight;
            int handleHeight = (int) ((float) viewableContentHeight / totalContentHeight * viewableContentHeight);
            handleHeight = Math.max(handleHeight, 20);
            int handleY = topY + (int) (scrollPercent * (viewableContentHeight - handleHeight));
            RenderUtils.drawRect(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, Theme.ORANGE.getRGB());
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.filterField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                if (filterText.isEmpty()) {
                    mc.displayGuiScreen(this.parentScreen);
                } else {
                    this.filterField.setText("");
                    this.filterText = "";
                    updateVisibleNodes();
                }
            } else {
                this.filterField.textboxKeyTyped(typedChar, keyCode);
                this.filterText = this.filterField.getText();
                updateVisibleNodes();
            }
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    // ===================================================================
    // INNER CLASS FOR TREE NODE & INSPECTION LOGIC
    // ===================================================================
    private static class Node {
        private static final int MAX_DEPTH = 8;
        private static final int MAX_STRING_LEN = 100;
        private static final List<Class<?>> EDITABLE_TYPES = Arrays.asList(String.class, int.class, Integer.class, byte.class, Byte.class, short.class, Short.class, long.class, Long.class, float.class, Float.class, double.class, Double.class, boolean.class, Boolean.class, UUID.class, ItemStack.class);

        private Object object;
        private final Object parentObject;
        private final Field reflectionField;
        private final Method reflectionMethod;
        private final String path;
        private final int depth;
        private final NodeType type;
        enum NodeType { FIELD, METHOD }

        private boolean expanded = false;
        private final List<Node> children = new ArrayList<>();
        private boolean childrenGenerated = false;

        public Node(Object object, String path, int depth, Object parentObject, Field reflectionField, Method reflectionMethod) {
            this.object = object;
            this.path = path;
            this.depth = depth;
            this.parentObject = parentObject;
            this.reflectionField = reflectionField;
            this.reflectionMethod = reflectionMethod;
            this.type = (reflectionField != null) ? NodeType.FIELD : NodeType.METHOD;
        }

        public boolean isExpanded() { return expanded; }
        public void setExpanded(boolean e) { this.expanded = e; }
        public List<Node> getChildren() { return children; }

        public String getCopyableText() {
            String indent = String.join("", Collections.nCopies(depth, "  "));
            return indent + StringUtils.stripControlCodes(getDisplayText());
        }

        public void draw(int x, int y, CustomFontRenderer font) {
            int indent = depth * 10;
            String text = getDisplayText();
            int iconOffset = 0;

            if (isExpandable()) {
                String expanderColor = (type == NodeType.METHOD) ? "§e" : (expanded ? "§c" : "§a");
                font.drawString(expanderColor + (expanded ? "[-] " : "[+] "), x + indent, y, Color.WHITE.getRGB());
                iconOffset = 18;
            } else if (isEditableEnum()) {
                font.drawString("§b[cycle] ", x + indent, y, Color.WHITE.getRGB());
                iconOffset = 24;
            } else if (isEditableByText()) {
                font.drawString("§d[E] ", x + indent, y, Color.WHITE.getRGB());
                iconOffset = 18;
            }

            int textX = x + indent + iconOffset;
            font.drawString(text, textX, y, Color.WHITE.getRGB());
        }

        public String getDisplayText() {
            String typeColor = "§b";
            String nameColor = (type == NodeType.METHOD) ? "§e" : "§f";

            if (type == NodeType.METHOD && reflectionMethod != null) {
                String returnType = reflectionMethod.getReturnType().getSimpleName();
                String valueStr = (object == null) ? "§cnull" : "§f" + sanitizeValue(path, object);
                return nameColor + path + " " + typeColor + "(" + returnType + ")§7: " + valueStr;
            }

            if (object == null) {
                return nameColor + path + ": §cnull";
            }
            Class<?> cls = object.getClass();
            if (cls.isEnum() && isEditableEnum()) {
                return nameColor + path + typeColor + " ("+cls.getSimpleName()+")" + "§7: §f< " + sanitizeValue(path, object) + " >";
            }
            if (isPrimitiveOrWrapperOrString(cls) || cls.isEnum()) {
                return nameColor + path + typeColor + " ("+cls.getSimpleName()+")" + "§7: §f" + sanitizeValue(path, object);
            }
            if (object instanceof Map) {
                return nameColor + path + "§7: " + typeColor + "Map §7(" + ((Map<?, ?>) object).size() + " entries)";
            }
            if (object instanceof Collection) {
                return nameColor + path + "§7: " + typeColor + "Collection §7(" + ((Collection<?>) object).size() + " items)";
            }
            if (cls.isArray()) {
                return nameColor + path + "§7: " + typeColor + "Array<" + cls.getComponentType().getSimpleName() + "> §7(" + Array.getLength(object) + " items)";
            }
            return nameColor + path + " " + typeColor + "(" + cls.getSimpleName() + ")";
        }

        public boolean isExpandable() {
            if (object == null || depth >= MAX_DEPTH) return false;
            Class<?> cls = object.getClass();
            return !(isPrimitiveOrWrapperOrString(cls) || cls.isEnum() || cls == UUID.class || cls == ItemStack.class);
        }

        public boolean isEditableEnum() {
            return type == NodeType.FIELD && reflectionField != null && !Modifier.isFinal(reflectionField.getModifiers()) &&
                    reflectionField.getType().isEnum();
        }

        public boolean isEditableByText() {
            return type == NodeType.FIELD && reflectionField != null && !Modifier.isFinal(reflectionField.getModifiers()) &&
                    EDITABLE_TYPES.contains(reflectionField.getType());
        }

        public String getCurrentValueAsString() {
            if (object instanceof ItemStack) {
                return itemStackToString((ItemStack)object);
            }
            return (object == null) ? "null" : String.valueOf(object);
        }

        public void cycleEnumValue(boolean forward) {
            if (!isEditableEnum() || parentObject == null) return;
            try {
                Class<?> enumType = reflectionField.getType();
                Object[] constants = enumType.getEnumConstants();
                int currentIndex = -1;
                for (int i = 0; i < constants.length; i++) {
                    if (constants[i] == this.object) {
                        currentIndex = i;
                        break;
                    }
                }
                int nextIndex = (currentIndex + (forward ? 1 : -1) + constants.length) % constants.length;
                Object newValue = constants[nextIndex];

                reflectionField.set(parentObject, newValue);
                this.object = newValue;
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                        Lang.get("inspector.title.short"),
                        Lang.format("inspector.message.value_set", String.valueOf(newValue)),
                        Notification.NotificationType.SUCCESS, 1500L));
            } catch(Exception e) {
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                        Lang.get("inspector.error.title"), e.getMessage(), Notification.NotificationType.ERROR, 3000L));
            }
        }

        public void trySetValue(String newValue) {
            if (!isEditableByText() || parentObject == null) return;
            try {
                Object parsedValue = parseValue(newValue, reflectionField.getType());
                reflectionField.set(parentObject, parsedValue);
                this.object = parsedValue;
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                        Lang.get("inspector.title.short"),
                        Lang.get("inspector.message.value_changed"),
                        Notification.NotificationType.SUCCESS, 2000L));
            } catch (Exception e) {
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                        Lang.get("inspector.error.title"), e.getMessage(), Notification.NotificationType.ERROR, 3000L));
            }
        }

        private Object parseValue(String value, Class<?> type) throws Exception {
            String trimmed = value.trim();
            if (trimmed.equalsIgnoreCase("null")) {
                if(type.isPrimitive()) throw new IllegalArgumentException(Lang.get("inspector.error.null_for_primitive"));
                return null;
            }
            if (type == UUID.class) return UUID.fromString(trimmed);
            if (type == ItemStack.class) return parseItemStack(trimmed);
            if (type == String.class) return value;
            if (type == int.class || type == Integer.class) return Integer.parseInt(trimmed);
            if (type == byte.class || type == Byte.class) return Byte.parseByte(trimmed);
            if (type == short.class || type == Short.class) return Short.parseShort(trimmed);
            if (type == long.class || type == Long.class) return Long.parseLong(trimmed);
            if (type == float.class || type == Float.class) return Float.parseFloat(trimmed);
            if (type == double.class || type == Double.class) return Double.parseDouble(trimmed);
            if (type == boolean.class || type == Boolean.class) {
                if (!trimmed.equalsIgnoreCase("true") && !trimmed.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(Lang.get("inspector.error.boolean_expected"));
                }
                return Boolean.parseBoolean(trimmed);
            }
            throw new IllegalArgumentException(Lang.format("inspector.error.unsupported_type", type.getSimpleName()));
        }

        private ItemStack parseItemStack(String text) throws Exception {
            if (text.isEmpty() || text.equalsIgnoreCase("null") || text.equalsIgnoreCase("empty")) return null;
            String cleanText = StringUtils.stripControlCodes(text);
            int nbtStartIndex = cleanText.indexOf('{');
            String itemPart = (nbtStartIndex == -1) ? cleanText : cleanText.substring(0, nbtStartIndex).trim();
            String nbtPart = (nbtStartIndex == -1) ? null : cleanText.substring(nbtStartIndex);
            String[] mainParts = itemPart.split("x");
            if (mainParts.length < 2) throw new IllegalArgumentException(Lang.get("inspector.error.invalid_syntax"));
            int stackSize = Integer.parseInt(mainParts[0].trim());
            String idAndDamage = mainParts[1].trim();
            String idStr;
            int damage = 0;
            if (idAndDamage.contains("/")) {
                String[] idParts = idAndDamage.split("/");
                idStr = idParts[0].trim();
                if (idParts.length > 1) damage = Integer.parseInt(idParts[1].trim());
            } else {
                idStr = idAndDamage;
            }
            Item item = (Item) Item.itemRegistry.getObject(idStr);
            if (item == null) {
                try {
                    item = Item.getItemById(Integer.parseInt(idStr));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(Lang.format("inspector.error.unknown_item_id", idStr));
                }
            }
            if (item == null) throw new IllegalArgumentException(Lang.format("inspector.error.unknown_item_id", idStr));

            ItemStack itemStack = new ItemStack(item, stackSize, damage);
            if (nbtPart != null) {
                try {
                    NBTBase nbt = JsonToNBT.func_150315_a(nbtPart);
                    if (nbt instanceof NBTTagCompound) {
                        itemStack.setTagCompound((NBTTagCompound) nbt);
                    } else {
                        throw new IllegalArgumentException(Lang.get("inspector.error.nbt_must_be_compound"));
                    }
                } catch (NBTException e) {
                    throw new Exception(Lang.format("inspector.error.nbt_parse_failed", e.getMessage()));
                }
            }
            return itemStack;
        }

        private String itemStackToString(ItemStack stack) {
            if (stack == null) return "null";
            String idString = Item.itemRegistry.getNameForObject(stack.getItem());
            if (idString == null) idString = String.valueOf(Item.getIdFromItem(stack.getItem()));

            String base = stack.stackSize + "x" + idString;
            if (stack.getItemDamage() != 0) {
                base += "/" + stack.getItemDamage();
            }
            if (stack.hasTagCompound()) {
                base += " " + JsonNbtParser.nbtToString(stack.getTagCompound());
            }
            return base;
        }

        public void generateChildren() {
            if (childrenGenerated || !isExpandable()) return;
            children.clear();

            try {
                for (Method method : object.getClass().getDeclaredMethods()) {
                    if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) continue;
                    String name = method.getName();
                    if ((name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2) || name.startsWith("has")) {
                        try {
                            method.setAccessible(true);
                            Object result = method.invoke(object);
                            String mcpName = ObfuscationMapper.getMcpName(name) + "()"; // Updated usage
                            children.add(new Node(result, mcpName, depth + 1, object, null, method));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            Class<?> currentClass = object.getClass();
            while(currentClass != null && currentClass != Object.class) {
                for(Field field : currentClass.getDeclaredFields()){
                    if(Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    String fieldName = field.getName();
                    String mcpFieldName = ObfuscationMapper.getMcpName(fieldName); // Updated usage
                    children.add(new Node(safeGetField(object, field), mcpFieldName, depth + 1, object, field, null));
                }
                currentClass = currentClass.getSuperclass();
            }
            children.sort(Comparator.comparing((Node n) -> n.type).thenComparing(n -> n.path));
            childrenGenerated = true;
        }

        private Object safeGetField(Object target, Field f) {
            try {
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable t) {
                return "§c<access_error>";
            }
        }

        private String sanitizeValue(String pathOrField, Object value) {
            if (value instanceof ItemStack) {
                return itemStackToString((ItemStack)value);
            }
            return truncate(String.valueOf(value), MAX_STRING_LEN);
        }

        private String truncate(String s, int max) {
            if (s == null) return "null";
            return s.length() > max ? s.substring(0, max) + "..." : s;
        }

        private boolean isPrimitiveOrWrapperOrString(Class<?> c) {
            return c.isPrimitive() || c == String.class || Number.class.isAssignableFrom(c) || c == Boolean.class;
        }
    }
}
