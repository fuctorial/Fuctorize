package ru.fuctorial.fuctorize.module.settings;


public class BooleanSetting extends Setting {
    public boolean enabled;

    public BooleanSetting(String name, boolean defaultValue) {
        this.name = name;
        this.enabled = defaultValue;
    }

    public void toggle() {
        this.enabled = !this.enabled;
    }
}