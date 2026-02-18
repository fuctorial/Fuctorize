 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.nbtedit.GuiNBTEdit;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import ru.fuctorial.fuctorize.utils.Statics;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

public class NBTEditor extends Module {

    public NBTEditor(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("nbteditor", Lang.get("module.nbteditor.name"), Category.EXPLOIT, ActivationType.SINGLE);
        addSetting(new BindSetting(Lang.get("module.nbteditor.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.nbteditor.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            toggle();
            return;
        }

        ItemStack heldItem = mc.thePlayer.getHeldItem();

        if (heldItem == null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + Lang.get("chat.nbteditor.hold_item")));
            toggle();
            return;
        }

        NBTTagCompound tag = heldItem.getTagCompound();

        if (tag == null) {
            tag = new NBTTagCompound();
            heldItem.setTagCompound(tag);
        }

        Statics.STATIC_ITEMSTACK = heldItem;
        Statics.STATIC_NBT = tag;

        mc.displayGuiScreen(new GuiNBTEdit((NBTTagCompound) tag.copy()));

        toggle();
    }
}