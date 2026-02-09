package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.Random;

public class AimAssist extends Module {

    private SliderSetting range;
    private SliderSetting fov;
    private SliderSetting speed;
    private SliderSetting hitChance;

    private BooleanSetting clickOnly;
    private BooleanSetting rayTraceCheck;

    private BooleanSetting players;
    private BooleanSetting mobs;
    private BooleanSetting animals;
    private BooleanSetting customNpcs;

    private final Random random = new Random();
    private Class<?> npcClass = null;

    private boolean isAssistAllowed = true;
    private long lastChanceCheck = 0;
    private static final long CHANCE_RESET_DELAY_MS = 250;

    public AimAssist(FuctorizeClient client) {
        super(client);
        try {
            npcClass = Class.forName("noppes.npcs.entity.EntityCustomNpc");
        } catch (ClassNotFoundException ignored) {}
    }

    @Override
    public void init() {
        setMetadata("aimassist", Lang.get("module.aimassist.name"), Category.COMBAT);

        range = new SliderSetting(Lang.get("module.aimassist.setting.range"), 6.0, 1.0, 128.0, 0.5); // Уменьшил дефолт для легита
        fov = new SliderSetting(Lang.get("module.aimassist.setting.fov"), 60.0, 5.0, 180.0, 5.0);
        speed = new SliderSetting(Lang.get("module.aimassist.setting.speed"), 6.0, 1.0, 40.0, 0.5);
        hitChance = new SliderSetting(Lang.get("module.aimassist.setting.hitchance"), 100.0, 1.0, 100.0, 1.0);

        clickOnly = new BooleanSetting(Lang.get("module.aimassist.setting.click_only"), true);
        rayTraceCheck = new BooleanSetting(Lang.get("module.aimassist.setting.visible_only"), true);

        players = new BooleanSetting(Lang.get("module.aimassist.setting.players"), true);
        mobs = new BooleanSetting(Lang.get("module.aimassist.setting.hostile_mobs"), true);
        animals = new BooleanSetting(Lang.get("module.aimassist.setting.friendly_mobs"), false);
        customNpcs = new BooleanSetting(Lang.get("module.aimassist.setting.customnpc"), true);

        addSetting(range);
        addSetting(fov);
        addSetting(speed);
        addSetting(hitChance);
        addSetting(clickOnly);
        addSetting(rayTraceCheck);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(customNpcs);
        addSetting(new BindSetting(Lang.get("module.aimassist.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.aimassist.desc");
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;

        // 1. Проверка клика
        if (clickOnly.enabled && !Mouse.isButtonDown(0)) {
            return;
        }


        // 3. Поиск цели
        EntityLivingBase target = getBestTarget(range.value, fov.value);
        if (target == null) return;

        // --- УДАЛЕНО: Проверка mc.objectMouseOver ---
        // Раньше тут был код, который выключал аим, если прицел уже на сущности.
        // Это мешало доводке до центра хитбокса. Теперь аим работает всегда, пока цель в FOV.

        // 4. Логика Шанса
        if (System.currentTimeMillis() - lastChanceCheck > CHANCE_RESET_DELAY_MS) {
            isAssistAllowed = (random.nextInt(100) < hitChance.value);
            lastChanceCheck = System.currentTimeMillis();
        }

        if (!isAssistAllowed) {
            return;
        }

        // 5. Доводка
        aimAt(target);
    }

    private void aimAt(EntityLivingBase target) {
        float[] rotations = getRotations(target);
        float targetYaw = rotations[0];
        float targetPitch = rotations[1];

        float currentYaw = mc.thePlayer.rotationYaw;
        float currentPitch = mc.thePlayer.rotationPitch;

        // Нормализуем разницу углов
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);

        float maxStep = (float) speed.value;
        float randomSpeedFactor = 0.9f + (random.nextFloat() * 0.2f);
        maxStep *= randomSpeedFactor;

        // Ограничиваем скорость поворота
        float yawChange = MathHelper.clamp_float(yawDiff, -maxStep, maxStep);
        float pitchChange = MathHelper.clamp_float(pitchDiff, -maxStep, maxStep);

        mc.thePlayer.rotationYaw += yawChange;
        mc.thePlayer.rotationPitch += pitchChange;

        // Небольшое дрожание для реализма
        float jitter = 0.05f;
        if (Math.abs(yawDiff) > maxStep || Math.abs(pitchDiff) > maxStep) {
            mc.thePlayer.rotationYaw += (random.nextFloat() - 0.5f) * jitter;
            mc.thePlayer.rotationPitch += (random.nextFloat() - 0.5f) * jitter;
        }
    }

    private EntityLivingBase getBestTarget(double rangeVal, double fovVal) {
        EntityLivingBase bestTarget = null;
        double minAngle = Double.MAX_VALUE;

        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityLivingBase)) continue;
            EntityLivingBase entity = (EntityLivingBase) o;

            if (!isValid(entity, rangeVal)) continue;

            float[] rotations = getRotations(entity);
            float yawDiff = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - rotations[0]);
            float pitchDiff = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationPitch - rotations[1]);

            // Считаем общее расстояние от центра прицела до цели
            double angleDist = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

            // fovVal/2, так как FOV считается от центра в обе стороны (60 FOV = 30 влево, 30 вправо)
            if (angleDist <= (fovVal / 2.0) && angleDist < minAngle) {
                minAngle = angleDist;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }

    private boolean isValid(EntityLivingBase entity, double rangeVal) {
        if (entity == mc.thePlayer) return false;
        if (entity.isDead || entity.getHealth() <= 0) return false;

        // Проверка дистанции
        if (mc.thePlayer.getDistanceToEntity(entity) > rangeVal) return false;

        // Проверка видимости
        if (rayTraceCheck.enabled && !mc.thePlayer.canEntityBeSeen(entity)) return false;

        // Проверка типа сущности
        if (entity instanceof EntityPlayer) {
            // Игнорируем фейкового игрока из FreeCam
            if (entity.getEntityId() == -100) return false;
            return players.enabled;
        }
        if (entity instanceof EntityMob) return mobs.enabled;
        if (entity instanceof EntityAnimal) return animals.enabled;
        if (npcClass != null && npcClass.isInstance(entity)) return customNpcs.enabled;

        return false;
    }

    private float[] getRotations(EntityLivingBase entity) {
        double diffX = entity.posX - mc.thePlayer.posX;
        double diffZ = entity.posZ - mc.thePlayer.posZ;

        // Целимся не в ноги, а чуть ниже глаз (грудь/шея) - это оптимально для PvP
        double entityEyeY = entity.posY + entity.getEyeHeight();
        double myEyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double diffY = (entity.posY + (entity.getEyeHeight() * 0.75)) - myEyeY;

        double dist = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(diffY, dist) * 180.0D / Math.PI);

        return new float[]{yaw, pitch};
    }
}