package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.ColorSetting;
import ru.fuctorial.fuctorize.module.settings.SeparatorSetting;
import ru.fuctorial.fuctorize.utils.Lang;

import java.awt.Color;

public class Colors extends Module {

    public static ColorSetting playersColor;
    public static ColorSetting mobsColor;
    public static ColorSetting animalsColor;
    public static ColorSetting itemsColor;
    public static ColorSetting chestsColor;
    public static ColorSetting enderChestsColor;
    public static ColorSetting anomalyColor;
    public static ColorSetting artifactColor;
    public static ColorSetting spawnColor;
    public static ColorSetting barrierColor;
    public static ColorSetting randomBoxColor;

    // --- НОВЫЕ ПОЛЯ ---
    public static ColorSetting customEspColor;
    public static ColorSetting crosshairColor;

    public Colors(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("colors", Lang.get("module.colors.name"), Category.SETTINGS);
        setShowInHud(false);
        setConfigOnly(true);
        setEnabledFromConfig(true);

        playersColor = new ColorSetting(Lang.get("module.colors.setting.players"), new Color(0x45B6E8));
        mobsColor = new ColorSetting(Lang.get("module.colors.setting.hostile_mobs"), new Color(0xFF0000));
        animalsColor = new ColorSetting(Lang.get("module.colors.setting.friendly_mobs"), new Color(0x00FF00));
        itemsColor = new ColorSetting(Lang.get("module.colors.setting.items"), new Color(0xFFFF00));
        addSetting(playersColor);
        addSetting(mobsColor);
        addSetting(animalsColor);
        addSetting(itemsColor);
        addSetting(new SeparatorSetting());

        // --- Custom ESP & Crosshair ---
        customEspColor = new ColorSetting(Lang.get("module.colors.setting.custom_esp"), new Color(0, 255, 255));
        crosshairColor = new ColorSetting(Lang.get("module.colors.setting.crosshair"), new Color(0, 255, 0));
        addSetting(customEspColor);
        addSetting(crosshairColor);

        addSetting(new SeparatorSetting());
        randomBoxColor = new ColorSetting(Lang.get("module.colors.setting.randombox"), new Color(180, 0, 255));
        addSetting(randomBoxColor);
        chestsColor = new ColorSetting(Lang.get("module.colors.setting.chests"), new Color(0xFFFF00));
        enderChestsColor = new ColorSetting(Lang.get("module.colors.setting.ender_chests"), new Color(0x800080));
        barrierColor = new ColorSetting(Lang.get("module.colors.setting.barriers_esp"), new Color(255, 255, 0, 100));
        addSetting(chestsColor);
        addSetting(enderChestsColor);
        addSetting(barrierColor);

        addSetting(new SeparatorSetting());
        anomalyColor = new ColorSetting(Lang.get("module.colors.setting.anomalies_stalker"), new Color(255, 80, 0));
        artifactColor = new ColorSetting(Lang.get("module.colors.setting.artifacts_stalker"), new Color(0, 255, 80));
        spawnColor = new ColorSetting(Lang.get("module.colors.setting.artifact_spawns"), new Color(150, 150, 150));
        addSetting(anomalyColor);
        addSetting(artifactColor);
        addSetting(spawnColor);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.colors.desc");
    }
}