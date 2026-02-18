package ru.fuctorial.fuctorize.utils.pathfinding;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

 
public final class YMath {
    private YMath() {}

     
    public static final int FEET_OFFSET_FROM_GROUND = 1;
    public static final int HEAD_OFFSET_FROM_GROUND = 2;

     
     

     
    public static int groundFromPlayerPosY(double playerPosY_IGNORED) {
        EntityPlayer player = FMLClientHandler.instance().getClient().thePlayer;
        if (player == null) return 0;  
         
         
        return MathHelper.floor_double(player.boundingBox.minY - 0.01);
    }

     
    public static int feetFromPlayerPosY(double playerPosY_IGNORED) {
        EntityPlayer player = FMLClientHandler.instance().getClient().thePlayer;
        if (player == null) return 0;
        return MathHelper.floor_double(player.boundingBox.minY);
    }

     
    public static int headFromPlayerPosY(double playerPosY_IGNORED) {
        EntityPlayer player = FMLClientHandler.instance().getClient().thePlayer;
        if (player == null) return 1;
         
        return MathHelper.floor_double(player.boundingBox.minY) + 1;
    }

     
    public static int capFromPlayerPosY(double playerPosY_IGNORED) {
        EntityPlayer player = FMLClientHandler.instance().getClient().thePlayer;
        if (player == null) return 2;
         
        return MathHelper.floor_double(player.boundingBox.minY) + 2;
    }


     

     
    public static int feetFromGround(int groundY) {
        return groundY + FEET_OFFSET_FROM_GROUND;
    }

     
    public static int headFromGround(int groundY) {
        return groundY + HEAD_OFFSET_FROM_GROUND;
    }

     
    public static int groundAbove(int groundY) {
        return groundY + 1;
    }

     
    public static int groundBelow(int groundY) {
        return groundY - 1;
    }
}
