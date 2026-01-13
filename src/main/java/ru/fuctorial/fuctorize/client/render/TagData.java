package ru.fuctorial.fuctorize.client.render;

import org.lwjgl.util.vector.Vector4f;

public class TagData {
    public final String nameLine;
    public final String infoLine;
    public final int mainColor;
    public final float healthPercent;

    public final Vector4f screenCoords;
    public final float distance;
    public final String key; // stable identity for smoothing/cache
    public final float scaleMul; // global size multiplier (1.0 = default)

    public TagData(String nameLine, String infoLine, int mainColor, float healthPercent, Vector4f screenCoords, float distance) {
        this(nameLine, infoLine, mainColor, healthPercent, screenCoords, distance, nameLine, 1.0f);
    }

    public TagData(String nameLine, String infoLine, int mainColor, float healthPercent, Vector4f screenCoords, float distance, String key) {
        this(nameLine, infoLine, mainColor, healthPercent, screenCoords, distance, key, 1.0f);
    }

    public TagData(String nameLine, String infoLine, int mainColor, float healthPercent, Vector4f screenCoords, float distance, String key, float scaleMul) {
        this.nameLine = nameLine;
        this.infoLine = infoLine;
        this.mainColor = mainColor;
        this.healthPercent = healthPercent;
        this.screenCoords = screenCoords;
        this.distance = distance;
        this.key = key;
        this.scaleMul = scaleMul;
    }
}
