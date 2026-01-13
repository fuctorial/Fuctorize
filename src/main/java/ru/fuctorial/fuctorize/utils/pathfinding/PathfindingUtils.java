package ru.fuctorial.fuctorize.utils.pathfinding;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.potion.Potion;

/**
 * РЈС‚РёР»РёС‚С‹ РґР»СЏ РїСЂРѕРІРµСЂРєРё РїСЂРѕС…РѕРґРёРјРѕСЃС‚Рё Р±Р»РѕРєРѕРІ РІ СЃРёСЃС‚РµРјРµ Pathfinding
 */
public class PathfindingUtils {

    public enum BlockClass {
        AIR,    // РїСЂРѕС…РѕРґРёРјРѕРµ
        SOLID,  // РЅРµРїСЂРѕС…РѕРґРёРјРѕРµ
        WATER,  // Р¶РёРґРєРѕСЃС‚СЊ
        AVOID   // РѕРїР°СЃРЅРѕРµ (Р»Р°РІР°/РѕРіРѕРЅСЊ/РєР°РєС‚СѓСЃ)
    }

    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, СЏРІР»СЏРµС‚СЃСЏ Р»Рё Р±Р»РѕРє РїСЂРѕС…РѕРґРёРјС‹Рј РґР»СЏ РёРіСЂРѕРєР°
     * @param world РњРёСЂ
     * @param x РљРѕРѕСЂРґРёРЅР°С‚Р° X Р±Р»РѕРєР°
     * @param y РљРѕРѕСЂРґРёРЅР°С‚Р° Y Р±Р»РѕРєР°
     * @param z РљРѕРѕСЂРґРёРЅР°С‚Р° Z Р±Р»РѕРєР°
     * @return true РµСЃР»Рё Р±Р»РѕРє РїСЂРѕС…РѕРґРёРј
     */
    public static final double DEFAULT_PLAYER_WIDTH = 0.6;
    public static final double DEFAULT_PLAYER_HEIGHT = 1.8;
    private static final int SURFACE_SEARCH_RANGE = 12;

    public static boolean isBlockPassable(World world, int x, int y, int z) {
        if (world == null) return false;
        if (!world.blockExists(x, y, z)) return false;

        Block block = world.getBlock(x, y, z);
        if (block == null || block instanceof BlockAir) {
            return true;
        }
        Material material = block.getMaterial();
        if (material == null) return true;
        if (isWater(block)) return true;
        if (isLava(block) || isDangerousBlock(block)) return false;
        if (material.isReplaceable() || material.isLiquid()) return true;
        if (block instanceof BlockLadder || block instanceof BlockVine) return true;

        AxisAlignedBB boundingBox = block.getCollisionBoundingBoxFromPool(world, x, y, z);
        return boundingBox == null;
    }

    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, РјРѕР¶РµС‚ Р»Рё РёРіСЂРѕРє СЃС‚РѕСЏС‚СЊ РЅР° Р±Р»РѕРєРµ
     * @param world РњРёСЂ
     * @param x РљРѕРѕСЂРґРёРЅР°С‚Р° X Р±Р»РѕРєР°
     * @param y РљРѕРѕСЂРґРёРЅР°С‚Р° Y Р±Р»РѕРєР°
     * @param z РљРѕРѕСЂРґРёРЅР°С‚Р° Z Р±Р»РѕРєР°
     * @return true РµСЃР»Рё РЅР° Р±Р»РѕРєРµ РјРѕР¶РЅРѕ СЃС‚РѕСЏС‚СЊ
     */
    public static boolean canStandOn(World world, int x, int y, int z) {
        if (world == null) return false;
        if (!world.blockExists(x, y, z)) return false;

        Block block = world.getBlock(x, y, z);
        if (block == null || block instanceof BlockAir) {
            return false;
        }

        // РџСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ Р±Р»РѕРє РёРјРµРµС‚ РєРѕР»Р»РёР·РёСЋ (РЅРµ РІРѕР·РґСѓС…)
        AxisAlignedBB boundingBox = block.getCollisionBoundingBoxFromPool(world, x, y, z);
        return boundingBox != null;
    }

    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, СЃРІРѕР±РѕРґРЅРѕ Р»Рё РїСЂРѕСЃС‚СЂР°РЅСЃС‚РІРѕ РґР»СЏ РёРіСЂРѕРєР° РІ СѓРєР°Р·Р°РЅРЅРѕР№ РїРѕР·РёС†РёРё
     * @param world РњРёСЂ
     * @param x РљРѕРѕСЂРґРёРЅР°С‚Р° X
     * @param y РљРѕРѕСЂРґРёРЅР°С‚Р° Y
     * @param z РљРѕРѕСЂРґРёРЅР°С‚Р° Z
     * @param width РЁРёСЂРёРЅР° РёРіСЂРѕРєР° (РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ 0.6)
     * @param height Р’С‹СЃРѕС‚Р° РёРіСЂРѕРєР° (РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ 1.8)
     * @return true РµСЃР»Рё РїСЂРѕСЃС‚СЂР°РЅСЃС‚РІРѕ СЃРІРѕР±РѕРґРЅРѕ
     */
    public static boolean isSpaceFree(World world, double x, double y, double z, double width, double height) {
        if (world == null) return false;

        int minX = MathHelper.floor_double(x - width / 2.0);
        int maxX = MathHelper.floor_double(x + width / 2.0);
        int minY = MathHelper.floor_double(y);
        int maxY = MathHelper.floor_double(y + height);
        int minZ = MathHelper.floor_double(z - width / 2.0);
        int maxZ = MathHelper.floor_double(z + width / 2.0);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (!isBlockPassable(world, bx, by, bz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * РљР»Р°СЃСЃРёС„РёРєР°С†РёСЏ Р±Р»РѕРєР° РїРѕ 2-Р±РёС‚РЅРѕР№ СЃС…РµРјРµ
     */
    public static BlockClass classifyBlock(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) {
            return BlockClass.SOLID;
        }
        Block block = world.getBlock(x, y, z);
        if (block == null || block instanceof BlockAir) {
            return BlockClass.AIR;
        }
        if (isLava(block) || isDangerousBlock(block)) {
            return BlockClass.AVOID;
        }
        if (isWater(block)) {
            return BlockClass.WATER;
        }
        AxisAlignedBB bb = block.getCollisionBoundingBoxFromPool(world, x, y, z);
        return bb == null ? BlockClass.AIR : BlockClass.SOLID;
    }

    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, СЃРІРѕР±РѕРґРЅРѕ Р»Рё РїСЂРѕСЃС‚СЂР°РЅСЃС‚РІРѕ РґР»СЏ РёРіСЂРѕРєР° (СЃС‚Р°РЅРґР°СЂС‚РЅС‹Рµ СЂР°Р·РјРµСЂС‹)
     * @param world РњРёСЂ
     * @param x РљРѕРѕСЂРґРёРЅР°С‚Р° X
     * @param y РљРѕРѕСЂРґРёРЅР°С‚Р° Y
     * @param z РљРѕРѕСЂРґРёРЅР°С‚Р° Z
     * @return true РµСЃР»Рё РїСЂРѕСЃС‚СЂР°РЅСЃС‚РІРѕ СЃРІРѕР±РѕРґРЅРѕ
     */
    public static boolean isSpaceFree(World world, double x, double y, double z) {
        return isSpaceFree(world, x, y, z, DEFAULT_PLAYER_WIDTH, DEFAULT_PLAYER_HEIGHT);
    }


    public static boolean isLookingAtBlock(int x, int y, int z) {
        MovingObjectPosition mop = Minecraft.getMinecraft().objectMouseOver;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return mop.blockX == x && mop.blockY == y && mop.blockZ == z;
        }
        return false;
    }


    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, РјРѕР¶РµС‚ Р»Рё РёРіСЂРѕРє РІР·Р°РёРјРѕРґРµР№СЃС‚РІРѕРІР°С‚СЊ СЃ Р±Р»РѕРєРѕРј (РґРѕС‚СЏРЅСѓС‚СЊСЃСЏ Рё РІРёРґРµС‚СЊ РµРіРѕ).
     * @param x X-РєРѕРѕСЂРґРёРЅР°С‚Р° Р±Р»РѕРєР°
     * @param y Y-РєРѕРѕСЂРґРёРЅР°С‚Р° Р±Р»РѕРєР°
     * @param z Z-РєРѕРѕСЂРґРёРЅР°С‚Р° Р±Р»РѕРєР°
     * @return true, РµСЃР»Рё Р±Р»РѕРє РґРѕСЃСЏРіР°РµРј.
     */
    public static boolean isBlockReachable(int x, int y, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) return false;

        // 1. РџСЂРѕРІРµСЂРєР° РґРёСЃС‚Р°РЅС†РёРё. РРіСЂРѕРє РЅРµ РјРѕР¶РµС‚ Р»РѕРјР°С‚СЊ Р±Р»РѕРєРё РґР°Р»СЊС€Рµ ~4.5 Р±Р»РѕРєРѕРІ.
        //    РСЃРїРѕР»СЊР·СѓРµРј РєРІР°РґСЂР°С‚ СЂР°СЃСЃС‚РѕСЏРЅРёСЏ РґР»СЏ РїСЂРѕРёР·РІРѕРґРёС‚РµР»СЊРЅРѕСЃС‚Рё. 5*5=25.
        if (player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 25.0) {
            return false;
        }

        // 2. РџСЂРѕРІРµСЂРєР° РїСЂСЏРјРѕР№ РІРёРґРёРјРѕСЃС‚Рё (Raycast).
        //    РџСѓСЃРєР°РµРј Р»СѓС‡ РёР· РіР»Р°Р· РёРіСЂРѕРєР° РІ С†РµРЅС‚СЂ С†РµР»РµРІРѕРіРѕ Р±Р»РѕРєР°.
        Vec3 eyesPos = Vec3.createVectorHelper(player.posX, player.boundingBox.minY + player.getEyeHeight(), player.posZ);
        Vec3 blockCenter = Vec3.createVectorHelper(x + 0.5, y + 0.5, z + 0.5);

        MovingObjectPosition mop = player.worldObj.rayTraceBlocks(eyesPos, blockCenter, false);

        // Р•СЃР»Рё Р»СѓС‡ РЅРё РІРѕ С‡С‚Рѕ РЅРµ СѓРїРµСЂСЃСЏ РР›Р СѓРїРµСЂСЃСЏ РёРјРµРЅРЅРѕ РІ РЅР°С€ С†РµР»РµРІРѕР№ Р±Р»РѕРє,
        // Р·РЅР°С‡РёС‚, РѕРЅ РІ РїСЂСЏРјРѕР№ РІРёРґРёРјРѕСЃС‚Рё.
        if (mop == null || (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && mop.blockX == x && mop.blockY == y && mop.blockZ == z)) {
            return true;
        }

        // Р•СЃР»Рё Р»СѓС‡ СѓРїРµСЂСЃСЏ РІ РґСЂСѓРіРѕР№ Р±Р»РѕРє, Р·РЅР°С‡РёС‚, С†РµР»СЊ Р·Р°РіРѕСЂРѕР¶РµРЅР°.
        return false;
    }

    /**
     * РќРћР’Р«Р™ РњР•РўРћР”: РџСЂРѕРІРµСЂСЏРµС‚, СЏРІР»СЏРµС‚СЃСЏ Р»Рё Р±Р»РѕРє С‚РІРµСЂРґС‹Рј Рё РЅРµРїСЂРѕС…РѕРґРёРјС‹Рј.
     * Р’ РѕС‚Р»РёС‡РёРµ РѕС‚ isBlockPassable, СЌС‚РѕС‚ РјРµС‚РѕРґ РЅРµ СЃС‡РёС‚Р°РµС‚ РІРѕРґСѓ РёР»Рё Р»Р°РІСѓ РїСЂРѕС…РѕРґРёРјС‹РјРё.
     */
    public static boolean isBlockSolid(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) {
            return true; // РЎС‡РёС‚Р°РµРј РЅРµСЃСѓС‰РµСЃС‚РІСѓСЋС‰РёРµ Р±Р»РѕРєРё С‚РІРµСЂРґС‹РјРё РЅР° РІСЃСЏРєРёР№ СЃР»СѓС‡Р°Р№
        }
        Block block = world.getBlock(x, y, z);
        if (block == null || block instanceof BlockAir) {
            return false;
        }
        // Р‘Р»РѕРє С‚РІРµСЂРґС‹Р№, РµСЃР»Рё Сѓ РЅРµРіРѕ РµСЃС‚СЊ РєРѕР»Р»Р°Р№РґРµСЂ (РЅРµР»СЊР·СЏ РїСЂРѕР№С‚Рё СЃРєРІРѕР·СЊ)
        return block.getCollisionBoundingBoxFromPool(world, x, y, z) != null;
    }

    /**
     * Р Р°СЃСЃС‡РёС‚С‹РІР°РµС‚ РІСЂРµРјСЏ РІ СЃРµРєСѓРЅРґР°С…, РЅРµРѕР±С…РѕРґРёРјРѕРµ РґР»СЏ СЂР°Р·СЂСѓС€РµРЅРёСЏ Р±Р»РѕРєР° РёРіСЂРѕРєРѕРј.
     * РЈС‡РёС‚С‹РІР°РµС‚ РёРЅСЃС‚СЂСѓРјРµРЅС‚, РїСЂРѕС‡РЅРѕСЃС‚СЊ Р±Р»РѕРєР°, С‡Р°СЂС‹ Рё СЌС„С„РµРєС‚С‹.
     * @param block Р‘Р»РѕРє, РєРѕС‚РѕСЂС‹Р№ РЅСѓР¶РЅРѕ СЃР»РѕРјР°С‚СЊ
     * @param player РРіСЂРѕРє, РєРѕС‚РѕСЂС‹Р№ Р»РѕРјР°РµС‚
     * @param world РњРёСЂ
     * @param x, y, z РљРѕРѕСЂРґРёРЅР°С‚С‹ Р±Р»РѕРєР°
     * @return Р’СЂРµРјСЏ РІ СЃРµРєСѓРЅРґР°С…
     */
    public static double calculateBlockBreakTime(Block block, EntityPlayer player, World world, int x, int y, int z) {
        if (block == null || block.getBlockHardness(world, x, y, z) < 0) {
            return Double.POSITIVE_INFINITY; // РќРµСЂР°Р·СЂСѓС€Р°РµРјС‹Р№ Р±Р»РѕРє (Р±РµРґСЂРѕРє)
        }

        float hardness = block.getBlockHardness(world, x, y, z);
        if (hardness == 0) return 0.0;

        ItemStack heldItem = player.getHeldItem();
        int meta = world.getBlockMetadata(x, y, z);
        boolean canHarvest = block.canHarvestBlock(player, meta);

        // === РЁРђР“ 1: BaseTime ===
        float baseTime = hardness * (canHarvest ? 1.5F : 5.0F);

        // === РЁРђР“ 2: ToolSpeed (СЃРєРѕСЂРѕСЃС‚СЊ РёРЅСЃС‚СЂСѓРјРµРЅС‚Р°) ===
        float toolSpeed = 1.0F; // РЎРєРѕСЂРѕСЃС‚СЊ СЂСѓРєРё
        if (heldItem != null) {
            toolSpeed = heldItem.getItem().getDigSpeed(heldItem, block, meta);
            if (toolSpeed > 1.0F) {
                // РџСЂРёРјРµРЅСЏРµРј Efficiency С‚РѕР»СЊРєРѕ РµСЃР»Рё РёРЅСЃС‚СЂСѓРјРµРЅС‚ СЌС„С„РµРєС‚РёРІРµРЅ РїСЂРѕС‚РёРІ Р±Р»РѕРєР°
                int efficiencyLevel = EnchantmentHelper.getEfficiencyModifier(player);
                if (efficiencyLevel > 0) {
                    toolSpeed += (efficiencyLevel * efficiencyLevel + 1);
                }
            }
        }

        // === РЁРђР“ 3: Haste/Mining Fatigue ===
        float potionMultiplier = 1.0F;

        if (player.isPotionActive(Potion.digSpeed)) {
            int hasteLevel = player.getActivePotionEffect(Potion.digSpeed).getAmplifier();
            potionMultiplier *= (1.0F + (hasteLevel + 1) * 0.2F);
        }

        if (player.isPotionActive(Potion.digSlowdown)) {
            int fatigueLevel = player.getActivePotionEffect(Potion.digSlowdown).getAmplifier();
            // Mining Fatigue: РєР°Р¶РґС‹Р№ СѓСЂРѕРІРµРЅСЊ РґРµР»РёС‚ СЃРєРѕСЂРѕСЃС‚СЊ РїСЂРёРјРµСЂРЅРѕ РЅР° 3.33
            switch (fatigueLevel) {
                case 0: potionMultiplier *= 0.3F; break;
                case 1: potionMultiplier *= 0.09F; break;
                case 2: potionMultiplier *= 0.0027F; break;
                default: potionMultiplier *= Math.pow(0.3F, fatigueLevel + 1); break;
            }
        }

        // === РЁРђР“ 4: Conditions (С€С‚СЂР°С„С‹ Р·Р° РІРѕРґСѓ Рё РІРѕР·РґСѓС…) ===
        float conditionMultiplier = 1.0F;

        // РЁС‚СЂР°С„ Р·Р° РІРѕРґСѓ (РµСЃР»Рё РЅРµС‚ Aqua Affinity)
        if (player.isInWater() && !EnchantmentHelper.getAquaAffinityModifier(player)) {
            conditionMultiplier *= 0.2F;
        }

        // РЁС‚СЂР°С„ Р·Р° РІРѕР·РґСѓС… (РЅРµ РЅР° Р·РµРјР»Рµ)
        if (!player.onGround) {
            conditionMultiplier *= 0.2F;
        }

        // === РЁРђР“ 5: Р¤РёРЅР°Р»СЊРЅС‹Р№ СЂР°СЃС‡С‘С‚ ===
        float speedMultiplier = toolSpeed * potionMultiplier * conditionMultiplier;

        if (speedMultiplier <= 0) return Double.POSITIVE_INFINITY;

        double breakTime = baseTime / speedMultiplier;

        // РћРєСЂСѓРіР»РµРЅРёРµ РґРѕ С‚РёРєРѕРІ (1 С‚РёРє = 0.05 СЃРµРє)
        breakTime = Math.ceil(breakTime * 20.0) / 20.0;

        return breakTime;
    }


    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, РµСЃС‚СЊ Р»Рё РїСЂСЏРјР°СЏ РІРёРґРёРјРѕСЃС‚СЊ РјРµР¶РґСѓ РґРІСѓРјСЏ СѓР·Р»Р°РјРё РїСѓС‚Рё.
     * РЈС‡РёС‚С‹РІР°РµС‚ С€РёСЂРёРЅСѓ Рё РІС‹СЃРѕС‚Сѓ РёРіСЂРѕРєР°.
     */
    public static boolean hasClearLineOfSight(World world, PathNode from, PathNode to) {
        if (world == null || from == null || to == null) {
            return false;
        }

        // РћРїСЂРµРґРµР»СЏРµРј РЅР°С‡Р°Р»СЊРЅСѓСЋ Рё РєРѕРЅРµС‡РЅСѓСЋ С‚РѕС‡РєРё РґР»СЏ Р»СѓС‡Р°.
        // РСЃРїРѕР»СЊР·СѓРµРј С†РµРЅС‚СЂ РЅРѕРі РґР»СЏ РЅР°С‡Р°Р»Р° Рё С†РµРЅС‚СЂ С‚РµР»Р° РґР»СЏ РєРѕРЅС†Р°.
        Vec3 startVec = Vec3.createVectorHelper(from.x + 0.5, from.getFeetY() + 0.1, from.z + 0.5);
        Vec3 endVec = Vec3.createVectorHelper(to.x + 0.5, to.getCenterY(), to.z + 0.5);

        // РСЃРїРѕР»СЊР·СѓРµРј rayTraceBlocks РґР»СЏ РїСЂРѕРІРµСЂРєРё РїСЂРµРїСЏС‚СЃС‚РІРёР№.
        // РўСЂРµС‚РёР№ РїР°СЂР°РјРµС‚СЂ `false` РІР°Р¶РµРЅ, С‡С‚РѕР±С‹ Р»СѓС‡ РЅРµ РѕСЃС‚Р°РЅР°РІР»РёРІР°Р»СЃСЏ РЅР° Р¶РёРґРєРѕСЃС‚СЏС….
        MovingObjectPosition result = world.rayTraceBlocks(startVec, endVec, false);

        // Р•СЃР»Рё Р»СѓС‡ РЅРё РІРѕ С‡С‚Рѕ РЅРµ СѓРїРµСЂСЃСЏ, Р·РЅР°С‡РёС‚, РїСѓС‚СЊ СЃРІРѕР±РѕРґРµРЅ.
        return result == null;
    }



    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, РјРѕР¶РµС‚ Р»Рё РёРіСЂРѕРє СЃС‚РѕСЏС‚СЊ РІ СѓРєР°Р·Р°РЅРЅРѕР№ РїРѕР·РёС†РёРё
     * @param world РњРёСЂ
     * @param x РљРѕРѕСЂРґРёРЅР°С‚Р° X
     * @param y РљРѕРѕСЂРґРёРЅР°С‚Р° Y
     * @param z РљРѕРѕСЂРґРёРЅР°С‚Р° Z
     * @return true РµСЃР»Рё РёРіСЂРѕРє РјРѕР¶РµС‚ СЃС‚РѕСЏС‚СЊ
     */
    public static boolean canStandAt(World world, double x, double y, double z) {
        // y is the player's world feet Y; convert to ground semantics
        if (world == null) return false;

        int blockX = MathHelper.floor_double(x);
        int feetYWorld = MathHelper.floor_double(y);
        int groundY = feetYWorld - 1;
        int blockZ = MathHelper.floor_double(z);

        if (!canStandOn(world, blockX, groundY, blockZ)) {
            return false;
        }

        int feetY = YMath.feetFromGround(groundY);
        int headY = YMath.headFromGround(groundY);

        boolean feetPassable = isBlockPassable(world, blockX, feetY, blockZ);
        boolean headPassable = isBlockPassable(world, blockX, headY, blockZ);

        if (!feetPassable || !headPassable) {
            return false;
        }

        return true;
    }

    /**
     * РџРѕР»СѓС‡Р°РµС‚ СЃС‚РѕРёРјРѕСЃС‚СЊ РїРµСЂРµРјРµС‰РµРЅРёСЏ С‡РµСЂРµР· Р±Р»РѕРє
     * @param world РњРёСЂ
     * @param x РљРѕРѕСЂРґРёРЅР°С‚Р° X Р±Р»РѕРєР°
     * @param y РљРѕРѕСЂРґРёРЅР°С‚Р° Y Р±Р»РѕРєР°
     * @param z РљРѕРѕСЂРґРёРЅР°С‚Р° Z Р±Р»РѕРєР°
     * @return РЎС‚РѕРёРјРѕСЃС‚СЊ РїРµСЂРµРјРµС‰РµРЅРёСЏ (1.0 РґР»СЏ РѕР±С‹С‡РЅС‹С… Р±Р»РѕРєРѕРІ, Р±РѕР»СЊС€Рµ РґР»СЏ Р¶РёРґРєРѕСЃС‚РµР№)
     */
    public static double getMovementCost(World world, int x, int y, int z) {
        if (world == null) return 1.0;
        if (!world.blockExists(x, y, z)) return 1.0;

        Block block = world.getBlock(x, y, z);
        if (block == null || block instanceof BlockAir) {
            return 1.0;
        }

        if (isWater(block)) {
            return 3.5; // СЃРёР»СЊРЅС‹Р№ С€С‚СЂР°С„ РІ РІРѕРґРµ
        }

        if (isLava(block)) {
            return 10.0; // РїРѕ Р»Р°РІРµ РёРґС‚Рё РЅРµ СЃС‚РѕРёС‚
        }

        if (block instanceof BlockWeb) {
            return 12.0;
        }

        if (block.getMaterial() == Material.snow) {
            return 2.0;
        }

        return 1.0;
    }

    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, СЏРІР»СЏРµС‚СЃСЏ Р»Рё Р±Р»РѕРє РІРѕРґРѕР№
     */
    public static boolean isWaterBlock(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) return false;
        Block block = world.getBlock(x, y, z);
        return isWater(block);
    }

    /**
     * РќР°С…РѕРґРёС‚ СѓСЂРѕРІРµРЅСЊ РІРѕРґС‹ (РїРѕРІРµСЂС…РЅРѕСЃС‚СЊ) РІ РґР°РЅРЅРѕР№ РїРѕР·РёС†РёРё
     * @return Y-РєРѕРѕСЂРґРёРЅР°С‚Сѓ РїРѕРІРµСЂС…РЅРѕСЃС‚Рё РІРѕРґС‹ РёР»Рё null РµСЃР»Рё РІРѕРґС‹ РЅРµС‚
     */
    public static Integer findWaterSurface(World world, int x, int startY, int z) {
        if (world == null) return null;

        // РС‰РµРј РІРІРµСЂС… РґРѕ РїРѕРІРµСЂС…РЅРѕСЃС‚Рё РІРѕРґС‹
        for (int y = startY; y <= Math.min(startY + 10, 255); y++) {
            boolean currentIsWater = isWaterBlock(world, x, y, z);
            boolean aboveIsAir = !world.blockExists(x, y + 1, z) ||
                    world.getBlock(x, y + 1, z) instanceof BlockAir;

            if (currentIsWater && aboveIsAir) {
                return y; // Р­С‚Рѕ РїРѕРІРµСЂС…РЅРѕСЃС‚СЊ РІРѕРґС‹
            }
        }

        return null;
    }

    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, РЅР°С…РѕРґРёС‚СЃСЏ Р»Рё РїРѕР·РёС†РёСЏ РІ РІРѕРґРµ
     */
    public static boolean isInWater(World world, int x, int y, int z) {
        // y is ground; feet is y+1
        return isWaterBlock(world, x, y, z) || isWaterBlock(world, x, YMath.feetFromGround(y), z);
    }

    /**
     * РњРѕР¶РµС‚ Р»Рё РёРіСЂРѕРє РїР»Р°РІР°С‚СЊ РІ СЌС‚РѕР№ РїРѕР·РёС†РёРё
     */
    public static boolean canSwimAt(World world, int x, int y, int z) {
        // Water at ground or just below (submerged feet)
        boolean hasWater = isWaterBlock(world, x, y, z) || isWaterBlock(world, x, YMath.groundBelow(y), z);
        if (!hasWater) return false;

        int feetY = YMath.feetFromGround(y);
        int headY = YMath.headFromGround(y);
        Block feet = world.getBlock(x, feetY, z);
        Block head = world.getBlock(x, headY, z);
        if (feet != null && !(feet instanceof BlockAir) && !isWater(feet)) return false;
        if (head != null && !(head instanceof BlockAir) && !isWater(head)) return false;
        return true;
    }




    /**
     * РќРћР’Р«Р™ РњР•РўРћР” - РРЎРџР РђР’Р›Р•РќРќРђРЇ Р’Р•Р РЎРРЇ
     * РџСЂРѕРІРµСЂСЏРµС‚, РµСЃС‚СЊ Р»Рё РїСЂСЏРјР°СЏ РІРёРґРёРјРѕСЃС‚СЊ РјРµР¶РґСѓ РёРіСЂРѕРєРѕРј Рё С†РµР»РµРІРѕР№ С‚РѕС‡РєРѕР№.
     * РСЃРїРѕР»СЊР·СѓРµС‚ ray-tracing РґР»СЏ РѕР±РЅР°СЂСѓР¶РµРЅРёСЏ РїСЂРµРїСЏС‚СЃС‚РІРёР№.
     * @param player РРіСЂРѕРє
     * @param targetNode Р¦РµР»РµРІРѕР№ СѓР·РµР» РїСѓС‚Рё
     * @return true, РµСЃР»Рё РїСѓС‚СЊ СЃРІРѕР±РѕРґРµРЅ РѕС‚ С‚РІРµСЂРґС‹С… Р±Р»РѕРєРѕРІ.
     */
    public static boolean hasClearLineOfSight(EntityPlayer player, PathNode targetNode) {
        if (player == null || targetNode == null) {
            return false;
        }

        World world = player.worldObj;

        // РќР°С‡Р°Р»СЊРЅР°СЏ С‚РѕС‡РєР°: РіР»Р°Р·Р° РёРіСЂРѕРєР°
        Vec3 startVec = Vec3.createVectorHelper(player.posX, player.boundingBox.minY + player.getEyeHeight(), player.posZ);

        // РљРѕРЅРµС‡РЅР°СЏ С‚РѕС‡РєР°: С†РµРЅС‚СЂ С†РµР»РµРІРѕРіРѕ СѓР·Р»Р°
        Vec3 endVec = Vec3.createVectorHelper(targetNode.x + 0.5, targetNode.getCenterY(), targetNode.z + 0.5);

        // --- РРЎРџР РђР’Р›Р•РќРР• ---
        // РСЃРїРѕР»СЊР·СѓРµРј РїСЂР°РІРёР»СЊРЅСѓСЋ РІРµСЂСЃРёСЋ РјРµС‚РѕРґР° rayTraceBlocks СЃ С‚СЂРµРјСЏ Р°СЂРіСѓРјРµРЅС‚Р°РјРё.
        // РўСЂРµС‚РёР№ РїР°СЂР°РјРµС‚СЂ (false) РѕР·РЅР°С‡Р°РµС‚ "РЅРµ РѕСЃС‚Р°РЅР°РІР»РёРІР°С‚СЊСЃСЏ РЅР° Р¶РёРґРєРѕСЃС‚СЏС…", С‡С‚Рѕ РЅР°Рј Рё РЅСѓР¶РЅРѕ.
        MovingObjectPosition result = world.rayTraceBlocks(startVec, endVec, false);

        // Р›РѕРіРёРєР° РїСЂРѕРІРµСЂРєРё СЂРµР·СѓР»СЊС‚Р°С‚Р° РѕСЃС‚Р°РµС‚СЃСЏ С‚РѕР№ Р¶Рµ: null РѕР·РЅР°С‡Р°РµС‚, С‡С‚Рѕ Р»СѓС‡ РЅРё РІРѕ С‡С‚Рѕ РЅРµ РїРѕРїР°Р».
        return result == null || result.typeOfHit == MovingObjectPosition.MovingObjectType.MISS;
    }

    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, РјРѕР¶РµС‚ Р»Рё РёРіСЂРѕРє РїРѕРґРЅСЏС‚СЊСЃСЏ РЅР° Р±Р»РѕРє (РїСЂРѕРІРµСЂРєР° РЅР° РІРѕР·РјРѕР¶РЅРѕСЃС‚СЊ РїСЂС‹Р¶РєР°)
     * @param world РњРёСЂ
     * @param fromX РќР°С‡Р°Р»СЊРЅР°СЏ X РєРѕРѕСЂРґРёРЅР°С‚Р°
     * @param fromY РќР°С‡Р°Р»СЊРЅР°СЏ Y РєРѕРѕСЂРґРёРЅР°С‚Р°
     * @param fromZ РќР°С‡Р°Р»СЊРЅР°СЏ Z РєРѕРѕСЂРґРёРЅР°С‚Р°
     * @param toX Р¦РµР»РµРІР°СЏ X РєРѕРѕСЂРґРёРЅР°С‚Р°
     * @param toY Р¦РµР»РµРІР°СЏ Y РєРѕРѕСЂРґРёРЅР°С‚Р°
     * @param toZ Р¦РµР»РµРІР°СЏ Z РєРѕРѕСЂРґРёРЅР°С‚Р°
     * @param maxClimb РњР°РєСЃРёРјР°Р»СЊРЅР°СЏ РІС‹СЃРѕС‚Р° РїРѕРґСЉРµРјР° (РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ 1.0)
     * @return true РµСЃР»Рё РјРѕР¶РЅРѕ РїРѕРґРЅСЏС‚СЊСЃСЏ
     */
    public static boolean canClimb(World world, double fromX, double fromY, double fromZ, 
                                   double toX, double toY, double toZ, double maxClimb) {
        if (world == null) return false;

        double deltaY = toY - fromY;
        if (deltaY > maxClimb || deltaY < -maxClimb * 2) {
            return false;
        }

        // РџСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ С†РµР»РµРІР°СЏ РїРѕР·РёС†РёСЏ РґРѕСЃС‚СѓРїРЅР°
        if (!canStandAt(world, toX, toY, toZ)) {
            return false;
        }

        // Р•СЃР»Рё РїРѕРґРЅРёРјР°РµРјСЃСЏ, РїСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ РЅРµС‚ РїСЂРµРїСЏС‚СЃС‚РІРёР№ РЅР° РїСѓС‚Рё
        if (deltaY > 0) {
            double checkY = fromY + 1.8; // Р’С‹СЃРѕС‚Р° РёРіСЂРѕРєР°
            if (!isSpaceFree(world, toX, checkY, toZ)) {
                return false;
            }
        }

        return true;
    }

    /**
     * РџРѕР»СѓС‡Р°РµС‚ РІС‹СЃРѕС‚Сѓ Р±Р»РѕРєР° РІ СѓРєР°Р·Р°РЅРЅРѕР№ РїРѕР·РёС†РёРё (РґР»СЏ РЅР°С…РѕР¶РґРµРЅРёСЏ РїРѕРІРµСЂС…РЅРѕСЃС‚Рё)
     * @param world РњРёСЂ
     * @param x РљРѕРѕСЂРґРёРЅР°С‚Р° X
     * @param z РљРѕРѕСЂРґРёРЅР°С‚Р° Z
     * @param startY РќР°С‡Р°Р»СЊРЅР°СЏ Y РєРѕРѕСЂРґРёРЅР°С‚Р° РґР»СЏ РїРѕРёСЃРєР°
     * @return Y РєРѕРѕСЂРґРёРЅР°С‚Р° РїРѕРІРµСЂС…РЅРѕСЃС‚Рё РёР»Рё startY РµСЃР»Рё РЅРµ РЅР°Р№РґРµРЅР°
     */
    public static double getSurfaceHeight(World world, double x, double z, double startY) {
        if (world == null) return startY;

        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        int blockY = MathHelper.floor_double(startY);

        // РС‰РµРј РІРЅРёР· РґРѕ С‚РІРµСЂРґРѕРіРѕ Р±Р»РѕРєР°
        for (int y = blockY; y >= blockY - SURFACE_SEARCH_RANGE; y--) {
            if (canStandOn(world, blockX, y, blockZ)) {
                return y + 1.0; // Р’РѕР·РІСЂР°С‰Р°РµРј РїРѕР·РёС†РёСЋ РЅР°Рґ Р±Р»РѕРєРѕРј
            }
        }

        // РС‰РµРј РІРІРµСЂС…
        for (int y = blockY + 1; y <= blockY + SURFACE_SEARCH_RANGE; y++) {
            if (canStandOn(world, blockX, y, blockZ)) {
                return y + 1.0;
            }
        }

        return startY;
    }

    public static boolean isDangerousToStand(World world, int x, int y, int z) {
        if (world == null) {
            return true;
        }
        if (y < 0 || y > world.getHeight() + 1) {
            return true;
        }

        Block blockAtFeet = world.blockExists(x, y, z) ? world.getBlock(x, y, z) : null;
        int groundY = y - 1; // world feet Y -> ground Y
        Block blockBelow = world.blockExists(x, groundY, z) ? world.getBlock(x, groundY, z) : null;

        if (isDangerousBlock(blockAtFeet) || isLava(blockAtFeet)) {
            return true;
        }

        if (isDangerousBlock(blockBelow) || isLava(blockBelow)) {
            return true;
        }

        return false;
    }

    public static boolean isDangerousBlock(Block block) {
        if (block == null) return false;
        return block instanceof BlockFire
                || block instanceof BlockCactus
                || block.getMaterial() == Material.lava;
    }

    public static boolean isWater(Block block) {
        return block instanceof BlockLiquid && block.getMaterial() == Material.water;
    }

    public static boolean isLava(Block block) {
        return block instanceof BlockLiquid && block.getMaterial() == Material.lava;
    }

    public static double estimateFallDamage(double dropHeight) {
        if (dropHeight <= 3.0) {
            return 0.0;
        }
        return dropHeight - 3.0;
    }

    public static int findSafeDropY(World world, int x, int startY, int z, int maxDrop) {
        if (world == null) return Integer.MIN_VALUE;
        int lowest = Math.max(1, startY - maxDrop);

        for (int y = startY; y >= lowest; y--) {
            if (canStandAt(world, x + 0.5, YMath.feetFromGround(y) + 0.01, z + 0.5)) {
                return y;
            }
            if (!isBlockPassable(world, x, y, z)) {
                break;
            }
        }
        return Integer.MIN_VALUE;
    }
    /**
     * Рассчитывает реальное время (в тиках) на разрушение блока,
     * учитывая лучший инструмент в хотбаре, эффекты и окружение.
     */
    public static double calculateBestBreakTime(Block block, int meta, EntityPlayer player) {
        if (block == null || block instanceof BlockAir) return 0;

        // Получаем прочность блока. Если < 0, значит блок неразрушим (бедрок)
        float hardness = block.getBlockHardness(player.worldObj, 0, 0, 0);
        if (hardness < 0) return 100000.0; // Огромный штраф
        if (hardness == 0) return 0;

        // 1. Ищем максимальную скорость добычи среди предметов в хотбаре (слоты 0-8)
        float bestToolSpeed = 1.0F;
        boolean canHarvest = block.getMaterial().isToolNotRequired();

        // Проверяем текущий предмет в руке
        ItemStack held = player.getHeldItem();
        if (held != null) {
            float speed = held.getItem().getDigSpeed(held, block, meta);
            if (speed > bestToolSpeed) {
                bestToolSpeed = speed;
            }
            if (!canHarvest && block.canHarvestBlock(player, meta)) {
                canHarvest = true;
            }
        }

        // Сканируем остальные слоты хотбара
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null) {
                float speed = stack.getItem().getDigSpeed(stack, block, meta);
                if (speed > bestToolSpeed) {
                    bestToolSpeed = speed;
                }
            }
        }

        // 2. Учитываем эффективность (Efficiency)
        // Берем текущий модификатор с игрока (упрощение для скорости)
        int efficiencyLevel = EnchantmentHelper.getEfficiencyModifier(player);
        if (efficiencyLevel > 0 && bestToolSpeed > 1.0F) {
            bestToolSpeed += (efficiencyLevel * efficiencyLevel + 1);
        }

        // 3. Учитываем эффекты зелий (Haste / Mining Fatigue)
        float speedMultiplier = bestToolSpeed;

        if (player.isPotionActive(Potion.digSpeed)) {
            int haste = player.getActivePotionEffect(Potion.digSpeed).getAmplifier();
            speedMultiplier *= (1.0F + (haste + 1) * 0.2F);
        }

        if (player.isPotionActive(Potion.digSlowdown)) {
            int fatigue = player.getActivePotionEffect(Potion.digSlowdown).getAmplifier();
            float fatigueFactor;
            switch (fatigue) {
                case 0: fatigueFactor = 0.3F; break;
                case 1: fatigueFactor = 0.09F; break;
                case 2: fatigueFactor = 0.0027F; break;
                default: fatigueFactor = 8.1E-4F; break; // Очень медленно
            }
            speedMultiplier *= fatigueFactor;
        }

        // 4. Штрафы среды (вода, полет)
        if (player.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(player)) {
            speedMultiplier /= 5.0F;
        }
        if (!player.onGround) {
            speedMultiplier /= 5.0F;
        }

        // 5. Итоговый расчет по формуле Minecraft
        // Damage = Speed / Hardness / (CanHarvest ? 30 : 100)
        float damagePerTick = speedMultiplier / hardness / (canHarvest ? 30.0F : 100.0F);

        // Если ломается моментально (Insta-mine)
        if (damagePerTick >= 1.0F) return 0;

        // Возвращаем количество тиков (округляем вверх)
        return Math.ceil(1.0F / damagePerTick);
    }


    public static Integer findBestStandingY(World world, int x, int currentY, int z,
                                            double maxStepUp, double maxDrop) {
        if (world == null) {
            return null;
        }

        // РЎРЅР°С‡Р°Р»Р° РїСЂРѕРІРµСЂСЏРµРј С‚РµРєСѓС‰СѓСЋ РІС‹СЃРѕС‚Сѓ
        if (canStandAt(world, x + 0.5, YMath.feetFromGround(currentY) + 0.01, z + 0.5)
                // РћРїР°СЃРЅРѕСЃС‚СЊ РѕС†РµРЅРёРІР°РµРј РЅР° СѓСЂРѕРІРЅРµ РЅРѕРі РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ РѕРїРѕСЂС‹
                && !isDangerousToStand(world, x, YMath.feetFromGround(currentY), z)) {
            return currentY;
        }

        // РџР РРћР РРўР•Рў: СЃРЅР°С‡Р°Р»Р° РІРІРµСЂС…, РїРѕС‚РѕРј РІРЅРёР· (С‡С‚РѕР±С‹ РЅРµ "РЅС‹СЂСЏС‚СЊ")
        int stepUpperBound = Math.max(0, (int) Math.ceil(maxStepUp));
        int maxDropInt = Math.max(0, (int) Math.floor(maxDrop));

        // РЎРЅР°С‡Р°Р»Р° РёС‰РµРј Р’РЎР• РІРѕР·РјРѕР¶РЅС‹Рµ РїРѕР·РёС†РёРё РІРІРµСЂС…
        for (int offset = 1; offset <= stepUpperBound; offset++) {
            int candidateY = currentY + offset;
            boolean canStand = canStandAt(world, x + 0.5, YMath.feetFromGround(candidateY) + 0.01, z + 0.5);
            // РћРїР°СЃРЅРѕСЃС‚СЊ РѕС†РµРЅРёРІР°РµРј РЅР° СѓСЂРѕРІРЅРµ РЅРѕРі РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ РѕРїРѕСЂС‹
            boolean dangerous = isDangerousToStand(world, x, YMath.feetFromGround(candidateY), z);
            
            if (canStand && !dangerous) {
                return candidateY;
            }
        }

        // РљР РРўРР§РќРћ: РЎРџРЈРЎРљР РџРћР›РќРћРЎРўР¬Р® Р—РђРџР Р•Р©Р•РќР«! РќРµ РёС‰РµРј РІРЅРёР· РІРѕРѕР±С‰Рµ
        // Р•СЃР»Рё РЅРµР»СЊР·СЏ СЃС‚РѕСЏС‚СЊ РЅР° С‚РµРєСѓС‰РµРј Y Рё РЅРµР»СЊР·СЏ РїРѕРґРЅСЏС‚СЊСЃСЏ - РІРѕР·РІСЂР°С‰Р°РµРј null
        // Р­С‚Рѕ Р·Р°СЃС‚Р°РІРёС‚ Р°Р»РіРѕСЂРёС‚Рј РёСЃРєР°С‚СЊ РїСѓС‚СЊ РІ РІРѕР·РґСѓС…Рµ РёР»Рё РїРѕРґРЅРёРјР°С‚СЊСЃСЏ РІС‹С€Рµ
        return null;
    }

    /**
     * РџРѕРёСЃРє СѓСЂРѕРІРЅСЏ СЃС‚РѕСЏРЅРёСЏ СЃ СЂР°Р·СЂРµС€РµРЅРёРµРј СЃРїСѓСЃРєРѕРІ (РґР»СЏ Baritone-РїРѕРґРѕР±РЅРѕРіРѕ СЃРїСѓСЃРєР°/РїР°РґРµРЅРёСЏ)
     */
    public static Integer findStandingYAllowingDown(World world, int x, int currentY, int z,
                                                    double maxStepUp, double maxDrop) {
        if (world == null) return null;
        // С‚РµРєСѓС‰РёР№
        if (canStandAt(world, x + 0.5, YMath.feetFromGround(currentY) + 0.01, z + 0.5) && !isDangerousToStand(world, x, YMath.feetFromGround(currentY), z)) {
            return currentY;
        }
        // РІРІРµСЂС…
        int up = Math.max(0, (int) Math.ceil(maxStepUp));
        for (int dy = 1; dy <= up; dy++) {
            int y = currentY + dy;
            if (canStandAt(world, x + 0.5, YMath.feetFromGround(y) + 0.01, z + 0.5) && !isDangerousToStand(world, x, YMath.feetFromGround(y), z)) {
                return y;
            }
        }
        // РІРЅРёР·
        int down = Math.max(0, (int) Math.floor(maxDrop));
        for (int dy = 1; dy <= down; dy++) {
            int y = currentY - dy;
            if (canStandAt(world, x + 0.5, YMath.feetFromGround(y) + 0.01, z + 0.5) && !isDangerousToStand(world, x, YMath.feetFromGround(y), z)) {
                return y;
            }
        }
        return null;
    }

    private static boolean isColumnClearForDrop(World world, int x, int targetY, int currentY, int z) {
        // РџСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ РјРµР¶РґСѓ С‚РµРєСѓС‰РµР№ Рё С†РµР»РµРІРѕР№ РІС‹СЃРѕС‚РѕР№ РЅРµС‚ Р±Р»РѕРєРѕРІ
        int min = Math.min(targetY, currentY);
        int max = Math.max(targetY, currentY);
        for (int y = min; y <= max; y++) {
            if (!isSpaceFree(world, x + 0.5, y + 0.5, z + 0.5)) {
                return false;
            }
        }
        return true;
    }
}
