package ru.fuctorial.fuctorize.client.gui.nbtedit;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.utils.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.*;

public class GuiEditSingleNBT extends Gui {

    // ИЗМЕНЕНИЕ: public, чтобы другие классы видели ширину окна
    public static final int WIDTH = 250;

    private int currentHeight = 120;

    private final Node<NamedNBT> node;
    private final NBTBase nbt;
    private final boolean canEditText;
    private final boolean canEditValue;
    private final GuiNBTTree parent;
    private int x;
    private int y;

    private GuiTextField keyField;
    private GuiTextField valueField;
    private GuiTextArea valueTextArea;
    private StyledButton saveButton;
    private StyledButton cancelButton;

    private String kError;
    private String vError;
    private boolean isHexMode = false;

    public GuiEditSingleNBT(GuiNBTTree parent, Node<NamedNBT> node, boolean editText, boolean editValue) {
        this.parent = parent;
        this.node = node;
        this.nbt = node.getObject().getNBT();
        this.canEditText = editText;
        this.canEditValue = editValue;

        if (this.nbt instanceof NBTTagByteArray || this.nbt instanceof NBTTagIntArray) {
            this.isHexMode = true;
            this.currentHeight = 230;
        }
    }

    // Метод принимает размеры ЭКРАНА и сам центрирует окно
    public void initGUI(int screenWidth, int screenHeight) {
        this.x = (screenWidth - WIDTH) / 2;
        this.y = (screenHeight - this.currentHeight) / 2;

        String initialKey = (this.keyField == null) ? this.node.getObject().getName() : this.keyField.getText();

        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        int labelWidth = Math.max(font.getStringWidth(Lang.get("nbtedit.label.name")), font.getStringWidth(Lang.get("nbtedit.label.value")));
        int fieldX = x + labelWidth + 15;
        int fieldWidth = WIDTH - (labelWidth + 20);

        this.keyField = new GuiTextField(fieldX, y + 25, fieldWidth, 20, false);
        this.keyField.setText(initialKey);
        this.keyField.func_82265_c(this.canEditText);
        this.keyField.setTextColor(Theme.TEXT_WHITE.getRGB());

        if (isHexMode) {
            String initialValueHex = (this.valueTextArea == null) ? getRawValueHex(this.nbt) : this.valueTextArea.getText();
            int areaHeight = this.currentHeight - 95;
            this.valueTextArea = new GuiTextArea(x + 10, y + 55, WIDTH - 20, areaHeight);
            this.valueTextArea.setText(initialValueHex);
        } else {
            String initialValue = (this.valueField == null) ? getRawValue(this.nbt) : this.valueField.getText();
            this.valueField = new GuiTextField(fieldX, y + 55, fieldWidth, 20, true);
            this.valueField.setMaxStringLength(256);
            this.valueField.setText(initialValue);
            this.valueField.func_82265_c(this.canEditValue);
            this.valueField.setTextColor(this.canEditValue ? Theme.TEXT_WHITE.getRGB() : Theme.TEXT_GRAY.getRGB());
        }

        int buttonWidth = (WIDTH - 25) / 2;
        this.saveButton = new StyledButton(1, x + 10, y + this.currentHeight - 30, buttonWidth, 20, Lang.get("generic.button.save"));
        this.cancelButton = new StyledButton(0, x + 15 + buttonWidth, y + this.currentHeight - 30, buttonWidth, 20, Lang.get("generic.button.cancel"));

        if (this.canEditText) {
            this.keyField.setFocused(true);
        } else if (this.canEditValue) {
            if (valueField != null) valueField.setFocused(true);
            if (valueTextArea != null) valueTextArea.setFocused(true);
        }

        checkValidInput();
    }

    public void click(int mx, int my) {
        this.keyField.mouseClicked(mx, my, 0);
        if (this.valueField != null) this.valueField.mouseClicked(mx, my, 0);
        if (this.valueTextArea != null) this.valueTextArea.mouseClicked(mx, my, 0);

        if (this.saveButton.mousePressed(Wrapper.INSTANCE.getMc(), mx, my)) {
            this.saveAndQuit();
        }
        if (this.cancelButton.mousePressed(Wrapper.INSTANCE.getMc(), mx, my)) {
            this.parent.closeWindow();
        }
    }

    private void saveAndQuit() {
        if (!saveButton.enabled) return;

        if (this.canEditText) {
            this.node.getObject().setName(this.keyField.getText());
        }

        String valueToSave = isHexMode ? this.valueTextArea.getText() : this.valueField.getText();
        try {
            setValidValue(this.node, valueToSave);
            this.parent.nodeEdited(this.node);
            this.parent.closeWindow();
        } catch (Exception e) {
            // Ignore save if invalid
        }
    }

    public void draw(int mx, int my) {
        if (FuctorizeClient.INSTANCE == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;

        RenderUtils.drawRect(0, 0, this.parent.getTreeWidth(), this.parent.getTreeHeight(), 0x99000000);

        Gui.drawRect(x, y, x + WIDTH, y + currentHeight, Theme.CATEGORY_BG.getRGB());
        Gui.drawRect(x - 1, y - 1, x + WIDTH + 1, y, Theme.BORDER.getRGB());
        Gui.drawRect(x - 1, y - 1, x, y + currentHeight + 1, Theme.BORDER.getRGB());
        Gui.drawRect(x + WIDTH, y - 1, x + WIDTH + 1, y + currentHeight + 1, Theme.BORDER.getRGB());
        Gui.drawRect(x - 1, y + currentHeight, x + WIDTH + 1, y + currentHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        float nameY = keyField.yPos + (keyField.height - font.getHeight()) / 2f;
        font.drawString(Lang.get("nbtedit.label.name"), x + 10, nameY, Theme.TEXT_WHITE.getRGB());

        float valueY;
        if (isHexMode) {
            valueY = valueTextArea.yPos - font.getHeight() - 3;
            font.drawString("Hex Value:", x + 10, valueY, Theme.TEXT_WHITE.getRGB());
        } else {
            valueY = valueField.yPos + (valueField.height - font.getHeight()) / 2f;
            font.drawString(Lang.get("nbtedit.label.value"), x + 10, valueY, Theme.TEXT_WHITE.getRGB());
        }

        this.keyField.drawTextBox();
        if (this.valueField != null) this.valueField.drawTextBox();
        if (this.valueTextArea != null) this.valueTextArea.drawTextBox();

        this.saveButton.drawButton(Wrapper.INSTANCE.getMc(), mx, my);
        this.cancelButton.drawButton(Wrapper.INSTANCE.getMc(), mx, my);

        String error = this.kError != null ? this.kError : this.vError;
        if (error != null) {
            float errorX = x + (WIDTH - font.getStringWidth(error)) / 2f;
            font.drawString(error, errorX, y + 8, 0xFFFF5555);
        }
    }

    public void update() {
        if (this.valueField != null) this.valueField.updateCursorCounter();
        if (this.valueTextArea != null) this.valueTextArea.updateCursorCounter();
        this.keyField.updateCursorCounter();
    }

    public void keyTyped(char c, int i) {
        switch (i) {
            case 1:
                this.parent.closeWindow();
                break;
            case 15:
                if (this.keyField.isFocused() && this.canEditValue) {
                    this.keyField.setFocused(false);
                    if (valueField != null) valueField.setFocused(true);
                    if (valueTextArea != null) valueTextArea.setFocused(true);
                } else if ((valueField != null && valueField.isFocused()) || (valueTextArea != null && valueTextArea.isFocused()) && this.canEditText) {
                    this.keyField.setFocused(true);
                    if (valueField != null) valueField.setFocused(false);
                    if (valueTextArea != null) valueTextArea.setFocused(false);
                }
                break;
            case 28:
            case 156:
                if (this.valueTextArea != null && this.valueTextArea.isFocused()) {
                    if (this.saveButton.enabled) this.saveAndQuit();
                } else if (this.saveButton.enabled) {
                    this.saveAndQuit();
                }
                break;
            default:
                this.keyField.textboxKeyTyped(c, i);
                if (this.valueField != null) this.valueField.textboxKeyTyped(c, i);
                if (this.valueTextArea != null) this.valueTextArea.textboxKeyTyped(c, i);
                this.checkValidInput();
                break;
        }
    }

    private void checkValidInput() {
        boolean valid = true;
        this.kError = null;
        this.vError = null;

        if (this.canEditText && !validName()) {
            valid = false;
            this.kError = Lang.get("nbtedit.error.duplicate_tag_name");
        }

        String textToCheck = isHexMode ? valueTextArea.getText() : valueField.getText();
        try {
            validValue(textToCheck, this.nbt.getId());
        } catch (IllegalArgumentException e) {
            this.vError = e.getMessage();
            valid = false;
        }

        this.saveButton.enabled = valid;
    }

    private boolean validName() {
        for (Node<NamedNBT> sibling : this.node.getParent().getChildren()) {
            if (sibling.getObject().getNBT() != this.nbt && sibling.getObject().getName().equals(this.keyField.getText())) {
                return false;
            }
        }
        return true;
    }

    private static void setValidValue(Node<NamedNBT> node, String value) {
        NamedNBT named = node.getObject();
        NBTBase base = named.getNBT();
        if (base instanceof NBTTagByte) named.setNBT(new NBTTagByte(ParseHelper.parseByte(value)));
        else if (base instanceof NBTTagShort) named.setNBT(new NBTTagShort(ParseHelper.parseShort(value)));
        else if (base instanceof NBTTagInt) named.setNBT(new NBTTagInt(ParseHelper.parseInt(value)));
        else if (base instanceof NBTTagLong) named.setNBT(new NBTTagLong(ParseHelper.parseLong(value)));
        else if (base instanceof NBTTagFloat) named.setNBT(new NBTTagFloat(ParseHelper.parseFloat(value)));
        else if (base instanceof NBTTagDouble) named.setNBT(new NBTTagDouble(ParseHelper.parseDouble(value)));
        else if (base instanceof NBTTagByteArray) named.setNBT(new NBTTagByteArray(ParseHelper.parseByteArray(value)));
        else if (base instanceof NBTTagIntArray) named.setNBT(new NBTTagIntArray(ParseHelper.parseIntArray(value)));
        else if (base instanceof NBTTagString) named.setNBT(new NBTTagString(value));
    }

    private static void validValue(String value, byte type) throws IllegalArgumentException {
        switch (type) {
            case 1: ParseHelper.parseByte(value); break;
            case 2: ParseHelper.parseShort(value); break;
            case 3: ParseHelper.parseInt(value); break;
            case 4: ParseHelper.parseLong(value); break;
            case 5: ParseHelper.parseFloat(value); break;
            case 6: ParseHelper.parseDouble(value); break;
            case 7: ParseHelper.parseByteArray(value); break;
            case 11: ParseHelper.parseIntArray(value); break;
        }
    }

    private static String getRawValueHex(NBTBase base) {
        if (base instanceof NBTTagByteArray) {
            return HexUtils.bytesToHex(((NBTTagByteArray) base).func_150292_c());
        }
        return getRawValue(base);
    }

    public void handleMouseInput() {
        if (this.valueTextArea != null) {
            this.valueTextArea.handleMouseInput();
        }
    }

    // ВАЖНО: В GuiScreen методы называются mouseClickMove и mouseMovedOrUp
    // Но так как GuiEditSingleNBT это просто Gui (а не GuiScreen), мы создаем свои методы
    // и вызываем их из GuiNBTTree (родительского экрана)

    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.valueTextArea != null) {
            this.valueTextArea.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.valueTextArea != null) {
            this.valueTextArea.mouseReleased(mouseX, mouseY, state);
        }
    }


    private static String getRawValue(NBTBase base) {
        if (base instanceof NBTTagString) return ((NBTTagString) base).func_150285_a_();
        if (base instanceof NBTTagByte) return "" + ((NBTTagByte) base).func_150290_f();
        if (base instanceof NBTTagShort) return "" + ((NBTTagShort) base).func_150289_e();
        if (base instanceof NBTTagInt) return "" + ((NBTTagInt) base).func_150287_d();
        if (base instanceof NBTTagLong) return "" + ((NBTTagLong) base).func_150291_c();
        if (base instanceof NBTTagFloat) return "" + ((NBTTagFloat) base).func_150288_h();
        if (base instanceof NBTTagDouble) return "" + ((NBTTagDouble) base).func_150286_g();

        if (base instanceof NBTTagIntArray) {
            StringBuilder sInts = new StringBuilder();
            for (int a : ((NBTTagIntArray) base).func_150302_c()) {
                sInts.append(a).append(" ");
            }
            return sInts.toString().trim();
        }
        return NBTStringHelper.toString(base);
    }
}