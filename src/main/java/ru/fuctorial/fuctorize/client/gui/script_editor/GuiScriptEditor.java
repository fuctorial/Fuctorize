 
package ru.fuctorial.fuctorize.client.gui.script_editor;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextArea;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.utils.NetUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GuiScriptEditor extends GuiScreen {

    private static final String[] HOOK_NAMES = {"init", "update", "interact", "dialog", "damaged", "killed", "attack", "target", "collide", "kills", "dialog_closed"};

    private final int targetEntityId;
    private final String targetNpcName;
    private GuiTextArea scriptTextArea;
    private Map<Integer, String> scriptData = new HashMap<>();
    private int activeHook = 0;

    public GuiScriptEditor(int entityId, String npcName) {
        this.targetEntityId = entityId;
        this.targetNpcName = npcName;
    }

    @Override
    public void initGui() {
        super.initGui();
        int panelWidth = Math.max(750, this.width - 40);
        int panelHeight = Math.max(400, this.height - 80);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int hookButtonWidth = 80;

        int currentX = panelX + 5;
        int currentY = panelY + 25;
        for (int i = 0; i < HOOK_NAMES.length; i++) {
            if (currentX + hookButtonWidth > panelX + panelWidth - 5) {
                currentX = panelX + 5;
                currentY += 22;
            }
            StyledButton button = new StyledButton(i, currentX, currentY, hookButtonWidth, 20, HOOK_NAMES[i]);
            if (i == activeHook) button.enabled = false;
            this.buttonList.add(button);
            currentX += hookButtonWidth + 2;
        }

        int textAreaY = currentY + 25;
        scriptTextArea = new GuiTextArea(panelX + 5, textAreaY, panelWidth - 10, panelHeight - (textAreaY - panelY) - 35);
        scriptTextArea.setText("Loading scripts...");
        scriptTextArea.setFocused(false);

        this.buttonList.add(new StyledButton(100, panelX + panelWidth - 165, panelY + panelHeight - 30, 160, 20, "Save and Inject"));
        this.buttonList.add(new StyledButton(101, panelX + 5, panelY + panelHeight - 30, 80, 20, "Close"));
    }

    public void setScriptData(NBTTagCompound compound) {
        if (compound == null) return;
        NBTTagList containers = compound.getTagList("ScriptsContainers", 10);
        this.scriptData.clear();
        for (int i = 0; i < containers.tagCount(); i++) {
            NBTTagCompound container = containers.getCompoundTagAt(i);
            int type = container.getInteger("Type");
            String script = container.getString("Script");
            scriptData.put(type, script);
        }
        this.scriptTextArea.setFocused(true);
        updateTextAreaContent();
    }

    private void updateTextAreaContent() {
        scriptTextArea.setText(scriptData.getOrDefault(activeHook, "// Script for this hook is missing."));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id < HOOK_NAMES.length) {
            scriptData.put(activeHook, scriptTextArea.getText());
            activeHook = button.id;
            for(Object obj : this.buttonList) {
                GuiButton b = (GuiButton) obj;
                if(b.id < HOOK_NAMES.length) b.enabled = true;
            }
            button.enabled = false;
            updateTextAreaContent();
        } else if (button.id == 100) {
            saveAndInject();
        } else if (button.id == 101) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Module scriptManager = FuctorizeClient.INSTANCE.moduleManager.getModuleByKey("scriptmanager");
        if (scriptManager != null && scriptManager.isEnabled()) {
            scriptManager.toggle();
        }
    }

    private void saveAndInject() {
        scriptData.put(activeHook, scriptTextArea.getText());
        NBTTagCompound dataScriptNBT = new NBTTagCompound();
        NBTTagList containers = new NBTTagList();

        for (Map.Entry<Integer, String> entry : scriptData.entrySet()) {
            NBTTagCompound containerNBT = new NBTTagCompound();
            containerNBT.setInteger("Type", entry.getKey());
            containerNBT.setString("Script", entry.getValue());
            containerNBT.setTag("Scripts", new NBTTagList());
            containerNBT.setString("Console", "");
            containers.appendTag(containerNBT);
        }

        dataScriptNBT.setTag("ScriptsContainers", containers);
        dataScriptNBT.setString("ScriptLanguage", "ECMAScript");
        dataScriptNBT.setBoolean("ScriptEnabled", true);

        sendPacket(41, dataScriptNBT);  

        FuctorizeClient.INSTANCE.notificationManager.show(new Notification(
                "ScriptManager", "Script has been sent to the server.", Notification.NotificationType.SUCCESS, 2500L
        ));
        this.mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int panelWidth = Math.max(750, this.width - 40);
        int panelHeight = Math.max(400, this.height - 80);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Theme.MAIN_BG.getRGB());
        String title = "Script Editor: " + targetNpcName;
        drawCenteredString(this.fontRendererObj, title, this.width / 2, panelY + 8, -1);

        scriptTextArea.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (scriptTextArea.isFocused()) {
            scriptTextArea.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == 1) {  
            this.mc.displayGuiScreen(null);
        }
    }

    private static void sendPacket(int enumOrdinal, Object... data) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(enumOrdinal);
            for (Object obj : data) {
                if (obj instanceof NBTTagCompound) {
                    writeNBT(buffer, (NBTTagCompound) obj);
                }
            }
            NetUtils.sendPacket(new FMLProxyPacket(buffer.copy(), "CustomNPCs"));
        } finally {
            if(buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

     
    private static void writeNBT(ByteBuf buf, NBTTagCompound compound) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream dataStream = new DataOutputStream(byteStream);
             
            CompressedStreamTools.write(compound, dataStream);
            byte[] bytes = byteStream.toByteArray();

            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}