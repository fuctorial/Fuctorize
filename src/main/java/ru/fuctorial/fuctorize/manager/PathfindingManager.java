package ru.fuctorial.fuctorize.manager;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.utils.pathfinding.PathFinder;
import ru.fuctorial.fuctorize.utils.pathfinding.PathPlanner;
import ru.fuctorial.fuctorize.utils.pathfinding.PathNode;
import ru.fuctorial.fuctorize.utils.pathfinding.PathResult;
import ru.fuctorial.fuctorize.utils.pathfinding.PathfindingUtils;
import ru.fuctorial.fuctorize.utils.pathfinding.PathingConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Менеджер, отвечающий за инициализацию и вызовы PathFinder.
 * Поддерживает асинхронный поиск и хранит последний рассчитанный маршрут для последующей визуализации.
 */
public class PathfindingManager {

    private static final Minecraft mc = FMLClientHandler.instance().getClient();
    private final FuctorizeClient client;

    private final ExecutorService executorService;

    private PathFinder pathFinder;
    // ИЗМЕНЕНИЕ: Добавлен 'volatile' для потокобезопасности
    private volatile PathResult lastResult;
    private volatile List<PathNode> lastPath;
    private Future<?> currentTask;
    private volatile boolean isCancelled = false;
    private final Map<PathNode, Double> temporaryPenalties = new HashMap<>();
    private double configuredClimbHeight = 1.125D;
    private int configuredMaxNodes = 30000;
    private boolean configuredAllowDiagonal = true;
    private boolean configuredUseEuclidean = true;
    private double lastComputedDropHeight = 4.0D;
    private int configuredPrimaryTimeoutMs = PathingConfig.PRIMARY_TIMEOUT_MS;
    private int configuredFailureTimeoutMs = PathingConfig.FAILURE_TIMEOUT_MS;
    private double configuredHeuristicWeight = PathingConfig.HEURISTIC_WEIGHT;

    public PathfindingManager(FuctorizeClient client) {
        this.client = client;
        this.executorService = Executors.newFixedThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "PathfindingThread");
            thread.setDaemon(true);
            return thread;
        });
        this.lastResult = null;
        this.lastPath = Collections.emptyList();
    }

    public void initialize() {
        rebuildPathFinder();
    }

    public void initialize(double maxClimbHeight,
                           int maxSearchNodes,
                           boolean allowDiagonal,
                           boolean useEuclideanHeuristic) {
        this.configuredClimbHeight = maxClimbHeight;
        this.configuredMaxNodes = maxSearchNodes;
        this.configuredAllowDiagonal = allowDiagonal;
        this.configuredUseEuclidean = useEuclideanHeuristic;
        rebuildPathFinder();
    }

    /**
     * Расширенная инициализация с таймаутами и весом эвристики.
     */
    public void initialize(double maxClimbHeight,
                           int maxSearchNodes,
                           boolean allowDiagonal,
                           boolean useEuclideanHeuristic,
                           int primaryTimeoutMs,
                           int failureTimeoutMs,
                           double heuristicWeight) {
        this.configuredClimbHeight = maxClimbHeight;
        this.configuredMaxNodes = maxSearchNodes;
        this.configuredAllowDiagonal = allowDiagonal;
        this.configuredUseEuclidean = useEuclideanHeuristic;
        this.configuredPrimaryTimeoutMs = primaryTimeoutMs;
        this.configuredFailureTimeoutMs = failureTimeoutMs;
        this.configuredHeuristicWeight = heuristicWeight;
        rebuildPathFinder();
    }

    public void updateWorld() {
        World world = mc.theWorld;
        if (world == null) {
            pathFinder = null;
            return;
        }

        double safeDrop = PathPlanner.computeSafeDropHeight(mc.thePlayer);
        if (pathFinder == null
                || pathFinder.getWorld() != world
                || Math.abs(safeDrop - lastComputedDropHeight) > 0.5D) {
            rebuildPathFinder();
        }
    }

    public PathResult findPathSync(double startX, double startY, double startZ,
                                   double targetX, double targetY, double targetZ) {
        updateWorld();
        if (pathFinder == null) {
            return PathResult.failure("PathFinder no init", 0);
        }

        PathResult result = pathFinder.findPath(startX, startY, startZ, targetX, targetY, targetZ);
        cacheResult(result);
        return result;
    }
    public void resetCancellationFlag() {
        this.isCancelled = false;
    }
    public CompletableFuture<PathResult> findPathAsync(double startX, double startY, double startZ,
                                                       double targetX, double targetY, double targetZ) {
        updateWorld();
        if (pathFinder == null) {
            CompletableFuture<PathResult> future = new CompletableFuture<>();
            future.complete(PathResult.failure("PathFinder no init", 0));
            return future;
        }

        if (currentTask != null && !currentTask.isDone()) {
            cancelSearch(); // Отменяем предыдущую задачу
        }

        final PathFinder finderSnapshot = pathFinder;
        final double sx = startX, sy = startY, sz = startZ;
        final double tx = targetX, ty = targetY, tz = targetZ;

        CompletableFuture<PathResult> future = CompletableFuture.supplyAsync(() -> {
            // Передаем флаг в метод поиска
            PathResult result = finderSnapshot.findPath(sx, sy, sz, tx, ty, tz, this::isCancelled);
            cacheResult(result);
            return result;
        }, executorService);

        currentTask = future;
        return future;
    }

    /**
     * Асинхронный поиск пути к ближайшей доступной позиции вокруг цели в заданном радиусе.
     */
    public CompletableFuture<PathResult> findPathToNearestAsync(double startX, double startY, double startZ,
                                                                double targetX, double targetY, double targetZ,
                                                                int searchRadius) {
        updateWorld();
        if (pathFinder == null) {
            CompletableFuture<PathResult> future = new CompletableFuture<>();
            future.complete(PathResult.failure("PathFinder no init", 0));
            return future;
        }

        if (currentTask != null && !currentTask.isDone()) {
            cancelSearch();
        }

        final double sx = startX, sy = startY, sz = startZ;
        final double tx = targetX, ty = targetY, tz = targetZ;
        final int radius = searchRadius;

        CompletableFuture<PathResult> future = CompletableFuture.supplyAsync(() -> {
            PathResult result = findPathToNearest(sx, sy, sz, tx, ty, tz, radius);
            cacheResult(result);
            return result;
        }, executorService);

        currentTask = future;
        return future;
    }


    public boolean isSearching() {
        return currentTask != null && !currentTask.isDone();
    }

    public void cancelSearch() {
        isCancelled = true; // <-- УСТАНАВЛИВАЕМ НАШ ФЛАГ
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true); // Стандартный механизм прерывания тоже оставляем
        }
    }

    public PathResult getLastResult() {
        return lastResult;
    }
    public boolean isCancelled() {
        return isCancelled;
    }
    public List<PathNode> getLastPathNodes() {
        return lastPath;
    }

    public PathFinder getPathFinder() {
        return pathFinder;
    }

    public void clearLastResult() {
        this.lastResult = null;
        this.lastPath = Collections.emptyList();
    }

    public void shutdown() {
        cancelSearch();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }



    // НОВЫЙ МЕТОД: Добавляет штраф для конкретного узла (блока)
    public void addTemporaryPenalty(PathNode node, double penaltyCost) {
        temporaryPenalties.put(node, penaltyCost);
        System.out.println("Added penalty for node: " + node.x + "," + node.y + "," + node.z);
    }

    // НОВЫЙ МЕТОД: Очищает все временные штрафы
    public void clearTemporaryPenalties() {
        temporaryPenalties.clear();
    }



    /**
     * ИЗМЕНЕНИЕ: Этот метод теперь итеративно ищет ДОСТИЖИМУЮ промежуточную точку.
     * Находит промежуточную точку в направлении цели, находящуюся в загруженных чанках.
     */
    public PathResult findPathTowardUnloadedTarget(double startX, double startY, double startZ,
                                                   double targetX, double targetY, double targetZ) {
        return PathPlanner.findPathTowardUnloadedTarget(
                pathFinder,
                mc.theWorld,
                startX, startY, startZ,
                targetX, targetY, targetZ,
                configuredClimbHeight,
                lastComputedDropHeight
        );
    }

    /**
     * Асинхронная обёртка для findPathTowardUnloadedTarget.
     * Необходима для исправления ошибки компиляции в BotNavigator.
     */
    public CompletableFuture<PathResult> findPathTowardUnloadedTargetAsync(double startX, double startY, double startZ,
                                                                           double targetX, double targetY, double targetZ) {
        updateWorld();
        if (pathFinder == null) {
            CompletableFuture<PathResult> future = new CompletableFuture<>();
            future.complete(PathResult.failure("PathFinder no init", 0));
            return future;
        }

        if (currentTask != null && !currentTask.isDone()) {
            cancelSearch();
        }

        isCancelled = false; // <-- СБРАСЫВАЕМ ФЛАГ

        return CompletableFuture.supplyAsync(() ->
                        findPathTowardUnloadedTarget(startX, startY, startZ, targetX, targetY, targetZ),
                executorService);
    }

    public PathResult findPathToNearest(double startX, double startY, double startZ,
                                        double targetX, double targetY, double targetZ,
                                        int searchRadius) {
        updateWorld();
        if (pathFinder == null) {
            return PathResult.failure("PathFinder no init", 0);
        }

        return PathPlanner.findPathToNearest(
                pathFinder,
                mc.theWorld,
                startX, startY, startZ,
                targetX, targetY, targetZ,
                searchRadius,
                configuredClimbHeight,
                lastComputedDropHeight
        );
    }

    private void cacheResult(PathResult result) {
        this.lastResult = result;
        if (result != null && result.isSuccess()) {
            this.lastPath = result.getPath();
        } else {
            this.lastPath = Collections.emptyList();
        }
    }

    private void rebuildPathFinder() {
        World world = mc.theWorld;
        EntityPlayer player = mc.thePlayer;

        if (world == null || player == null) {
            this.pathFinder = null;
            return;
        }

        double safeDrop = PathPlanner.computeSafeDropHeight(player);
        this.lastComputedDropHeight = safeDrop;

        // Внимательно сверяем этот вызов
        this.pathFinder = new PathFinder(
                world,                       // 1. World world
                player,                      // 2. EntityPlayer player
                configuredClimbHeight,       // 3. double maxClimbHeight
                safeDrop,                    // 4. double maxDropHeight
                configuredMaxNodes,          // 5. int maxSearchNodes
                configuredAllowDiagonal,     // 6. boolean allowDiagonal
                configuredUseEuclidean,      // 7. boolean useEuclideanHeuristic
                configuredPrimaryTimeoutMs,  // 8. int primaryTimeoutMs
                configuredFailureTimeoutMs,  // 9. int failureTimeoutMs
                PathingConfig.EDGE_COUNTER_THRESHOLD, // 10. int edgeCounterThreshold
                configuredHeuristicWeight,   // 11. double heuristicWeight
                temporaryPenalties           // 12. Map<PathNode, Double> temporaryPenalties
        );
    }

    
}

