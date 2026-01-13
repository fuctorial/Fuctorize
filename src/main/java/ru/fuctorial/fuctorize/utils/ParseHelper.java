package ru.fuctorial.fuctorize.utils;

public class ParseHelper {

    public static byte parseByte(String s) throws NumberFormatException {
        try {
            return Byte.parseByte(s.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Not a valid byte");
        }
    }

    public static short parseShort(String s) throws NumberFormatException {
        try {
            return Short.parseShort(s.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Not a valid short");
        }
    }

    public static int parseInt(String s) throws NumberFormatException {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Not a valid int");
        }
    }

    public static long parseLong(String s) throws NumberFormatException {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Not a valid long");
        }
    }

    public static float parseFloat(String s) throws NumberFormatException {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Not a valid float");
        }
    }

    public static double parseDouble(String s) throws NumberFormatException {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Not a valid double");
        }
    }

    // --- ИЗМЕНЕНИЕ: Используем HexUtils ---
    public static byte[] parseByteArray(String s) throws IllegalArgumentException {
        try {
            return HexUtils.hexToBytes(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a valid HEX string: " + e.getMessage());
        }
    }

    public static int[] parseIntArray(String s) throws NumberFormatException {
        try {
            String[] input = s.trim().split("\\s+"); // Сплит по любым пробелам
            int[] arr = new int[input.length];
            for (int i = 0; i < input.length; ++i) {
                arr[i] = ParseHelper.parseInt(input[i]);
            }
            return arr;
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Not a valid int array");
        }
    }
}