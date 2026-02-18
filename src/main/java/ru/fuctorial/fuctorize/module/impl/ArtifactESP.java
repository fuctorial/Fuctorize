 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.render.TagData;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.*;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector4f;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ArtifactESP extends Module {

    private static Class<?> tileEntityArtifactClass;
    private static Method isEmptyMethod;
    private static Method getSpotMethod;
    private static Class<?> artifactSpotClass;
    private static Method getItemMethod;
    private static boolean reflectionSuccessful = false;

    private BooleanSetting showArtifacts;
    private BooleanSetting showSpawns;
    private BooleanSetting showTags;
    private BooleanSetting tagDistance;
    private SliderSetting distance;

    private final DecimalFormat df = new DecimalFormat("0.0");
    private final List<TagData> artifactTags = new ArrayList<>();

    public ArtifactESP(FuctorizeClient client) {
        super(client);
        initializeReflection();
    }

    @Override
    public void init() {
        setMetadata("artifactesp", Lang.get("module.artifactesp.name"), Category.RENDER);

        showArtifacts = new BooleanSetting(Lang.get("module.artifactesp.setting.show_artifacts"), true);
        showSpawns = new BooleanSetting(Lang.get("module.artifactesp.setting.show_spawns"), true);
        showTags = new BooleanSetting(Lang.get("module.artifactesp.setting.show_tags"), true);
        tagDistance = new BooleanSetting(Lang.get("module.artifactesp.setting.tag_distance"), true);
        distance = new SliderSetting(Lang.get("module.artifactesp.setting.distance"), 64.0, 16.0, 256.0, 1.0);

        addSetting(showArtifacts);
        addSetting(showSpawns);
        addSetting(new SeparatorSetting());
        addSetting(showTags);
        addSetting(tagDistance);
        addSetting(new SeparatorSetting());
        addSetting(distance);
        addSetting(new BindSetting(Lang.get("module.artifactesp.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.artifactesp.desc");
    }

    private void initializeReflection() {
        if (reflectionSuccessful) return;
        try {
            tileEntityArtifactClass = Class.forName("org.rhino.stalker.core.common.block.tileentity.TileEntityArtifact");
            isEmptyMethod = tileEntityArtifactClass.getMethod("isEmpty");
            getSpotMethod = tileEntityArtifactClass.getMethod("getSpot");
            artifactSpotClass = Class.forName("org.rhino.stalker.core.common.world.artifact.ArtifactSpot");
            getItemMethod = artifactSpotClass.getMethod("getItem");
            reflectionSuccessful = true;
            System.out.println("Fuctorize/ArtifactESP: Successfully reflected Stalker Core classes!");
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
            System.err.println("Fuctorize/ArtifactESP: Stalker Core mod not found. Module will be inactive.");
            reflectionSuccessful = false;
        }
    }

    @Override
    public void onRender3D(RenderWorldLastEvent event) {
        if (!reflectionSuccessful || mc.thePlayer == null || mc.theWorld == null) return;

        artifactTags.clear();
        RenderUtils.begin3DRendering();
        GL11.glLineWidth(1.5F);

        for (Object o : mc.theWorld.loadedTileEntityList) {
            if (tileEntityArtifactClass.isInstance(o)) {
                TileEntity artifactTile = (TileEntity) o;
                if (mc.thePlayer.getDistance(artifactTile.xCoord, artifactTile.yCoord, artifactTile.zCoord) > distance.value) continue;
                try {
                    boolean isEmpty = (Boolean) isEmptyMethod.invoke(artifactTile);
                    if (isEmpty) {
                        if (showSpawns.enabled) renderESPBox(artifactTile, Colors.spawnColor.getColor());
                    } else {
                        if (showArtifacts.enabled) renderESPBox(artifactTile, Colors.artifactColor.getColor());
                    }
                    if (showTags.enabled) {
                        calculateAndStoreTagData(artifactTile, isEmpty);
                    }
                } catch (Exception ignored) {}
            }
        }
        RenderUtils.end3DRendering();
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (!reflectionSuccessful) return;
        client.nameTagRenderer.renderTagList(artifactTags);
    }

    private void calculateAndStoreTagData(TileEntity tile, boolean isEmpty) throws Exception {
        Vector4f coords = RenderUtils.projectTo2D(tile.xCoord + 0.5, tile.yCoord + 1.2, tile.zCoord + 0.5);
        if (coords != null && coords.z < 1.0f) {
            String nameLine;
            int mainColor;

            if (isEmpty) {
                nameLine = Lang.get("tag.artifactesp.empty_spawn");
                mainColor = Colors.spawnColor.getColor();
            } else {
                mainColor = Colors.artifactColor.getColor();
                try {
                    Object spot = getSpotMethod.invoke(tile);
                    Item item = (spot != null) ? (Item) getItemMethod.invoke(spot) : null;
                    nameLine = (item != null) ? item.getItemStackDisplayName(new ItemStack(item)) : Lang.get("tag.artifactesp.artifact");
                } catch (Exception e) {
                    nameLine = Lang.get("tag.artifactesp.artifact");
                }
            }

            float distanceVal = (float) mc.thePlayer.getDistance(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5);
            String infoLine = tagDistance.enabled ? df.format(distanceVal) + "m" : "";

            String stableKey = "T:" + tile.xCoord + "," + tile.yCoord + "," + tile.zCoord;
            artifactTags.add(new TagData(nameLine, infoLine, mainColor, -1, coords, distanceVal, stableKey));
        }
    }

    private void renderESPBox(TileEntity te, int color) {
        double x = te.xCoord - RenderManager.instance.renderPosX;
        double y = te.yCoord - RenderManager.instance.renderPosY;
        double z = te.zCoord - RenderManager.instance.renderPosZ;
        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
        RenderUtils.setColor(color, 0.15f);
        RenderUtils.drawFilledBoundingBox(bb);
        RenderUtils.setColor(color, 0.9f);
        RenderUtils.drawOutlinedBoundingBox(bb);
    }
}
