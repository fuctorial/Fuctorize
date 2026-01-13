package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.client.FMLClientHandler;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class PlayerUtils {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();

    public static boolean isPlayerMoving() {
        if (mc.thePlayer == null) return false;
        return mc.thePlayer.moveForward != 0 || mc.thePlayer.moveStrafing != 0;
    }

    public static boolean canPlayerSprint() {
        if (mc.thePlayer == null) return false;
        return !mc.thePlayer.isCollidedHorizontally
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6
                && !mc.thePlayer.isUsingItem()
                && !mc.thePlayer.isSneaking();
    }

    public static MovingObjectPosition rayTrace(double distance) {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        Vec3 playerPos = Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 lookVec = mc.thePlayer.getLookVec();
        Vec3 targetVec = playerPos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return mc.theWorld.rayTraceBlocks(playerPos, targetVec, false);
    }

    public static MovingObjectPosition rayTraceThroughUnloadedChunks(double distance) {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        World world = mc.theWorld;
        Vec3 playerPos = Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 lookVec = mc.thePlayer.getLookVec();

        Vec3 currentPos = Vec3.createVectorHelper(playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);
        Vec3 stepVec = Vec3.createVectorHelper(lookVec.xCoord * 0.1, lookVec.yCoord * 0.1, lookVec.zCoord * 0.1);

        for (int i = 0; i < distance * 10; i++) {
            currentPos = currentPos.addVector(stepVec.xCoord, stepVec.yCoord, stepVec.zCoord);
            int x = MathHelper.floor_double(currentPos.xCoord);
            int y = MathHelper.floor_double(currentPos.yCoord);
            int z = MathHelper.floor_double(currentPos.zCoord);

            if (world.blockExists(x, y, z)) {
                Block block = world.getBlock(x, y, z);
                if (block != null && !(block instanceof BlockAir)) {
                    // Возвращаем точный MOP до этой точки
                    return mc.theWorld.rayTraceBlocks(playerPos, currentPos, false);
                }
            }
        }
        Vec3 endPos = playerPos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return new MovingObjectPosition(
                MathHelper.floor_double(endPos.xCoord),
                MathHelper.floor_double(endPos.yCoord),
                MathHelper.floor_double(endPos.zCoord),
                -1,
                endPos,
                false
        );
    }

    public static Block getBlock(int x, int y, int z) {
        if (mc.theWorld == null) return null;
        return mc.theWorld.getBlock(x, y, z);
    }
    /**
     * Выполняет трассировку луча для поиска сущности на заданной дистанции.
     * @param range Дистанция поиска.
     * @return Найденная сущность (EntityLivingBase) или null.
     */
    public static EntityLivingBase getTarget(double range) {
        if (mc.theWorld == null || mc.renderViewEntity == null) {
            return null;
        }

        EntityLivingBase target = null;
        Entity viewer = mc.renderViewEntity;

        if (!(viewer instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase livingViewer = (EntityLivingBase) viewer;

        // FIX: Calculate eye position correctly by adding eye height to the entity's Y position.
        Vec3 eyesPos = Vec3.createVectorHelper(livingViewer.posX, livingViewer.posY + livingViewer.getEyeHeight(), livingViewer.posZ);

        Vec3 lookVec = livingViewer.getLook(1.0F);
        Vec3 targetVec = eyesPos.addVector(lookVec.xCoord * range, lookVec.yCoord * range, lookVec.zCoord * range);

        double closestEntityDist = Double.MAX_VALUE;

        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(viewer, viewer.boundingBox.addCoord(lookVec.xCoord * range, lookVec.yCoord * range, lookVec.zCoord * range).expand(1.0D, 1.0D, 1.0D));

        for (Entity entity : entities) {
            if (entity.canBeCollidedWith() && entity instanceof EntityLivingBase && entity.isEntityAlive()) {
                float collisionBorderSize = entity.getCollisionBorderSize();
                AxisAlignedBB entityBB = entity.boundingBox.expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);

                MovingObjectPosition intercept = entityBB.calculateIntercept(eyesPos, targetVec);

                if (intercept != null) {
                    double dist = eyesPos.distanceTo(intercept.hitVec);
                    if (dist < closestEntityDist && dist <= range) {
                        closestEntityDist = dist;
                        target = (EntityLivingBase) entity;
                    }
                }
            }
        }
        return target;
    }

    /**
     * Проверяет, сфокусировано ли в данный момент какое-либо поле ввода на экране.
     * Работает как для стандартных GuiTextField, так и для кастомных полей ввода Fuctorize.
     * @return true, если игрок печатает в текстовом поле.
     */
    public static boolean isAnyTextFieldFocused() {
        GuiScreen currentScreen = mc.currentScreen;
        if (currentScreen == null) {
            return false;
        }

        // Проверяем поля текущего экрана и всех его родительских классов
        Class<?> screenClass = currentScreen.getClass();
        while (screenClass != null && screenClass != Object.class) {
            for (Field field : screenClass.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(currentScreen);

                    // Проверка на ванильный GuiTextField
                    if (fieldValue instanceof net.minecraft.client.gui.GuiTextField) {
                        if (((net.minecraft.client.gui.GuiTextField) fieldValue).isFocused()) {
                            return true;
                        }
                    }
                    // Проверка на наш кастомный GuiTextField
                    if (fieldValue instanceof ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField) {
                        if (((ru.fuctorial.fuctorize.client.gui.nbtedit.GuiTextField) fieldValue).isFocused()) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки доступа
                }
            }
            screenClass = screenClass.getSuperclass();
        }
        return false;
    }
}