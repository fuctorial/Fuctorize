package ru.fuctorial.fuctorize.client.render;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;


 




 
public interface IRenderable {

     
    void onRender3D(RenderWorldLastEvent event);

     
    void onRender2D(RenderGameOverlayEvent.Text event);
}