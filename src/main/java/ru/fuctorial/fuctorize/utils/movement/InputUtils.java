package ru.fuctorial.fuctorize.utils.movement;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

public final class InputUtils {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();

    public static void pressAttack() {

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), true);
    }

    public static void releaseAttack() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
    }

    public static void pressSprint() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
    }

    public static void releaseSprint() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
    }

    public static void resetAll() {
        releaseAttack();
        releaseSprint();
    }
}