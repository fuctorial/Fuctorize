package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.client.FMLClientHandler;
import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import org.lwjgl.util.vector.Vector4f;

public class RenderUtils {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();
    private static final RenderItem itemRenderer = new RenderItem();

     
     
     

    public static Vector4f projectTo2D(double x, double y, double z) {
        try {
            float renderX = (float) (x - RenderManager.instance.renderPosX);
            float renderY = (float) (y - RenderManager.instance.renderPosY);
            float renderZ = (float) (z - RenderManager.instance.renderPosZ);

            FloatBuffer screenCoords = BufferUtils.createFloatBuffer(4);
            FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
            FloatBuffer projection = BufferUtils.createFloatBuffer(16);
            IntBuffer viewport = BufferUtils.createIntBuffer(16);

            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

            if (Project.gluProject(renderX, renderY, renderZ, modelview, projection, viewport, screenCoords)) {
                ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
                float scale = sr.getScaleFactor();
                float sx = screenCoords.get(0) / scale;
                float sy = (sr.getScaledHeight() * scale - screenCoords.get(1)) / scale;
                return new Vector4f(sx, sy, screenCoords.get(2), screenCoords.get(3));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void begin2DRendering() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        GL11.glOrtho(0, sr.getScaledWidth(), sr.getScaledHeight(), 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void end2DRendering() {
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    public static void begin3DRendering() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDepthMask(false);
    }

    public static void end3DRendering() {
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

     
    public static void beginWorld3DRender() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
         
        GL11.glEnable(GL11.GL_DEPTH_TEST);  
         
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDepthMask(false);  
    }

     
    public static void endWorld3DRender() {
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    public static void startScissor(int x, int y, int width, int height) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glScissor(x * scale, (mc.displayHeight - (y + height) * scale), width * scale, height * scale);
    }

    public static void stopScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

     
     
     

    public static void setColor(int hexColor, float alpha) {
        float r = (float) (hexColor >> 16 & 255) / 255.0F;
        float g = (float) (hexColor >> 8 & 255) / 255.0F;
        float b = (float) (hexColor & 255) / 255.0F;
        GL11.glColor4f(r, g, b, alpha);
    }

    public static void setColor(Color color, float alpha) {
        float r = (float) color.getRed() / 255.0F;
        float g = (float) color.getGreen() / 255.0F;
        float b = (float) color.getBlue() / 255.0F;
        GL11.glColor4f(r, g, b, alpha);
    }

    public static int getColor(int r, int g, int b) {
        return 255 << 24 | r << 16 | g << 8 | b;
    }

    public static int getColor(int a, int r, int g, int b) {
        return a << 24 | r << 16 | g << 8 | b;
    }

     
     
     

    public static void drawRect(float paramXStart, float paramYStart, float paramXEnd, float paramYEnd, int paramColor) {
        float alpha = (paramColor >> 24 & 255) / 255.0f;
        float red = (paramColor >> 16 & 255) / 255.0f;
        float green = (paramColor >> 8 & 255) / 255.0f;
        float blue = (paramColor & 255) / 255.0f;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glPushMatrix();
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glBegin(7);
        GL11.glVertex2d(paramXEnd, paramYStart);
        GL11.glVertex2d(paramXStart, paramYStart);
        GL11.glVertex2d(paramXStart, paramYEnd);
        GL11.glVertex2d(paramXEnd, paramYEnd);
        GL11.glEnd();
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
    }

     
     
     

     
    public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, Color color, float alpha, float lineWidth) {
         
        GL11.glLineWidth(lineWidth);

         
        setColor(color, alpha);

         
        double renderX1 = x1 - RenderManager.renderPosX;
        double renderY1 = y1 - RenderManager.renderPosY;
        double renderZ1 = z1 - RenderManager.renderPosZ;
        double renderX2 = x2 - RenderManager.renderPosX;
        double renderY2 = y2 - RenderManager.renderPosY;
        double renderZ2 = z2 - RenderManager.renderPosZ;

         
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINES);
        tessellator.addVertex(renderX1, renderY1, renderZ1);
        tessellator.addVertex(renderX2, renderY2, renderZ2);
        tessellator.draw();
    }

     
    public static void drawBox(double x1, double y1, double z1, double x2, double y2, double z2, Color color, float alpha) {
         
        setColor(color, alpha);

         
        double rX1 = x1 - RenderManager.renderPosX;
        double rY1 = y1 - RenderManager.renderPosY;
        double rZ1 = z1 - RenderManager.renderPosZ;
        double rX2 = x2 - RenderManager.renderPosX;
        double rY2 = y2 - RenderManager.renderPosY;
        double rZ2 = z2 - RenderManager.renderPosZ;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

         
        tessellator.addVertex(rX1, rY1, rZ1);
        tessellator.addVertex(rX1, rY1, rZ2);
        tessellator.addVertex(rX2, rY1, rZ2);
        tessellator.addVertex(rX2, rY1, rZ1);

         
        tessellator.addVertex(rX1, rY2, rZ1);
        tessellator.addVertex(rX2, rY2, rZ1);
        tessellator.addVertex(rX2, rY2, rZ2);
        tessellator.addVertex(rX1, rY2, rZ2);

         
        tessellator.addVertex(rX1, rY1, rZ1);
        tessellator.addVertex(rX2, rY1, rZ1);
        tessellator.addVertex(rX2, rY2, rZ1);
        tessellator.addVertex(rX1, rY2, rZ1);

         
        tessellator.addVertex(rX1, rY1, rZ2);
        tessellator.addVertex(rX1, rY2, rZ2);
        tessellator.addVertex(rX2, rY2, rZ2);
        tessellator.addVertex(rX2, rY1, rZ2);

         
        tessellator.addVertex(rX1, rY1, rZ1);
        tessellator.addVertex(rX1, rY2, rZ1);
        tessellator.addVertex(rX1, rY2, rZ2);
        tessellator.addVertex(rX1, rY1, rZ2);

         
        tessellator.addVertex(rX2, rY1, rZ1);
        tessellator.addVertex(rX2, rY1, rZ2);
        tessellator.addVertex(rX2, rY2, rZ2);
        tessellator.addVertex(rX2, rY2, rZ1);

        tessellator.draw();
    }

    public static void drawOutlinedBoundingBox(AxisAlignedBB bb) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(3);
        tessellator.addVertex(bb.minX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.minZ);
        tessellator.draw();
        tessellator.startDrawing(3);
        tessellator.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.draw();
        tessellator.startDrawing(1);
        tessellator.addVertex(bb.minX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.maxZ);
        tessellator.draw();
    }

    public static void drawFilledBoundingBox(AxisAlignedBB bb) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(bb.minX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.maxZ);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.addVertex(bb.maxX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.maxZ);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.minZ);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.addVertex(bb.minX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.minZ);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.addVertex(bb.minX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.minZ);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.addVertex(bb.minX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.maxZ);
        tessellator.addVertex(bb.minX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.minX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.minZ);
        tessellator.addVertex(bb.maxX, bb.maxY, bb.maxZ);
        tessellator.addVertex(bb.maxX, bb.minY, bb.maxZ);
        tessellator.draw();
    }

     
     
     

    public static void drawItem(int x, int y, ItemStack stack) {
        itemRenderer.renderItemIntoGUI(mc.fontRenderer, mc.renderEngine, stack, x, y);
        itemRenderer.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, stack, x, y);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
    }
}