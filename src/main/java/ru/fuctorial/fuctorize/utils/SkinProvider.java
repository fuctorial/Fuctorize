package ru.fuctorial.fuctorize.utils;

public enum SkinProvider {
    // Official API section
    MOJANG("Официальный (Mojang)", "https://minotar.net/skin/%s"),

    // Unofficial Projects section
    // FIX #1: Use the correct endpoint for TLauncher skins.
    TLAUNCHER("TLauncher", "http://auth.tlauncher.org/skin/profile/texture/login/%s"),
    EXCALIBUR_CRAFT("Excalibur-Craft", "http://ex-server.ru/skins/%s.png");

    private final String displayName;
    private final String urlTemplate;

    SkinProvider(String displayName, String urlTemplate) {
        this.displayName = displayName;
        this.urlTemplate = urlTemplate;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUrlForPlayer(String username) {
        // TLauncher skins are case-sensitive and often lowercase. Let's handle it here.
        if (this == TLAUNCHER) {
            return String.format(urlTemplate, username.toLowerCase());
        }
        return String.format(urlTemplate, username);
    }
}