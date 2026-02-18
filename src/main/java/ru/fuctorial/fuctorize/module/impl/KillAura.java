 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.RotationUtils;
import ru.fuctorial.fuctorize.utils.TimerUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {

     
    private SliderSetting range;
    private SliderSetting cps;
    private BooleanSetting autoBlock;
    private BooleanSetting rayTrace;
    private BooleanSetting players;
    private BooleanSetting mobs;
    private BooleanSetting animals;
    private BooleanSetting customNpcs;
    private BooleanSetting swing;
    private BooleanSetting aimbot;

     
    private EntityLivingBase target;
    private final TimerUtils attackTimer = new TimerUtils();
    private boolean isBlocking = false;
    private Class<?> npcClass = null;

    public KillAura(FuctorizeClient client) {
        super(client);
        try {
            npcClass = Class.forName("noppes.npcs.entity.EntityNPCInterface");
        } catch (ClassNotFoundException e) {
             
        }
    }

    @Override
    public void init() {
        setMetadata("killaura", Lang.get("module.killaura.name"), Category.COMBAT);

        range = new SliderSetting(Lang.get("module.killaura.setting.attack_range"), 4.2, 3.0, 6.0, 0.1);
        cps = new SliderSetting(Lang.get("module.killaura.setting.cps"), 10.0, 1.0, 20.0, 0.5);
        autoBlock = new BooleanSetting(Lang.get("module.killaura.setting.auto_block"), true);
        rayTrace = new BooleanSetting(Lang.get("module.killaura.setting.raytrace"), false);
        swing = new BooleanSetting(Lang.get("module.killaura.setting.swing_animation"), true);
        aimbot = new BooleanSetting(Lang.get("module.killaura.setting.aimbot"), true);
        players = new BooleanSetting(Lang.get("module.killaura.setting.players"), true);
        mobs = new BooleanSetting(Lang.get("module.killaura.setting.hostile_mobs"), false);
        animals = new BooleanSetting(Lang.get("module.killaura.setting.friendly_mobs"), false);
        customNpcs = new BooleanSetting(Lang.get("module.killaura.setting.customnpc"), false);

        addSetting(range);
        addSetting(cps);
        addSetting(autoBlock);
        addSetting(rayTrace);
        addSetting(swing);
        addSetting(aimbot);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(customNpcs);
        addSetting(new BindSetting(Lang.get("module.killaura.setting.bind"), Keyboard.KEY_R));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.killaura.desc");
    }

    @Override
    public void onEnable() {
        target = null;
        isBlocking = false;
    }

    @Override
    public void onDisable() {
        stopBlocking();
        target = null;
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        target = findTarget();

        if (target == null) {
            stopBlocking();
            return;
        }

        if (aimbot.enabled) {
            RotationUtils.faceEntity(target, 180f);
        }

        long delay = (long) (1000 / cps.value);
        if (attackTimer.hasReached(delay)) {
            performAttack(target);
            attackTimer.reset();
        }

        startBlocking();
    }

    private void performAttack(EntityLivingBase target) {
        stopBlocking();

        if(aimbot.enabled) {
            performStandardAttack(target);
        } else {
            performSilentAttack(target);
        }

        startBlocking();
    }

    private void performStandardAttack(EntityLivingBase target){
        if (swing.enabled) mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, target);
    }

    private void performSilentAttack(EntityLivingBase target) {
        float[] rotations = RotationUtils.getRotations(target);
        if (rotations != null) {
            NetUtils.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(rotations[0], rotations[1], mc.thePlayer.onGround));
            if (swing.enabled) mc.thePlayer.swingItem();
            NetUtils.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        }
    }

    private EntityLivingBase findTarget() {
        List<EntityLivingBase> potentialTargets = new ArrayList<>();
        for (Object entityObject : mc.theWorld.loadedEntityList) {
            if (entityObject instanceof EntityLivingBase) {
                EntityLivingBase entity = (EntityLivingBase) entityObject;
                if (isValidTarget(entity)) {
                    potentialTargets.add(entity);
                }
            }
        }

        if (potentialTargets.isEmpty()) {
            return null;
        }

        potentialTargets.sort(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceSqToEntity(e)));

        return potentialTargets.get(0);
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == mc.thePlayer || entity.isDead || entity.getHealth() <= 0) {
            return false;
        }

        double effectiveRange = range.value;
        if (entity.getDistanceSqToEntity(mc.thePlayer) > effectiveRange * effectiveRange) {
            return false;
        }

        if (rayTrace.enabled && !mc.thePlayer.canEntityBeSeen(entity)) {
            return false;
        }

        if (entity instanceof EntityPlayer && players.enabled) {
            return true;
        }
        if ((entity instanceof EntityMob) && mobs.enabled) {
            return true;
        }
        if ((entity instanceof EntityAnimal) && animals.enabled) {
            return true;
        }
        if (npcClass != null && npcClass.isInstance(entity) && customNpcs.enabled) {
            return true;
        }
        return false;
    }

    private void startBlocking() {
        if (autoBlock.enabled && mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
            if (!isBlocking) {
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
                isBlocking = true;
            }
        }
    }

    private void stopBlocking() {
        if (isBlocking) {
            isBlocking = false;
        }
    }
}