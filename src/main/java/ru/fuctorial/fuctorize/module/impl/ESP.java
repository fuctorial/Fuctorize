// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\module\impl\ESP.java
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.render.TagData;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.*;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.glu.Project;
import org.lwjgl.util.vector.Vector4f;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class ESP extends Module {

    private BooleanSetting players;
    private BooleanSetting mobs;
    private BooleanSetting animals;
    private BooleanSetting items;
    private BooleanSetting chests;
    private BooleanSetting enderChests;
    private BooleanSetting nameTags;
    private SliderSetting nameTagsScale;
    private BooleanSetting tagHealth;
    private BooleanSetting tagDistance;
    private BooleanSetting tagItemCount;

    private Class<?> npcClass = null;
    private final List<TagData> tagDataList = new ArrayList<>();
    private EntityLivingBase lastPlayerReference;
    // Cached projection state for NameTags per frame
    private final FloatBuffer ntModelview = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer ntProjection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer ntViewport = BufferUtils.createIntBuffer(16);
    private int ntScaleFactor = 1;
    private double ntCamX, ntCamY, ntCamZ;
    private Frustrum ntFrustrum;
    private long ntNow;
    private final Map<Integer, float[]> ntLastRect = new HashMap<>();
    private final Map<Integer, Long> ntLastSeen = new HashMap<>();
    private static final long NT_STALE_MS = 200L;

    public ESP(FuctorizeClient client) {
        super(client);
        try {
            npcClass = Class.forName("noppes.npcs.entity.EntityCustomNpc");
            System.out.println("Fuctorize/ESP: CustomNPCs mod detected.");
        } catch (ClassNotFoundException e) {
            System.out.println("Fuctorize/ESP: CustomNPCs mod not found, NPC ESP will be disabled.");
        }
    }

    @Override
    public void init() {
        setMetadata("esp", Lang.get("module.esp.name"), Category.RENDER);

        players = new BooleanSetting(Lang.get("module.esp.setting.players"), true);
        mobs = new BooleanSetting(Lang.get("module.esp.setting.hostile_mobs"), true);
        animals = new BooleanSetting(Lang.get("module.esp.setting.friendly_mobs"), false);
        items = new BooleanSetting(Lang.get("module.esp.setting.items"), false);
        chests = new BooleanSetting(Lang.get("module.esp.setting.chests"), true);
        enderChests = new BooleanSetting(Lang.get("module.esp.setting.ender_chests"), true);

        nameTags = new BooleanSetting(Lang.get("module.esp.setting.nametags"), true);
        nameTagsScale = new SliderSetting(Lang.get("module.esp.setting.nametags_scale"), 1.0, 0.5, 2.0, 0.05);
        tagHealth = new BooleanSetting(Lang.get("module.esp.setting.tag_health"), true);
        tagDistance = new BooleanSetting(Lang.get("module.esp.setting.tag_distance"), true);
        tagItemCount = new BooleanSetting(Lang.get("module.esp.setting.tag_item_count"), true);

        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(items);
        addSetting(chests);
        addSetting(enderChests);
        addSetting(new SeparatorSetting());
        addSetting(nameTags);
        addSetting(nameTagsScale);
        addSetting(tagHealth);
        addSetting(tagDistance);
        addSetting(tagItemCount);
        addSetting(new BindSetting(Lang.get("module.esp.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.esp.desc");
    }

    @Override
    public void onRender3D(RenderWorldLastEvent event) {
        if (mc.thePlayer != null) this.lastPlayerReference = mc.thePlayer;
        EntityLivingBase renderView = mc.renderViewEntity instanceof EntityLivingBase ? (EntityLivingBase) mc.renderViewEntity : lastPlayerReference;
        if (renderView == null || mc.theWorld == null) return;

        tagDataList.clear();

        // Prepare per-frame projection state for stable NameTags
        try {
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, ntModelview);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, ntProjection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, ntViewport);
        } catch (Throwable ignored) {}
        ntScaleFactor = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        ntCamX = RenderManager.instance.renderPosX;
        ntCamY = RenderManager.instance.renderPosY;
        ntCamZ = RenderManager.instance.renderPosZ;
        // Interpolated camera for frustum to minimize flicker
        {
            float pt = event.partialTicks;
            double lx = renderView.lastTickPosX;
            double ly = renderView.lastTickPosY;
            double lz = renderView.lastTickPosZ;
            double vx = lx + (renderView.posX - lx) * pt;
            double vy = ly + (renderView.posY - ly) * pt;
            double vz = lz + (renderView.posZ - lz) * pt;
            ntFrustrum = new Frustrum();
            ntFrustrum.setPosition(vx, vy, vz);
        }
        ntNow = System.currentTimeMillis();
        if (!ntLastSeen.isEmpty()) {
            ntLastSeen.entrySet().removeIf(e -> ntNow - e.getValue() > 1000L);
            ntLastRect.keySet().removeIf(k -> !ntLastSeen.containsKey(k));
        }

        RenderUtils.begin3DRendering();
        GL11.glLineWidth(1.5F);

        for (Object o : mc.theWorld.loadedEntityList) {
            Entity entity = (Entity) o;
            int color = getColorForEntity(entity, renderView);
            if (color != 0) {
                renderESPBox(entity.boundingBox, color);
                if (nameTags.enabled) {
                    calculateAndStoreTagData(entity, event.partialTicks, color, renderView);
                }
            }
        }

        for(Object o : mc.theWorld.loadedTileEntityList) {
            if (chests.enabled && o instanceof TileEntityChest) {
                TileEntityChest chest = (TileEntityChest) o;
                AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(chest.xCoord, chest.yCoord, chest.zCoord, chest.xCoord + 1, chest.yCoord + 1, chest.zCoord + 1);
                renderESPBox(bb, Colors.chestsColor.getColor());
            }
            if (enderChests.enabled && o instanceof TileEntityEnderChest) {
                TileEntityEnderChest chest = (TileEntityEnderChest) o;
                AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(chest.xCoord, chest.yCoord, chest.zCoord, chest.xCoord + 1, chest.yCoord + 1, chest.zCoord + 1);
                renderESPBox(bb, Colors.enderChestsColor.getColor());
            }
        }

        RenderUtils.end3DRendering();
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        client.nameTagRenderer.renderTagList(tagDataList);
    }

    private void calculateAndStoreTagData(Entity entity, float partialTicks, int espColor, EntityLivingBase renderView) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        Vector4f coords;

        if (entity instanceof EntityPlayer) {
            double w = Math.max(0.01, entity.width / 1.5);
            double h = entity.height + (((EntityPlayer) entity).isSneaking() ? -0.3 : 0.2);
            double minX = x - w * 0.5, minY = y, minZ = z - w * 0.5;
            double maxX = x + w * 0.5, maxY = y + h, maxZ = z + w * 0.5;
            double eps = 0.02; minX -= eps; minY -= eps; minZ -= eps; maxX += eps; maxY += eps; maxZ += eps;

            AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            if (ntFrustrum != null && !ntFrustrum.isBoundingBoxInFrustum(aabb)) return;

            float[] rect = projectAabbToGui(minX, minY, minZ, maxX, maxY, maxZ);
            if (rect == null) {
                rect = projectFallbackVertical(x, minY, maxY, z);
                if (rect == null) {
                    float[] prev = ntLastRect.get(entity.getEntityId());
                    Long t = ntLastSeen.get(entity.getEntityId());
                    if (prev != null && t != null && (ntNow - t) <= NT_STALE_MS) {
                        rect = prev;
                    } else {
                        return;
                    }
                }
            }
            ntLastRect.put(entity.getEntityId(), rect);
            ntLastSeen.put(entity.getEntityId(), ntNow);
            float cx = (rect[0] + rect[2]) * 0.5f;
            float topY = rect[1];
            coords = new Vector4f(cx, topY, 0.5f, 0f);
        } else {
            Vector4f p = RenderUtils.projectTo2D(x, y + entity.height + 0.4, z);
            if (p == null || p.z >= 1.0f) return;
            coords = p;
        }

        String nameLine = "";
        String infoLine = "";
        float healthPercent = -1;

        float distance = renderView.getDistanceToEntity(entity);
        List<String> infoParts = new ArrayList<>();

        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            nameLine = living.getCommandSenderName();
            if (tagHealth.enabled) {
                healthPercent = Math.max(0f, Math.min(1f, living.getHealth() / Math.max(1f, living.getMaxHealth())));
                infoParts.add(new DecimalFormat("0").format(living.getHealth()) + " / " + new DecimalFormat("0").format(living.getMaxHealth()));
            }
        } else if (entity instanceof EntityItem) {
            ItemStack stack = ((EntityItem) entity).getEntityItem();
            nameLine = stack.getDisplayName();
            if (tagItemCount.enabled) infoParts.add("x" + stack.stackSize);
        } else {
            nameLine = entity.getCommandSenderName();
        }

        if (tagDistance.enabled) {
            infoParts.add(new DecimalFormat("0.0").format(distance) + "m");
        }
        infoLine = String.join(" | ", infoParts);

        String stableKey = "E:" + entity.getEntityId();
        float scaleMul = (float) (nameTagsScale != null ? nameTagsScale.value : 1.0);
        tagDataList.add(new TagData(nameLine, infoLine, espColor, healthPercent, coords, distance, stableKey, scaleMul));
    }

    private float[] projectAabbToGui(double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ) {
        double[] xs = {minX, maxX};
        double[] ys = {minY, maxY};
        double[] zs = {minZ, maxZ};
        boolean any = false;
        float minSX = Float.POSITIVE_INFINITY, minSY = Float.POSITIVE_INFINITY;
        float maxSX = Float.NEGATIVE_INFINITY, maxSY = Float.NEGATIVE_INFINITY;

        FloatBuffer out = BufferUtils.createFloatBuffer(4);
        for (int ix = 0; ix < 2; ix++)
            for (int iy = 0; iy < 2; iy++)
                for (int iz = 0; iz < 2; iz++) {
                    double wx = xs[ix] - ntCamX;
                    double wy = ys[iy] - ntCamY;
                    double wz = zs[iz] - ntCamZ;
                    out.clear();
                    if (Project.gluProject((float) wx, (float) wy, (float) wz, ntModelview, ntProjection, ntViewport, out)) {
                        float z = out.get(2);
                        if (z >= 0f && z < 1f) {
                            float sx = out.get(0) / ntScaleFactor;
                            float sy = (mc.displayHeight - out.get(1)) / ntScaleFactor;
                            if (sx < minSX) minSX = sx;
                            if (sy < minSY) minSY = sy;
                            if (sx > maxSX) maxSX = sx;
                            if (sy > maxSY) maxSY = sy;
                            any = true;
                        }
                    }
                }
        if (!any) return null;
        if (!(minSX < maxSX && minSY < maxSY)) return null;
        return new float[]{minSX, minSY, maxSX, maxSY};
    }

    private float[] projectFallbackVertical(double cx, double minY, double maxY, double cz) {
        FloatBuffer out = BufferUtils.createFloatBuffer(4);
        double tx = cx - ntCamX, ty = maxY - ntCamY, tz = cz - ntCamZ;
        out.clear();
        boolean topOk = Project.gluProject((float) tx, (float) ty, (float) tz, ntModelview, ntProjection, ntViewport, out);
        float topZ = topOk ? out.get(2) : 2f;
        float topSX = 0f, topSY = 0f;
        if (topOk && topZ >= 0f && topZ < 1f) {
            topSX = out.get(0) / ntScaleFactor;
            topSY = (mc.displayHeight - out.get(1)) / ntScaleFactor;
        } else {
            return null;
        }
        out.clear();
        double bx = cx - ntCamX, by = minY - ntCamY, bz = cz - ntCamZ;
        boolean botOk = Project.gluProject((float) bx, (float) by, (float) bz, ntModelview, ntProjection, ntViewport, out);
        float botZ = botOk ? out.get(2) : 2f;
        float botSY;
        if (botOk && botZ >= 0f && botZ < 1f) {
            botSY = (mc.displayHeight - out.get(1)) / ntScaleFactor;
        } else {
            botSY = topSY + 35f;
        }
        float minSY = Math.min(topSY, botSY);
        float maxSY = Math.max(topSY, botSY);
        return new float[]{topSX - 0.5f, minSY, topSX + 0.5f, maxSY};
    }

    private int getColorForEntity(Entity entity, EntityLivingBase renderView) {
        if (entity == renderView) return 0;
        if (players.enabled && entity instanceof EntityPlayer) return Colors.playersColor.getColor();
        if (mobs.enabled && entity instanceof EntityMob) return Colors.mobsColor.getColor();
        if (animals.enabled && entity instanceof EntityAnimal) return Colors.animalsColor.getColor();
        if (items.enabled && entity instanceof EntityItem) return Colors.itemsColor.getColor();
        if (npcClass != null && npcClass.isInstance(entity)) return new Color(0xFF8C00).getRGB();
        return 0;
    }

    private void renderESPBox(AxisAlignedBB bb, int color) {
        AxisAlignedBB renderBB = bb.copy().offset(-RenderManager.instance.renderPosX, -RenderManager.instance.renderPosY, -RenderManager.instance.renderPosZ);
        RenderUtils.setColor(color, 0.15f);
        RenderUtils.drawFilledBoundingBox(renderBB);
        RenderUtils.setColor(color, 0.9f);
        RenderUtils.drawOutlinedBoundingBox(renderBB);
    }
}
