package ru.fuctorial.fuctorize.client.hud;

import ru.fuctorial.fuctorize.utils.AnimationUtils;






public class BypassNotification {
    public final String title;
    public final String message;
    private final long creationTime;
    private final long lifeTime; // Время жизни в миллисекундах
    public final AnimationUtils animation;

    public BypassNotification(String title, String message, long lifeTime) {
        this.title = title;
        this.message = message;
        this.lifeTime = lifeTime;
        this.creationTime = System.currentTimeMillis();

        // Анимация "выезда" и "затухания"
        this.animation = new AnimationUtils(300, AnimationUtils.Easing.EASE_OUT_QUAD);
        this.animation.setDirection(true);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - creationTime > lifeTime;
    }

    /**
     * Получаем текущий фактор анимации для смещения и прозрачности.
     */
    public double getAnimationFactor() {
        long timePassed = System.currentTimeMillis() - creationTime;

        // Начинаем анимацию затухания за 500 мс до конца
        if (timePassed > lifeTime - 500) {
            animation.setDirection(false);
        }

        return animation.getAnimationFactor();
    }
}