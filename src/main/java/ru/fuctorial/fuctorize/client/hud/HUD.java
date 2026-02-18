package ru.fuctorial.fuctorize.client.hud;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.render.IRenderable;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import ru.fuctorial.fuctorize.module.Module;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

 




public class HUD implements IRenderable {

    @Override
    public void onRender3D(RenderWorldLastEvent event) {
    }

    @Override
    public void onRender2D(RenderGameOverlayEvent.Text event) {
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.moduleManager == null || FuctorizeClient.INSTANCE.fontManager == null || FuctorizeClient.INSTANCE.fontManager.regular_18 == null) {
            return;
        }

        List<Module> enabledModules = FuctorizeClient.INSTANCE.moduleManager.getModules().stream()
                .filter(m -> m.isEnabled())
                .filter(m -> m.isShownInHud())
                .sorted(Comparator.comparingInt((Module m) -> FuctorizeClient.INSTANCE.fontManager.regular_18.getStringWidth(m.getName())).reversed())
                .collect(Collectors.toList());

        int y = 4;
        final int rightMargin = 3;
        final int paddingH = 3;
        final int paddingV = 1;
        final int outline = new Color(0, 0, 0, 200).getRGB();
        final int bg = new Color(10, 10, 10, 160).getRGB();
        for (Module module : enabledModules) {
            String text = module.getName();
            int textWidth = FuctorizeClient.INSTANCE.fontManager.regular_18.getStringWidth(text);
            int textHeight = FuctorizeClient.INSTANCE.fontManager.regular_18.getHeight();

            int xText = event.resolution.getScaledWidth() - textWidth - rightMargin - paddingH;
            int x0 = xText - paddingH;
            int x1 = event.resolution.getScaledWidth() - rightMargin;
            int y0 = y - paddingV;
            int y1 = y + textHeight + paddingV;

             
            RenderUtils.drawRect(x0 - 0.5f, y0 - 0.5f, x1 + 0.5f, y1 + 0.5f, outline);
            RenderUtils.drawRect(x0, y0, x1, y1, bg);

             
            FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(text, xText + 1, y + 1, new Color(0, 0, 0, 140).getRGB());
            FuctorizeClient.INSTANCE.fontManager.regular_18.drawString(text, xText, y, new Color(235, 235, 235).getRGB());

            y += textHeight + 3;
        }
    }
}
