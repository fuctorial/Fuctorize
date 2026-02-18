package ru.fuctorial.fuctorize.utils;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.ReflectionHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

public class ReflectionUtils {

    private static Field landMovementFactorField;
    private static Field jumpMovementFactorField;
    private static Class<?> serverClass;
    private static Method fillBufferMethod;
    private static Method channelRead0Method;
    private static Field isInWebField;
    private static Field currentGameTypeField;

    static {
         
        try {
            landMovementFactorField = ReflectionHelper.findField(EntityLivingBase.class, "landMovementFactor", "field_70741_aB");
            landMovementFactorField.setAccessible(true);
        } catch (Exception ex) {
            System.err.println("Fuctorize/Reflection: Failed to find 'landMovementFactor' field!");
        }
        try {
            jumpMovementFactorField = ReflectionHelper.findField(EntityLivingBase.class, "jumpMovementFactor", "field_70773_bE");
            jumpMovementFactorField.setAccessible(true);
        } catch (Exception ex) {
            System.err.println("Fuctorize/Reflection: Failed to find 'jumpMovementFactor' field!");
        }

         
        try {
            isInWebField = ReflectionHelper.findField(Entity.class, "isInWeb", "field_70134_J");
            isInWebField.setAccessible(true);
        } catch (Exception ex) {
            System.err.println("Fuctorize/Reflection: Failed to find 'isInWeb' field!");
        }

         
        try {
            serverClass = Class.forName("noppes.npcs.Server");
            fillBufferMethod = serverClass.getDeclaredMethod("fillBuffer", ByteBuf.class, Enum.class, Object[].class);
            fillBufferMethod.setAccessible(true);
            System.out.println("Fuctorize/Reflection: Successfully reflected CustomNPCs Server class and methods!");
        } catch (Exception e) {
            System.err.println("Fuctorize/Reflection: Failed to find CustomNPCs Server class. NPCInteract might not work correctly.");
            serverClass = null;
            fillBufferMethod = null;
        }

         
        try {
            channelRead0Method = NetworkManager.class.getDeclaredMethod("channelRead0", ChannelHandlerContext.class, Packet.class);
            channelRead0Method.setAccessible(true);
            System.out.println("Fuctorize/Reflection: Successfully reflected NetworkManager.channelRead0 method!");
        } catch (Exception e) {
            System.err.println("Fuctorize/Reflection: CRITICAL - Could not find 'channelRead0' in NetworkManager. Receiving packets manually will not work.");
        }

         
        try {
             
            currentGameTypeField = ReflectionHelper.findField(net.minecraft.client.multiplayer.PlayerControllerMP.class, "currentGameType", "field_78779_k");
            currentGameTypeField.setAccessible(true);
            System.out.println("Fuctorize/Reflection: Successfully reflected PlayerControllerMP.currentGameType field!");
        } catch (Exception e) {
            System.err.println("Fuctorize/Reflection: CRITICAL - Failed to find 'currentGameType' field! FakeCreative module may not work correctly.");
        }
    }

    public static void setLandMovementFactor(EntityLivingBase entity, float value) {
        if (landMovementFactorField != null) {
            try {
                landMovementFactorField.setFloat(entity, value);
            } catch (IllegalAccessException e) {   }
        }
    }

     
    public static net.minecraft.world.WorldSettings.GameType getCurrentGameType(net.minecraft.client.multiplayer.PlayerControllerMP controller) {
        if (currentGameTypeField != null) {
            try {
                return (net.minecraft.world.WorldSettings.GameType) currentGameTypeField.get(controller);
            } catch (Exception e) {
                 
            }
        }
         
        return net.minecraft.world.WorldSettings.GameType.SURVIVAL;
    }

    public static void setJumpMovementFactor(EntityLivingBase entity, float value) {
        if (jumpMovementFactorField != null) {
            try {
                jumpMovementFactorField.setFloat(entity, value);
            } catch (IllegalAccessException e) {   }
        }
    }

     
    public static void setInWeb(Entity entity, boolean value) {
        if (isInWebField != null) {
            try {
                isInWebField.setBoolean(entity, value);
            } catch (IllegalAccessException e) {
                 
            }
        }
    }

    public static FMLProxyPacket createNpcsPacket(Enum<?> packetType, Object... obs) {
        if (fillBufferMethod == null) return null;
        ByteBuf buffer = Unpooled.buffer();
        try {
            boolean success = (boolean) fillBufferMethod.invoke(null, buffer, packetType, obs);
            if (!success) return null;
            return new FMLProxyPacket(buffer, "CustomNPCs");
        } catch (Exception e) {
            return null;
        }
    }

    public static FMLProxyPacket createNpcsPlayerPacket(Enum<?> packetType, Object... obs) {
        if (fillBufferMethod == null) return null;
        ByteBuf buffer = Unpooled.buffer();
        try {
            boolean success = (boolean) fillBufferMethod.invoke(null, buffer, packetType, obs);
            if (!success) return null;
            return new FMLProxyPacket(buffer, "CustomNPCsPlayer");
        } catch (Exception e) {
            return null;
        }
    }

    public static void receivePacket(NetworkManager netManager, Packet packet) {
        if (channelRead0Method == null) {
            System.err.println("Fuctorize/Reflection: Cannot receive packet, channelRead0 method not found.");
            return;
        }
        try {
            channelRead0Method.invoke(netManager, null, packet);
        } catch (Exception e) {
            System.err.println("Fuctorize/Reflection: Failed to invoke channelRead0.");
            e.printStackTrace();
        }
    }
}