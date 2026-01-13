package ru.fuctorial.fuctorize.client.render;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;


// ru.fuctorial/fuctorize/client/render/IRenderable.java




/**
 * Интерфейс для всех объектов, которые могут что-то рендерить.
 * Это позволяет централизованно управлять всем рендерингом через RenderManager.
 */
public interface IRenderable {

    /**
     * Вызывается для рендеринга 3D-объектов в мире (ESP, Tracers).
     */
    void onRender3D(RenderWorldLastEvent event);

    /**
     * Вызывается для рендеринга 2D-элементов на экране (HUD, NameTags).
     */
    void onRender2D(RenderGameOverlayEvent.Text event);
}