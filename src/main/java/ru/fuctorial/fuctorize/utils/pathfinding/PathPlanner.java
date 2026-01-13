package ru.fuctorial.fuctorize.utils.pathfinding;

import net.minecraft.world.World;

/**
 * "Dumb" PathPlanner by request.
 * Walks to distant coords without overthinking safety at the destination.
 */
public final class PathPlanner {

    private PathPlanner() {}

    public static PathResult findPathTowardUnloadedTarget(
            PathFinder finder,
            World world,
            double startX, double startY, double startZ,
            double targetX, double targetY, double targetZ, // Аргумент метода (конечная цель)
            double configuredClimbHeight,
            double lastComputedDropHeight
    ) {
        if (finder == null || world == null) {
            return PathResult.failure("World/PathFinder not ready", 0);
        }

        double dx = targetX - startX;
        double dy = targetY - startY;
        double dz = targetZ - startZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Если цель близко (< 20 блоков) - ищем обычный путь прямо к ней
        if (dist < 20.0) {
            return finder.findPath(startX, startY, startZ, targetX, targetY, targetZ);
        }

        // Нормализуем вектор направления
        double ndx = dx / dist;
        double ndz = dz / dist;

        // Ищем точку на расстоянии ~80 блоков (или меньше, если чанки не прогружены)
        // Не пытаемся идти на 300 блоков сразу, A* захлебнется. Идем сегментами.
        double searchDist = Math.min(dist, 80.0);

        // Итерация назад от дальней точки к нам, пока не найдем твердую землю
        for (double d = searchDist; d > 5.0; d -= 5.0) {
            int tx = (int) Math.floor(startX + ndx * d);
            int tz = (int) Math.floor(startZ + ndz * d);

            // Если чанк не загружен - уменьшаем дистанцию и пробуем снова
            // 64 - произвольная высота для проверки существования чанка
            if (!world.blockExists(tx, 64, tz)) {
                continue;
            }

            // Ищем любой валидный Y в этой колонне (от верха до низа)
            // Расширенный диапазон поиска Y (от игрока +20 до -50)
            int pY = (int)startY;

            // ИСПРАВЛЕНО: Переименовали переменную в foundY, чтобы не конфликтовать с аргументом targetY
            Integer foundY = PathfindingUtils.findStandingYAllowingDown(world, tx, pY, tz, 20.0, 50.0);

            if (foundY != null) {
                // НАШЛИ ТОЧКУ!
                // Не проверяем супер-безопасность. Если findStandingY вернул точку - значит стоять можно.
                // Запускаем поиск пути к foundY.
                PathResult res = finder.findPath(startX, startY, startZ, tx + 0.5, foundY, tz + 0.5);

                // Если путь найден (даже частичный) - возвращаем его
                // Разрешаем частичные пути длиннее 5 блоков
                if (res.isSuccess() || (res.isPartial() && res.getPathLength() > 5)) {
                    System.out.println("[PathPlanner] Found intermediate target at dist " + d + " -> " + tx + "," + foundY + "," + tz);
                    return res;
                }
            }
        }

        // Если совсем ничего не нашли (например, мы в океане), возвращаем ошибку.
        // BotNavigator сам попытается пересчитать через секунду.
        return PathResult.failure("No path found toward frontier (dumb mode)", 0);
    }

    // Оставляем метод для совместимости (на случай, если он вызывается где-то еще)
    public static PathResult findPathToNearest(
            PathFinder finder,
            World world,
            double startX, double startY, double startZ,
            double targetX, double targetY, double targetZ,
            int searchRadius,
            double configuredClimbHeight,
            double lastComputedDropHeight
    ) {
        if (finder == null || world == null) return PathResult.failure("Not ready", 0);

        // Простая заглушка: пробуем прямой путь, так как "умный поиск" мы отключили
        return finder.findPath(startX, startY, startZ, targetX, targetY, targetZ);
    }

    public static double computeSafeDropHeight(net.minecraft.entity.player.EntityPlayer player) {
        return 20.0; // Всегда разрешаем падать высоко в этом режиме
    }
}