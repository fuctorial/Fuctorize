package ru.fuctorial.fuctorize.utils.pathfinding;

import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;

/**
 * Простой in-memory кэш классификации блоков (AIR/SOLID/WATER/AVOID).
 * Ключ: x,y,z упакованы в long.
 */
class SimpleBlockCache {
    private final Map<Long, PathfindingUtils.BlockClass> cache = new HashMap<Long, PathfindingUtils.BlockClass>(8192);
    private final World world;

    public SimpleBlockCache(World world) {
        this.world = world;
    }

    public PathfindingUtils.BlockClass get(int x, int y, int z) {
        long key = pack(x, y, z);
        PathfindingUtils.BlockClass cls = cache.get(key);
        if (cls != null) return cls;
        cls = PathfindingUtils.classifyBlock(world, x, y, z);
        cache.put(key, cls);
        return cls;
    }

    private static long pack(int x, int y, int z) {
        long lx = ((long)x & 0x3FFFFFFL);
        long ly = ((long)y & 0xFFFL);
        long lz = ((long)z & 0x3FFFFFFL);
        return (lx << 38) | (lz << 12) | ly;
    }
}

