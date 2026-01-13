package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.ChatUtils;
import ru.fuctorial.fuctorize.utils.TimerUtils;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

public class MoneyRevealer extends Module {

    private SliderSetting delay;
    private final TimerUtils timer = new TimerUtils();

    private static Class<?> economyClass = null;
    private static Field balanceField = null;
    private static boolean reflectionFailed = false;

    public MoneyRevealer(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("moneyrevealer", "Money Revealer", Category.MISC);

        // Настройка в миллисекундах: от 1 до 500
        delay = new SliderSetting("Delay (ms)", 100.0, 1.0, 500.0, 1.0);

        addSetting(delay);
        addSetting(new BindSetting("Bind", Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return "Logs local balance to client chat (Debug).";
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null) {
            toggle();
            return;
        }

        if (economyClass == null && !reflectionFailed) {
            try {
                economyClass = Class.forName("ru.zixel.economy.EconomyMod");
                balanceField = economyClass.getField("localMoneyBalance");

                ChatUtils.printMessage(EnumChatFormatting.GREEN + "[MoneyRevealer] Hooked into EconomyMod.");
            } catch (Exception e) {
                reflectionFailed = true;
                ChatUtils.printMessage(EnumChatFormatting.RED + "[MoneyRevealer] Failed to hook: " + e.getClass().getSimpleName());
                toggle();
                return;
            }
        }

        if (reflectionFailed) {
            ChatUtils.printMessage(EnumChatFormatting.RED + "[MoneyRevealer] Reflection previously failed. Module disabled.");
            toggle();
            return;
        }

        printBalance();
        timer.reset();
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || reflectionFailed || balanceField == null) return;

        // Теперь значение слайдера берется напрямую как миллисекунды
        if (timer.hasReached((long) delay.value)) {
            printBalance();
            timer.reset();
        }
    }

    private void printBalance() {
        try {
            int balance = balanceField.getInt(null);
            // Вывод только в локальный чат
            ChatUtils.printMessage(EnumChatFormatting.GOLD + "[Debug] Balance: " + EnumChatFormatting.WHITE + balance);
        } catch (Exception e) {
            ChatUtils.printMessage(EnumChatFormatting.RED + "[Debug] Error reading balance.");
            e.printStackTrace();
        }
    }
}