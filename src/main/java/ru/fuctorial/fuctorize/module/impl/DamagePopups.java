 
package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.hud.DamageIndicator;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.ModContainerHelper;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DamagePopups extends Module {

    private final Map<Integer, Float> healthMap = new ConcurrentHashMap<>();
    private final List<DamageIndicator> indicators = new CopyOnWriteArrayList<>();
    private final int MAX_DISTANCE = 32;
    private final DecimalFormat df = new DecimalFormat("0.0");

    public DamagePopups(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("damagepopups", Lang.get("module.damagepopups.name"), Category.RENDER);
        addSetting(new BindSetting(Lang.get("module.damagepopups.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.damagepopups.desc");
    }

    @Override
    public void onEnable() {
        ModContainerHelper.runWithFuctorizeContainer(() -> MinecraftForge.EVENT_BUS.register(this));
        healthMap.clear();
        indicators.clear();
    }

    @Override
    public void onDisable() {
        ModContainerHelper.runWithFuctorizeContainer(() -> MinecraftForge.EVENT_BUS.unregister(this));
        healthMap.clear();
        indicators.clear();
    }

    @Override
    public void onUpdate() {
        if (!isEnabled()) {
            if (!healthMap.isEmpty()) healthMap.clear();
            if (!indicators.isEmpty()) indicators.clear();
            return;
        }

        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        Iterator<Map.Entry<Integer, Float>> iterator = healthMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Float> entry = iterator.next();
            if (mc.theWorld.getEntityByID(entry.getKey()) == null) {
                iterator.remove();
            }
        }

        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityLivingBase)) continue;
            EntityLivingBase entity = (EntityLivingBase) obj;

            if (entity == mc.thePlayer || mc.thePlayer.getDistanceToEntity(entity) > MAX_DISTANCE) {
                healthMap.remove(entity.getEntityId());
                continue;
            }
            if (entity.isDead) {
                healthMap.remove(entity.getEntityId());
                continue;
            }

            float currentHealth = entity.getHealth();
            if (healthMap.containsKey(entity.getEntityId())) {
                float lastHealth = healthMap.get(entity.getEntityId());
                if (Math.abs(currentHealth - lastHealth) > 0.01f) {
                    float damage = lastHealth - currentHealth;
                    createOrUpdateIndicator(entity, damage);
                }
            }
            healthMap.put(entity.getEntityId(), currentHealth);
        }
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        if (event.entityPlayer == mc.thePlayer && event.target instanceof EntityLivingBase) {
            createOrUpdateIndicator((EntityLivingBase) event.target, 0);
        }
    }

    private void createOrUpdateIndicator(EntityLivingBase entity, float damage) {
        boolean isHeal = damage < 0;
        String damageText = (isHeal ? "§a+" : "§c-") + (Math.abs(damage) < 1 && Math.abs(damage) > 0 ? df.format(Math.abs(damage)) : (int)Math.abs(damage));

        float healthAfterDamage = entity.getHealth() + (isHeal ? Math.abs(damage) : 0);
        int currentHp = MathHelper.ceiling_float_int(healthAfterDamage);
        if (currentHp < 0) currentHp = 0;

        int maxHp = MathHelper.ceiling_float_int(entity.getMaxHealth());
        String hpText = "§f" + currentHp + " / " + maxHp;
        Color color = isHeal ? new Color(85, 255, 85) : new Color(255, 85, 85);

        for (DamageIndicator indicator : indicators) {
            if (indicator.entityId == entity.getEntityId()) {
                indicator.update(damageText, hpText, color.getRGB());
                return;
            }
        }

        IChatComponent nameComponent = entity.func_145748_c_();
        String entityName = EnumChatFormatting.getTextWithoutFormattingCodes(nameComponent.getFormattedText());
        if (indicators.size() > 10) indicators.remove(0);
        indicators.add(new DamageIndicator(entity.getEntityId(), entityName, damageText, hpText, color.getRGB(), 2000));
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (mc.thePlayer == null || indicators.isEmpty() || client.fontManager == null || !client.fontManager.isReady()) {
            return;
        }

        indicators.removeIf(DamageIndicator::isFinished);

        ScaledResolution sr = event.resolution;
        CustomFontRenderer nameFont = client.fontManager.regular_18;
        CustomFontRenderer damageFont = client.fontManager.bold_32;
        CustomFontRenderer hpFont = client.fontManager.regular_18;

        final float baseX = sr.getScaledWidth() / 2f;
        final float baseY = sr.getScaledHeight() / 2f - 100f;

        RenderUtils.begin2DRendering();

        for (DamageIndicator indicator : indicators) {
            double animFactor = indicator.getAnimationFactor();
            if (animFactor <= 0) continue;

            double verticalShift = (1.0 - animFactor) * -30.0;
            int alpha = (int) (255 * animFactor);
            if (alpha < 4) continue;

            float currentY = (float) (baseY + verticalShift);

            int finalDamageColor = (indicator.damageColor & 0x00FFFFFF) | (alpha << 24);
            int finalNameAndHpColor = (0x00FFFFFF) | (alpha << 24);

            String entityName = indicator.entityName;
            float nameX = baseX - nameFont.getStringWidth(entityName) / 2f;
            nameFont.drawString(entityName, nameX, currentY, finalNameAndHpColor);
            currentY += nameFont.getHeight() + 2;

            String damageText = indicator.damageText;
            String hpText = indicator.hpText;
            float damageAndHpWidth = damageFont.getStringWidth(damageText) + 8 + hpFont.getStringWidth(hpText);
            float damageX = baseX - damageAndHpWidth / 2f;
            damageFont.drawString(damageText, damageX, currentY, finalDamageColor);

            float hpX = damageX + damageFont.getStringWidth(damageText) + 8;
            float hpY = currentY + (damageFont.getHeight() - hpFont.getHeight()) / 2f;
            hpFont.drawString(hpText, hpX, hpY, finalNameAndHpColor);
        }

        RenderUtils.end2DRendering();
    }
}
