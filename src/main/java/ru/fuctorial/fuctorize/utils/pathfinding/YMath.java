package ru.fuctorial.fuctorize.utils.pathfinding;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

/**
 * <p><b>Полностью переделанный YMath от 15.11.2025.</b></p>
 *
 * <p><b>ПРОБЛЕМА:</b> Старый код использовал {@code mc.thePlayer.posY}, которое является НЕСТАБИЛЬНЫМ значением.
 * Оно зависит от {@code yOffset}, который может меняться (модами, клиентом и т.д.),
 * что и приводило к "ебучему бреду" с координатами.</p>
 *
 * <p><b>РЕШЕНИЕ:</b> Новый код использует {@code mc.thePlayer.boundingBox.minY}.
 * Это <b>физическая, реальная координата низа хитбокса игрока</b> ("подошва ботинок").
 * Это значение не зависит от `yOffset` и всегда показывает, где игрок находится в мире на самом деле.</p>
 *
 * <p><b>Теперь этот класс — железобетонный источник правды о Y-координатах.</b></p>
 */
public final class YMath {
    private YMath() {}

    // Константы для семантики кода
    public static final int FEET_OFFSET_FROM_GROUND = 1;
    public static final int HEAD_OFFSET_FROM_GROUND = 2;

    // --- ОСНОВНЫЕ МЕТОДЫ, КОТОРЫЕ ТЕПЕРЬ РАБОТАЮТ ПРАВИЛЬНО ---
    // Все они теперь игнорируют ненадежный playerPosY и берут данные из boundingBox.

    /**
     * Возвращает Y-координату блока, НА КОТОРОМ стоит игрок.
     * Единственный надежный способ.
     * @param playerPosY_IGNORED Этот параметр больше не используется, но оставлен для совместимости.
     * @return Y-координата блока под ногами.
     */
    public static int groundFromPlayerPosY(double playerPosY_IGNORED) {
        EntityPlayer player = FMLClientHandler.instance().getClient().thePlayer;
        if (player == null) return 0; // Защита
        // boundingBox.minY - это реальная координата ног.
        // Вычитаем 0.01, чтобы при minY = 5.0 (стоит на блоке Y=4) результат был 4, а не 5.
        return MathHelper.floor_double(player.boundingBox.minY - 0.01);
    }

    /**
     * Возвращает Y-координату блока, В КОТОРОМ находятся ноги игрока.
     * @param playerPosY_IGNORED Этот параметр больше не используется.
     * @return Y-координата блока, где находятся ноги.
     */
    public static int feetFromPlayerPosY(double playerPosY_IGNORED) {
        EntityPlayer player = FMLClientHandler.instance().getClient().thePlayer;
        if (player == null) return 0;
        return MathHelper.floor_double(player.boundingBox.minY);
    }

    /**
     * Возвращает Y-координату блока, В КОТОРОМ находится голова игрока.
     * @param playerPosY_IGNORED Этот параметр больше не используется.
     * @return Y-координата блока, где находится голова.
     */
    public static int headFromPlayerPosY(double playerPosY_IGNORED) {
        EntityPlayer player = FMLClientHandler.instance().getClient().thePlayer;
        if (player == null) return 1;
        // Голова находится в блоке над ногами.
        return MathHelper.floor_double(player.boundingBox.minY) + 1;
    }

    /**
     * Возвращает Y-координату блока НАД головой игрока (тот, что мешает прыгнуть).
     * @param playerPosY_IGNORED Этот параметр больше не используется.
     * @return Y-координата блока над головой.
     */
    public static int capFromPlayerPosY(double playerPosY_IGNORED) {
        EntityPlayer player = FMLClientHandler.instance().getClient().thePlayer;
        if (player == null) return 2;
        // "Кепка" - это блок на 2 выше уровня ног.
        return MathHelper.floor_double(player.boundingBox.minY) + 2;
    }


    // --- Вспомогательные методы (их трогать не нужно, они работают с относительными целыми числами) ---

    /**
     * Возвращает Y-координату пространства для ног, зная Y земли.
     */
    public static int feetFromGround(int groundY) {
        return groundY + FEET_OFFSET_FROM_GROUND;
    }

    /**
     * Возвращает Y-координату пространства для головы, зная Y земли.
     */
    public static int headFromGround(int groundY) {
        return groundY + HEAD_OFFSET_FROM_GROUND;
    }

    /**
     * Возвращает Y-координату блока НАД указанным.
     */
    public static int groundAbove(int groundY) {
        return groundY + 1;
    }

    /**
     * Возвращает Y-координату блока ПОД указанным.
     */
    public static int groundBelow(int groundY) {
        return groundY - 1;
    }
}
