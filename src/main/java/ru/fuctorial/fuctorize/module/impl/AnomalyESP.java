 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.render.TagData;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.*;
import ru.fuctorial.fuctorize.utils.Lang;  
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.renderer.entity.RenderManager;
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

public class AnomalyESP extends Module {

    private static Class<?> tileEntityAnomalyClass;
    private static Class<?> anomalyClass;
    private static Method getAnomalyMethod;
    private static Method getNameMethod;
    private static Method getBoundsMethod;
    private static Method getStateMethod;
    private static Method getScaleMethod;
    private static boolean reflectionSuccessful = false;

    private BooleanSetting showAnomalies;
    private BooleanSetting showTags;
    private BooleanSetting tagState;
    private BooleanSetting tagDistance;
    private SliderSetting distance;

    private final DecimalFormat df = new DecimalFormat("0.0");
    private final List<TagData> anomalyTags = new ArrayList<>();

    public AnomalyESP(FuctorizeClient client) {
        super(client);
        initializeReflection();
    }

    @Override
    public void init() {
        setMetadata("anomalyesp", Lang.get("module.anomalyesp.name"), Category.RENDER);

        showAnomalies = new BooleanSetting(Lang.get("module.anomalyesp.setting.show_anomalies"), true);
        showTags = new BooleanSetting(Lang.get("module.anomalyesp.setting.show_tags"), true);
        tagState = new BooleanSetting(Lang.get("module.anomalyesp.setting.tag_state"), true);
        tagDistance = new BooleanSetting(Lang.get("module.anomalyesp.setting.tag_distance"), true);
        distance = new SliderSetting(Lang.get("module.anomalyesp.setting.distance"), 128.0, 16.0, 256.0, 1.0);

        addSetting(showAnomalies);
        addSetting(new SeparatorSetting());
        addSetting(showTags);
        addSetting(tagState);
        addSetting(tagDistance);
        addSetting(new SeparatorSetting());
        addSetting(distance);
        addSetting(new BindSetting(Lang.get("module.anomalyesp.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.anomalyesp.desc");
    }

    private void initializeReflection() {
        if (reflectionSuccessful) return;
        try {
            tileEntityAnomalyClass = Class.forName("org.rhino.stalker.anomaly.common.entity.TileEntityAnomaly");
            anomalyClass = Class.forName("org.rhino.stalker.anomaly.common.Anomaly");
            getAnomalyMethod = tileEntityAnomalyClass.getMethod("getAnomaly");
            getStateMethod = tileEntityAnomalyClass.getMethod("getState");
            getScaleMethod = tileEntityAnomalyClass.getMethod("getScale");
            getNameMethod = anomalyClass.getMethod("getName");
            getBoundsMethod = anomalyClass.getMethod("getBounds", double.class);
            reflectionSuccessful = true;
            System.out.println("Fuctorize/AnomalyESP: Successfully reflected Stalker Anomaly classes!");
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
            System.err.println("Fuctorize/AnomalyESP: Stalker Anomaly mod not found. Module will be inactive.");
            reflectionSuccessful = false;
        }
    }

    @Override
    public void onRender3D(RenderWorldLastEvent event) {
        if (!reflectionSuccessful || mc.thePlayer == null || mc.theWorld == null) return;

        anomalyTags.clear();
        RenderUtils.begin3DRendering();
        GL11.glLineWidth(1.5F);

        for (Object o : mc.theWorld.loadedTileEntityList) {
            if (tileEntityAnomalyClass.isInstance(o)) {
                TileEntity anomalyTile = (TileEntity) o;
                if (mc.thePlayer.getDistance(anomalyTile.xCoord, anomalyTile.yCoord, anomalyTile.zCoord) > distance.value) continue;

                try {
                    Object anomalyInstance = getAnomalyMethod.invoke(anomalyTile);
                    if (anomalyInstance == null) continue;
                    double scale = (Double) getScaleMethod.invoke(anomalyTile);
                    AxisAlignedBB bb = (AxisAlignedBB) getBoundsMethod.invoke(anomalyInstance, scale);
                    if (bb == null) continue;

                    double x = anomalyTile.xCoord - RenderManager.instance.renderPosX;
                    double y = anomalyTile.yCoord - RenderManager.instance.renderPosY;
                    double z = anomalyTile.zCoord - RenderManager.instance.renderPosZ;
                    AxisAlignedBB worldBB = bb.getOffsetBoundingBox(x, y, z);

                    if (showAnomalies.enabled) {
                        renderESPBox(worldBB, Colors.anomalyColor.getColor());  
                    }
                    if (showTags.enabled) {
                        calculateAndStoreTagData(anomalyTile, anomalyInstance, scale);
                    }
                } catch (Exception ignored) {}
            }
        }

        RenderUtils.end3DRendering();
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (!reflectionSuccessful) return;
        client.nameTagRenderer.renderTagList(anomalyTags);
    }

    private void calculateAndStoreTagData(TileEntity tile, Object anomalyInstance, double scale) throws Exception {
        AxisAlignedBB bb = (AxisAlignedBB) getBoundsMethod.invoke(anomalyInstance, scale);
        Vector4f coords = RenderUtils.projectTo2D(tile.xCoord + 0.5, tile.yCoord + bb.maxY + 0.3, tile.zCoord + 0.5);

        if (coords != null && coords.z < 1.0f) {
            Enum<?> stateEnum = (Enum<?>) getStateMethod.invoke(tile);
            String stateName = stateEnum != null ? stateEnum.name() : Lang.get("tag.anomalyesp.unknown_state");

            String nameLine = (String) getNameMethod.invoke(anomalyInstance);
            List<String> infoParts = new ArrayList<>();
            String stateFormatted = stateName.substring(0, 1).toUpperCase() + stateName.substring(1).toLowerCase();

            if (tagState.enabled) infoParts.add(stateFormatted);

            float distanceVal = (float) mc.thePlayer.getDistance(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5);
            if (tagDistance.enabled) infoParts.add(df.format(distanceVal) + "m");

            String infoLine = String.join(" | ", infoParts);

            String stableKey = "T:" + tile.xCoord + "," + tile.yCoord + "," + tile.zCoord;
            anomalyTags.add(new TagData(nameLine, infoLine, Colors.anomalyColor.getColor(), -1, coords, distanceVal, stableKey));
        }
    }

    private void renderESPBox(AxisAlignedBB bb, int color) {
        RenderUtils.setColor(color, 0.15f);
        RenderUtils.drawFilledBoundingBox(bb);
        RenderUtils.setColor(color, 0.9f);
        RenderUtils.drawOutlinedBoundingBox(bb);
    }
}
