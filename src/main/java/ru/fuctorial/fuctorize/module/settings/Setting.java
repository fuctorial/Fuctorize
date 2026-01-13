package ru.fuctorial.fuctorize.module.settings;

import ru.fuctorial.fuctorize.module.Module;


// ru.fuctorial/fuctorize/module/settings/Setting.java




public class Setting {
    public String name;
    private Module parent; // <-- ДОБАВЛЕНО

    public Module getParent() { // <-- ДОБАВЛЕНО
        return parent;
    }

    public void setParent(Module parent) { // <-- ДОБАВЛЕНО
        this.parent = parent;
    }
}