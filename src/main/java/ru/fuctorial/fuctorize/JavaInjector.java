// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\JavaInjector.java
package ru.fuctorial.fuctorize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

public class JavaInjector {

    public JavaInjector() {
        // Запускаем клиент
        Fuctorize.start();

        // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
        // Вместо прямого создания GUI, мы планируем его создание на следующий тик
        // в основном потоке игры.
        FuctorizeClient.INSTANCE.scheduleTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            // Проверяем, действительно ли мы на главном меню, чтобы избежать
            // неожиданного поведения, если игрок уже в мире.
            if (mc.currentScreen instanceof GuiMainMenu) {
                GuiOpenEvent event = new GuiOpenEvent(new GuiMainMenu());
                MinecraftForge.EVENT_BUS.post(event);
                mc.displayGuiScreen(event.gui);
            }
        });
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---
    }

    /**
     * This is the entry point for the C++ unload function.
     * It MUST be public and non-static so the native code can call it on the
     * instance it creates.
     */
    public void shutdown() {
        // It calls the main stop method in the @Mod class.
        Fuctorize.stop();
    }
}