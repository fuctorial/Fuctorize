package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.ModeSetting;  
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.TimerUtils;

import java.util.HashMap;
import java.util.Map;

public class Dropper extends Module {

    private ModeSetting mode;
    private SliderSetting delay;
    private final TimerUtils timer = new TimerUtils();

    private int targetItemId = -1;
    private int targetItemMeta = -1;
    private String targetItemName = "";

     
    private final Map<Integer, Integer> inventorySnapshot = new HashMap<>();

    public Dropper(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("dropper", Lang.get("module.dropper.name"), Category.PLAYER);

         
        mode = new ModeSetting("Mode", "Target", "Target", "Inventory", "Hotbar");
        delay = new SliderSetting(Lang.get("module.dropper.setting.delay"), 50.0, 0.0, 1000.0, 10.0);

        addSetting(mode);
        addSetting(delay);
        addSetting(new BindSetting(Lang.get("module.dropper.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.dropper.desc");
    }

    @Override
    public void onEnable() {
        if (mode.isMode("Target")) {
            resetTarget();
            takeInventorySnapshot();  
        } else {
             
            timer.reset();
        }
    }

    @Override
    public void onDisable() {
        resetTarget();
        inventorySnapshot.clear();
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null) return;

         
        if (mode.isMode("Target")) {
            if (targetItemId == -1) {
                checkForNewItems();
            } else {
                if (timer.hasReached((long) delay.value)) {
                    dropTargetItems();
                    timer.reset();
                }
            }
            return;
        }

         
        if (timer.hasReached((long) delay.value)) {
            if (mode.isMode("Inventory")) {
                 
                if (dropFirstItemInRange(9, 35)) {
                    timer.reset();
                }
            } else if (mode.isMode("Hotbar")) {
                 
                if (dropFirstItemInRange(36, 44)) {
                    timer.reset();
                }
            }
        }
    }

     
    private boolean dropFirstItemInRange(int startSlot, int endSlot) {
         
         
        for (int i = startSlot; i <= endSlot; i++) {
            Slot slot = (Slot) mc.thePlayer.inventoryContainer.inventorySlots.get(i);
            if (slot.getHasStack()) {
                dropSlot(slot.slotNumber);
                return true;  
            }
        }
        return false;
    }

    private void dropSlot(int slotNumber) {
        mc.playerController.windowClick(
                mc.thePlayer.inventoryContainer.windowId,
                slotNumber,
                1,  
                4,  
                mc.thePlayer
        );
    }

     

    private void resetTarget() {
        targetItemId = -1;
        targetItemMeta = -1;
        targetItemName = "";
    }

    private int getStackHash(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0;
        return Item.getIdFromItem(stack.getItem()) | (stack.getItemDamage() << 16);
    }

    private void takeInventorySnapshot() {
        inventorySnapshot.clear();
        if (mc.thePlayer == null) return;
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            if (stack != null) {
                int hash = getStackHash(stack);
                inventorySnapshot.put(hash, inventorySnapshot.getOrDefault(hash, 0) + stack.stackSize);
            }
        }
    }

    private void checkForNewItems() {
        Map<Integer, Integer> currentInventory = new HashMap<>();
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            if (stack != null) {
                int hash = getStackHash(stack);
                currentInventory.put(hash, currentInventory.getOrDefault(hash, 0) + stack.stackSize);
            }
        }

        for (Map.Entry<Integer, Integer> entry : currentInventory.entrySet()) {
            int hash = entry.getKey();
            int currentCount = entry.getValue();
            int oldCount = inventorySnapshot.getOrDefault(hash, 0);

            if (currentCount > oldCount) {
                targetItemId = hash & 0xFFFF;
                targetItemMeta = hash >> 16;
                ItemStack tempStack = new ItemStack(Item.getItemById(targetItemId), 1, targetItemMeta);
                targetItemName = tempStack.getDisplayName();
                client.notificationManager.show(new Notification(
                        Lang.get("notification.dropper.target_set"),
                        targetItemName,
                        Notification.NotificationType.INFO,
                        2000L
                ));
                return;
            }
        }
        inventorySnapshot.clear();
        inventorySnapshot.putAll(currentInventory);
    }

    private void dropTargetItems() {
        for (Object slotObj : mc.thePlayer.inventoryContainer.inventorySlots) {
            Slot slot = (Slot) slotObj;
            if (slot.getHasStack() && slot.inventory == mc.thePlayer.inventory) {
                ItemStack stack = slot.getStack();
                if (stack != null && Item.getIdFromItem(stack.getItem()) == targetItemId && stack.getItemDamage() == targetItemMeta) {
                    dropSlot(slot.slotNumber);
                    if (delay.value > 0) break;
                }
            }
        }
    }

    @Override
    public String getName() {
        if (mode.isMode("Target")) {
            if (targetItemId != -1) {
                return super.getName() + " \u00A77[" + targetItemName + "]";
            }
            return super.getName() + " \u00A77[Wait]";
        }
        return super.getName() + " \u00A77[" + mode.getMode() + "]";
    }
}