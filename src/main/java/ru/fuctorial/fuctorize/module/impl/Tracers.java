// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\module\impl\Tracers.java (ИЗМЕНЕННЫЙ)
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.*;
import ru.fuctorial.fuctorize.utils.Lang; // <- Импорт
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class Tracers extends Module {

    private BooleanSetting players;
    private BooleanSetting mobs;
    private BooleanSetting animals;
    private BooleanSetting items;
    private BooleanSetting chests;
    private BooleanSetting enderChests;
    private SliderSetting lineWidth;
    private ModeSetting lineOrigin;

    private Class<?> npcClass = null;
    private Entity lastPlayerReference;

    public Tracers(FuctorizeClient client) {
        super(client);
        try {
            npcClass = Class.forName("noppes.npcs.entity.EntityCustomNpc");
            System.out.println("Fuctorize/Tracers: CustomNPCs mod detected.");
        } catch (ClassNotFoundException e) {
            System.out.println("Fuctorize/Tracers: CustomNPCs mod not found, NPC tracers will be disabled.");
        }
    }

    @Override
    public void init() {
        setMetadata("tracers", Lang.get("module.tracers.name"), Category.RENDER);

        players = new BooleanSetting(Lang.get("module.tracers.setting.players"), true);
        mobs = new BooleanSetting(Lang.get("module.tracers.setting.hostile_mobs"), true);
        animals = new BooleanSetting(Lang.get("module.tracers.setting.friendly_mobs"), false);
        items = new BooleanSetting(Lang.get("module.tracers.setting.items"), false);
        chests = new BooleanSetting(Lang.get("module.tracers.setting.chests"), true);
        enderChests = new BooleanSetting(Lang.get("module.tracers.setting.ender_chests"), true);
        lineWidth = new SliderSetting(Lang.get("module.tracers.setting.line_width"), 1.5, 0.5, 5.0, 0.1);
        lineOrigin = new ModeSetting(Lang.get("module.tracers.setting.line_origin"), Lang.get("module.tracers.setting.origin.center"), Lang.get("module.tracers.setting.origin.center"), Lang.get("module.tracers.setting.origin.feet"));

        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(items);
        addSetting(chests);
        addSetting(enderChests);
        addSetting(new SeparatorSetting());
        addSetting(lineWidth);
        addSetting(lineOrigin);
        addSetting(new BindSetting(Lang.get("module.tracers.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.tracers.desc");
    }

    @Override
    public void onRender3D(RenderWorldLastEvent event) {
        if (mc.thePlayer != null) this.lastPlayerReference = mc.thePlayer;
        Entity renderView = mc.renderViewEntity != null ? mc.renderViewEntity : lastPlayerReference;
        if (renderView == null || mc.theWorld == null) return;

        RenderUtils.begin3DRendering();
        GL11.glLineWidth((float) lineWidth.value);

        for (Object o : mc.theWorld.loadedEntityList) {
            Entity entity = (Entity) o;
            int color = getColorForEntity(entity, renderView);
            if (color != 0) {
                drawTracerLine(entity, color, event.partialTicks);
            }
        }

        for(Object o : mc.theWorld.loadedTileEntityList) {
            if (chests.enabled && o instanceof TileEntityChest) {
                TileEntityChest chest = (TileEntityChest) o;
                drawTracerLineToPos(chest.xCoord + 0.5, chest.yCoord + 0.5, chest.zCoord + 0.5, Colors.chestsColor.getColor(), event.partialTicks);
            }
            if (enderChests.enabled && o instanceof TileEntityEnderChest) {
                TileEntityEnderChest chest = (TileEntityEnderChest) o;
                drawTracerLineToPos(chest.xCoord + 0.5, chest.yCoord + 0.5, chest.zCoord + 0.5, Colors.enderChestsColor.getColor(), event.partialTicks);
            }
        }

        RenderUtils.end3DRendering();
    }

    private void drawTracerLine(Entity entity, int color, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks + entity.height / 2.0F;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        drawTracerLineToPos(x, y, z, color, partialTicks);
    }

    private void drawTracerLineToPos(double x, double y, double z, int color, float partialTicks) {
        double renderX = x - RenderManager.renderPosX;
        double renderY = y - RenderManager.renderPosY;
        double renderZ = z - RenderManager.renderPosZ;

        double startX, startY, startZ;
        Entity renderViewEntity = mc.renderViewEntity;

        boolean isFirstPerson = mc.gameSettings.thirdPersonView == 0;

        if (isFirstPerson && lineOrigin.isMode("From Center")) {
            startX = startY = startZ = 0;
        } else {
            double viewerX = renderViewEntity.lastTickPosX + (renderViewEntity.posX - renderViewEntity.lastTickPosX) * partialTicks;
            double viewerY = renderViewEntity.lastTickPosY + (renderViewEntity.posY - renderViewEntity.lastTickPosY) * partialTicks;
            double viewerZ = renderViewEntity.lastTickPosZ + (renderViewEntity.posZ - renderViewEntity.lastTickPosZ) * partialTicks;
            double originY = lineOrigin.isMode("From Feet") ? viewerY : viewerY + renderViewEntity.getEyeHeight();
            startX = viewerX - RenderManager.renderPosX;
            startY = originY - RenderManager.renderPosY;
            startZ = viewerZ - RenderManager.renderPosZ;
        }

        RenderUtils.setColor(color, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(startX, startY, startZ);
        GL11.glVertex3d(renderX, renderY, renderZ);
        GL11.glEnd();
    }

    private int getColorForEntity(Entity entity, Entity renderView) {
        if (entity == renderView) return 0;
        if (players.enabled && entity instanceof EntityPlayer) return Colors.playersColor.getColor();
        if (mobs.enabled && entity instanceof EntityMob) return Colors.mobsColor.getColor();
        if (animals.enabled && entity instanceof EntityAnimal) return Colors.animalsColor.getColor();
        if (items.enabled && entity instanceof EntityItem) return Colors.itemsColor.getColor();
        if (npcClass != null && npcClass.isInstance(entity)) return new Color(0xFF8C00).getRGB();
        return 0;
    }
}
