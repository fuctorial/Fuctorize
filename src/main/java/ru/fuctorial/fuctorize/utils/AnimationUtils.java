package ru.fuctorial.fuctorize.utils;


public class AnimationUtils {
    private long startTime;
    private long duration;  
    private boolean forward;
    private Easing easing;

    public AnimationUtils(long duration) {
        this(duration, Easing.EASE_OUT_QUAD);
    }

    public AnimationUtils(long duration, Easing easing) {
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
        this.forward = true;
        this.easing = easing;
    }

    public void setDirection(boolean forward) {
        if (this.forward == forward) return;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > duration) elapsed = duration;
        this.startTime = System.currentTimeMillis() - (duration - elapsed);
        this.forward = forward;
    }

    public boolean isForward() {
        return this.forward;
    }

     
     
    public void setDuration(long duration) {
        this.duration = duration;
    }
     


    public double getAnimationFactor() {
        double factor;
        long elapsed = System.currentTimeMillis() - startTime;

        if (forward) {
            factor = (double) elapsed / duration;
        } else {
            factor = 1.0 - ((double) elapsed / duration);
        }

        factor = Math.max(0, Math.min(1.0, factor));

        return easing.ease(factor);
    }

    public enum Easing {
        LINEAR,
        EASE_IN_QUAD,
        EASE_OUT_QUAD,
        EASE_IN_OUT_QUAD;

        public double ease(double t) {
            switch (this) {
                case EASE_IN_QUAD:
                    return t * t;
                case EASE_OUT_QUAD:
                    return t * (2 - t);
                case EASE_IN_OUT_QUAD:
                    return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
                case LINEAR:
                default:
                    return t;
            }
        }
    }
}