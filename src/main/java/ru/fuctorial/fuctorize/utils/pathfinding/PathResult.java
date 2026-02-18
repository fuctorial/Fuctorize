package ru.fuctorial.fuctorize.utils.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

 
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

     
    public static PathResult success(List<PathNode> path, int nodesExplored) {
        return new PathResult(true, path, null, nodesExplored, true, false);
    }

     
    public static PathResult partial(List<PathNode> path, int nodesExplored) {
        return new PathResult(true, path, null, nodesExplored, false, true);
    }

     
    public static PathResult failure(String errorMessage, int nodesExplored) {
        return new PathResult(false, null, errorMessage, nodesExplored, false, false);
    }

     
    public boolean isSuccess() {
        return success;
    }

     
    public boolean isTargetReached() { return reachedTarget; }

     
    public boolean isPartial() { return partial; }

     
    public List<PathNode> getPath() {
        return Collections.unmodifiableList(path);
    }

     
    public String getErrorMessage() {
        return errorMessage;
    }

     
    public int getNodesExplored() {
        return nodesExplored;
    }

     
    public int getPathLength() {
        return path.size();
    }

     
    public double getPathDistance() {
        if (path.size() < 2) return 0.0;

        double distance = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            distance += path.get(i).distanceTo(path.get(i + 1));
        }
        return distance;
    }

     
    public boolean isEmpty() {
        return path.isEmpty();
    }
}

