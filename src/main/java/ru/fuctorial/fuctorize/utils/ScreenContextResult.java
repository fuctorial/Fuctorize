package ru.fuctorial.fuctorize.utils;

/**
 * Контейнер для результата анализа экрана.
 * Содержит человекочитаемую строку и стабильный хэш состояния.
 */
public class ScreenContextResult {
    public final String human; // may be null
    public final String hash;  // never null

    public ScreenContextResult(String human, String hash) {
        this.human = human;
        this.hash = hash;
    }
}