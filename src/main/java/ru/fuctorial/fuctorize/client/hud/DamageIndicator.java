package ru.fuctorial.fuctorize.client.hud;

import ru.fuctorial.fuctorize.utils.AnimationUtils;


// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\hud\DamageIndicator.java




public class DamageIndicator {
    public final int entityId;
    public String entityName;
    public String damageText;
    public String hpText;
    public int damageColor;
    private long creationTime;
    private final long lifeTime;
    public final AnimationUtils animation;

    public DamageIndicator(int entityId, String entityName, String damageText, String hpText, int damageColor, long lifeTime) {
        this.entityId = entityId;
        this.entityName = entityName;
        this.damageText = damageText;
        this.hpText = hpText;
        this.damageColor = damageColor;
        this.lifeTime = lifeTime;
        this.creationTime = System.currentTimeMillis();

        this.animation = new AnimationUtils(250, AnimationUtils.Easing.EASE_OUT_QUAD);
        this.animation.setDirection(true);
    }

    public void update(String newDamageText, String newHpText, int newDamageColor) {
        this.damageText = newDamageText;
        this.hpText = newHpText;
        this.damageColor = newDamageColor;
        this.creationTime = System.currentTimeMillis();
        this.animation.setDuration(250);
        this.animation.setDirection(true);
    }

    public boolean isFinished() {
        long timePassed = System.currentTimeMillis() - creationTime;
        if (timePassed > lifeTime && animation.isForward()) {
            animation.setDuration(400);
            animation.setDirection(false);
        }
        return !animation.isForward() && animation.getAnimationFactor() <= 0;
    }

    public double getAnimationFactor() {
        return animation.getAnimationFactor();
    }
}