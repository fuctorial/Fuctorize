package ru.fuctorial.fuctorize.module.impl;

import com.google.common.collect.BiMap;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.relauncher.ReflectionHelper;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.RegistryNamespaced;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.List;

public class BarrierESP extends Module {

    private static Block originalBarrierBlock;
    private static int barrierBlockId = -1;
    private static boolean reflectionSuccess = false;
    private static Field registryObjectsField;
    private static Field underlyingIntegerMapField;
    private static Field objectListField;

    private static final boolean isTargetModLoaded = Loader.isModLoaded("custom_model_api");

    public BarrierESP(FuctorizeClient client) {
        super(client);
        if (isTargetModLoaded) {
            initializeReflection();
        } else {
            System.err.println("Fuctorize/BarrierESP: Target mod 'custom_model_api' not found. Module will be inactive.");
        }
    }

    @Override
    public void init() {
        setMetadata("barrieresp", Lang.get("module.barrieresp.name"), Category.RENDER);
        addSetting(new BindSetting(Lang.get("module.barrieresp.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.barrieresp.desc");
    }

    private void initializeReflection() {
        if (reflectionSuccess) return;
        System.out.println("Fuctorize/BarrierESP: Initializing reflection...");
        try {
            registryObjectsField = ReflectionHelper.findField(RegistryNamespaced.class.getSuperclass(), "registryObjects", "field_82596_a");
            registryObjectsField.setAccessible(true);

            underlyingIntegerMapField = ReflectionHelper.findField(RegistryNamespaced.class, "underlyingIntegerMap", "field_148759_a");
            underlyingIntegerMapField.setAccessible(true);

            Class<?> identityMapClass = Class.forName("net.minecraft.util.ObjectIntIdentityMap");
            objectListField = ReflectionHelper.findField(identityMapClass, "identityToObjects", "field_148748_b");
            objectListField.setAccessible(true);

            reflectionSuccess = true;
            System.out.println("Fuctorize/BarrierESP: Reflection initialized successfully.");
        } catch (Exception e) {
            System.err.println("Fuctorize/BarrierESP: CRITICAL - Reflection failed during initialization. Module will not work.");
            e.printStackTrace();
            reflectionSuccess = false;
        }
    }

    @Override
    public void onEnable() {
        if (!isTargetModLoaded) {
            client.notificationManager.show(new Notification(Lang.get("notification.barrieresp.title.error"), Lang.get("notification.barrieresp.message.mod_not_found"), Notification.NotificationType.ERROR, 4000L));
            toggle();
            return;
        }

        if (!reflectionSuccess) {
            client.notificationManager.show(new Notification(Lang.get("notification.barrieresp.title.error"), Lang.get("notification.barrieresp.message.reflection_failed"), Notification.NotificationType.ERROR, 4000L));
            toggle();
            return;
        }

        FMLCommonHandler.instance().bus().register(this);

        if (mc.theWorld != null) {
            swapAndReload(true);
        }
    }

    @Override
    public void onDisable() {
        FMLCommonHandler.instance().bus().unregister(this);

        if (!reflectionSuccess) return;

        if (originalBarrierBlock != null && mc.theWorld != null) {
            swapAndReload(false);
        } else if (originalBarrierBlock != null) {
            swapBlock(false);
        }
    }

    @SubscribeEvent
    public void onConnect(ClientConnectedToServerEvent event) {
        if (this.isEnabled()) {
            swapAndReload(true);
        }
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectionFromServerEvent event) {
        if (originalBarrierBlock != null) {
            swapBlock(false);
        }
    }

    private void swapAndReload(boolean enable) {
        swapBlock(enable);
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void swapBlock(boolean enable) {
        if (!reflectionSuccess) return;
        try {
            RegistryNamespaced blockRegistry = GameData.getBlockRegistry();
            String barrierName = "custom_model_api:[cm-block]_barrier";

            if (enable) {
                if (originalBarrierBlock != null) return;

                originalBarrierBlock = (Block) blockRegistry.getObject(barrierName);

                if (originalBarrierBlock == null || originalBarrierBlock == Blocks.air) {
                    client.notificationManager.show(new Notification(Lang.get("notification.barrieresp.title.error"), Lang.get("notification.barrieresp.message.block_not_found"), Notification.NotificationType.ERROR, 4000L));
                    originalBarrierBlock = null;
                    this.setState(false);
                    return;
                }

                if (originalBarrierBlock == Blocks.glass) {
                    System.out.println("Fuctorize/BarrierESP: Barrier is already swapped. Skipping.");
                    return;
                }

                barrierBlockId = blockRegistry.getIDForObject(originalBarrierBlock);
                if (barrierBlockId < 0) {
                    client.notificationManager.show(new Notification(Lang.get("notification.barrieresp.title.error"), Lang.get("notification.barrieresp.message.invalid_block_id"), Notification.NotificationType.ERROR, 4000L));
                    this.setState(false);
                    return;
                }

                BiMap registryMap = (BiMap) registryObjectsField.get(blockRegistry);
                Object identityMap = underlyingIntegerMapField.get(blockRegistry);
                List objectList = (List) objectListField.get(identityMap);

                registryMap.forcePut(barrierName, Blocks.glass);

                if (barrierBlockId < objectList.size()) {
                    objectList.set(barrierBlockId, Blocks.glass);
                } else {
                    System.err.println("Fuctorize/BarrierESP: Barrier ID is out of bounds for the object list!");
                }
                System.out.println("Fuctorize/BarrierESP: Swapped BarrierBlock (ID: " + barrierBlockId + ") with Glass.");

            } else {
                if (barrierBlockId != -1 && originalBarrierBlock != null) {
                    BiMap registryMap = (BiMap) registryObjectsField.get(blockRegistry);
                    Object identityMap = underlyingIntegerMapField.get(blockRegistry);
                    List objectList = (List) objectListField.get(identityMap);

                    registryMap.forcePut(barrierName, originalBarrierBlock);

                    if (barrierBlockId < objectList.size()) {
                        objectList.set(barrierBlockId, originalBarrierBlock);
                    }

                    System.out.println("Fuctorize/BarrierESP: Restored original BarrierBlock (ID: " + barrierBlockId + ").");

                    originalBarrierBlock = null;
                    barrierBlockId = -1;
                }
            }
        } catch (Exception e) {
            System.err.println("Fuctorize/BarrierESP: FAILED to swap block via reflection!");
            e.printStackTrace();
            client.notificationManager.show(new Notification(Lang.get("notification.barrieresp.title.error"), e.getClass().getSimpleName(), Notification.NotificationType.ERROR, 4000L));
            this.setState(false);
        }
    }
}