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

 
public class PathfindingUtils {

    public enum BlockClass {
        AIR,     
        SOLID,   
        WATER,   
        AVOID    
    }

     
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

     
    public static boolean canStandOn(World world, int x, int y, int z) {
        if (world == null) return false;
        if (!world.blockExists(x, y, z)) return false;

        Block block = world.getBlock(x, y, z);
        if (block == null || block instanceof BlockAir) {
            return false;
        }

         
        AxisAlignedBB boundingBox = block.getCollisionBoundingBoxFromPool(world, x, y, z);
        return boundingBox != null;
    }

     
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


     
    public static boolean isBlockReachable(int x, int y, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) return false;

         
         
        if (player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 25.0) {
            return false;
        }

         
         
        Vec3 eyesPos = Vec3.createVectorHelper(player.posX, player.boundingBox.minY + player.getEyeHeight(), player.posZ);
        Vec3 blockCenter = Vec3.createVectorHelper(x + 0.5, y + 0.5, z + 0.5);

        MovingObjectPosition mop = player.worldObj.rayTraceBlocks(eyesPos, blockCenter, false);

         
         
        if (mop == null || (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && mop.blockX == x && mop.blockY == y && mop.blockZ == z)) {
            return true;
        }

         
        return false;
    }

     
    public static boolean isBlockSolid(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) {
            return true;  
        }
        Block block = world.getBlock(x, y, z);
        if (block == null || block instanceof BlockAir) {
            return false;
        }
         
        return block.getCollisionBoundingBoxFromPool(world, x, y, z) != null;
    }

     
    public static double calculateBlockBreakTime(Block block, EntityPlayer player, World world, int x, int y, int z) {
        if (block == null || block.getBlockHardness(world, x, y, z) < 0) {
            return Double.POSITIVE_INFINITY;  
        }

        float hardness = block.getBlockHardness(world, x, y, z);
        if (hardness == 0) return 0.0;

        ItemStack heldItem = player.getHeldItem();
        int meta = world.getBlockMetadata(x, y, z);
        boolean canHarvest = block.canHarvestBlock(player, meta);

         
        float baseTime = hardness * (canHarvest ? 1.5F : 5.0F);

         
        float toolSpeed = 1.0F;  
        if (heldItem != null) {
            toolSpeed = heldItem.getItem().getDigSpeed(heldItem, block, meta);
            if (toolSpeed > 1.0F) {
                 
                int efficiencyLevel = EnchantmentHelper.getEfficiencyModifier(player);
                if (efficiencyLevel > 0) {
                    toolSpeed += (efficiencyLevel * efficiencyLevel + 1);
                }
            }
        }

         
        float potionMultiplier = 1.0F;

        if (player.isPotionActive(Potion.digSpeed)) {
            int hasteLevel = player.getActivePotionEffect(Potion.digSpeed).getAmplifier();
            potionMultiplier *= (1.0F + (hasteLevel + 1) * 0.2F);
        }

        if (player.isPotionActive(Potion.digSlowdown)) {
            int fatigueLevel = player.getActivePotionEffect(Potion.digSlowdown).getAmplifier();
             
            switch (fatigueLevel) {
                case 0: potionMultiplier *= 0.3F; break;
                case 1: potionMultiplier *= 0.09F; break;
                case 2: potionMultiplier *= 0.0027F; break;
                default: potionMultiplier *= Math.pow(0.3F, fatigueLevel + 1); break;
            }
        }

         
        float conditionMultiplier = 1.0F;

         
        if (player.isInWater() && !EnchantmentHelper.getAquaAffinityModifier(player)) {
            conditionMultiplier *= 0.2F;
        }

         
        if (!player.onGround) {
            conditionMultiplier *= 0.2F;
        }

         
        float speedMultiplier = toolSpeed * potionMultiplier * conditionMultiplier;

        if (speedMultiplier <= 0) return Double.POSITIVE_INFINITY;

        double breakTime = baseTime / speedMultiplier;

         
        breakTime = Math.ceil(breakTime * 20.0) / 20.0;

        return breakTime;
    }


     
    public static boolean hasClearLineOfSight(World world, PathNode from, PathNode to) {
        if (world == null || from == null || to == null) {
            return false;
        }

         
         
        Vec3 startVec = Vec3.createVectorHelper(from.x + 0.5, from.getFeetY() + 0.1, from.z + 0.5);
        Vec3 endVec = Vec3.createVectorHelper(to.x + 0.5, to.getCenterY(), to.z + 0.5);

         
         
        MovingObjectPosition result = world.rayTraceBlocks(startVec, endVec, false);

         
        return result == null;
    }



     
    public static boolean canStandAt(World world, double x, double y, double z) {
         
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

     
    public static double getMovementCost(World world, int x, int y, int z) {
        if (world == null) return 1.0;
        if (!world.blockExists(x, y, z)) return 1.0;

        Block block = world.getBlock(x, y, z);
        if (block == null || block instanceof BlockAir) {
            return 1.0;
        }

        if (isWater(block)) {
            return 3.5;  
        }

        if (isLava(block)) {
            return 10.0;  
        }

        if (block instanceof BlockWeb) {
            return 12.0;
        }

        if (block.getMaterial() == Material.snow) {
            return 2.0;
        }

        return 1.0;
    }

     
    public static boolean isWaterBlock(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) return false;
        Block block = world.getBlock(x, y, z);
        return isWater(block);
    }

     
    public static Integer findWaterSurface(World world, int x, int startY, int z) {
        if (world == null) return null;

         
        for (int y = startY; y <= Math.min(startY + 10, 255); y++) {
            boolean currentIsWater = isWaterBlock(world, x, y, z);
            boolean aboveIsAir = !world.blockExists(x, y + 1, z) ||
                    world.getBlock(x, y + 1, z) instanceof BlockAir;

            if (currentIsWater && aboveIsAir) {
                return y;  
            }
        }

        return null;
    }

     
    public static boolean isInWater(World world, int x, int y, int z) {
         
        return isWaterBlock(world, x, y, z) || isWaterBlock(world, x, YMath.feetFromGround(y), z);
    }

     
    public static boolean canSwimAt(World world, int x, int y, int z) {
         
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




     
    public static boolean hasClearLineOfSight(EntityPlayer player, PathNode targetNode) {
        if (player == null || targetNode == null) {
            return false;
        }

        World world = player.worldObj;

         
        Vec3 startVec = Vec3.createVectorHelper(player.posX, player.boundingBox.minY + player.getEyeHeight(), player.posZ);

         
        Vec3 endVec = Vec3.createVectorHelper(targetNode.x + 0.5, targetNode.getCenterY(), targetNode.z + 0.5);

         
         
         
        MovingObjectPosition result = world.rayTraceBlocks(startVec, endVec, false);

         
        return result == null || result.typeOfHit == MovingObjectPosition.MovingObjectType.MISS;
    }

     
    public static boolean canClimb(World world, double fromX, double fromY, double fromZ, 
                                   double toX, double toY, double toZ, double maxClimb) {
        if (world == null) return false;

        double deltaY = toY - fromY;
        if (deltaY > maxClimb || deltaY < -maxClimb * 2) {
            return false;
        }

         
        if (!canStandAt(world, toX, toY, toZ)) {
            return false;
        }

         
        if (deltaY > 0) {
            double checkY = fromY + 1.8;  
            if (!isSpaceFree(world, toX, checkY, toZ)) {
                return false;
            }
        }

        return true;
    }

     
    public static double getSurfaceHeight(World world, double x, double z, double startY) {
        if (world == null) return startY;

        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        int blockY = MathHelper.floor_double(startY);

         
        for (int y = blockY; y >= blockY - SURFACE_SEARCH_RANGE; y--) {
            if (canStandOn(world, blockX, y, blockZ)) {
                return y + 1.0;  
            }
        }

         
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
        int groundY = y - 1;  
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
     
    public static double calculateBestBreakTime(Block block, int meta, EntityPlayer player) {
        if (block == null || block instanceof BlockAir) return 0;

         
        float hardness = block.getBlockHardness(player.worldObj, 0, 0, 0);
        if (hardness < 0) return 100000.0;  
        if (hardness == 0) return 0;

         
        float bestToolSpeed = 1.0F;
        boolean canHarvest = block.getMaterial().isToolNotRequired();

         
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

         
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null) {
                float speed = stack.getItem().getDigSpeed(stack, block, meta);
                if (speed > bestToolSpeed) {
                    bestToolSpeed = speed;
                }
            }
        }

         
         
        int efficiencyLevel = EnchantmentHelper.getEfficiencyModifier(player);
        if (efficiencyLevel > 0 && bestToolSpeed > 1.0F) {
            bestToolSpeed += (efficiencyLevel * efficiencyLevel + 1);
        }

         
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
                default: fatigueFactor = 8.1E-4F; break;  
            }
            speedMultiplier *= fatigueFactor;
        }

         
        if (player.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(player)) {
            speedMultiplier /= 5.0F;
        }
        if (!player.onGround) {
            speedMultiplier /= 5.0F;
        }

         
         
        float damagePerTick = speedMultiplier / hardness / (canHarvest ? 30.0F : 100.0F);

         
        if (damagePerTick >= 1.0F) return 0;

         
        return Math.ceil(1.0F / damagePerTick);
    }


    public static Integer findBestStandingY(World world, int x, int currentY, int z,
                                            double maxStepUp, double maxDrop) {
        if (world == null) {
            return null;
        }

         
        if (canStandAt(world, x + 0.5, YMath.feetFromGround(currentY) + 0.01, z + 0.5)
                 
                && !isDangerousToStand(world, x, YMath.feetFromGround(currentY), z)) {
            return currentY;
        }

         
        int stepUpperBound = Math.max(0, (int) Math.ceil(maxStepUp));
        int maxDropInt = Math.max(0, (int) Math.floor(maxDrop));

         
        for (int offset = 1; offset <= stepUpperBound; offset++) {
            int candidateY = currentY + offset;
            boolean canStand = canStandAt(world, x + 0.5, YMath.feetFromGround(candidateY) + 0.01, z + 0.5);
             
            boolean dangerous = isDangerousToStand(world, x, YMath.feetFromGround(candidateY), z);
            
            if (canStand && !dangerous) {
                return candidateY;
            }
        }

         
         
         
        return null;
    }

     
    public static Integer findStandingYAllowingDown(World world, int x, int currentY, int z,
                                                    double maxStepUp, double maxDrop) {
        if (world == null) return null;
         
        if (canStandAt(world, x + 0.5, YMath.feetFromGround(currentY) + 0.01, z + 0.5) && !isDangerousToStand(world, x, YMath.feetFromGround(currentY), z)) {
            return currentY;
        }
         
        int up = Math.max(0, (int) Math.ceil(maxStepUp));
        for (int dy = 1; dy <= up; dy++) {
            int y = currentY + dy;
            if (canStandAt(world, x + 0.5, YMath.feetFromGround(y) + 0.01, z + 0.5) && !isDangerousToStand(world, x, YMath.feetFromGround(y), z)) {
                return y;
            }
        }
         
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
