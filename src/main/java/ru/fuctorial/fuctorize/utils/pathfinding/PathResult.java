package ru.fuctorial.fuctorize.utils.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Результат поиска пути алгоритмом A*
 */
public class PathResult {

    private final boolean success;
    private final List<PathNode> path;
    private final String errorMessage;
    private final int nodesExplored;
    private final boolean reachedTarget;
    private final boolean partial;

    private PathResult(boolean success, List<PathNode> path, String errorMessage, int nodesExplored,
                       boolean reachedTarget, boolean partial) {
        this.success = success;
        this.path = path != null ? new ArrayList<PathNode>(path) : Collections.<PathNode>emptyList();
        this.errorMessage = errorMessage;
        this.nodesExplored = nodesExplored;
        this.reachedTarget = reachedTarget;
        this.partial = partial;
    }

    /**
     * Создает успешный результат
     * @param path Найденный путь
     * @param nodesExplored Количество исследованных узлов
     * @return Результат поиска пути
     */
    public static PathResult success(List<PathNode> path, int nodesExplored) {
        return new PathResult(true, path, null, nodesExplored, true, false);
    }

    /**
     * Создает успешный частичный результат (частичный маршрут)
     * @param path Частично найденный путь (сегмент)
     * @param nodesExplored Количество исследованных узлов
     * @return Результат частичного поиска пути
     */
    public static PathResult partial(List<PathNode> path, int nodesExplored) {
        return new PathResult(true, path, null, nodesExplored, false, true);
    }

    /**
     * Создает неуспешный результат
     * @param errorMessage Сообщение об ошибке
     * @param nodesExplored Количество исследованных узлов
     * @return Результат поиска пути
     */
    public static PathResult failure(String errorMessage, int nodesExplored) {
        return new PathResult(false, null, errorMessage, nodesExplored, false, false);
    }

    /**
     * Проверяет, был ли поиск пути успешным
     * @return true если путь найден
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Достигнута ли целевая точка.
     */
    public boolean isTargetReached() { return reachedTarget; }

    /**
     * Является ли маршрут частичным (сегментом).
     */
    public boolean isPartial() { return partial; }

    /**
     * Получает найденный путь
     * @return Список узлов пути (от старта до цели)
     */
    public List<PathNode> getPath() {
        return Collections.unmodifiableList(path);
    }

    /**
     * Получает сообщение об ошибке
     * @return Сообщение об ошибке или null
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Получает количество исследованных узлов
     * @return Количество узлов
     */
    public int getNodesExplored() {
        return nodesExplored;
    }

    /**
     * Получает длину пути
     * @return Количество узлов в пути или 0 если путь не найден
     */
    public int getPathLength() {
        return path.size();
    }

    /**
     * Получает расстояние пути (сумма расстояний между соседними узлами)
     * @return Расстояние пути
     */
    public double getPathDistance() {
        if (path.size() < 2) return 0.0;

        double distance = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            distance += path.get(i).distanceTo(path.get(i + 1));
        }
        return distance;
    }

    /**
     * Проверяет, пуст ли путь
     * @return true если путь пуст
     */
    public boolean isEmpty() {
        return path.isEmpty();
    }
}

