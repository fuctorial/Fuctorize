package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;


// ru.fuctorial/fuctorize/utils/ChatUtils.java





public class ChatUtils {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();

    /**
     * Отправляет сообщение в чат игрока.
     * @param message Сообщение для отправки (уже может содержать цветовые коды).
     */
    public static void printMessage(String message) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }
}