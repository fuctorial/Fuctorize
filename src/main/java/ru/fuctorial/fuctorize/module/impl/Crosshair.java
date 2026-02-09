package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.BooleanSetting;
import ru.fuctorial.fuctorize.module.settings.ColorSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.ModContainerHelper;
import ru.fuctorial.fuctorize.utils.RenderUtils;

import java.awt.Color;

public class Crosshair extends Module {


    private BooleanSetting outline;
    private BooleanSetting dot;
    private BooleanSetting tShape; // Убирает верхнюю палочку (как в CS)
    private SliderSetting size;   // Длина линий
    private SliderSetting width;  // Толщина линий
    private SliderSetting gap;    // Отступ от центра

    public Crosshair(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("crosshair", Lang.get("module.crosshair.name"), Category.RENDER);

        //color = new ColorSetting(Lang.get("module.crosshair.setting.color"), new Color(0, 255, 0));
        outline = new BooleanSetting(Lang.get("module.crosshair.setting.outline"), true);
        dot = new BooleanSetting(Lang.get("module.crosshair.setting.dot"), false);
        tShape = new BooleanSetting(Lang.get("module.crosshair.setting.t_shape"), false);

        size = new SliderSetting(Lang.get("module.crosshair.setting.size"), 5.0, 0.5, 50.0, 0.5);
        width = new SliderSetting(Lang.get("module.crosshair.setting.width"), 1.0, 0.5, 10.0, 0.5);
        gap = new SliderSetting(Lang.get("module.crosshair.setting.gap"), 2.0, 0.0, 20.0, 0.5);

        //addSetting(color);
        addSetting(size);
        addSetting(width);
        addSetting(gap);
        addSetting(outline);
        addSetting(dot);
        addSetting(tShape);
        addSetting(new BindSetting(Lang.get("module.crosshair.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.crosshair.desc");
    }

    @Override
    public void onEnable() {
        // Регистрируемся в шине событий, чтобы ловить RenderGameOverlayEvent.Pre
        ModContainerHelper.runWithFuctorizeContainer(() -> MinecraftForge.EVENT_BUS.register(this));
    }

    @Override
    public void onDisable() {
        ModContainerHelper.runWithFuctorizeContainer(() -> MinecraftForge.EVENT_BUS.unregister(this));
    }

    /**
     * Отменяем рендер ванильного прицела.
     */
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            event.setCanceled(true);
        }
    }

    /**
     * Рисуем наш прицел.
     */
    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (mc.gameSettings.thirdPersonView != 0) return; // Не рисуем от 3-го лица

        ScaledResolution sr = event.resolution;
        float cx = sr.getScaledWidth() / 2.0f;
        float cy = sr.getScaledHeight() / 2.0f;

        float len = (float) size.value;
        float thick = (float) width.value;
        float dist = (float) gap.value;
        int mainColor = Colors.crosshairColor.getColor();
        int outlineColor = Color.BLACK.getRGB();

        // 1. Рисуем точку (Dot)
        if (dot.enabled) {
            if (outline.enabled) {
                drawBorderedRect(cx - thick / 2, cy - thick / 2, thick, thick, 1.0f, outlineColor, mainColor);
            } else {
                RenderUtils.drawRect(cx - thick / 2, cy - thick / 2, cx + thick / 2, cy + thick / 2, mainColor);
            }
        }

        // 2. Рисуем линии
        // Верхняя
        if (!tShape.enabled) {
            drawClippedLine(cx, cy, 0, -1, dist, len, thick, mainColor, outline.enabled);
        }
        // Нижняя
        drawClippedLine(cx, cy, 0, 1, dist, len, thick, mainColor, outline.enabled);
        // Левая
        drawClippedLine(cx, cy, -1, 0, dist, len, thick, mainColor, outline.enabled);
        // Правая
        drawClippedLine(cx, cy, 1, 0, dist, len, thick, mainColor, outline.enabled);
    }

    /**
     * Рисует одну линию прицела с учетом обводки.
     */
    private void drawClippedLine(float cx, float cy, int xDir, int yDir, float gap, float length, float thickness, int color, boolean outline) {
        // Вычисляем координаты
        float xStart = cx + (xDir * gap) - (yDir != 0 ? thickness / 2 : 0);
        float yStart = cy + (yDir * gap) - (xDir != 0 ? thickness / 2 : 0);

        float w, h;

        if (xDir != 0) { // Горизонтальная линия
            w = length;
            h = thickness;
            // Коррекция направления для отрисовки влево
            if (xDir < 0) xStart -= length;
        } else { // Вертикальная линия
            w = thickness;
            h = length;
            // Коррекция направления для отрисовки вверх
            if (yDir < 0) yStart -= length;
        }

        if (outline) {
            // Рисуем черную подложку (обводку) на 0.5-1 пиксель шире
            RenderUtils.drawRect(xStart - 1f, yStart - 1f, xStart + w + 1f, yStart + h + 1f, Color.BLACK.getRGB());
        }

        RenderUtils.drawRect(xStart, yStart, xStart + w, yStart + h, color);
    }

    // Хелпер для точки с обводкой
    private void drawBorderedRect(float x, float y, float width, float height, float lineW, int borderColor, int insideColor) {
        RenderUtils.drawRect(x - lineW, y - lineW, x + width + lineW, y + height + lineW, borderColor);
        RenderUtils.drawRect(x, y, x + width, y + height, insideColor);
    }
}