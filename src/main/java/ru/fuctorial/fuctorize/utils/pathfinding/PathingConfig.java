package ru.fuctorial.fuctorize.utils.pathfinding;

 
public final class PathingConfig {
    private PathingConfig() {}

     
    public static final double TICKS_WALK = 4.633;
    public static final double TICKS_SPRINT = 3.564;  
    public static final double TICKS_WATER = 9.091;
    public static final double TICKS_SOUL_SAND = 9.266;  
    public static final double TICKS_LADDER_UP = 8.511;
    public static final double BREAK_TIME_COST_MULTIPLIER = 1.5;
    public static final double TICKS_LADDER_DOWN = 6.667;
    public static final double TICKS_SNEAK = 15.385;
    public static final double TICKS_SWIM_SURFACE = 5.0;       
    public static final double TICKS_SWIM_UNDERWATER = 12.0;   

     
    public static final double CORNER_CUT_PENALTY = 8.0;

     
     
     
     
    public static final double IN_TRENCH_PENALTY = 60.0;

     
    public static final double HEURISTIC_WEIGHT = 3.57;  

     
    public static final int PRIMARY_TIMEOUT_MS = 2000;
    public static final int FAILURE_TIMEOUT_MS = 8000;
    public static final int PLAN_AHEAD_PRIMARY_TIMEOUT_MS = 4000;
    public static final int PLAN_AHEAD_FAILURE_TIMEOUT_MS = 6000;

     
    public static final boolean LIMIT_TURN_ANGLE = true;  
    public static final double MAX_YAW_CHANGE_DEG = 45.0;    
    public static final double MAX_PITCH_CHANGE_DEG = 45.0;  
    public static final double MAX_ABS_PITCH_DEG = 75.0;     

     
    public static final int MIN_SEGMENT_BLOCKS = 1;
    public static final int EDGE_COUNTER_THRESHOLD = 50;
    public static final double DIAGONAL_COST_FACTOR = Math.sqrt(2.0);
    public static final double MIN_IMPROVEMENT_EPS = 0.01;  

     
    public static final double BLOCK_BREAK_ADDITIONAL_PENALTY = 8.0;   
    public static final double BLOCK_PLACEMENT_PENALTY_TICKS = 30.0;   

    public static final double BACKTRACK_FACTOR = 0.5;
    public static final double PARKOUR_PENALTY_TICKS = 6.0;  

     
    public static final boolean DEBUG = true;
}


