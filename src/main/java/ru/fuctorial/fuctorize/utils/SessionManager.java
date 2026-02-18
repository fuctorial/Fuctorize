package ru.fuctorial.fuctorize.utils;

import java.util.UUID;

 
public class SessionManager {
    private static UUID lastKnownSessionUUID = null;

    public static void updateSession(UUID newSessionUUID) {
        if (newSessionUUID != null && !newSessionUUID.equals(lastKnownSessionUUID)) {
            lastKnownSessionUUID = newSessionUUID;
             
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