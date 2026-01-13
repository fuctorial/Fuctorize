package ru.fuctorial.fuctorize.utils;

import java.util.concurrent.atomic.AtomicBoolean;

public class UnloadCallback {
    private static final AtomicBoolean cleanupComplete = new AtomicBoolean(false);

    public static void signalCleanupComplete() {
        cleanupComplete.set(true);
        System.out.println(">>> FUCTORIZE UNLOAD: Java cleanup complete. Signaling C++.");
    }

    /**
     * Этот метод будет вызываться из C++ через JNI для проверки флага.
     * Он НЕ должен быть native.
     * @return true, если очистка завершена.
     */
    public static boolean isCleanupComplete() {
        return cleanupComplete.get();
    }

    public static void reset() {
        cleanupComplete.set(false);
    }
}