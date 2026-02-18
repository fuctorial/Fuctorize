package ru.fuctorial.fuctorize.utils;

import org.lwjgl.opengl.GL11;


 




public class ChamsUtils {

     
    public static void preRender(int visible, int hidden, boolean throughWalls) {
         
        GL11.glDisable(GL11.GL_TEXTURE_2D);  
        GL11.glEnable(GL11.GL_BLEND);        
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (throughWalls) {
             
            GL11.glDisable(GL11.GL_DEPTH_TEST);  
            GL11.glDepthMask(false);             

             
            setColor(hidden);
        } else {
             
            GL11.glEnable(GL11.GL_DEPTH_TEST);   
            GL11.glDepthMask(true);              

             
            setColor(visible);
        }
    }

     
    public static void postRender() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);  
        GL11.glDisable(GL11.GL_BLEND);      
    }

     
    private static void setColor(int color) {
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glColor4f(r, g, b, a);
    }
}