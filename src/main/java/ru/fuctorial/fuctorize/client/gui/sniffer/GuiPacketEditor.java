package ru.fuctorial.fuctorize.client.gui.sniffer;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.Unpooled;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextArea;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.module.impl.PacketSniffer;
import ru.fuctorial.fuctorize.utils.*;

import java.awt.Color;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.StringUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GuiPacketEditor extends GuiScreen {
    private enum Mode {VIEW_RAW, EDIT_FIELDS}
    private enum ByteArrayDisplayMode {HEX, TEXT}

    private Mode currentMode = Mode.EDIT_FIELDS;
    private static final List<Class<?>> SUPPORTED_TYPES = Arrays.asList(String.class, int.class, Integer.class, byte.class, Byte.class, short.class, Short.class, long.class, Long.class, float.class, Float.class, double.class, Double.class, boolean.class, Boolean.class, ItemStack.class, UUID.class, byte[].class, int[].class, String[].class, long[].class, float[].class, double[].class);
    private final GuiScreen parentScreen;
    private final PacketInfo packetInfo;

    // --- Raw View Components ---
    private GuiTextArea rawViewArea;
    // ---------------------------

    private final List<EditableField> editableFields = new ArrayList<>();
    private final Map<EditableField, String> fieldErrors = new HashMap<>();
    private int editFieldsScrollOffset = 0;
    private int panelX, panelY, panelWidth, panelHeight;
    private int viewableAreaHeight;
    private StyledButton sendButton;

    // --- Scrollbar Logic ---
    private boolean isDraggingScrollbar = false;
    private int initialClickY = 0;
    private int initialScrollY = 0;
    private static final int SCROLLBAR_WIDTH = 8;

    public GuiPacketEditor(PacketInfo packetInfo, GuiScreen parentScreen) {
        this.packetInfo = packetInfo;
        this.parentScreen = parentScreen;
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != Object.class && clazz != null) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            return;
        }

        panelWidth = Math.min(800, this.width - 40);
        panelHeight = Math.min(600, this.height - 80);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        int headerHeight = 35;
        viewableAreaHeight = panelHeight - headerHeight - 5;

        this.buttonList.clear();

        // --- Buttons ---
        int padding = 10;
        int availableWidth = panelWidth - 20;
        int buttonHeight = 20;
        int buttonsY = panelY + panelHeight + 10;

        String txtSend = Lang.get("editor.button.send_packet");
        String txtBack = Lang.get("editor.button.back");

        if (availableWidth > 320) {
            int btnW = 150;
            int totalW = btnW * 2 + padding;
            int startX = panelX + (panelWidth - totalW) / 2;
            this.sendButton = new StyledButton(1, startX, buttonsY, btnW, buttonHeight, txtSend);
            this.buttonList.add(this.sendButton);
            this.buttonList.add(new StyledButton(0, startX + btnW + padding, buttonsY, btnW, buttonHeight, txtBack));
        } else {
            int btnW = Math.min(200, availableWidth);
            int startX = panelX + (panelWidth - btnW) / 2;
            this.sendButton = new StyledButton(1, startX, buttonsY, btnW, buttonHeight, txtSend);
            this.buttonList.add(this.sendButton);
            this.buttonList.add(new StyledButton(0, startX, buttonsY + buttonHeight + 5, btnW, buttonHeight, txtBack));
        }

        // --- Initialize Raw View Area ---
        int separatorY = panelY + 22 + 15;
        int topTextY = separatorY + 8;
        int textAreaHeight = viewableAreaHeight;
        int textAreaWidth = panelWidth - 20;

        // Создаем Text Area для сырых данных
        this.rawViewArea = new GuiTextArea(panelX + 10, topTextY, textAreaWidth, textAreaHeight);

        // Заполняем HEX данными или дампом
        String hexContent;
        if (packetInfo.rawPayloadBytes != null && packetInfo.rawPayloadBytes.length > 0) {
            hexContent = HexUtils.bytesToHex(packetInfo.rawPayloadBytes);
        } else {
            // Если байтов нет (ванильный пакет), показываем текстовое представление,
            // но редактировать его как HEX нельзя (поэтому Send будет работать хитро)
            hexContent = packetInfo.getSerializedData();
        }
        this.rawViewArea.setText(hexContent);
        // -------------------------------

        CustomFontRenderer textFont = FuctorizeClient.INSTANCE.fontManager.regular_18;

        if (editableFields.isEmpty()) {
            if (!(packetInfo.rawPacket instanceof FMLProxyPacket)) {
                List<String> blacklistedFields = Arrays.asList("netHandler", "field_148842_a", "dispatcher", "target", "field_149369_a");
                List<Field> fieldsToEdit = getAllFields(packetInfo.rawPacket.getClass());

                for (Field field : fieldsToEdit) {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()) || blacklistedFields.contains(field.getName())) {
                        continue;
                    }
                    field.setAccessible(true);
                    editableFields.add(new EditableField(field));
                }

                int maxLabelWidth = 0;
                for (EditableField ef : editableFields) {
                    int labelWidth = textFont.getStringWidth(ef.getDisplayLabel());
                    if (ef.field.getType() == byte[].class) {
                        labelWidth += 80;
                    }
                    if (labelWidth > maxLabelWidth) maxLabelWidth = labelWidth;
                }

                for (EditableField ef : editableFields) {
                    String oldText = ef.currentValue;
                    int textFieldX = panelX + 10 + maxLabelWidth + 8;
                    int textFieldWidth = panelWidth - (maxLabelWidth + 10 + 8 + 25);

                    if (ef.isEnum) {
                        ef.setDimensions(textFieldX, 0, textFieldWidth, 18);
                    } else if (ef.isLongText || ef.field.getType().isArray()) {
                        GuiTextArea textArea = new GuiTextArea(textFieldX, 0, textFieldWidth, 18);
                        textArea.setText(oldText);
                        ef.textField = textArea;
                    } else {
                        GuiTextField textField = new GuiTextField(textFieldX, 0, textFieldWidth, 18, false);
                        textField.setText(oldText);
                        ef.textField = textField;
                    }

                    if (!ef.isEditableByUser) {
                        if (ef.textField instanceof GuiTextField) {
                            ((GuiTextField) ef.textField).func_82265_c(false);
                            ((GuiTextField) ef.textField).setTextColor(0x808080);
                        }
                        if (ef.textField instanceof GuiTextArea) {
                            ((GuiTextArea) ef.textField).setFocused(false);
                        }
                    }
                }
            }
        }

        validateAllFields();
    }

    private void sendPacket() {
        if (currentMode == Mode.VIEW_RAW) {
            sendRawPacket();
            return;
        }

        if (!fieldErrors.isEmpty()) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    Lang.get("editor.notify.send_error.title"),
                    Lang.get("editor.notify.send_error.invalid_fields"),
                    Notification.NotificationType.ERROR, 3000L));
            return;
        }
        try {
            Packet packetToSend = PacketSerializer.clonePacket(this.packetInfo);
            if (packetToSend == null) throw new Exception("Failed to clone the packet.");

            for (EditableField ef : editableFields) {
                if (ef.isEditable()) {
                    Object parsedValue = parseValue(ef.getEditedText(), ef.field.getType());
                    ef.field.set(packetToSend, parsedValue);
                }
            }
            dispatchPacket(packetToSend);
        } catch (Exception e) {
            e.printStackTrace();
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Error", e.getMessage(), Notification.NotificationType.ERROR, 5000L));
        }
    }

    private void sendRawPacket() {
        try {
            String hexData = rawViewArea.getText();

            // 1. Проверяем, является ли пакет FMLProxyPacket (наиболее частый случай для raw hex)
            if (packetInfo.rawPacket instanceof FMLProxyPacket) {
                byte[] newPayload = HexUtils.hexToBytes(hexData);
                FMLProxyPacket original = (FMLProxyPacket) packetInfo.rawPacket;

                // Создаем новый пакет с тем же каналом, но новыми данными
                FMLProxyPacket newPacket = new FMLProxyPacket(Unpooled.copiedBuffer(newPayload), original.channel());
                newPacket.setDispatcher(original.getDispatcher());
                newPacket.setTarget(original.getTarget());

                dispatchPacket(newPacket);
                return;
            }

            // Для других типов пакетов реконструкция из HEX сложнее без буфера
            // Покажем предупреждение или попробуем, если есть поддержка (в будущем)
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                    "Not Supported",
                    "Raw HEX editing is currently only supported for CustomPayload/FML packets.",
                    Notification.NotificationType.WARNING, 4000L
            ));

        } catch (IllegalArgumentException e) {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Invalid Hex", "Check your input format.", Notification.NotificationType.ERROR, 3000L));
        } catch (Exception e) {
            e.printStackTrace();
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification("Error", e.getMessage(), Notification.NotificationType.ERROR, 3000L));
        }
    }

    private void dispatchPacket(Packet packetToSend) {
        if ("SENT".equals(packetInfo.direction)) {
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(packetToSend);
                PacketSniffer.logManuallySentPacket(packetToSend);
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                        Lang.get("editor.notify.sent.title"),
                        Lang.format("editor.notify.sent.to_server", packetInfo.cleanName),
                        Notification.NotificationType.SUCCESS, 2000L));
            }
        } else {
            NetHandlerPlayClient netHandler = mc.getNetHandler();
            if (netHandler != null) {
                NetworkManager networkManager = netHandler.getNetworkManager();
                ReflectionUtils.receivePacket(networkManager, packetToSend);
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                        Lang.get("editor.notify.processed.title"),
                        Lang.format("editor.notify.processed.locally", packetInfo.cleanName),
                        Notification.NotificationType.SUCCESS, 2000L));
            }
        }
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == 0) this.mc.displayGuiScreen(this.parentScreen);
        if (button.id == 1) sendPacket();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parentScreen);
            return;
        }

        if (currentMode == Mode.VIEW_RAW) {
            if (rawViewArea.isFocused()) {
                rawViewArea.textboxKeyTyped(typedChar, keyCode);
            }
            return;
        }

        if (currentMode == Mode.EDIT_FIELDS) {
            boolean fieldFocused = false;
            for (EditableField ef : editableFields) {
                if (ef.isFocused()) {
                    ef.keyTyped(typedChar, keyCode);
                    validateField(ef);
                    fieldFocused = true;
                    break;
                }
            }
            if (!fieldFocused) super.keyTyped(typedChar, keyCode);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Переключение вкладок
        if (mouseY >= panelY + 22 && mouseY <= panelY + 37) {
            if (mouseX >= panelX + 5 && mouseX <= panelX + 5 + 80) {
                currentMode = Mode.VIEW_RAW;
                isDraggingScrollbar = false;
                return;
            } else if (mouseX >= panelX + 90 && mouseX <= panelX + 90 + 80) {
                currentMode = Mode.EDIT_FIELDS;
                isDraggingScrollbar = false;
                return;
            }
        }

        // Handling clicks in View Raw
        if (currentMode == Mode.VIEW_RAW) {
            rawViewArea.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        // Scrollbar Logic for Edit Fields
        int contentHeight = calculateEditFieldsHeight();
        if (contentHeight > viewableAreaHeight) {
            int scrollbarX = panelX + panelWidth - 8;
            int topY = panelY + 22 + 15 + 8;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH &&
                    mouseY >= topY && mouseY <= topY + viewableAreaHeight) {
                if (mouseButton == 0) {
                    isDraggingScrollbar = true;
                    initialClickY = mouseY;
                    initialScrollY = editFieldsScrollOffset;
                    return;
                }
            }
        }

        if (currentMode == Mode.EDIT_FIELDS) {
            int adjustedMouseY = mouseY - editFieldsScrollOffset;
            for (EditableField ef : editableFields) {
                if (ef.field.getType() == byte[].class && ef.isViewToggleButtonClicked(mouseX, adjustedMouseY)) {
                    ef.toggleByteArrayView();
                    int savedScroll = editFieldsScrollOffset;
                    this.initGui();
                    editFieldsScrollOffset = savedScroll;
                    return;
                }
                ef.mouseClicked(mouseX, adjustedMouseY, mouseButton);
                if (ef.isMouseOver(mouseX, adjustedMouseY)) {
                    validateAllFields();
                }
            }
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) {
            isDraggingScrollbar = false;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (isDraggingScrollbar && clickedMouseButton == 0) {
            int contentHeight = calculateEditFieldsHeight();
            if (contentHeight <= viewableAreaHeight) return;
            int deltaY = mouseY - initialClickY;
            int maxScroll = contentHeight - viewableAreaHeight;
            float ratio = (float)contentHeight / (float)viewableAreaHeight;
            int scrollDelta = (int) (deltaY * ratio * -1);
            int newScroll = initialScrollY + scrollDelta;
            if (newScroll > 0) newScroll = 0;
            if (newScroll < -maxScroll) newScroll = -maxScroll;
            editFieldsScrollOffset = newScroll;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (currentMode == Mode.VIEW_RAW) {
                // rawViewArea handles its own scrolling inside standard mouse handling or we can delegate if implemented
            } else {
                handleScroll(dWheel, viewableAreaHeight, calculateEditFieldsHeight(), editFieldsScrollOffset, (newOffset) -> editFieldsScrollOffset = newOffset);
            }
        }
    }

    private void handleScroll(int dWheel, int viewHeight, int contentHeight, int currentOffset, java.util.function.Consumer<Integer> setNewOffset) {
        if (contentHeight > viewHeight) {
            int newOffset = currentOffset + (dWheel > 0 ? 20 : -20);
            if (newOffset > 0) newOffset = 0;
            int maxScroll = contentHeight - viewHeight;
            if (newOffset < -maxScroll) newOffset = -maxScroll;
            setNewOffset.accept(newOffset);
        }
    }

    private int calculateEditFieldsHeight() {
        int h = 0;
        for (EditableField ef : editableFields) {
            h += ef.getHeight() + 7;
        }
        return h;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) {
            if (mc.currentScreen == this) this.initGui();
            return;
        }
        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        RenderUtils.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY - 1, panelX, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX + panelWidth, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(panelX - 1, panelY + panelHeight, panelX + panelWidth + 1, panelY + panelHeight + 1, Theme.BORDER.getRGB());
        String title = String.format("[%s] %s", packetInfo.direction, packetInfo.cleanName);
        float titleX = panelX + (panelWidth - titleFont.getStringWidth(title)) / 2.0f;
        titleFont.drawString(title, titleX, panelY + 8, Theme.ORANGE.getRGB());
        int separatorY = panelY + 22;
        RenderUtils.drawRect(panelX + 5, separatorY, panelX + panelWidth - 5, separatorY + 1, Theme.DIVIDER.getRGB());
        drawTabs(mouseX, mouseY);
        separatorY += 15;
        RenderUtils.drawRect(panelX + 5, separatorY, panelX + panelWidth - 5, separatorY + 1, Theme.DIVIDER.getRGB());
        int topTextY = separatorY + 8;

        if (currentMode == Mode.VIEW_RAW) {
            // Draw Raw Text Area
            rawViewArea.drawTextBox();
        } else {
            drawEditView(topTextY, mouseX, mouseY);
        }

        for (Object button : this.buttonList) {
            ((GuiButton) button).drawButton(this.mc, mouseX, mouseY);
        }
    }

    private void drawTabs(int mouseX, int mouseY) {
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        int tabY = panelY + 23;
        int tabHeight = 14;
        int tabWidth = 80;
        boolean isViewHovered = mouseX >= panelX + 5 && mouseX <= panelX + 5 + tabWidth && mouseY >= tabY && mouseY < tabY + tabHeight;
        RenderUtils.drawRect(panelX + 5, tabY, panelX + 5 + tabWidth, tabY + tabHeight, currentMode == Mode.VIEW_RAW ? Theme.ORANGE.getRGB() : (isViewHovered ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB()));
        String viewText = Lang.get("editor.tab.view_raw");
        float viewTextX = panelX + 5 + (tabWidth - font.getStringWidth(viewText)) / 2f;
        float viewTextY = tabY + (tabHeight - font.getHeight()) / 2f + 1;
        font.drawString(viewText, viewTextX, viewTextY, -1);
        boolean isEditHovered = mouseX >= panelX + 90 && mouseX <= panelX + 90 + tabWidth && mouseY >= tabY && mouseY < tabY + tabHeight;
        int editTabColor = Theme.COMPONENT_BG.getRGB();
        if (currentMode == Mode.EDIT_FIELDS) {
            editTabColor = Theme.ORANGE.getRGB();
        } else if (isEditHovered) {
            editTabColor = Theme.COMPONENT_BG_HOVER.getRGB();
        }
        RenderUtils.drawRect(panelX + 90, tabY, panelX + 90 + tabWidth, tabY + tabHeight, editTabColor);
        String editText = Lang.get("editor.tab.edit_fields");
        float editTextX = panelX + 90 + (tabWidth - font.getStringWidth(editText)) / 2f;
        float editTextY = tabY + (tabHeight - font.getHeight()) / 2f + 1;
        font.drawString(editText, editTextX, editTextY, -1);
    }

    private void drawEditView(int topTextY, int mouseX, int mouseY) {
        CustomFontRenderer textFont = FuctorizeClient.INSTANCE.fontManager.regular_18;
        RenderUtils.startScissor(panelX + 5, topTextY, panelWidth - 10, viewableAreaHeight);

        GL11.glPushMatrix();
        GL11.glTranslatef(0, editFieldsScrollOffset, 0);

        int currentY = topTextY;
        int adjustedMouseY = mouseY - editFieldsScrollOffset;

        if (editableFields.isEmpty()) {
            String message = Lang.get("editor.message.no_fields");
            if (packetInfo.rawPacket instanceof FMLProxyPacket) {
                message = Lang.get("editor.message.fml_edit_hint");
            }
            float textX = panelX + (panelWidth - textFont.getStringWidth(message)) / 2f;
            textFont.drawString(message, textX, topTextY + viewableAreaHeight / 2f - 4, Theme.TEXT_GRAY.getRGB());
        } else {
            for (EditableField ef : editableFields) {
                int fieldH = ef.getHeight() + 7;
                int relativeY = currentY + editFieldsScrollOffset;

                if (relativeY + fieldH < topTextY || relativeY > topTextY + viewableAreaHeight) {
                    ef.setYPos(currentY);
                    currentY += fieldH;
                    continue;
                }

                ef.setYPos(currentY);
                String labelColor = (ef.isEditableByUser) ? "§f" : "§8";
                String label = labelColor + ef.getDisplayLabel();
                if (ef.field.getType() == byte[].class) {
                    ef.drawViewToggleButton(mouseX, adjustedMouseY);
                    int buttonWidth = 75 + 5;
                    textFont.drawString(label, panelX + 10 + buttonWidth, ef.getLabelY(), -1);
                } else {
                    textFont.drawString(label, panelX + 10, ef.getLabelY(), -1);
                }
                ef.drawValueControl(mouseX, adjustedMouseY);
                currentY += fieldH;
            }
        }
        GL11.glPopMatrix();
        RenderUtils.stopScissor();

        drawScrollBar(topTextY, editFieldsScrollOffset, calculateEditFieldsHeight());
    }

    private void drawScrollBar(int topY, int offset, int contentH) {
        if (contentH > viewableAreaHeight) {
            int scrollbarX = panelX + panelWidth - 8;
            RenderUtils.drawRect(scrollbarX, topY, scrollbarX + 4, topY + viewableAreaHeight, 0x55000000);
            float scrollPercent = (float) -offset / (contentH - viewableAreaHeight);
            int handleHeight = (int) ((float) viewableAreaHeight / contentH * viewableAreaHeight);
            handleHeight = Math.max(handleHeight, 20);
            int handleY = topY + (int) (scrollPercent * (viewableAreaHeight - handleHeight));
            int color = isDraggingScrollbar ? Theme.ORANGE.brighter().getRGB() : Theme.ORANGE.getRGB();
            RenderUtils.drawRect(scrollbarX, handleY, scrollbarX + 4, handleY + handleHeight, color);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // Внутренний класс EditableField... (тот же, что и раньше, без изменений логики, просто должен быть внутри)
    private class EditableField {
        final Field field;
        String currentValue;
        Object textField;
        boolean isLongText = false;
        boolean isEnum = false;
        boolean isEditableByUser = true;
        Object[] enumConstants;
        int currentEnumIndex;
        private int x, y, width, height;
        ByteArrayDisplayMode byteArrayDisplayMode = ByteArrayDisplayMode.HEX;
        String decodedBytesAsText = "";

        EditableField(Field field) {
            this.field = field;
            try {
                Object value = field.get(packetInfo.rawPacket);
                if ("session".equals(field.getName()) && SessionManager.hasActiveSession()) {
                    this.currentValue = SessionManager.getLastKnownSessionUUID().toString();
                } else if (field.getType().isArray()) {
                    this.isLongText = true;
                    if (value == null) {
                        this.currentValue = "null";
                    } else if (value instanceof byte[]) {
                        byte[] bytes = (byte[]) value;
                        StringBuilder hex = new StringBuilder();
                        for (byte b : bytes) hex.append(String.format("%02X ", b));
                        this.currentValue = hex.toString().trim();
                        this.decodedBytesAsText = decodeBytesAsText(bytes);
                    } else if (value instanceof int[]) {
                        this.currentValue = Arrays.stream((int[]) value).mapToObj(String::valueOf).collect(Collectors.joining(" "));
                    } else if (value instanceof long[]) {
                        this.currentValue = LongStream.of((long[]) value).mapToObj(String::valueOf).collect(Collectors.joining(" "));
                    } else if (value instanceof float[]) {
                        this.currentValue = floatsToString((float[]) value);
                    } else if (value instanceof double[]) {
                        this.currentValue = DoubleStream.of((double[]) value).mapToObj(String::valueOf).collect(Collectors.joining(" "));
                    } else if (value instanceof String[]) {
                        this.currentValue = String.join("\n", (String[]) value);
                    } else {
                        this.isEditableByUser = false;
                        String typeName = field.getType().getComponentType().getSimpleName();
                        int length = Array.getLength(value);
                        this.currentValue = String.format("%s[%d] %s", typeName, length, Lang.get("editor.field.not_editable"));
                    }
                } else {
                    this.currentValue = (value == null) ? "null" : value.toString();
                    if (field.getType().isEnum()) {
                        this.isEnum = true;
                        this.enumConstants = field.getType().getEnumConstants();
                        this.currentEnumIndex = -1;
                        if (value != null) {
                            for (int i = 0; i < this.enumConstants.length; i++) {
                                if (this.enumConstants[i].toString().equals(this.currentValue)) {
                                    this.currentEnumIndex = i;
                                    break;
                                }
                            }
                        }
                    } else {
                        if (value instanceof ItemStack || value instanceof NBTBase || (value instanceof String && value.toString().length() > 30)) {
                            this.isLongText = true;
                        }
                        if (value instanceof ItemStack) {
                            StringBuilder sb = new StringBuilder();
                            PacketSerializer.formatItemStack((ItemStack) value, sb);
                            this.currentValue = sb.toString();
                        }
                    }
                }
                if (!SUPPORTED_TYPES.contains(field.getType()) && !field.getType().isEnum() && !field.getType().isArray()) {
                    this.isEditableByUser = false;
                }
            } catch (Exception e) {
                this.currentValue = "Error: " + e.getClass().getSimpleName();
                this.isEditableByUser = false;
            }
        }

        private String floatsToString(float[] array) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length; i++) {
                sb.append(array[i]);
                if (i < array.length - 1) sb.append(" ");
            }
            return sb.toString();
        }

        public String getDisplayLabel() {
            String srgName = field.getName();
            String mcpName = ObfuscationMapper.getMcpName(srgName);
            String fieldDisplayName = mcpName.equals(srgName) ? srgName : String.format("%s §8(%s)", mcpName, srgName);
            String typeName = field.getType().getSimpleName();
            return String.format("%s §7[%s]:", fieldDisplayName, typeName);
        }

        public boolean isEditable() {
            if (field.getType() == byte[].class) {
                return isEditableByUser && byteArrayDisplayMode == ByteArrayDisplayMode.HEX;
            }
            return isEditableByUser;
        }

        public void setDimensions(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        public String getEditedText() {
            if (field.getType() == byte[].class) {
                if (byteArrayDisplayMode == ByteArrayDisplayMode.TEXT) {
                    return decodedBytesAsText;
                }
            }
            if (isEnum) return currentEnumIndex >= 0 ? enumConstants[currentEnumIndex].toString() : "null";
            if (textField instanceof GuiTextField) return ((GuiTextField) textField).getText();
            if (textField instanceof GuiTextArea) return ((GuiTextArea) textField).getText();
            return currentValue;
        }

        public boolean isFocused() {
            if (!isEditableByUser || isEnum || textField == null) return false;
            if (textField instanceof GuiTextField) return ((GuiTextField) textField).isFocused();
            if (textField instanceof GuiTextArea) return ((GuiTextArea) textField).isFocused();
            return false;
        }

        public void keyTyped(char c, int key) {
            if (!isEditableByUser || isEnum || textField == null) return;
            if (textField instanceof GuiTextField) ((GuiTextField) textField).textboxKeyTyped(c, key);
            if (textField instanceof GuiTextArea) ((GuiTextArea) textField).textboxKeyTyped(c, key);
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
        }

        public void mouseClicked(int mouseX, int mouseY, int button) {
            if (!isEditableByUser) return;
            if (isEnum) {
                if (button == 0 && isMouseOver(mouseX, mouseY)) {
                    currentEnumIndex = (currentEnumIndex + 1) % enumConstants.length;
                }
            } else if (textField != null) {
                if (textField instanceof GuiTextField) ((GuiTextField) textField).mouseClicked(mouseX, mouseY, button);
                if (textField instanceof GuiTextArea) ((GuiTextArea) textField).mouseClicked(mouseX, mouseY, button);
            }
        }

        public void drawValueControl(int mouseX, int mouseY) {
            if (isEnum) drawEnumBox(mouseX, mouseY);
            else {
                drawBorders();
                drawTextBox();
            }
        }

        private void drawEnumBox(int mouseX, int mouseY) {
            boolean hovered = isEditableByUser && isMouseOver(mouseX, mouseY);
            RenderUtils.drawRect(getXPos(), getYPos(), getXPos() + getWidth(), getYPos() + getHeight(), hovered ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());
            String text = "< " + getEditedText() + " >";
            int textColor = fieldErrors.containsKey(this) ? Color.RED.getRGB() : (isEditableByUser ? Theme.TEXT_WHITE.getRGB() : Theme.TEXT_GRAY.getRGB());
            CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
            font.drawString(text, getXPos() + (getWidth() - font.getStringWidth(text)) / 2f, getYPos() + (getHeight() - font.getHeight()) / 2f, textColor);
        }

        public void drawTextBox() {
            if (textField instanceof GuiTextField) ((GuiTextField) textField).drawTextBox();
            if (textField instanceof GuiTextArea) ((GuiTextArea) textField).drawTextBox();
        }

        public void setYPos(int y) {
            this.y = y;
            if (textField instanceof GuiTextField) ((GuiTextField) textField).yPos = y;
            if (textField instanceof GuiTextArea) ((GuiTextArea) textField).yPos = y;
        }

        public int getHeight() {
            if (isEnum) return this.height;
            if (textField instanceof GuiTextField) return ((GuiTextField) textField).height;
            if (textField instanceof GuiTextArea) return ((GuiTextArea) textField).getHeight();
            return 18;
        }

        public int getXPos() { if (isEnum) return this.x; if (textField instanceof GuiTextField) return ((GuiTextField) textField).xPos; if (textField instanceof GuiTextArea) return ((GuiTextArea) textField).xPos; return 0; }
        public int getYPos() { if (isEnum) return this.y; if (textField instanceof GuiTextField) return ((GuiTextField) textField).yPos; if (textField instanceof GuiTextArea) return ((GuiTextArea) textField).yPos; return 0; }
        public int getWidth() { if (isEnum) return this.width; if (textField instanceof GuiTextField) return ((GuiTextField) textField).width; if (textField instanceof GuiTextArea) return ((GuiTextArea) textField).width; return 0; }

        public int getLabelY() {
            CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
            return getYPos() + (getHeight() - font.getHeight()) / 2;
        }

        public void toggleByteArrayView() {
            if (this.field.getType() != byte[].class) return;
            this.byteArrayDisplayMode = (this.byteArrayDisplayMode == ByteArrayDisplayMode.HEX) ? ByteArrayDisplayMode.TEXT : ByteArrayDisplayMode.HEX;
            if (this.textField instanceof GuiTextArea) {
                ((GuiTextArea) this.textField).setText(getEditedText());
            }
        }

        public void drawViewToggleButton(int mouseX, int mouseY) {
            CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
            int buttonWidth = 75;
            int buttonHeight = 16;
            int buttonX = panelX + 10;
            int buttonY = this.y + (this.getHeight() - buttonHeight) / 2;
            boolean isHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
            RenderUtils.drawRect(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, isHovered ? Theme.COMPONENT_BG_HOVER.getRGB() : Theme.COMPONENT_BG.getRGB());
            String text = (byteArrayDisplayMode == ByteArrayDisplayMode.HEX) ? Lang.get("editor.button.view_as_text") : Lang.get("editor.button.view_as_hex");
            float textX = buttonX + (buttonWidth - font.getStringWidth(text)) / 2f;
            float textY = buttonY + (buttonHeight - font.getHeight()) / 2f + 1;
            font.drawString(text, textX, textY, -1);
        }

        public boolean isViewToggleButtonClicked(int mouseX, int mouseY) {
            int buttonWidth = 75;
            int buttonHeight = 16;
            int buttonX = panelX + 10;
            int buttonY = this.y + (this.getHeight() - buttonHeight) / 2;
            return mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        }

        private String decodeBytesAsText(byte[] bytes) {
            try {
                String utf8 = new String(bytes, StandardCharsets.UTF_8);
                return utf8.replaceAll("[\\p{C}]", ".");
            } catch (Exception e) {
                return Lang.get("editor.error.decode_bytes");
            }
        }

        public void drawBorders() {
            int tfX = getXPos(), tfY = getYPos(), tfW = getWidth(), tfH = getHeight();
            RenderUtils.drawRect(tfX - 1, tfY - 1, tfX + tfW + 1, tfY + tfH + 1, Theme.COMPONENT_BG.getRGB());
            int borderColor = Theme.COMPONENT_BG.darker().getRGB();
            if (fieldErrors.containsKey(this)) borderColor = Color.RED.getRGB();
            else if (isFocused()) borderColor = Theme.ORANGE.getRGB();
            RenderUtils.drawRect(tfX - 2, tfY - 2, tfX + tfW + 2, tfY - 1, borderColor);
            RenderUtils.drawRect(tfX - 2, tfY - 2, tfX - 1, tfY + tfH + 2, borderColor);
            RenderUtils.drawRect(tfX + tfW + 1, tfY - 2, tfX + tfW + 2, tfY + tfH + 2, borderColor);
            RenderUtils.drawRect(tfX - 2, tfY + tfH + 1, tfX + tfW + 2, tfY + tfH + 2, borderColor);
        }
    }

    private void validateField(EditableField ef) {
        if (!ef.isEditable()) {
            fieldErrors.remove(ef);
            return;
        }
        try {
            parseValue(ef.getEditedText(), ef.field.getType());
            fieldErrors.remove(ef);
        } catch (Exception e) {
            fieldErrors.put(ef, e.getMessage());
        }
        updateSendButtonState();
    }

    private void validateAllFields() {
        fieldErrors.clear();
        for (EditableField ef : editableFields) {
            if (ef.isEditable()) {
                validateField(ef);
            }
        }
        updateSendButtonState();
    }

    private void updateSendButtonState() {
        if (sendButton != null) {
            sendButton.enabled = fieldErrors.isEmpty();
        }
    }

    private Object parseValue(String value, Class<?> type) throws Exception {
        String trimmedValue = value.trim();
        if (type == byte[].class) return parseByteArrayFromHex(trimmedValue);
        if (type == int[].class) return parseIntArray(trimmedValue);
        if (type == long[].class) return parseLongArray(trimmedValue);
        if (type == float[].class) return parseFloatArray(trimmedValue);
        if (type == double[].class) return parseDoubleArray(trimmedValue);
        if (type == String[].class) return parseStringArray(trimmedValue);

        if (trimmedValue.equalsIgnoreCase("null")) {
            if (type.isPrimitive()) throw new IllegalArgumentException("Primitive type '" + type.getSimpleName() + "' cannot be null.");
            return null;
        }
        if (type == UUID.class) {
            try { return UUID.fromString(trimmedValue); }
            catch (Exception e) { throw new IllegalArgumentException("Not a valid UUID string."); }
        }
        if (type == ItemStack.class) return parseItemStack(trimmedValue);
        if (type.isEnum()) {
            try {
                if (Enum.class.isAssignableFrom(type)) {
                    return Enum.valueOf((Class<Enum>) type, trimmedValue.toUpperCase());
                } else {
                    throw new IllegalArgumentException(type.getSimpleName() + " is not an Enum type.");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'" + trimmedValue + "' is not a valid constant for enum " + type.getSimpleName());
            }
        }
        try {
            if (type == String.class) return value;
            if (type == int.class || type == Integer.class) return Integer.parseInt(trimmedValue);
            if (type == byte.class || type == Byte.class) return Byte.parseByte(trimmedValue);
            if (type == short.class || type == Short.class) return Short.parseShort(trimmedValue);
            if (type == long.class || type == Long.class) return Long.parseLong(trimmedValue);
            if (type == float.class || type == Float.class) return Float.parseFloat(trimmedValue);
            if (type == double.class || type == Double.class) return Double.parseDouble(trimmedValue);
            if (type == boolean.class || type == Boolean.class) {
                String lowercased = trimmedValue.toLowerCase();
                if (lowercased.equals("true") || lowercased.equals("false")) return Boolean.parseBoolean(lowercased);
                throw new IllegalArgumentException("must be 'true' or 'false'");
            }
        } catch (NumberFormatException e) {
            throw new NumberFormatException("not a valid " + type.getSimpleName());
        }
        throw new IllegalArgumentException("Unsupported type: " + type.getSimpleName());
    }
    private long[] parseLongArray(String text) throws Exception { if (text.isEmpty() || text.equalsIgnoreCase("null")) return null; String[] parts = text.trim().split("\\s+"); long[] data = new long[parts.length]; for (int i = 0; i < parts.length; i++) data[i] = Long.parseLong(parts[i]); return data; }
    private float[] parseFloatArray(String text) throws Exception { if (text.isEmpty() || text.equalsIgnoreCase("null")) return null; String[] parts = text.trim().split("\\s+"); float[] data = new float[parts.length]; for (int i = 0; i < parts.length; i++) data[i] = Float.parseFloat(parts[i]); return data; }
    private double[] parseDoubleArray(String text) throws Exception { if (text.isEmpty() || text.equalsIgnoreCase("null")) return null; String[] parts = text.trim().split("\\s+"); double[] data = new double[parts.length]; for (int i = 0; i < parts.length; i++) data[i] = Double.parseDouble(parts[i]); return data; }
    private byte[] parseByteArrayFromHex(String hex) throws Exception { return HexUtils.hexToBytes(hex); }
    private int[] parseIntArray(String text) throws Exception { if (text.isEmpty() || text.equalsIgnoreCase("null")) return null; String[] parts = text.trim().split("\\s+"); int[] data = new int[parts.length]; for (int i = 0; i < parts.length; i++) data[i] = Integer.parseInt(parts[i]); return data; }
    private String[] parseStringArray(String text) throws Exception { if (text.equalsIgnoreCase("null")) return null; return text.split("\n"); }
    private ItemStack parseItemStack(String text) throws Exception {
        if (text.isEmpty() || text.equalsIgnoreCase("null") || text.equalsIgnoreCase("empty")) return null;
        String cleanText = StringUtils.stripControlCodes(text);
        int nbtStartIndex = cleanText.indexOf('{');
        String itemPart = (nbtStartIndex == -1) ? cleanText : cleanText.substring(0, nbtStartIndex).trim();
        String nbtPart = (nbtStartIndex == -1) ? null : cleanText.substring(nbtStartIndex);
        String[] mainParts = itemPart.split("x");
        if (mainParts.length < 2) throw new IllegalArgumentException("Invalid format. Expected 'Amount x ItemID[/Damage]'");
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
        if (idStr.equalsIgnoreCase("<NULL_ITEM>")) return null;
        int itemId;
        try {
            itemId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ItemID must be a number, but found: " + idStr);
        }
        net.minecraft.item.Item item = net.minecraft.item.Item.getItemById(itemId);
        if (item == null) throw new IllegalArgumentException("Unknown item ID: " + itemId);
        ItemStack itemStack = new ItemStack(item, stackSize, damage);
        if (nbtPart != null) {
            try {
                net.minecraft.nbt.NBTBase nbt = net.minecraft.nbt.JsonToNBT.func_150315_a(nbtPart);
                if (nbt instanceof net.minecraft.nbt.NBTTagCompound) {
                    itemStack.setTagCompound((net.minecraft.nbt.NBTTagCompound) nbt);
                } else {
                    throw new IllegalArgumentException("NBT data must be a compound tag.");
                }
            } catch (net.minecraft.nbt.NBTException e) {
                throw new Exception("Invalid NBT data: " + e.getMessage());
            }
        }
        return itemStack;
    }
}