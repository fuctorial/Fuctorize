package ru.fuctorial.fuctorize.utils;

import org.lwjgl.opengl.GL11;


// ru.fuctorial/fuctorize/utils/ChamsUtils.java




public class ChamsUtils {

    /**
     * Подготавливает OpenGL для рендера Chams.
     *
     * @param visible   Цвет для видимых частей модели (в формате ARGB).
     * @param hidden    Цвет для частей модели за стеной (в формате ARGB).
     * @param throughWalls Если true, рендер будет виден сквозь стены.
     */
    public static void preRender(int visible, int hidden, boolean throughWalls) {
        // --- Общие настройки ---
        GL11.glDisable(GL11.GL_TEXTURE_2D); // Отключаем текстуры, чтобы видеть сплошной цвет
        GL11.glEnable(GL11.GL_BLEND);       // Включаем смешивание цветов для прозрачности
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (throughWalls) {
            // --- Рендер СКВОЗЬ СТЕНЫ ---
            GL11.glDisable(GL11.GL_DEPTH_TEST); // Отключаем тест глубины
            GL11.glDepthMask(false);            // Запрещаем запись в буфер глубины

            // Устанавливаем цвет для "спрятанных" частей
            setColor(hidden);
        } else {
            // --- Рендер ПОВЕРХ ВСЕГО (ВИДИМЫЕ ЧАСТИ) ---
            GL11.glEnable(GL11.GL_DEPTH_TEST);  // Включаем тест глубины
            GL11.glDepthMask(true);             // Разрешаем запись в буфер

            // Устанавливаем цвет для видимых частей
            setColor(visible);
        }
    }

    /**
     * Восстанавливает стандартные настройки OpenGL после рендера Chams.
     */
    public static void postRender() {
        GL11.glEnable(GL11.GL_TEXTURE_2D); // Включаем текстуры обратно
        GL11.glDisable(GL11.GL_BLEND);     // Отключаем смешивание
    }

    // Вспомогательный метод для установки цвета из ARGB
    private static void setColor(int color) {
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glColor4f(r, g, b, a);
    }
}