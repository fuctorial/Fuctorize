package ru.fuctorial.fuctorize.utils.pathfinding;

/**
 * Utility for parsing textual target coordinates into world-space doubles.
 * Keeps parsing rules out of UI/modules.
 */
public final class PathTargetParser {

    private PathTargetParser() {}

    /**
     * Parses coordinates from a string. Supports absolute numbers and
     * relative tokens with '~' (e.g., "~", "~3", "-10").
     * Returns null on invalid input.
     */
    public static double[] parseTargetCoordinates(String raw,
                                                  double originX,
                                                  double originY,
                                                  double originZ) {
        if (raw == null) return null;
        String normalized = raw.replace(",", " ").trim();
        if (normalized.isEmpty()) return null;

        String[] parts = normalized.split("\\s+");
        if (parts.length < 3) return null;

        try {
            double x = parseCoordinatePart(parts[0], originX);
            double y = parseCoordinatePart(parts[1], originY);
            double z = parseCoordinatePart(parts[2], originZ);
            return new double[] { x, y, z };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double parseCoordinatePart(String token, double origin) {
        token = token.trim();
        if (token.startsWith("~")) {
            if (token.length() == 1) {
                return origin;
            }
            double offset = Double.parseDouble(token.substring(1));
            return origin + offset;
        }
        return Double.parseDouble(token);
    }
}

