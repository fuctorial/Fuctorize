// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\manager\NotificationManager.java
package ru.fuctorial.fuctorize.manager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.hud.Notification;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import java.awt.Color;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public class NotificationManager {
    private final CopyOnWriteArrayList<Notification> notifications = new CopyOnWriteArrayList<>();
    private final Minecraft mc = Minecraft.getMinecraft();

    public void show(Notification notification) {
        notifications.add(0, notification);
    }

    public void showReplacing(Notification notification) {
        notifications.removeIf(n -> n.title.equals(notification.title));
        notifications.add(0, notification);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Add this check to prevent notifications during a screenshot bypass.
        if (RenderManager.renderingForceDisabled) {
            return;
        }

        // Step 1: Remove expired notifications. This logic does not require fonts.
        // It should always run if the list is not empty.
        if (!notifications.isEmpty()) {
            notifications.removeIf(n -> n.isExpired() && n.getAnimationFactor() <= 0);
        }

        // Step 2: Check if ready to render.
        // If fonts are not ready or there are no more notifications, exit.
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady() || notifications.isEmpty()) {
            return;
        }

        // Step 3: Render the remaining notifications.
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer messageFont = FuctorizeClient.INSTANCE.fontManager.regular_18;

        float startY = 5.0f;
        float spacing = 5.0f;

        RenderUtils.begin2DRendering();

        for (Notification notification : notifications) {
            double animFactor = notification.getAnimationFactor();

            String title = notification.title;
            String message = notification.message;

            float titleWidth = titleFont.getStringWidth(title);
            float messageWidth = messageFont.getStringWidth(message);
            float boxWidth = Math.max(titleWidth, messageWidth) + 25.0f;
            float boxHeight = titleFont.getHeight() + messageFont.getHeight() + 8.0f;

            float boxX = (float) (sr.getScaledWidth() - (boxWidth + 5.0f) * animFactor);
            float boxY = startY;

            Color baseColor = notification.type.color;
            int mainColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 200).getRGB();
            int backColor = new Color(30, 30, 30, 220).getRGB();

            RenderUtils.drawRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, backColor);
            RenderUtils.drawRect(boxX, boxY, boxX + 4, boxY + boxHeight, mainColor);

            int textColor = new Color(255, 255, 255, (int)(255 * animFactor)).getRGB();
            titleFont.drawString(title, boxX + 10, boxY + 4, textColor);
            messageFont.drawString(message, boxX + 10, boxY + titleFont.getHeight() + 6, textColor);

            startY += (boxHeight + spacing);
        }

        RenderUtils.end2DRendering();
    }
}