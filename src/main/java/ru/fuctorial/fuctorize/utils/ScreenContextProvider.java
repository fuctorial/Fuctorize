package ru.fuctorial.fuctorize.utils;

import net.minecraft.client.gui.GuiScreen;

/**
 * Интерфейс для специальных обработчиков, которые предоставляют
 * контекст для конкретных типов GuiScreen.
 */
public interface ScreenContextProvider {
    /**
     * Возвращает ScreenContextResult. hash должен быть ненулевым — он используется для сравнения.
     * human может быть null.
     */
    ScreenContextResult getContext(GuiScreen screen);
}