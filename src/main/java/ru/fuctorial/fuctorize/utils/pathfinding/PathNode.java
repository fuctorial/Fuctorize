package ru.fuctorial.fuctorize.utils.pathfinding;

public class PathNode implements Comparable<PathNode> {

    public final int x;
    public final int y;  
    public final int z;
    public int steps;
    public MovementType moveType;
    public int breakBlocks;
    public int placeBlocks;
    public boolean needsJump;

    public double gCost;
    public double hCost;
    public double fCost;
    public PathNode parent;

    public PathNode(int x, int y, int z) {
        this.x = x;
        this.y = y;  
        this.z = z;
        this.steps = 0;
        this.moveType = null;
        this.breakBlocks = 0;
        this.placeBlocks = 0;
        this.needsJump = false;
        this.gCost = 0.0;
        this.hCost = 0.0;
        this.fCost = 0.0;
        this.parent = null;
    }

     

     
    public int getFeetY() {
        return ru.fuctorial.fuctorize.utils.pathfinding.YMath.feetFromGround(this.y);
    }

     
    public double getCenterY() {
        return getFeetY() + 0.5;
    }

     

    public void updateFCost() {
        this.fCost = this.gCost + this.hCost;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PathNode pathNode = (PathNode) obj;
        return x == pathNode.x && y == pathNode.y && z == pathNode.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public int compareTo(PathNode other) {
        if (other == null) return 1;
        int fCompare = Double.compare(this.fCost, other.fCost);
        if (fCompare != 0) return fCompare;
        return Double.compare(this.hCost, other.hCost);
    }

    @Override
    public String toString() {
        return String.format("PathNode{x=%d, y=%d, z=%d, steps=%d, type=%s, break=%d, place=%d, jump=%s, f=%.2f, g=%.2f, h=%.2f}",
                x, y, z, steps, moveType, breakBlocks, placeBlocks, needsJump, fCost, gCost, hCost);
    }

     
    public double distanceTo(PathNode other) {
        if (other == null) return Double.MAX_VALUE;
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
