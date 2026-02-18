package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModContainer;

import java.lang.reflect.Field;

 
public final class ModContainerHelper {

    private static LoadController loadController;
    private static Field activeContainerField;
    private static ModContainer dummyContainer;

    private ModContainerHelper() {}

    public static void init(LoadController controller, Field activeField, ModContainer dummy) {
        loadController = controller;
        activeContainerField = activeField;
        dummyContainer = dummy;
    }

    public static void runWithFuctorizeContainer(Runnable action) {
        if (action == null) {
            return;
        }

        if (loadController == null || activeContainerField == null || dummyContainer == null) {
            action.run();
            return;
        }

        ModContainer previous = null;
        synchronized (ModContainerHelper.class) {
            try {
                previous = (ModContainer) activeContainerField.get(loadController);
                if (previous != dummyContainer) {
                    activeContainerField.set(loadController, dummyContainer);
                }
                action.run();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                action.run();
            } finally {
                if (previous != null && previous != dummyContainer) {
                    try {
                        activeContainerField.set(loadController, previous);
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
        }
    }
}
