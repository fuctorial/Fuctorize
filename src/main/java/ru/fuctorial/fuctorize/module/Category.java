// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\module\Category.java
package ru.fuctorial.fuctorize.module;

import ru.fuctorial.fuctorize.utils.Lang;

public enum Category {
    // Functional categories
    COMBAT("category.combat"),
    MOVEMENT("category.movement"),
    RENDER("category.render"),
    PLAYER("category.player"),
    EXPLOIT("category.exploit"),
    WORLD("category.world"),
    MISC("category.misc"),
    FUN("category.fun"),

    // Configuration categories
    SETTINGS("category.settings"); // For config modules

    private final String key;

    Category(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return Lang.get(key);
    }
}
