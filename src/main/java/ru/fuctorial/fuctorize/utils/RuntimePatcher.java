package ru.fuctorial.fuctorize.utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import cpw.mods.fml.common.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import ru.fuctorial.fuctorize.Fuctorize;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.MinecraftServer;

public class RuntimePatcher {

     
    private static final String[] MOD_CONTROLLER_FIELD_SRG = {"modController"};
    private static final String[] EVENT_CHANNELS_FIELD_SRG = {"eventChannels"};
    private static final String[] ACTIVE_MOD_LIST_FIELD_SRG = {"mods"};  
    private static final String[] MOD_ID_FIELD_FMC_SRG = {"modId"};
    private static final String[] VERSION_FIELD_FMC_SRG = {"version"};
    private static final String[] METADATA_FIELD_FMC_SRG = {"modMetadata"};
    private static final String[] DESCRIPTOR_FIELD_FMC_SRG = {"descriptor"};
    private static final String[] MOD_ID_FIELD_MD_SRG = {"modId"};
    private static final String[] NAME_FIELD_MD_SRG = {"name"};
    private static final String[] VERSION_FIELD_MD_SRG = {"version"};
    private static final String[] SERVER_MOD_LOADER_FIELD_SRG = {"serverModLoader"};

    public static void patch(String newModId, String newVersion) {
        try {
             
            LoadController clientController = ReflectionHelper.getPrivateValue(Loader.class, Loader.instance(), MOD_CONTROLLER_FIELD_SRG);
            patchController(clientController, newModId, newVersion);
            System.out.println("FUCTORIZE PATCHER: Client side patched successfully.");

             
            if (MinecraftServer.getServer() != null && MinecraftServer.getServer().isSnooperEnabled()) {
                Loader serverLoader = ReflectionHelper.getPrivateValue(MinecraftServer.class, MinecraftServer.getServer(), SERVER_MOD_LOADER_FIELD_SRG);
                if (serverLoader != null) {
                    LoadController serverController = ReflectionHelper.getPrivateValue(Loader.class, serverLoader, MOD_CONTROLLER_FIELD_SRG);
                    patchController(serverController, newModId, newVersion);
                    System.out.println("FUCTORIZE PATCHER: Integrated server side patched successfully.");
                }
            }
        } catch (Exception e) {
            System.err.println("FUCTORIZE PATCHER: CRITICAL ERROR DURING RUNTIME PATCHING!");
            e.printStackTrace();
        }
    }

    private static void patchController(LoadController modController, String newModId, String newVersion) throws Exception {
        if (modController == null) {
            System.err.println("FUCTORIZE PATCHER: Provided ModController is null. Skipping patch.");
            return;
        }

         
        List<ModContainer> activeModList = modController.getActiveModList();
         
        List<ModContainer> mutableModList = new ArrayList<>(activeModList);

        FMLModContainer originalContainer = findMyContainer(mutableModList);
        if (originalContainer == null) {
            System.err.println("FUCTORIZE PATCHER: Could not find own mod container. Aborting patch.");
            return;
        }

         
        Map<String, EventBus> eventBusMap = ReflectionHelper.getPrivateValue(LoadController.class, modController, EVENT_CHANNELS_FIELD_SRG);
        HashMap<String, EventBus> mutableEventBusMap = Maps.newHashMap(eventBusMap);
        EventBus fmlEventBus = mutableEventBusMap.get(originalContainer.getModId());

         
        mutableModList.remove(originalContainer);
        mutableEventBusMap.remove(originalContainer.getModId());

         
        applyMetadata(originalContainer, newModId, newVersion);

         
        if (newModId != null && !newModId.isEmpty()) {
            mutableModList.add(originalContainer);
            mutableEventBusMap.put(newModId, fmlEventBus);
        }

         
        try {
            Field modsField = ReflectionHelper.findField(LoadController.class, ACTIVE_MOD_LIST_FIELD_SRG);
            modsField.setAccessible(true);

             
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(modsField, modsField.getModifiers() & ~Modifier.FINAL);

            modsField.set(modController, mutableModList);
        } catch (Exception e) {
            System.err.println("FUCTORIZE PATCHER: Failed to write back the modified mod list into LoadController.");
            throw e;
        }

         
        ReflectionHelper.setPrivateValue(LoadController.class, modController, ImmutableMap.copyOf(mutableEventBusMap), EVENT_CHANNELS_FIELD_SRG);
    }

    private static void applyMetadata(FMLModContainer container, String modId, String version) {
        try {
            ReflectionHelper.setPrivateValue(FMLModContainer.class, container, modId, MOD_ID_FIELD_FMC_SRG);
            ReflectionHelper.setPrivateValue(FMLModContainer.class, container, version, VERSION_FIELD_FMC_SRG);

            ModMetadata metadata = ReflectionHelper.getPrivateValue(FMLModContainer.class, container, METADATA_FIELD_FMC_SRG);
            ReflectionHelper.setPrivateValue(ModMetadata.class, metadata, modId, MOD_ID_FIELD_MD_SRG);
            ReflectionHelper.setPrivateValue(ModMetadata.class, metadata, modId, NAME_FIELD_MD_SRG);
            ReflectionHelper.setPrivateValue(ModMetadata.class, metadata, version, VERSION_FIELD_MD_SRG);

            Map<String, Object> descriptor = ReflectionHelper.getPrivateValue(FMLModContainer.class, container, DESCRIPTOR_FIELD_FMC_SRG);
            descriptor.put("modid", modId);
            descriptor.put("name", modId);
            descriptor.put("version", version);
        } catch (Exception e) {
            System.err.println("FUCTORIZE PATCHER: Failed to apply metadata.");
            e.printStackTrace();
        }
    }

    private static FMLModContainer findMyContainer(List<ModContainer> modList) {
        for (ModContainer container : modList) {
            if (container.getMod() instanceof Fuctorize) {
                return (FMLModContainer) container;
            }
        }
        return null;
    }
}