package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.render.TagData;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.SeparatorSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector4f;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class RandomBoxESP extends Module {

    private static Class<?> targetBlockClass;
    private static boolean reflectionSuccessful = false;

    private BooleanSetting showBoxes;
    private BooleanSetting showTags;
    private BooleanSetting tagDistance;
    private SliderSetting distance;

    private final DecimalFormat df = new DecimalFormat("0.0");
    private final List<TagData> boxTags = new ArrayList<>();

    public RandomBoxESP(FuctorizeClient client) {
        super(client);
        initializeReflection();
    }

    @Override
    public void init() {
        setMetadata("randomboxesp", Lang.get("module.randomboxesp.name"), Category.RENDER);

        showBoxes = new BooleanSetting(Lang.get("module.randomboxesp.setting.show_boxes"), true);
        showTags = new BooleanSetting(Lang.get("module.randomboxesp.setting.show_tags"), true);
        tagDistance = new BooleanSetting(Lang.get("module.randomboxesp.setting.tag_distance"), true);
        distance = new SliderSetting(Lang.get("module.randomboxesp.setting.distance"), 128.0, 16.0, 500.0, 1.0);

        addSetting(showBoxes);
        addSetting(new SeparatorSetting());
        addSetting(showTags);
        addSetting(tagDistance);
        addSetting(new SeparatorSetting());
        addSetting(distance);
        addSetting(new BindSetting(Lang.get("module.randomboxesp.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.randomboxesp.desc");
    }

    private void initializeReflection() {
        if (reflectionSuccessful) return;
        try {
            // Целевой класс блока из ТЗ
            targetBlockClass = Class.forName("org.rhino.stalker.core.side.client.block.CBlockRandomBox");
            reflectionSuccessful = true;
            System.out.println("Fuctorize/RandomBoxESP: Successfully reflected CBlockRandomBox!");
        } catch (ClassNotFoundException e) {
            System.err.println("Fuctorize/RandomBoxESP: Stalker Core (RandomBox) class not found. Module will be inactive.");
            reflectionSuccessful = false;
        }
    }

    @Override
    public void onRender3D(RenderWorldLastEvent event) {
        if (!reflectionSuccessful || mc.thePlayer == null || mc.theWorld == null) return;

        boxTags.clear();
        RenderUtils.begin3DRendering();
        GL11.glLineWidth(1.5F);

        // Мы итерируем TileEntity, так как "Ящики" (Boxes), как правило, имеют TileEntity для хранения лута.
        // Это более производительно, чем сканировать все блоки в радиусе.
        // Если у блока нет TileEntity, этот метод не сработает, и придется делать сканирование чанков.
        for (Object o : mc.theWorld.loadedTileEntityList) {
            if (o instanceof TileEntity) {
                TileEntity tile = (TileEntity) o;

                // Проверяем дистанцию
                if (mc.thePlayer.getDistanceSq(tile.xCoord, tile.yCoord, tile.zCoord) > distance.value * distance.value) continue;

                // Получаем блок по координатам TileEntity
                Block block = mc.theWorld.getBlock(tile.xCoord, tile.yCoord, tile.zCoord);

                if (block != null && targetBlockClass.isInstance(block)) {
                    if (showBoxes.enabled) {
                        renderESPBox(tile, Colors.randomBoxColor.getColor());
                    }
                    if (showTags.enabled) {
                        calculateAndStoreTagData(tile);
                    }
                }
            }
        }
        RenderUtils.end3DRendering();
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (!reflectionSuccessful) return;
        client.nameTagRenderer.renderTagList(boxTags);
    }

    private void calculateAndStoreTagData(TileEntity tile) {
        // Проекция координат на 2D экран
        Vector4f coords = RenderUtils.projectTo2D(tile.xCoord + 0.5, tile.yCoord + 1.2, tile.zCoord + 0.5);

        if (coords != null && coords.z < 1.0f) {
            String nameLine = Lang.get("tag.randomboxesp.name");

            float distanceVal = (float) mc.thePlayer.getDistance(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5);
            String infoLine = tagDistance.enabled ? df.format(distanceVal) + "m" : "";

            String stableKey = "RB:" + tile.xCoord + "," + tile.yCoord + "," + tile.zCoord;

            boxTags.add(new TagData(nameLine, infoLine, Colors.randomBoxColor.getColor(), -1, coords, distanceVal, stableKey));
        }
    }

    private void renderESPBox(TileEntity te, int color) {
        double x = te.xCoord - RenderManager.instance.renderPosX;
        double y = te.yCoord - RenderManager.instance.renderPosY;
        double z = te.zCoord - RenderManager.instance.renderPosZ;
        // Стандартный размер блока 1x1x1
        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);

        RenderUtils.setColor(color, 0.15f);
        RenderUtils.drawFilledBoundingBox(bb);
        RenderUtils.setColor(color, 0.9f);
        RenderUtils.drawOutlinedBoundingBox(bb);
    }
}