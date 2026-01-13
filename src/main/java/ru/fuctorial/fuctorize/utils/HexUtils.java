package ru.fuctorial.fuctorize.utils;

public class HexUtils {

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b)); // Добавляем пробел для читаемости
        }
        return sb.toString().trim();
    }

    public static byte[] hexToBytes(String hex) throws IllegalArgumentException {
        // Удаляем пробелы, переносы строк и прочий мусор, оставляем только HEX символы
        String clean = hex.replaceAll("[^0-9A-Fa-f]", "");

        if (clean.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string length must be even");
        }

        int len = clean.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int digit1 = Character.digit(clean.charAt(i), 16);
            int digit2 = Character.digit(clean.charAt(i + 1), 16);
            if (digit1 == -1 || digit2 == -1) {
                throw new IllegalArgumentException("Invalid hex character");
            }
            data[i / 2] = (byte) ((digit1 << 4) + digit2);
        }
        return data;
    }
}