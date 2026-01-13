package ru.fuctorial.fuctorize.utils.movement;

/**
 * Определяет текущее состояние или действие, выполняемое контроллером движения.
 */
public enum MovementState {
    IDLE,             // Бездействие
    WALKING,          // Обычная ходьба
    JUMPING,          // В процессе прыжка
    SWIMMING_UP,      // Всплытие в воде
    SWIMMING_DOWN,    // Погружение в воде
    PLACING_BLOCK,    // Установка блока (например, pillar)
    BREAKING_BLOCK,   // Ломание блока
    FALLING           // В процессе падения
}