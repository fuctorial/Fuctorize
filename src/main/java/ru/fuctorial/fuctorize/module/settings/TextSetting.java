package ru.fuctorial.fuctorize.module.settings;


// ru.fuctorial/fuctorize/module/settings/TextSetting.java




public class TextSetting extends Setting {
    public String text;

    public TextSetting(String name, String defaultText) {
        this.name = name;
        this.text = defaultText;
    }
}