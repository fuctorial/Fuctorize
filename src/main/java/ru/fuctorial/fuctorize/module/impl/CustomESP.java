 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.sniffer.GuiCustomEspSettings;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.client.render.TagData;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.*;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector4f;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomESP extends Module {

    private BooleanSetting selectButton;
    private BooleanSetting filterSettingsButton;
    private BooleanSetting resetButton;

     
    private BooleanSetting useFilters;

    private SliderSetting range;
    private SliderSetting limit;
    private SliderSetting updateDelay;

     
    private BooleanSetting showTags;
    private BooleanSetting tagDistance;

    private TargetType currentType = TargetType.NONE;
    private Block targetBlock = null;
    private Class<?> targetEntityClass = null;
    private String targetName = "None";

    private enum TargetType { NONE, BLOCK, ENTITY }

     
    private final Map<String, String> nbtFilterMap = new ConcurrentHashMap<>();

    private final List<int[]> cachedBlocks = new CopyOnWriteArrayList<>();
    private long lastBlockUpdate = 0;

     
    private final List<TagData> espTags = new ArrayList<>();
    private final DecimalFormat df = new DecimalFormat("0.0");

    public CustomESP(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("customesp", Lang.get("module.customesp.name"), Category.RENDER);

        selectButton = new BooleanSetting(Lang.get("module.customesp.setting.select"), false) {
            @Override
            public void toggle() {
                performSelection();
                this.enabled = false;
            }
        };

        filterSettingsButton = new BooleanSetting("Edit NBT Filters...", false) {
            @Override
            public void toggle() {
                mc.displayGuiScreen(new GuiCustomEspSettings(mc.currentScreen, CustomESP.this));
                this.enabled = false;
            }
        };

        useFilters = new BooleanSetting("Use NBT Filter", false);

        resetButton = new BooleanSetting(Lang.get("module.customesp.setting.reset"), false) {
            @Override
            public void toggle() {
                resetTargetData();
                this.enabled = false;
                client.notificationManager.show(new Notification(
                        Lang.get("module.customesp.name"),
                        Lang.get("notification.customesp.reset"),
                        Notification.NotificationType.INFO,
                        2000L
                ));
            }
        };

        range = new SliderSetting(Lang.get("module.customesp.setting.range"), 64.0, 1.0, 256.0, 1.0);
        limit = new SliderSetting(Lang.get("module.customesp.setting.limit"), 100.0, 10.0, 2000.0, 10.0);
        updateDelay = new SliderSetting("Block Scan Delay (ms)", 500.0, 100.0, 2000.0, 100.0);

         
        showTags = new BooleanSetting(Lang.get("module.customesp.setting.show_tags"), true);
        tagDistance = new BooleanSetting("Tag Distance", true);

        addSetting(selectButton);
        addSetting(resetButton);
        addSetting(new SeparatorSetting());
        addSetting(useFilters);
        addSetting(filterSettingsButton);
        addSetting(new SeparatorSetting());
        addSetting(range);
        addSetting(limit);
        addSetting(updateDelay);
        addSetting(new SeparatorSetting());
        addSetting(showTags);
        addSetting(tagDistance);
        addSetting(new BindSetting(Lang.get("module.customesp.setting.bind"), Keyboard.KEY_NONE));
    }

    public Map<String, String> getNbtFilterMap() {
        return nbtFilterMap;
    }

    public void forceUpdate() {
        cachedBlocks.clear();
        lastBlockUpdate = 0;
    }

    @Override
    public String getDescription() {
        return Lang.get("module.customesp.desc");
    }

    @Override
    public String getName() {
        if (currentType != TargetType.NONE) {
            String modeInfo = useFilters.enabled ? " \u00A7a[NBT]" : "";
            return super.getName() + " \u00A77[" + targetName + "]" + modeInfo;
        }
        return super.getName();
    }

    private void performSelection() {
        MovingObjectPosition mop = mc.objectMouseOver;

        if (mop == null || mop.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
            client.notificationManager.show(new Notification(
                    Lang.get("module.customesp.name"),
                    "§cLook at something!",
                    Notification.NotificationType.ERROR,
                    2000L
            ));
            return;
        }

        nbtFilterMap.clear();

        if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mop.entityHit != null) {
            Entity target = mop.entityHit;
            targetEntityClass = target.getClass();
            currentType = TargetType.ENTITY;

             
            NBTTagCompound nbt = new NBTTagCompound();
            target.writeToNBT(nbt);
            cleanEntityNbt(nbt);
            populateFilterFromNbt(nbt);

            String name = EntityList.getEntityString(target);
            if (name == null) name = target.getCommandSenderName();
            if (name == null) name = target.getClass().getSimpleName();
            targetName = name;

            cachedBlocks.clear();
            client.notificationManager.show(new Notification("CustomESP", "Entity selected: " + targetName, Notification.NotificationType.SUCCESS, 2000L));
        }
        else if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            Block block = mc.theWorld.getBlock(mop.blockX, mop.blockY, mop.blockZ);

            if (block == null || block instanceof BlockAir) {
                client.notificationManager.show(new Notification("CustomESP", "§cЭто воздух.", Notification.NotificationType.ERROR, 1000L));
                return;
            }

            targetBlock = block;
            currentType = TargetType.BLOCK;

             
            TileEntity te = mc.theWorld.getTileEntity(mop.blockX, mop.blockY, mop.blockZ);
            if (te != null) {
                NBTTagCompound nbt = new NBTTagCompound();
                te.writeToNBT(nbt);
                nbt.removeTag("x"); nbt.removeTag("y"); nbt.removeTag("z");
                populateFilterFromNbt(nbt);
            }

            cachedBlocks.clear();
            lastBlockUpdate = 0;

            int meta = mc.theWorld.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ);
            try {
                Item item = Item.getItemFromBlock(block);
                if (item != null) {
                    targetName = new ItemStack(item, 1, meta).getDisplayName();
                } else {
                    targetName = block.getLocalizedName();
                }
            } catch (Exception e) {
                targetName = block.getUnlocalizedName();
            }

            String extraInfo = !nbtFilterMap.isEmpty() ? " (Has NBT)" : "";
            client.notificationManager.show(new Notification("CustomESP", "Block selected: " + targetName + extraInfo, Notification.NotificationType.SUCCESS, 2000L));

            if (!nbtFilterMap.isEmpty()) {
                mc.displayGuiScreen(new GuiCustomEspSettings(mc.currentScreen, this));
            }
        }
    }

    private void cleanEntityNbt(NBTTagCompound nbt) {
        nbt.removeTag("Pos");
        nbt.removeTag("Motion");
        nbt.removeTag("Rotation");
        nbt.removeTag("UUIDMost");
        nbt.removeTag("UUIDLeast");
        nbt.removeTag("OnGround");
        nbt.removeTag("Dimension");
        nbt.removeTag("PortalCooldown");
        nbt.removeTag("FallDistance");
        nbt.removeTag("Fire");
        nbt.removeTag("Air");
    }

    private void populateFilterFromNbt(NBTTagCompound nbt) {
        if (nbt == null) return;
        for (Object keyObj : nbt.func_150296_c()) {
            String key = (String) keyObj;
            NBTBase tag = nbt.getTag(key);
            String value = tag.toString().replaceAll("\"", "");
            if (tag.getId() <= 6) {
                value = value.replaceAll("[bsfl]$", "");
            }
            nbtFilterMap.put(key, value);
        }
    }

    private void resetTargetData() {
        currentType = TargetType.NONE;
        targetBlock = null;
        targetEntityClass = null;
        targetName = "None";
        nbtFilterMap.clear();
        cachedBlocks.clear();
    }

    @Override
    public void onUpdate() {
        if (currentType == TargetType.BLOCK && targetBlock != null && mc.thePlayer != null) {
            if (System.currentTimeMillis() - lastBlockUpdate > updateDelay.value) {
                new Thread(this::scanBlocksAsync).start();
                lastBlockUpdate = System.currentTimeMillis();
            }
        }
    }

    private void scanBlocksAsync() {
        if (mc.theWorld == null || mc.thePlayer == null || targetBlock == null) return;

        List<int[]> found = new ArrayList<>();
        int r = (int) range.value;
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.posY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        int limitVal = (int) limit.value;
        boolean strict = useFilters.enabled;

        int safetyLimit = limitVal * 5;
        int count = 0;

        for (int x = px - r; x <= px + r; x++) {
            for (int y = py - r; y <= py + r; y++) {
                for (int z = pz - r; z <= pz + r; z++) {
                    try {
                        if (mc.theWorld.getBlock(x, y, z) == targetBlock) {
                            if (!strict || checkFilterForBlock(x, y, z)) {
                                found.add(new int[]{x, y, z});
                                count++;
                            }
                        }
                    } catch (Exception ignored) {}
                    if (count >= safetyLimit) break;
                }
                if (count >= safetyLimit) break;
            }
            if (count >= safetyLimit) break;
        }

        found.sort(Comparator.comparingDouble(pos ->
                mc.thePlayer.getDistanceSq(pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5)
        ));

        if (found.size() > limitVal) found = found.subList(0, limitVal);

        cachedBlocks.clear();
        cachedBlocks.addAll(found);
    }

    private boolean checkFilterForBlock(int x, int y, int z) {
        if (nbtFilterMap.isEmpty()) return true;

        TileEntity te = mc.theWorld.getTileEntity(x, y, z);
        if (te == null) return false;

        NBTTagCompound nbt = new NBTTagCompound();
        te.writeToNBT(nbt);

        for (Map.Entry<String, String> entry : nbtFilterMap.entrySet()) {
            String key = entry.getKey();
            String expectedVal = entry.getValue();

            if (!nbt.hasKey(key)) return false;

            String actualVal = nbt.getTag(key).toString().replaceAll("\"", "").replaceAll("[bsfl]$", "");
            if (!actualVal.equals(expectedVal)) return false;
        }
        return true;
    }

    private boolean checkFilterForEntity(Entity entity) {
        if (nbtFilterMap.isEmpty()) return true;

        NBTTagCompound nbt = new NBTTagCompound();
        entity.writeToNBT(nbt);

        for (Map.Entry<String, String> entry : nbtFilterMap.entrySet()) {
            String key = entry.getKey();
            String expectedVal = entry.getValue();

            if (!nbt.hasKey(key)) return false;

            String actualVal = nbt.getTag(key).toString().replaceAll("\"", "").replaceAll("[bsfl]$", "");
            if (!actualVal.equals(expectedVal)) return false;
        }
        return true;
    }

    @Override
    public void onRender3D(RenderWorldLastEvent event) {
        if (currentType == TargetType.NONE || mc.theWorld == null || mc.thePlayer == null) return;

        espTags.clear();
        RenderUtils.begin3DRendering();
        GL11.glLineWidth(1.5F);

        int espColor = ru.fuctorial.fuctorize.module.impl.Colors.customEspColor.getColor();
        float partialTicks = event.partialTicks;
        int maxCount = (int) limit.value;
        int renderCount = 0;
        boolean strict = useFilters.enabled;

        if (currentType == TargetType.ENTITY) {
            List<Entity> validEntities = new ArrayList<>();
            double rSq = range.value * range.value;

            for (Object obj : mc.theWorld.loadedEntityList) {
                Entity entity = (Entity) obj;
                if (entity == mc.thePlayer) continue;

                if (targetEntityClass != null && targetEntityClass.isInstance(entity)) {
                    if (mc.thePlayer.getDistanceSqToEntity(entity) <= rSq) {
                        if (!strict || checkFilterForEntity(entity)) {
                            validEntities.add(entity);
                        }
                    }
                }
            }

            validEntities.sort(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceSqToEntity(e)));

            for (Entity entity : validEntities) {
                if (renderCount >= maxCount) break;
                renderEntityBox(entity, espColor, partialTicks);
                if (showTags.enabled) {
                    calculateEntityTag(entity, partialTicks, espColor);
                }
                renderCount++;
            }
        }
        else if (currentType == TargetType.BLOCK) {
            for (int[] pos : cachedBlocks) {
                if (mc.theWorld.getBlock(pos[0], pos[1], pos[2]) == targetBlock) {
                    renderBlockBox(pos[0], pos[1], pos[2], espColor);
                    if (showTags.enabled) {
                        calculateBlockTag(pos[0], pos[1], pos[2], espColor);
                    }
                }
            }
        }

        RenderUtils.end3DRendering();
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        client.nameTagRenderer.renderTagList(espTags);
    }

     

    private void calculateEntityTag(Entity entity, float partialTicks, int color) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        Vector4f coords = RenderUtils.projectTo2D(x, y + entity.height + 0.5, z);

        if (coords != null && coords.z < 1.0f) {
            String infoLine = "";
            float dist = mc.thePlayer.getDistanceToEntity(entity);

            if (tagDistance.enabled) {
                infoLine = df.format(dist) + "m";
            }

            String stableKey = "CE:" + entity.getEntityId();
            espTags.add(new TagData(targetName, infoLine, color, -1, coords, dist, stableKey));
        }
    }

    private void calculateBlockTag(int x, int y, int z, int color) {
        Vector4f coords = RenderUtils.projectTo2D(x + 0.5, y + 1.2, z + 0.5);

        if (coords != null && coords.z < 1.0f) {
            String infoLine = "";
            float dist = (float) mc.thePlayer.getDistance(x + 0.5, y + 0.5, z + 0.5);

            if (tagDistance.enabled) {
                infoLine = df.format(dist) + "m";
            }

            String stableKey = "CB:" + x + "," + y + "," + z;
            espTags.add(new TagData(targetName, infoLine, color, -1, coords, dist, stableKey));
        }
    }

    private void renderEntityBox(Entity entity, int color, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - RenderManager.instance.renderPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - RenderManager.instance.renderPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - RenderManager.instance.renderPosZ;

        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                x - entity.width / 2, y, z - entity.width / 2,
                x + entity.width / 2, y + entity.height, z + entity.width / 2
        );

        RenderUtils.setColor(color, 0.2f);
        RenderUtils.drawFilledBoundingBox(bb);
        RenderUtils.setColor(color, 1.0f);
        RenderUtils.drawOutlinedBoundingBox(bb);
    }

    private void renderBlockBox(int x, int y, int z, int color) {
        AxisAlignedBB bb = targetBlock.getSelectedBoundingBoxFromPool(mc.theWorld, x, y, z);
        if (bb == null) {
            bb = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
        }
        AxisAlignedBB renderBB = bb.getOffsetBoundingBox(
                -RenderManager.instance.renderPosX,
                -RenderManager.instance.renderPosY,
                -RenderManager.instance.renderPosZ
        );

        RenderUtils.setColor(color, 0.2f);
        RenderUtils.drawFilledBoundingBox(renderBB);
        RenderUtils.setColor(color, 1.0f);
        RenderUtils.drawOutlinedBoundingBox(renderBB);
    }
}