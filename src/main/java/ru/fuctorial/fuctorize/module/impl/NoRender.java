package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S2APacketParticles;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.utils.Lang;

public class NoRender extends Module {

    public BooleanSetting items;  
    private BooleanSetting particles;
    private BooleanSetting explosions;
    private BooleanSetting fallingBlocks;

     
    public static int removedItemsCount = 0;

    public NoRender(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("norender", Lang.get("module.norender.name"), Category.RENDER);

        items = new BooleanSetting(Lang.get("module.norender.setting.items"), true);
        particles = new BooleanSetting(Lang.get("module.norender.setting.particles"), true);
        explosions = new BooleanSetting(Lang.get("module.norender.setting.explosions"), true);
        fallingBlocks = new BooleanSetting(Lang.get("module.norender.setting.falling_blocks"), false);

        addSetting(items);
        addSetting(particles);
        addSetting(explosions);
        addSetting(fallingBlocks);
        addSetting(new BindSetting(Lang.get("module.norender.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.norender.desc");
    }

    @Override
    public void onUpdate() {
        if (mc.theWorld == null) return;

         
        int currentTickItems = 0;

         
        if (items.enabled || fallingBlocks.enabled || explosions.enabled) {
            for (Object obj : mc.theWorld.loadedEntityList) {

                 
                if (items.enabled && obj instanceof EntityItem) {
                    EntityItem item = (EntityItem) obj;
                    if (!item.isDead) {
                        currentTickItems++;
                        item.setDead();
                    }
                }
                else if (fallingBlocks.enabled && obj instanceof EntityFallingBlock) {
                    ((EntityFallingBlock) obj).setDead();
                }
                else if (explosions.enabled && obj instanceof EntityTNTPrimed) {
                    ((EntityTNTPrimed) obj).setDead();
                }
            }
        }

         
        removedItemsCount = currentTickItems;
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.theWorld == null) return;

        if (particles.enabled && event.getPacket() instanceof S2APacketParticles) {
            event.setCanceled(true);
        }

        if (explosions.enabled && event.getPacket() instanceof S27PacketExplosion) {
            event.setCanceled(true);
        }

        if (event.getPacket() instanceof S0EPacketSpawnObject) {
            S0EPacketSpawnObject packet = (S0EPacketSpawnObject) event.getPacket();

             
             
             

             
            if (fallingBlocks.enabled && packet.func_149001_c() == 70) {
                event.setCanceled(true);
            }

             
            if (explosions.enabled && packet.func_149001_c() == 50) {
                event.setCanceled(true);
            }
        }
    }

    @Override
    public void onDisable() {
        removedItemsCount = 0;
    }
}