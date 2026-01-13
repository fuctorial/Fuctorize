package ru.fuctorial.fuctorize.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class LaunchArgumentParser {

    private static final Map<String, String> discoveredTokens = new LinkedHashMap<>();
    private static boolean parsed = false;
    private static boolean launchedWithTokens = false;

    public static void parse() {
        if (parsed) return;
        System.out.println("Fuctorize: Parsing JVM launch arguments...");

        String command = System.getProperty("sun.java.command");
        if (command != null) {
            String[] commandArgs = command.split(" ");

            for (int i = 0; i < commandArgs.length - 1; i++) {
                String arg = commandArgs[i];
                if (arg.startsWith("--")) {
                    String key = arg.substring(2);
                    String value = commandArgs[i + 1];
                    if (!value.startsWith("--")) {
                        System.out.println("Fuctorize: Found launch argument: " + key + " = " + value);
                        discoveredTokens.put(key, value);
                    }
                }
            }
        }

        // --- FINAL FIX: Universal online mode detection based on heuristic, not hardcoded names ---
        launchedWithTokens = false;
        for (Map.Entry<String, String> entry : discoveredTokens.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue();

            // Ignore username and properties, as they can be short and non-token-like.
            if (key.equals("username") || key.equals("userproperties")) {
                continue;
            }

            // Heuristic: A long, alphanumeric string is very likely a session token.
            if (value != null && value.length() > 16 && value.matches("[a-zA-Z0-9]+")) {
                System.out.println("Fuctorize: Heuristic match found for token '" + key + "'. Activating ONLINE account mode.");
                launchedWithTokens = true;
                break; // One match is enough to confirm online mode.
            }
        }

        if (!launchedWithTokens) {
            System.out.println("Fuctorize: No heuristic token matches found. Activating OFFLINE account mode.");
        }

        parsed = true;
    }

    /**
     * Checks if the game was launched with session-specific tokens based on a universal heuristic.
     * @return true if tokens were found.
     */
    public static boolean isLaunchedWithTokens() {
        if (!parsed) {
            parse();
        }
        return launchedWithTokens;
    }

    /**
     * Gets all discovered key-value arguments.
     * @return A map of all found arguments.
     */
    public static Map<String, String> getDiscoveredTokens() {
        if (!parsed) {
            parse();
        }
        return discoveredTokens;
    }
}