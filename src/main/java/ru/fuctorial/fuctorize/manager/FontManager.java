// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\client\manager\FontManager.java
package ru.fuctorial.fuctorize.manager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import net.minecraft.client.Minecraft;

import java.awt.Font;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

public class FontManager {

    private final Queue<FontTask> initializationQueue = new LinkedList<>();
    public CustomFontRenderer regular_18;
    public CustomFontRenderer bold_22;
    public CustomFontRenderer bold_32;
    Minecraft mc = Minecraft.getMinecraft();

    private volatile boolean isReady = false;

    public FontManager() {
        initializationQueue.add(new FontTask("assets/fuctorize/fonts/Inter-Regular.ttf", 9f, (font) -> regular_18 = font));
        initializationQueue.add(new FontTask("assets/fuctorize/fonts/Inter-Bold.ttf", 11f, (font) -> bold_22 = font));
        initializationQueue.add(new FontTask("assets/fuctorize/fonts/Inter-Bold.ttf", 16f, (font) -> bold_32 = font));
    }

    public boolean isReady() {
        return this.isReady;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (!initializationQueue.isEmpty() && !isReady) {
                FontTask task = initializationQueue.poll();
                if (task != null) {
                    System.out.println(">>> FUCTORIZE/FontManager: Initializing " + task.fontPath + " on client tick.");
                    task.consumer.accept(createFont(task.fontPath, task.size));
                }

                if (initializationQueue.isEmpty()) {
                    isReady = true;
                    System.out.println(">>> FUCTORIZE/FontManager: All fonts initialized.");
                }
            }
        }
    }

    private CustomFontRenderer createFont(String fontPath, float size) {
        Font awtFont;
        try {
            System.out.println(">>> FONT-DEBUG: Trying resource lookup for: /" + fontPath);
            InputStream inputStream = FontManager.class.getResourceAsStream("/" + fontPath);
            if (inputStream == null) {
                System.out.println(">>> FONT-DEBUG: Not found by Class.getResourceAsStream. Trying ContextClassLoader with variants...");
                ClassLoader ctx = Thread.currentThread().getContextClassLoader();
                // try exact
                inputStream = ctx.getResourceAsStream(fontPath);
                // try without leading "assets/"
                if (inputStream == null && fontPath.startsWith("assets/")) {
                    inputStream = ctx.getResourceAsStream(fontPath.substring("assets/".length()));
                }
                // try with leading slash
                if (inputStream == null) {
                    inputStream = ctx.getResourceAsStream("/" + fontPath);
                }
            }
            if (inputStream == null) {
                throw new RuntimeException("CRITICAL: Font not found at " + fontPath + " by ANY ClassLoader.");
            }

            awtFont = Font.createFont(Font.TRUETYPE_FONT, inputStream);
            awtFont = awtFont.deriveFont(size * 4F);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return new CustomFontRenderer(awtFont);
    }

    private static class FontTask {
        String fontPath;
        float size;
        Consumer<CustomFontRenderer> consumer;

        FontTask(String path, float size, Consumer<CustomFontRenderer> c) {
            this.fontPath = path;
            this.size = size;
            this.consumer = c;
        }
    }

    public void destroy() {
        if (regular_18 != null) regular_18.destroy();
        if (bold_22 != null) bold_22.destroy();
        if (bold_32 != null) bold_32.destroy();
        isReady = false;
        initializationQueue.clear();
        initializationQueue.add(new FontTask("assets/fuctorize/fonts/Inter-Regular.ttf", 9f, (font) -> regular_18 = font));
        initializationQueue.add(new FontTask("assets/fuctorize/fonts/Inter-Bold.ttf", 11f, (font) -> bold_22 = font));
        initializationQueue.add(new FontTask("assets/fuctorial/fuctorize/fonts/Inter-Bold.ttf", 16f, (font) -> bold_32 = font));
    }


}