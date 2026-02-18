package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.RenderUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class EntityCounter extends Module {

    private SliderSetting range;
    private BooleanSetting showPlayers;
    private BooleanSetting showItems;
    private BooleanSetting showMobs;
    private BooleanSetting showNpcs;
    private BooleanSetting showTiles;

    private int countPlayers = 0;
    private int countItems = 0;
    private int countMobs = 0;
    private int countAnimals = 0;
    private int countNpcs = 0;
    private int countTiles = 0;

    private Class<?> npcClass;

    public EntityCounter(FuctorizeClient client) {
        super(client);
        try {
            npcClass = Class.forName("noppes.npcs.entity.EntityNPCInterface");
        } catch (ClassNotFoundException ignored) {}
    }

    @Override
    public void init() {
        setMetadata("entitycounter", Lang.get("module.entitycounter.name"), Category.RENDER);

        range = new SliderSetting(Lang.get("module.entitycounter.setting.range"), 100.0, 10.0, 500.0, 10.0);
        showPlayers = new BooleanSetting(Lang.get("module.entitycounter.setting.players"), true);
        showItems = new BooleanSetting(Lang.get("module.entitycounter.setting.items"), true);
        showMobs = new BooleanSetting(Lang.get("module.entitycounter.setting.mobs"), true);
        showNpcs = new BooleanSetting(Lang.get("module.entitycounter.setting.npcs"), true);
        showTiles = new BooleanSetting(Lang.get("module.entitycounter.setting.tiles"), false);

        addSetting(range);
        addSetting(showPlayers);
        addSetting(showItems);
        addSetting(showMobs);
        addSetting(showNpcs);
        addSetting(showTiles);
        addSetting(new BindSetting(Lang.get("module.entitycounter.setting.bind"), Keyboard.KEY_NONE));

        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.entitycounter.desc");
    }

    @Override
    public void onUpdate() {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        int p = 0, i = 0, m = 0, a = 0, n = 0, t = 0;
        double rSq = range.value * range.value;

         
         
        NoRender noRender = (NoRender) client.moduleManager.getModuleByKey("norender");
        boolean isNoRenderItemsActive = noRender != null && noRender.isEnabled() && noRender.items.enabled;

         
        if (isNoRenderItemsActive) {
            i = NoRender.removedItemsCount;
        }

        for (Object obj : mc.theWorld.loadedEntityList) {
            Entity entity = (Entity) obj;
            if (entity == mc.thePlayer) continue;

             
            if (isNoRenderItemsActive && entity instanceof EntityItem) continue;

            if (mc.thePlayer.getDistanceSqToEntity(entity) > rSq) continue;

            if (entity instanceof EntityPlayer) {
                p++;
            } else if (npcClass != null && npcClass.isInstance(entity)) {
                n++;
            } else if (!isNoRenderItemsActive && (entity instanceof EntityItem || entity instanceof EntityXPOrb)) {
                i++;
            } else if (entity instanceof EntityMob) {
                m++;
            } else if (entity instanceof EntityAnimal) {
                a++;
            }
        }

        if (showTiles.enabled) {
            for (Object obj : mc.theWorld.loadedTileEntityList) {
                TileEntity tile = (TileEntity) obj;
                if (mc.thePlayer.getDistanceSq(tile.xCoord, tile.yCoord, tile.zCoord) <= rSq) {
                    t++;
                }
            }
        }

        this.countPlayers = p;
        this.countItems = i;
        this.countMobs = m;
        this.countAnimals = a;
        this.countNpcs = n;
        this.countTiles = t;
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;

        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;
        List<String> lines = new ArrayList<>();

        if (showPlayers.enabled) lines.add("Players: " + getColorForCount(countPlayers, 10, 50) + countPlayers);
        if (showNpcs.enabled && countNpcs > 0) lines.add("NPCs: " + "\u00A7e" + countNpcs);

         
        NoRender noRender = (NoRender) client.moduleManager.getModuleByKey("norender");
        String itemSuffix = (noRender != null && noRender.isEnabled() && noRender.items.enabled) ? " (Global)" : "";

        if (showItems.enabled) lines.add("Items" + itemSuffix + ": " + getColorForCount(countItems, 500, 2000) + countItems);

        if (showMobs.enabled) lines.add("Mobs: " + "\u00A77" + (countMobs + countAnimals));
        if (showTiles.enabled) lines.add("Tiles: " + "\u00A7b" + countTiles);

        if (lines.isEmpty()) return;

        ScaledResolution sr = event.resolution;
        float startX = 5;
        float startY = sr.getScaledHeight() / 2.0f - (lines.size() * 12) / 2.0f;

        float maxWidth = 0;
        for (String s : lines) {
            float w = font.getStringWidth(s);
            if (w > maxWidth) maxWidth = w;
        }

        RenderUtils.drawRect(startX - 2, startY - 2, startX + maxWidth + 2, startY + lines.size() * 12 + 2, 0x90000000);

        for (String line : lines) {
            font.drawString(line, startX, startY, -1);
            startY += 12;
        }
    }

    private String getColorForCount(int count, int warn, int danger) {
        if (count >= danger) return "\u00A7c";
        if (count >= warn) return "\u00A7e";
        return "\u00A7a";
    }
}