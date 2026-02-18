 
package ru.fuctorial.fuctorize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

public class JavaInjector {

    public JavaInjector() {
         
        Fuctorize.start();

         
         
         
        FuctorizeClient.INSTANCE.scheduleTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
             
             
            if (mc.currentScreen instanceof GuiMainMenu) {
                GuiOpenEvent event = new GuiOpenEvent(new GuiMainMenu());
                MinecraftForge.EVENT_BUS.post(event);
                mc.displayGuiScreen(event.gui);
            }
        });
         
    }

     
    public void shutdown() {
         
        Fuctorize.stop();
    }
}