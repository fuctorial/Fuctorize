package ru.fuctorial.fuctorize.client.hud;

import ru.fuctorial.fuctorize.utils.AnimationUtils;
import java.awt.Color;


// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\hud\Notification.java





public class Notification {
    public final String title;
    public final String message;
    private final long creationTime;
    private final long lifeTime;
    public final NotificationType type;
    public final AnimationUtils animation;

    public enum NotificationType {
        INFO(new Color(60, 160, 240)),
        SUCCESS(new Color(60, 220, 100)),
        WARNING(new Color(240, 180, 60)),
        ERROR(new Color(220, 60, 60));

        public final Color color;
        NotificationType(Color color) {
            this.color = color;
        }
    }

    public Notification(String title, String message, NotificationType type, long lifeTime) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.lifeTime = lifeTime;
        this.creationTime = System.currentTimeMillis();
        this.animation = new AnimationUtils(400, AnimationUtils.Easing.EASE_OUT_QUAD);
        this.animation.setDirection(true);
    }

    public boolean isExpired() {
        long fadeTime = 500; // Время на исчезновение
        return System.currentTimeMillis() - creationTime > lifeTime + fadeTime;
    }

    public double getAnimationFactor() {
        long timePassed = System.currentTimeMillis() - creationTime;
        long fadeStartTime = lifeTime;

        // Начинаем анимацию затухания
        if (timePassed > fadeStartTime) {
            animation.setDirection(false);
        }

        return animation.getAnimationFactor();
    }
}