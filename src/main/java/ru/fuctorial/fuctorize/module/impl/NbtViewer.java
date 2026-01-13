// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\NbtViewer.java
package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiNBTEdit;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;

public class NbtViewer extends Module {

    public NbtViewer(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("nbtviewer", Lang.get("module.nbtviewer.name"), Category.MISC, ActivationType.SINGLE);
        addSetting(new BindSetting(Lang.get("module.nbtviewer.setting.bind"), Keyboard.KEY_LBRACKET));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.nbtviewer.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            toggle();
            return;
        }

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null) {
            notifyNoTarget();
            toggle();
            return;
        }

        try {
            if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mop.entityHit != null) {
                Entity entity = mop.entityHit;
                // --- ИЗМЕНЕНИЕ: Передаем саму сущность в конструктор ---
                mc.displayGuiScreen(new GuiNBTEdit(entity));

            } else if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                TileEntity tileEntity = mc.theWorld.getTileEntity(mop.blockX, mop.blockY, mop.blockZ);
                if (tileEntity != null) {
                    // --- ИЗМЕНЕНИЕ: Передаем саму тайл-сущность в конструктор ---
                    mc.displayGuiScreen(new GuiNBTEdit(tileEntity));
                } else {
                    notifyNoTarget();
                }
            } else {
                notifyNoTarget();
            }
        } catch (Exception e) {
            client.notificationManager.show(new Notification(Lang.get("notification.nbtviewer.title"), Lang.get("notification.nbtviewer.read_error"), Notification.NotificationType.ERROR, 3000L));
            e.printStackTrace();
        } finally {
            toggle();
        }
    }

    private void notifyNoTarget() {
        client.notificationManager.show(new Notification(Lang.get("notification.nbtviewer.title"), Lang.get("notification.nbtviewer.no_target"), Notification.NotificationType.WARNING, 2000L));
    }
}
