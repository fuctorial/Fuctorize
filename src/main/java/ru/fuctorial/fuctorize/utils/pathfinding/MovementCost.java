package ru.fuctorial.fuctorize.utils.pathfinding;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import java.util.Map;

final class MovementCost {
    private MovementCost() {}

    static double compute(World world,
                          EntityPlayer player,
                          SimpleBlockCache blockCache,
                          Map<PathNode, Double> temporaryPenalties,
                          PathNode from,
                          PathNode to) {
        if (player == null) return 1000.0;

        final int dy = to.y - from.y;
        final boolean diagonal = (to.x != from.x) && (to.z != from.z);
        double baseTicks;

        // Базовая стоимость перемещения (ходьба/плавание)
        PathfindingUtils.BlockClass feet = blockCache.get(to.x, YMath.feetFromGround(to.y), to.z);
        if (to.moveType == MovementType.SWIM_SURFACE) {
            baseTicks = PathingConfig.TICKS_SWIM_SURFACE;
        } else if (to.moveType == MovementType.SWIM_UNDERWATER) {
            baseTicks = PathingConfig.TICKS_SWIM_UNDERWATER;
        } else if (feet == PathfindingUtils.BlockClass.WATER) {
            baseTicks = PathingConfig.TICKS_WATER;
        } else {
            baseTicks = PathingConfig.TICKS_WALK;
        }

        if (to.moveType == MovementType.PARKOUR) {
            baseTicks += PathingConfig.PARKOUR_PENALTY_TICKS;
        }

        if (dy > 0) baseTicks += PathingConfig.TICKS_LADDER_UP; // Или просто +1.0, если прыжок
        else if (dy < 0) {
            // Стоимость падения (урон переводим в "время", чтобы бот боялся боли)
            int drop = -dy;
            if (drop > 3) {
                double fallDamage = PathfindingUtils.estimateFallDamage(drop);
                // 1 единица урона = 15 тикам задержки (психологический барьер)
                baseTicks += drop * 2.0 + fallDamage * 15.0;
            }
        }

        if (diagonal) {
            baseTicks *= PathingConfig.DIAGONAL_COST_FACTOR;
        }

        // --- УМНЫЙ РАСЧЕТ СТОИМОСТИ ЛОМАНИЯ ---
        if (to.breakBlocks > 0) {
            double breakCost = 0;

            // Нам нужно узнать, КАКИЕ блоки ломаются в этом узле.
            // В идеале PathNode должен хранить это, но ради экономии памяти мы пересчитываем.
            // Обычно ломаются блоки:
            // 1. На уровне ног (feetY)
            // 2. На уровне головы (headY)
            // 3. Если DIG UP - блок над головой

            int feetY = YMath.feetFromGround(to.y);
            int headY = YMath.headFromGround(to.y);

            // Проверяем ноги
            if (!PathfindingUtils.isBlockPassable(world, to.x, feetY, to.z)) {
                Block b = world.getBlock(to.x, feetY, to.z);
                int meta = world.getBlockMetadata(to.x, feetY, to.z);
                breakCost += PathfindingUtils.calculateBestBreakTime(b, meta, player);
            }

            // Проверяем голову
            if (!PathfindingUtils.isBlockPassable(world, to.x, headY, to.z)) {
                Block b = world.getBlock(to.x, headY, to.z);
                int meta = world.getBlockMetadata(to.x, headY, to.z);
                breakCost += PathfindingUtils.calculateBestBreakTime(b, meta, player);
            }

            // Если DIG, возможно копаем вверх или вниз
            if (to.moveType == MovementType.DIG) {
                // Для точного расчета можно добавить логику, но head/feet покрывают 90% случаев.
            }

            // Добавляем стоимость переключения инструмента и замаха
            if (breakCost > 0) {
                breakCost += 5.0; // Константа на "тупление" перед ударом
            }

            baseTicks += breakCost;
        }
        // --------------------------------------

        if (to.placeBlocks > 0) {
            baseTicks += PathingConfig.BLOCK_PLACEMENT_PENALTY_TICKS;
        }

        if (feet == PathfindingUtils.BlockClass.AVOID) {
            baseTicks += 1000.0;
        }

        if (temporaryPenalties != null && temporaryPenalties.containsKey(to)) {
            baseTicks += temporaryPenalties.get(to);
        }

        return baseTicks;
    }
}