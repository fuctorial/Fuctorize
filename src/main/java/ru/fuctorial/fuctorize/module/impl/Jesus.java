 
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.ModeSetting;
import ru.fuctorial.fuctorize.utils.Lang;  
import ru.fuctorial.fuctorize.utils.NetUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Jesus extends Module {

    private static final int SOLID_LAYER_DEPTH = 5;  

    private ModeSetting mode;
    private String walkModeName;
    private String solidModeName;
    private final Map<BlockPos, Block> replacedBlocks = new HashMap<>();

    public Jesus(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("jesus", Lang.get("module.jesus.name"), Category.MOVEMENT);

        walkModeName = Lang.get("module.jesus.setting.mode.walk");
        solidModeName = Lang.get("module.jesus.setting.mode.solid");
        mode = new ModeSetting(Lang.get("module.jesus.setting.mode"), walkModeName, walkModeName, solidModeName);
        addSetting(mode);
        addSetting(new BindSetting(Lang.get("module.jesus.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.jesus.desc");
    }

    @Override
    public void onEnable() {
        revertReplacedBlocks();
    }

    @Override
    public void onDisable() {
        revertReplacedBlocks();
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (mode.isMode(solidModeName)) {
            handleSolidWaterMode();
        } else if (mode.isMode(walkModeName)) {
            if (!replacedBlocks.isEmpty()) {
                revertReplacedBlocks();
            }
            handleAquaWalkMode();
        }
    }

    private void handleAquaWalkMode() {
        if (mc.thePlayer.isInWater() && !mc.thePlayer.isSneaking() && mc.theWorld.getBlock(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY + 1.0D),
                MathHelper.floor_double(mc.thePlayer.posZ)).getMaterial() == Material.air) {
            mc.thePlayer.motionY = 0.08D;
            return;
        }

        if (isPlayerSubmerged() || mc.thePlayer.isSneaking()) {
            return;
        }

        if (isLiquidUnderneath()) {
            if (mc.thePlayer.fallDistance > 2.5F) {
                NetUtils.sendPacket(new C03PacketPlayer(true));
                mc.thePlayer.fallDistance = 0.0F;
            }
            mc.thePlayer.onGround = true;
            mc.thePlayer.motionY = 0.0D;
        }
    }

    private void handleSolidWaterMode() {
        if (isPlayerSubmerged() || mc.thePlayer.capabilities.isFlying) {
            revertReplacedBlocks();
            return;
        }
        cleanUpDistantBlocks();
        int playerX = MathHelper.floor_double(mc.thePlayer.posX);
        int playerY = MathHelper.floor_double(mc.thePlayer.boundingBox.minY) - 1;
        int playerZ = MathHelper.floor_double(mc.thePlayer.posZ);
        int minY = Math.max(0, playerY - (SOLID_LAYER_DEPTH - 1));
        for (int x = playerX - 1; x <= playerX + 1; x++) {
            for (int z = playerZ - 1; z <= playerZ + 1; z++) {
                for (int y = playerY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = mc.theWorld.getBlock(x, y, z);
                    if (block != null && block.getMaterial().isLiquid() && !replacedBlocks.containsKey(pos)) {
                        replacedBlocks.put(pos, block);
                        mc.theWorld.setBlock(x, y, z, Blocks.stone, 0, 2);
                    }
                }
            }
        }
    }

    private boolean isLiquidUnderneath() {
        if (mc.thePlayer == null || mc.thePlayer.capabilities.isFlying) return false;
        AxisAlignedBB boundingBox = mc.thePlayer.boundingBox.getOffsetBoundingBox(0.0D, -0.1D, 0.0D);
        int minX = MathHelper.floor_double(boundingBox.minX);
        int maxX = MathHelper.floor_double(boundingBox.maxX + 1.0D);
        int minY = MathHelper.floor_double(boundingBox.minY);
        int maxY = MathHelper.floor_double(boundingBox.maxY + 1.0D);
        int minZ = MathHelper.floor_double(boundingBox.minZ);
        int maxZ = MathHelper.floor_double(boundingBox.maxZ + 1.0D);
        for (int x = minX; x < maxX; ++x) {
            for (int y = minY; y < maxY; ++y) {
                for (int z = minZ; z < maxZ; ++z) {
                    Block block = mc.theWorld.getBlock(x, y, z);
                    if (block != null && block.getMaterial().isLiquid() && mc.thePlayer.boundingBox.minY >= y) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isPlayerSubmerged() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInsideOfMaterial(Material.lava);
    }

    private void revertReplacedBlocks() {
        if (!replacedBlocks.isEmpty()) {
            for (Map.Entry<BlockPos, Block> entry : replacedBlocks.entrySet()) {
                BlockPos pos = entry.getKey();
                mc.theWorld.setBlock(pos.x, pos.y, pos.z, entry.getValue(), 0, 2);
            }
            replacedBlocks.clear();
        }
    }

    private void cleanUpDistantBlocks() {
        if (replacedBlocks.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, Block>> iterator = replacedBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Block> entry = iterator.next();
            BlockPos pos = entry.getKey();
            double dx = mc.thePlayer.posX - (pos.x + 0.5);
            double dz = mc.thePlayer.posZ - (pos.z + 0.5);
            double horizontalDistanceSq = dx * dx + dz * dz;
            if (horizontalDistanceSq > 16) {
                mc.theWorld.setBlock(pos.x, pos.y, pos.z, entry.getValue(), 0, 2);
                iterator.remove();
            }
        }
    }

    private static class BlockPos {
        final int x, y, z;
        public BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; BlockPos blockPos = (BlockPos) o; return x == blockPos.x && y == blockPos.y && z == blockPos.z; }
        @Override public int hashCode() { return (y * 31 + x) * 31 + z; }
    }
}
