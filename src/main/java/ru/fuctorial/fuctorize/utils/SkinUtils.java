package ru.fuctorial.fuctorize.utils;

import ru.fuctorial.fuctorize.client.gui.menu.GuiAccountManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gui.Gui;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkinUtils {

    private static final Map<String, ResourceLocation> skinCache = new ConcurrentHashMap<>();
    private static final ResourceLocation STEVE_SKIN = new ResourceLocation("textures/entity/steve.png");

     
    private static final IImageBuffer skinValidator = new IImageBuffer() {
        @Override
        public BufferedImage parseUserSkin(BufferedImage image) {
             
            if (image == null) {
                 
                return null;
            }
             
             
             
            if (image.getWidth() == 64 && (image.getHeight() == 32 || image.getHeight() == 64)) {
                 
                return image;
            }
             
             
            return null;
        }

        @Override
        public void func_152634_a() {
             
        }
    };

     
    public static void clearSkinCache() {
        skinCache.clear();
    }

    public static void bindSkin(String username) {
        if (username == null || username.isEmpty()) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(STEVE_SKIN);
            return;
        }

        SkinProvider provider = GuiAccountManager.currentSkinProvider;
        String cacheKey = provider.name() + ":" + username;
        ResourceLocation skinLocation = skinCache.get(cacheKey);

        if (skinLocation == null) {
            skinLocation = new ResourceLocation("skins/" + provider.name().toLowerCase() + "/" + username.toLowerCase());
            try {
                 
                ThreadDownloadImageData skinData = new ThreadDownloadImageData(
                        (File)null,
                        provider.getUrlForPlayer(username),
                        STEVE_SKIN,
                        skinValidator  
                );
                 
                Minecraft.getMinecraft().getTextureManager().loadTexture(skinLocation, (ITextureObject)skinData);
                skinCache.put(cacheKey, skinLocation);
            } catch (Exception e) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(STEVE_SKIN);
                return;
            }
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(skinLocation);
    }

    public static void drawHead(int x, int y, int size) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.func_152125_a(x, y, 8, 8, 8, 8, size, size, 64, 64);
        Gui.func_152125_a(x, y, 40, 8, 8, 8, size, size, 64, 64);
    }
}