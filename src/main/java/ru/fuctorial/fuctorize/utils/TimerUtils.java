package ru.fuctorial.fuctorize.utils;


// ru.fuctorial/fuctorize/utils/TimerUtils.java



public class TimerUtils {
    private long lastMS = 0L;

    public boolean hasReached(long milliseconds) {
        return System.currentTimeMillis() - lastMS >= milliseconds;
    }

    public void reset() {
        lastMS = System.currentTimeMillis();
    }
}