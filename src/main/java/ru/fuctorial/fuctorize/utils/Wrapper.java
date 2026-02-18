package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;


 




 
public class Wrapper {

    public static final Wrapper INSTANCE = new Wrapper();
    private final Minecraft mc = FMLClientHandler.instance().getClient();

    public Minecraft getMc() {
        return mc;
    }

    public EntityClientPlayerMP getPlayer() {
        return mc.thePlayer;
    }

    public WorldClient getWorld() {
        return mc.theWorld;
    }

    public GameSettings getGameSettings() {
        return mc.gameSettings;
    }

    public FontRenderer getFontRenderer() {
        return mc.fontRenderer;
    }
}