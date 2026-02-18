package ru.fuctorial.fuctorize.utils;

public enum SkinProvider {
     
    MOJANG("Официальный (Mojang)", "https://minotar.net/skin/%s"),

     
     
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
         
        if (this == TLAUNCHER) {
            return String.format(urlTemplate, username.toLowerCase());
        }
        return String.format(urlTemplate, username);
    }
}