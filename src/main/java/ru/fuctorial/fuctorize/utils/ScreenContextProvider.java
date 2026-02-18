package ru.fuctorial.fuctorize.utils;

import net.minecraft.client.gui.GuiScreen;

 
public interface ScreenContextProvider {
     
    ScreenContextResult getContext(GuiScreen screen);
}