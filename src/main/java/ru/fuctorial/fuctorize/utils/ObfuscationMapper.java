package ru.fuctorial.fuctorize.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A utility for converting obfuscated (SRG) field and method names
 * to human-readable (MCP) names by loading CSV mapping files.
 * This class consolidates the functionality of the previous ObfuscationMapper and PacketFieldMapper.
 */
public class ObfuscationMapper {

    private static final Map<String, String> srgToMcpMap = new HashMap<>();
    private static boolean isLoaded = false;

    private static void loadMappings() {
        if (isLoaded) {
            return;
        }
        System.out.println("Fuctorize/ObfuscationMapper: Loading SRG -> MCP mappings...");
        // Load fields and methods into a single map
        loadCsv("/assets/fuctorize/mappings/fields.csv");
        loadCsv("/assets/fuctorize/mappings/methods.csv");
        isLoaded = true;
        System.out.println("Fuctorize/ObfuscationMapper: Loaded " + srgToMcpMap.size() + " mappings.");
    }

    private static void loadCsv(String resourcePath) {
        try (InputStream is = ObfuscationMapper.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("Fuctorize/ObfuscationMapper: CRITICAL - Mapping file not found: " + resourcePath);
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                reader.readLine(); // Skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 3);
                    if (parts.length >= 2) {
                        String srgName = parts[0].trim();
                        String mcpName = parts[1].trim();
                        if (!srgName.isEmpty() && !mcpName.isEmpty()) {
                            srgToMcpMap.put(srgName, mcpName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Fuctorize/ObfuscationMapper: Failed to load mappings from " + resourcePath);
            e.printStackTrace();
        }
    }

    /**
     * Gets the human-readable MCP name for a field or method.
     * @param srgName The SRG name (e.g., "field_149567_a").
     * @return The MCP name (e.g., "entityId") or the original SRG name if not found.
     */
    public static String getMcpName(String srgName) {
        if (!isLoaded) {
            loadMappings();
        }
        return srgToMcpMap.getOrDefault(srgName, srgName);
    }
}