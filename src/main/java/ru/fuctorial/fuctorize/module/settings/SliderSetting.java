package ru.fuctorial.fuctorize.module.settings;


public class SliderSetting extends Setting {
    public double value;
    public final double min, max, increment;

    public SliderSetting(String name, double defaultValue, double min, double max, double increment) {
        this.name = name;
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.increment = increment;
    }
}