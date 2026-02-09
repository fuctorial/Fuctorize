// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\AdvancedTooltip.java
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.gui.ScaledResolution;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting; // Добавим настройку ширины
import ru.fuctorial.fuctorize.utils.Lang;
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
    private SliderSetting maxWidth; // Настройка макс. ширины

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
        // Настройка ширины (в пикселях). По дефолту 300 - оптимально для чтения.
        maxWidth = new SliderSetting("Max Width", 300.0, 100.0, 1000.0, 10.0);

        addSetting(showId);
        addSetting(showDurability);
        addSetting(showClasses);
        addSetting(showFullClassName);
        addSetting(showNBT);
        addSetting(compactMode);
        addSetting(maxWidth);
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

        // 1. Собираем сырые данные
        List<String> rawInfo = new ArrayList<>();
        Item item = event.itemStack.getItem();

        // ID
        if (showId.enabled) {
            String idString = EnumChatFormatting.DARK_GRAY + Lang.get("tooltip.advancedtooltip.label.id") + " " + EnumChatFormatting.GRAY + Item.getIdFromItem(item) + ":" + event.itemStack.getItemDamage();
            rawInfo.add(idString);
        }

        // Durability
        if (showDurability.enabled && event.itemStack.isItemStackDamageable()) {
            int maxDamage = event.itemStack.getMaxDamage();
            int currentDamage = event.itemStack.getItemDamage();
            String durabilityString = EnumChatFormatting.DARK_GRAY + Lang.get("tooltip.advancedtooltip.label.durability") + " " + EnumChatFormatting.GRAY + (maxDamage - currentDamage) + " / " + maxDamage;
            rawInfo.add(durabilityString);
        }

        // Classes
        if (showClasses.enabled) {
            String itemClassName = showFullClassName.enabled ? item.getClass().getName() : item.getClass().getSimpleName();
            rawInfo.add(EnumChatFormatting.DARK_GRAY + "Item Class: " + EnumChatFormatting.GRAY + itemClassName);

            if (item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).field_150939_a;
                if (block != null) {
                    String blockClassName = showFullClassName.enabled ? block.getClass().getName() : block.getClass().getSimpleName();
                    rawInfo.add(EnumChatFormatting.DARK_GRAY + "Block Class: " + EnumChatFormatting.GRAY + blockClassName);
                }
            }
        }

        // NBT
        if (showNBT.enabled && event.itemStack.hasTagCompound()) {
            String nbtString = EnumChatFormatting.DARK_GRAY + "NBT: " + EnumChatFormatting.GRAY + event.itemStack.getTagCompound().toString();
            rawInfo.add(nbtString);
        }

        if (rawInfo.isEmpty()) return;

        // 2. Рассчитываем ограничения
        if (client.fontManager == null || !client.fontManager.isReady()) return;

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = sr.getScaledWidth();
        // Максимальная ширина: либо настройка, либо (ширина экрана - отступы), что меньше
        int wrapWidth = Math.min((int) maxWidth.value, screenWidth - 20);

        // 3. Форматируем и добавляем в тултип
        event.toolTip.add(""); // Пустая строка-разделитель

        if (compactMode.enabled) {
            // В компактном режиме соединяем все в одну строку через " | "
            String combined = String.join(EnumChatFormatting.DARK_GRAY + " | ", rawInfo);
            // И затем разбиваем эту огромную строку, чтобы она влезла
            List<String> wrapped = client.fontManager.regular_18.wrapText(combined, wrapWidth);
            event.toolTip.addAll(wrapped);
        } else {
            // В обычном режиме проверяем каждую строку
            for (String line : rawInfo) {
                if (client.fontManager.regular_18.getStringWidth(line) > wrapWidth) {
                    // Если строка длинная (например, NBT), разбиваем её
                    event.toolTip.addAll(client.fontManager.regular_18.wrapText(line, wrapWidth));
                } else {
                    event.toolTip.add(line);
                }
            }
        }
    }
}