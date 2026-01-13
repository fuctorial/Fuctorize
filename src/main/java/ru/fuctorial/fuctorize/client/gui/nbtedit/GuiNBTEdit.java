// 19. C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\client\gui\nbtedit\GuiNBTEdit.java
package ru.fuctorial.fuctorize.client.gui.nbtedit;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C10PacketCreativeInventoryAction;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.utils.JsonNbtParser;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NBTTree;
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.Statics;

import java.util.function.Consumer;

public class GuiNBTEdit extends GuiScreen {

    private static final int BUTTON_ID_SAVE = 0;
    private static final int BUTTON_ID_CANCEL = 1;
    private static final int BUTTON_ID_FROM_JSON = 2;
    private static final int BUTTON_ID_TO_JSON = 3;
    private static final int BUTTON_ID_INFO = 4;
    private static final int BUTTON_ID_COPY = 5;
    private static final int BUTTON_ID_ATTEMPT_EDIT = 6;
    private static final int BUTTON_ID_SEND_TO_SERVER = 7;
    private static final int BUTTON_ID_CANCEL_EDIT = 8;

    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 35;

    private final GuiNBTTree guiTree;
    private GuiTextField nbtString;
    private final NBTTagCompound originalNbt;

    private boolean isViewOnly;
    private boolean isEditingInViewMode = false;
    private String viewOnlyTitle = Lang.get("nbtedit.title.editor");
    private Entity targetEntity = null;
    private TileEntity targetTileEntity = null;

    private final boolean isCallbackMode;
    private final Consumer<NBTTagCompound> saveCallback;
    private final Consumer<NBTTagCompound> fromJsonCallback;

    public GuiNBTEdit(NBTTagCompound tag) {
        this.originalNbt = (NBTTagCompound) tag.copy();
        this.guiTree = new GuiNBTTree(new NBTTree(tag));
        this.isViewOnly = false;
        this.isCallbackMode = false;
        this.saveCallback = null;
        this.fromJsonCallback = newNbt -> {
            Statics.STATIC_NBT = newNbt;
            mc.displayGuiScreen(new GuiNBTEdit(newNbt));
        };
    }

    public GuiNBTEdit(NBTTagCompound tag, Consumer<NBTTagCompound> onSave) {
        this.originalNbt = (NBTTagCompound) tag.copy();
        this.guiTree = new GuiNBTTree(new NBTTree(tag));
        this.isViewOnly = false;
        this.isCallbackMode = true;
        this.saveCallback = onSave;
        this.fromJsonCallback = newNbt -> mc.displayGuiScreen(new GuiNBTEdit(newNbt, onSave));
    }

    public GuiNBTEdit(TileEntity tileEntity) {
        this.targetTileEntity = tileEntity;
        NBTTagCompound nbt = new NBTTagCompound();
        tileEntity.writeToNBT(nbt);
        this.originalNbt = (NBTTagCompound) nbt.copy();
        this.guiTree = new GuiNBTTree(new NBTTree(nbt), true);
        this.isViewOnly = true;
        this.viewOnlyTitle = Lang.format("nbtedit.title.tile", tileEntity.getBlockType().getLocalizedName(), tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
        this.isCallbackMode = false; this.saveCallback = null; this.fromJsonCallback = null;
    }

    public GuiNBTEdit(Entity entity) {
        this.targetEntity = entity;
        NBTTagCompound nbt = new NBTTagCompound();
        entity.writeToNBT(nbt);
        this.originalNbt = (NBTTagCompound) nbt.copy();
        this.guiTree = new GuiNBTTree(new NBTTree(nbt), true);
        this.isViewOnly = true;
        this.viewOnlyTitle = Lang.format("nbtedit.title.entity", entity.getCommandSenderName(), entity.getEntityId());
        this.isCallbackMode = false; this.saveCallback = null; this.fromJsonCallback = null;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.guiTree.initGUI(this.width, this.height, this.height - FOOTER_HEIGHT);
        int buttonY = this.height - FOOTER_HEIGHT + (FOOTER_HEIGHT - 20) / 2;
        int padding = 10;

        if (isViewOnly) {
            if (isEditingInViewMode) {
                int buttonWidth = 70;
                this.buttonList.add(new StyledButton(BUTTON_ID_SEND_TO_SERVER, padding, buttonY, 100, 20, Lang.get("nbtedit.button.send_to_server")));
                this.buttonList.add(new StyledButton(BUTTON_ID_CANCEL_EDIT, padding + 105, buttonY, 80, 20, Lang.get("nbtedit.button.cancel_edit")));
                StyledButton fromJsonButton = new StyledButton(BUTTON_ID_FROM_JSON, padding + 190, buttonY, buttonWidth, 20, Lang.get("nbtedit.button.from_json"));
                StyledButton toJsonButton = new StyledButton(BUTTON_ID_TO_JSON, fromJsonButton.xPosition + buttonWidth + padding / 2, buttonY, buttonWidth, 20, Lang.get("nbtedit.button.to_json"));
                this.buttonList.add(fromJsonButton);
                this.buttonList.add(toJsonButton);
                int textFieldX = toJsonButton.xPosition + toJsonButton.width + padding;
                int textFieldWidth = this.width - textFieldX - padding - 5;
                this.nbtString = new GuiTextField(textFieldX, buttonY, textFieldWidth, 20, false);
                this.nbtString.setMaxStringLength(32767);
                this.nbtString.setText(JsonNbtParser.nbtToString(this.guiTree.getNBTTree().toNBTTagCompound()));
            } else {
                int buttonWidth = (this.width - (padding * 4)) / 3;
                this.buttonList.add(new StyledButton(BUTTON_ID_COPY, padding, buttonY, buttonWidth, 20, Lang.get("nbtedit.button.copy_full")));
                this.buttonList.add(new StyledButton(BUTTON_ID_ATTEMPT_EDIT, padding * 2 + buttonWidth, buttonY, buttonWidth, 20, Lang.get("nbtedit.button.attempt_edit_send")));
                this.buttonList.add(new StyledButton(BUTTON_ID_CANCEL, padding * 3 + buttonWidth * 2, buttonY, buttonWidth, 20, Lang.get("nbtedit.button.close")));
            }
        } else {
            int buttonWidth = 70;
            StyledButton saveButton = new StyledButton(BUTTON_ID_SAVE, padding, buttonY, buttonWidth, 20, Lang.get("nbtedit.button.save"));
            StyledButton cancelButton = new StyledButton(BUTTON_ID_CANCEL, saveButton.xPosition + buttonWidth + padding / 2, buttonY, buttonWidth, 20, Lang.get("nbtedit.button.cancel"));
            StyledButton fromJsonButton = new StyledButton(BUTTON_ID_FROM_JSON, cancelButton.xPosition + buttonWidth + padding, buttonY, buttonWidth, 20, Lang.get("nbtedit.button.from_json"));
            StyledButton toJsonButton = new StyledButton(BUTTON_ID_TO_JSON, fromJsonButton.xPosition + buttonWidth + padding / 2, buttonY, buttonWidth, 20, Lang.get("nbtedit.button.to_json"));
            this.buttonList.add(saveButton);
            this.buttonList.add(cancelButton);
            this.buttonList.add(fromJsonButton);
            this.buttonList.add(toJsonButton);
            int infoButtonWidth = 40;
            StyledButton infoButton = new StyledButton(BUTTON_ID_INFO, this.width - padding - infoButtonWidth, buttonY, infoButtonWidth, 20, Lang.get("nbtedit.button.info"));
            this.buttonList.add(infoButton);
            int textFieldX = toJsonButton.xPosition + toJsonButton.width + padding;
            int textFieldWidth = infoButton.xPosition - textFieldX - padding;
            this.nbtString = new GuiTextField(textFieldX, buttonY, textFieldWidth, 20, false);
            this.nbtString.setMaxStringLength(32767);
            this.nbtString.setText("");
            this.nbtString.setCursorPositionZero();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!this.guiTree.isTreeBuilt()) {
            this.guiTree.rebuildAndSetup();
            if (this.guiTree.isTreeBuilt() && !this.isViewOnly) {
                this.nbtString.setText(JsonNbtParser.nbtToString(this.guiTree.getNBTTree().toNBTTagCompound()));
                this.nbtString.setCursorPositionZero();
            }
        }
        this.drawDefaultBackground();

        // 1. Рисуем дерево (список тегов)
        this.guiTree.draw(mouseX, mouseY);

        // 2. Рисуем верхний заголовок (Хедер)
        Gui.drawRect(0, 0, this.width, HEADER_HEIGHT, Theme.CATEGORY_BG.getRGB());
        Gui.drawRect(0, HEADER_HEIGHT, this.width, HEADER_HEIGHT + 1, Theme.BORDER.getRGB());
        if (FuctorizeClient.INSTANCE != null && FuctorizeClient.INSTANCE.fontManager != null && FuctorizeClient.INSTANCE.fontManager.isReady()) {
            CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
            String title = this.isViewOnly ? this.viewOnlyTitle : Lang.get("nbtedit.title.editor_full");
            float titleX = (this.width - titleFont.getStringWidth(title)) / 2f;
            float titleY = (HEADER_HEIGHT - titleFont.getHeight()) / 2f;
            titleFont.drawString(title, titleX, titleY, Theme.TEXT_WHITE.getRGB());
        }

        // 3. Рисуем нижнюю панель (Футер)
        int footerY = this.height - FOOTER_HEIGHT;
        Gui.drawRect(0, footerY, this.width, this.height, Theme.CATEGORY_BG.getRGB());
        Gui.drawRect(0, footerY, this.width, footerY + 1, Theme.BORDER.getRGB());

        // 4. Рисуем кнопки (они рендерятся супер-классом) и текстовое поле (если нужно)
        // Важно: если открыто окно редактирования, мы не даем взаимодействовать с элементами родителя, но рисовать их можно.
        // Однако, чтобы не перекрывать окно редактирования, лучше скрыть текстовое поле.
        if (this.guiTree.getWindow() == null) {
            if (this.nbtString != null && (isEditingInViewMode || !isViewOnly)) {
                this.nbtString.drawTextBox();
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);

        // 5. ИСПРАВЛЕНИЕ: Рисуем оверлеи (окно редактирования/меню) ПОВЕРХ ВСЕГО ОСТАЛЬНОГО
        this.guiTree.drawOverlays(mouseX, mouseY);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    // ... Остальные методы (doesGuiPauseGame, updateScreen, keyTyped, mouseClickMove, mouseMovedOrUp, mouseClicked, handleMouseInput, quitWithoutSaving, applyNbtAndQuit, actionPerformed, sendNbtToServer) ...
    // Они не менялись, просто убедись, что они на месте.

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        if (mc.thePlayer == null || !mc.thePlayer.isEntityAlive()) {
            this.quitWithoutSaving();
        }
        this.guiTree.updateScreen();
        if (this.nbtString != null) {
            this.nbtString.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.guiTree.getWindow() != null) {
            this.guiTree.getWindow().keyTyped(typedChar, keyCode);
            return;
        }
        if (isViewOnly && !isEditingInViewMode) {
            if (keyCode == Keyboard.KEY_ESCAPE) this.quitWithoutSaving();
            if (keyCode == Keyboard.KEY_UP) this.guiTree.arrowKeyPressed(true);
            if (keyCode == Keyboard.KEY_DOWN) this.guiTree.arrowKeyPressed(false);
            return;
        }
        if (this.nbtString != null) {
            this.nbtString.textboxKeyTyped(typedChar, keyCode);
        }
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE: this.quitWithoutSaving(); break;
            case Keyboard.KEY_DELETE: this.guiTree.deleteSelected(); break;
            case Keyboard.KEY_RETURN: case Keyboard.KEY_NUMPADENTER: this.guiTree.editSelected(); break;
            case Keyboard.KEY_UP: this.guiTree.arrowKeyPressed(true); break;
            case Keyboard.KEY_DOWN: this.guiTree.arrowKeyPressed(false); break;
            case Keyboard.KEY_C: if (isCtrlKeyDown()) this.guiTree.copy(); break;
            case Keyboard.KEY_V: if (isCtrlKeyDown() && this.guiTree.canPaste()) this.guiTree.paste(); break;
            case Keyboard.KEY_X: if (isCtrlKeyDown()) this.guiTree.cut(); break;
            default: break;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        // Передаем событие перетаскивания в дерево (а оно передаст в окно, если оно открыто)
        this.guiTree.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        // Передаем событие отпускания кнопки
        this.guiTree.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.nbtString != null && this.guiTree.getWindow() == null && (isEditingInViewMode || !isViewOnly)) {
            this.nbtString.mouseClicked(mouseX, mouseY, mouseButton);
        }
        this.guiTree.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        // Если открыто модальное окно внутри дерева, скролл должен идти туда
        if (this.guiTree.getWindow() != null) {
            this.guiTree.handleMouseInput();
            return;
        }

        // Старая логика скролла самого дерева
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            this.guiTree.shift(scroll > 0 ? 16 : -16);
        }
    }

    private void quitWithoutSaving() {
        if (!isCallbackMode && !isViewOnly) {
            Statics.STATIC_ITEMSTACK = null;
            Statics.STATIC_NBT = null;
        }
        this.mc.displayGuiScreen(null);
    }

    private void applyNbtAndQuit(NBTTagCompound nbtToApply) {
        if (isCallbackMode) {
            if (saveCallback != null) {
                saveCallback.accept(nbtToApply);
            }
        } else {
            if (Statics.STATIC_ITEMSTACK != null) {
                if (nbtToApply == null || nbtToApply.hasNoTags()) {
                    Statics.STATIC_ITEMSTACK.setTagCompound(null);
                } else {
                    Statics.STATIC_ITEMSTACK.setTagCompound(nbtToApply);
                }
            }
            Statics.STATIC_ITEMSTACK = null;
            Statics.STATIC_NBT = null;
        }
        this.mc.displayGuiScreen(null);
        this.mc.setIngameFocus();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        switch (button.id) {
            case BUTTON_ID_SAVE:
                applyNbtAndQuit(this.guiTree.getNBTTree().toNBTTagCompound());
                break;
            case BUTTON_ID_CANCEL:
                quitWithoutSaving();
                break;
            case BUTTON_ID_FROM_JSON:
                try {
                    NBTTagCompound nbtFromJson = JsonNbtParser.stringToNbt(this.nbtString.getText());
                    if (nbtFromJson.hasKey("Instance") && !(nbtFromJson.getTag("Instance") instanceof net.minecraft.nbt.NBTTagByteArray)) {
                        mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + Lang.get("nbtedit.error.instance_not_bytearray")));
                        return;
                    }
                    if (this.fromJsonCallback != null) {
                        this.fromJsonCallback.accept(nbtFromJson);
                    } else if (isEditingInViewMode) {
                        this.guiTree.getNBTTree().fromNBTTagCompound(nbtFromJson);
                        this.guiTree.rebuildAndSetup();
                    }
                } catch (Exception e) {
                    this.mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + Lang.format("nbtedit.error.invalid_format", e.getMessage())));
                }
                break;
            case BUTTON_ID_TO_JSON:
                this.nbtString.setText(JsonNbtParser.nbtToString(this.guiTree.getNBTTree().toNBTTagCompound()));
                this.nbtString.setCursorPositionZero();
                break;
            case BUTTON_ID_INFO:
                this.mc.displayGuiScreen(new GuiNBTInfo(this));
                break;
            case BUTTON_ID_COPY:
                setClipboardString(this.guiTree.getNBTTree().toNBTTagCompound().toString());
                FuctorizeClient.INSTANCE.notificationManager.show(new Notification(Lang.get("notification.nbtviewer.title"), Lang.get("notification.nbtviewer.copied"), Notification.NotificationType.SUCCESS, 2000L));
                break;
            case BUTTON_ID_ATTEMPT_EDIT:
                this.isEditingInViewMode = true;
                this.guiTree.setViewOnly(false);
                this.initGui();
                break;
            case BUTTON_ID_SEND_TO_SERVER:
                sendNbtToServer();
                break;
            case BUTTON_ID_CANCEL_EDIT:
                this.isEditingInViewMode = false;
                this.guiTree.setViewOnly(true);
                this.guiTree.getNBTTree().fromNBTTagCompound(this.originalNbt);
                this.guiTree.rebuildAndSetup();
                this.initGui();
                break;
        }
    }

    private void sendNbtToServer() {
        NBTTagCompound modifiedNbt = this.guiTree.getNBTTree().toNBTTagCompound();

        if (this.targetTileEntity != null) {
            ItemStack dummyStack = new ItemStack(Items.writable_book);
            NBTTagCompound itemNbt = new NBTTagCompound();
            itemNbt.setTag("BlockEntityTag", modifiedNbt);
            dummyStack.setTagCompound(itemNbt);
            NetUtils.sendPacket(new C08PacketPlayerBlockPlacement(
                    this.targetTileEntity.xCoord, this.targetTileEntity.yCoord, this.targetTileEntity.zCoord,
                    255, dummyStack, 0.0f, 0.0f, 0.0f
            ));
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(Lang.get("notification.nbtsender.title"), Lang.get("notification.nbtsender.tile_try"), Notification.NotificationType.INFO, 2500L));
        } else if (this.targetEntity != null) {
            ItemStack dummyStack = new ItemStack(Items.spawn_egg);
            NBTTagCompound itemNbt = new NBTTagCompound();
            itemNbt.setTag("EntityTag", modifiedNbt);
            dummyStack.setTagCompound(itemNbt);
            NetUtils.sendPacket(new C10PacketCreativeInventoryAction(36, dummyStack));
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(Lang.get("notification.nbtsender.title"), Lang.get("notification.nbtsender.entity_try"), Notification.NotificationType.WARNING, 3000L));
        } else {
            FuctorizeClient.INSTANCE.notificationManager.show(new Notification(Lang.get("notification.nbtsender.title"), Lang.get("notification.nbtsender.no_target"), Notification.NotificationType.ERROR, 2000L));
        }
        this.mc.displayGuiScreen(null);
    }
}