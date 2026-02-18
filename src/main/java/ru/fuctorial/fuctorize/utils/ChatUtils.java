package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;


 





public class ChatUtils {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();

     
    public static void printMessage(String message) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }
}