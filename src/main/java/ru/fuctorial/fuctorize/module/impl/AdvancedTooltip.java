// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\module\impl\AdvancedTooltip.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.utils.ModContainerHelper;

public class AdvancedTooltip extends Module {

    private BooleanSetting showId;
    private BooleanSetting showDurability;
    private BooleanSetting showClasses;
    private BooleanSetting showFullClassName;
    private BooleanSetting showNBT;
    private BooleanSetting compactMode;

    public AdvancedTooltip(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("advancedtooltip", Lang.get("module.advancedtooltip.name"), Category.MISC);

        showId = new BooleanSetting(Lang.get("module.advancedtooltip.setting.show_id"), true);
        showDurability = new BooleanSetting(Lang.get("module.advancedtooltip.setting.show_durability"), true);
        showClasses = new BooleanSetting(Lang.get("module.advancedtooltip.setting.show_classes"), true);
        showFullClassName = new BooleanSetting(Lang.get("module.advancedtooltip.setting.show_full_class_name"), true);
        showNBT = new BooleanSetting(Lang.get("module.advancedtooltip.setting.show_nbt"), true);
        compactMode = new BooleanSetting(Lang.get("module.advancedtooltip.setting.compact_mode"), false);

        addSetting(showId);
        addSetting(showDurability);
        addSetting(showClasses);
        addSetting(showFullClassName);
        addSetting(showNBT);
        addSetting(compactMode);
        addSetting(new BindSetting(Lang.get("module.advancedtooltip.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.advancedtooltip.desc");
    }

    @Override
    public void onEnable() {
        ModContainerHelper.runWithFuctorizeContainer(() -> MinecraftForge.EVENT_BUS.register(this));
    }

    @Override
    public void onDisable() {
        ModContainerHelper.runWithFuctorizeContainer(() -> MinecraftForge.EVENT_BUS.unregister(this));
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (event.itemStack == null || event.itemStack.getItem() == null) {
            return;
        }

        List<String> extraInfo = new ArrayList<>();
        Item item = event.itemStack.getItem();

        if (showId.enabled) {
            String idString = EnumChatFormatting.DARK_GRAY + Lang.get("tooltip.advancedtooltip.label.id") + EnumChatFormatting.GRAY + Item.getIdFromItem(item) + ":" + event.itemStack.getItemDamage();
            extraInfo.add(idString);
        }

        if (showDurability.enabled && event.itemStack.isItemStackDamageable()) {
            int maxDamage = event.itemStack.getMaxDamage();
            int currentDamage = event.itemStack.getItemDamage();
            String durabilityString = EnumChatFormatting.DARK_GRAY + Lang.get("tooltip.advancedtooltip.label.durability") + EnumChatFormatting.GRAY + (maxDamage - currentDamage) + " / " + maxDamage;
            extraInfo.add(durabilityString);
        }

        if (showClasses.enabled) {
            String itemClassName = showFullClassName.enabled ? item.getClass().getName() : item.getClass().getSimpleName();
            extraInfo.add(EnumChatFormatting.DARK_GRAY + Lang.get("tooltip.advancedtooltip.label.item_class") + EnumChatFormatting.GRAY + itemClassName);

            if (item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).field_150939_a; // field_150939_a is blockInstance
                if (block != null) {
                    String blockClassName = showFullClassName.enabled ? block.getClass().getName() : block.getClass().getSimpleName();
                    extraInfo.add(EnumChatFormatting.DARK_GRAY + Lang.get("tooltip.advancedtooltip.label.block_class") + EnumChatFormatting.GRAY + blockClassName);
                }
            }
        }

        if (showNBT.enabled && event.itemStack.hasTagCompound()) {
            String nbtString = EnumChatFormatting.DARK_GRAY + Lang.get("tooltip.advancedtooltip.label.nbt") + EnumChatFormatting.GRAY + event.itemStack.getTagCompound().toString();
            extraInfo.add(nbtString);
        }

        if (!extraInfo.isEmpty()) {
            event.toolTip.add("");
            if (compactMode.enabled) {
                event.toolTip.add(String.join(EnumChatFormatting.DARK_GRAY + " | ", extraInfo));
            } else {
                event.toolTip.addAll(extraInfo);
            }
        }
    }
}