package ru.fuctorial.fuctorize.utils;


 



public class TimerUtils {
    private long lastMS = 0L;

    public boolean hasReached(long milliseconds) {
        return System.currentTimeMillis() - lastMS >= milliseconds;
    }

    public void reset() {
        lastMS = System.currentTimeMillis();
    }
}