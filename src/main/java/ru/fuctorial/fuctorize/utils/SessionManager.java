package ru.fuctorial.fuctorize.utils;

import java.util.UUID;

/**
 * Manages the last known server-sent session UUID.
 * This is crucial for sending packets that require a valid session context,
 * like those in StalkerCore.
 */
public class SessionManager {
    private static UUID lastKnownSessionUUID = null;

    public static void updateSession(UUID newSessionUUID) {
        if (newSessionUUID != null && !newSessionUUID.equals(lastKnownSessionUUID)) {
            lastKnownSessionUUID = newSessionUUID;
            // *** ИЗМЕНЕНИЕ ЗДЕСЬ: Добавлен лог в консоль для отладки ***
            System.out.println("[Fuctorize/SessionManager] Captured new session UUID: " + newSessionUUID);
        }
    }

    public static UUID getLastKnownSessionUUID() {
        return lastKnownSessionUUID;
    }

    public static boolean hasActiveSession() {
        return lastKnownSessionUUID != null;
    }
}