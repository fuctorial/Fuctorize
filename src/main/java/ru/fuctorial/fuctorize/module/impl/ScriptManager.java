// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\ScriptManager.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.script_editor.GuiScriptEditor;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.PlayerUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Keyboard;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class ScriptManager extends Module {

    private boolean isWaitingForData = false;
    private int targetId = -1;

    public ScriptManager(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("scriptmanager", Lang.get("module.scriptmanager.name"), Category.EXPLOIT);
        addSetting(new BindSetting(Lang.get("module.scriptmanager.setting.open_editor"), Keyboard.KEY_J));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.scriptmanager.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.currentScreen != null) {
            toggle();
            return;
        }

        Entity target = PlayerUtils.getTarget(16.0);
        try {
            Class<?> npcInterface = Class.forName("noppes.npcs.entity.EntityNPCInterface");
            if (target != null && npcInterface.isInstance(target)) {
                this.targetId = target.getEntityId();
                String npcName = target.getCommandSenderName();

                client.notificationManager.show(new Notification(Lang.get("notification.scriptmanager.title"), Lang.get("notification.scriptmanager.message.requesting_scripts") + npcName, Notification.NotificationType.INFO, 2000L));

                isWaitingForData = true;
                mc.displayGuiScreen(new GuiScriptEditor(this.targetId, npcName));

                sendPacket(1, this.targetId);
                client.scheduleTask(() -> {
                    if (this.targetId != -1) {
                        sendPacket(42);
                    }
                });
            } else {
                client.notificationManager.show(new Notification(Lang.get("notification.scriptmanager.title"), Lang.get("notification.scriptmanager.message.npc_not_found"), Notification.NotificationType.ERROR, 2000L));
                toggle();
            }
        } catch (ClassNotFoundException e) {
            client.notificationManager.show(new Notification(Lang.get("notification.scriptmanager.title"), Lang.get("notification.scriptmanager.message.mod_not_found"), Notification.NotificationType.ERROR, 3000L));
            toggle();
        }
    }

    @Override
    public void onDisable() {
        isWaitingForData = false;
        targetId = -1;
        if (mc.currentScreen instanceof GuiScriptEditor) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || !isWaitingForData || !(event.getPacket() instanceof FMLProxyPacket)) {
            return;
        }

        FMLProxyPacket proxyPacket = (FMLProxyPacket) event.getPacket();
        if (!proxyPacket.channel().equals("CustomNPCs")) {
            return;
        }

        ByteBuf payload = proxyPacket.payload().copy();
        try {
            if (payload.readableBytes() < 4) return;
            int enumId = payload.readInt();
            if (enumId == 11) {
                event.setCanceled(true);
                return;
            }
            if (enumId == 20 && mc.currentScreen instanceof GuiScriptEditor) {
                try {
                    NBTTagCompound nbt = readNBT(payload);
                    if (nbt != null && nbt.hasKey("ScriptsContainers")) {
                        ((GuiScriptEditor) mc.currentScreen).setScriptData(nbt);
                        isWaitingForData = false;
                        event.setCanceled(true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } finally {
            if (payload.refCnt() > 0) {
                payload.release();
            }
        }
    }

    public static void sendPacket(int enumOrdinal, Object... data) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(enumOrdinal);
            for (Object obj : data) {
                if (obj instanceof Integer) {
                    buffer.writeInt((Integer) obj);
                }
            }
            NetUtils.sendPacket(new FMLProxyPacket(buffer.copy(), "CustomNPCs"));
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    public static NBTTagCompound readNBT(ByteBuf buf) throws IOException {
        short length = buf.readShort();
        if (length < 0) return null;
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);

        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes));
        return CompressedStreamTools.read(stream);
    }
}
