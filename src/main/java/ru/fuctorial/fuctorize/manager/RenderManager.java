// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\manager\RenderManager.java
package ru.fuctorial.fuctorize.manager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ru.fuctorial.fuctorize.client.render.IRenderable;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;


public class RenderManager {

    /**
     * If false, all client rendering is globally disabled.
     * This is used to safely stop rendering when not in a world,
     * preventing crashes from modules that might try to access a null player/world.
     */
    public static boolean allowRendering = true;

    /**
     * If true, ANY client rendering is immediately blocked at the highest level.
     * Used for reliably bypassing screenshot requests.
     * `volatile` ensures changes from the network thread are visible to the render thread.
     */
    public static volatile boolean renderingForceDisabled = false;

    private final List<IRenderable> renderables = new CopyOnWriteArrayList<>();

    public void register(IRenderable renderable) {
        if (!renderables.contains(renderable)) {
            renderables.add(renderable);
        }
    }

    public void unregister(IRenderable renderable) {
        renderables.remove(renderable);
    }

    @SubscribeEvent
    public void onRender3D(RenderWorldLastEvent event) {
        // Check both flags. The force-disable flag has precedence.
        if (renderingForceDisabled || !allowRendering) {
            return;
        }

        for (IRenderable renderable : renderables) {
            if (renderable instanceof ru.fuctorial.fuctorize.module.Module) {
                ru.fuctorial.fuctorize.module.Module m = (ru.fuctorial.fuctorize.module.Module) renderable;
                if (!m.isEnabled()) {
                    continue;
                }
            }
            renderable.onRender3D(event);
        }

        if (ru.fuctorial.fuctorize.FuctorizeClient.INSTANCE != null && ru.fuctorial.fuctorize.FuctorizeClient.INSTANCE.botNavigator != null) {
            ru.fuctorial.fuctorize.FuctorizeClient.INSTANCE.botNavigator.renderPath(event.partialTicks);
        }
    }

    @SubscribeEvent
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        // Check both flags here as well.
        if (renderingForceDisabled || !allowRendering) {
            return;
        }

        for (IRenderable renderable : renderables) {
            if (renderable instanceof ru.fuctorial.fuctorize.module.Module) {
                ru.fuctorial.fuctorize.module.Module m = (ru.fuctorial.fuctorize.module.Module) renderable;
                if (!m.isEnabled()) {
                    continue;
                }
            }
            renderable.onRender2D(event);
        }
    }

    // Some environments may not fire Text consistently. Also notify modules at Post/ALL.
    @SubscribeEvent
    public void onRender2DPost(RenderGameOverlayEvent.Post event) {
        if (renderingForceDisabled || !allowRendering) {
            return;
        }
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        for (IRenderable renderable : renderables) {
            if (!(renderable instanceof ru.fuctorial.fuctorize.module.Module)) continue;
            ru.fuctorial.fuctorize.module.Module m = (ru.fuctorial.fuctorize.module.Module) renderable;
            if (!m.isEnabled()) continue;
            m.onRender2DPost(event);
        }
    }
}
