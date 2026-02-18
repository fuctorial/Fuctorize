package ru.fuctorial.fuctorize.module.settings;

import java.util.Arrays;
import java.util.List;


 





public class ModeSetting extends Setting {
    public int index;
    public final List<String> modes;

    public ModeSetting(String name, String defaultMode, String... modes) {
        this.name = name;
        this.modes = Arrays.asList(modes);
        this.index = this.modes.indexOf(defaultMode);
    }

    public String getMode() {
        return modes.get(index);
    }

    public boolean isMode(String mode) {
        return modes.get(index).equalsIgnoreCase(mode);
    }

    public void cycle() {
        if (index < modes.size() - 1) {
            index++;
        } else {
            index = 0;
        }
    }
}