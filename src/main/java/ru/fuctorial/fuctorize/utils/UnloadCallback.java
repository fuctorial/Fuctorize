package ru.fuctorial.fuctorize.utils;

import java.util.concurrent.atomic.AtomicBoolean;

public class UnloadCallback {
    private static final AtomicBoolean cleanupComplete = new AtomicBoolean(false);

    public static void signalCleanupComplete() {
        cleanupComplete.set(true);
        System.out.println(">>> FUCTORIZE UNLOAD: Java cleanup complete. Signaling C++.");
    }

     
    public static boolean isCleanupComplete() {
        return cleanupComplete.get();
    }

    public static void reset() {
        cleanupComplete.set(false);
    }
}