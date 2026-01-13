package ru.fuctorial.fuctorize.client.render;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.utils.RenderUtils;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import org.lwjgl.opengl.GL11;

// Файл: C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\client\render\NameTagRenderer.java




public class NameTagRenderer {

    // Smoothing storage for vertical offsets to avoid abrupt jumps
    private final Map<String, Float> yOffsetLerp = new HashMap<>();
    private final Map<String, Long> yOffsetSeen = new HashMap<>();
    private static final long ENTRY_TTL_MS = 1500L;
    private final Map<String, Float> plannedOffsetY = new HashMap<>();

    public void renderTagList(List<TagData> tags) {
        if (tags.isEmpty() || FuctorizeClient.INSTANCE.fontManager == null || FuctorizeClient.INSTANCE.fontManager.regular_18 == null) {
            return;
        }

        // Nearer first, so closer tags win when capping density
        tags.sort(Comparator.comparingDouble(t -> t.distance));

        ru.fuctorial.fuctorize.client.font.CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        // Layout plan: compute small downward offsets for farther tags to reduce overlap.
        // Near first for planning so near tags stay at base position, far ones adapt.
        plannedOffsetY.clear();
        List<TagData> planList = new ArrayList<>(tags);
        planList.sort(Comparator.comparingDouble(t -> t.distance)); // near → far

        // Accumulate occupied rects in screen space
        List<float[]> occupied = new ArrayList<>(); // [x0,y0,x1,y1]
        for (TagData d : planList) {
            String nameLine = d.nameLine;
            String infoLine = d.infoLine;
            if (d.healthPercent != -1) {
                infoLine = getHealthColor(d.healthPercent) + d.infoLine;
            }

            // Same scale and metrics as in renderSingleTag
            float scale = 1.0f - (Math.max(0.0f, Math.min(d.distance, 64.0f)) / 128.0f);
            scale = Math.max(0.7f, scale);
            scale = (float) Math.round(scale * 20f) / 20f;
            if (d.scaleMul > 0f) {
                scale *= d.scaleMul;
            }

            float nameWidth = font.getStringWidth(nameLine);
            float infoWidth = font.getStringWidth(net.minecraft.util.StringUtils.stripControlCodes(infoLine));
            float totalWidth = Math.max(nameWidth, infoWidth);
            float padding = 2.0f;
            float boxWidth = totalWidth + padding * 2;
            float boxHeight = font.getHeight() + (infoLine.isEmpty() ? 0 : font.getHeight()) + padding;

            float scaledW = boxWidth * scale;
            float scaledH = boxHeight * scale;
            float baseX = d.screenCoords.x - scaledW / 2f;
            float baseY = d.screenCoords.y - scaledH;

            // Snap center to pixels like in rendering
            float tx = baseX + scaledW / 2f;
            float ty = baseY + scaledH;
            float snappedTX = (float) Math.round(tx);
            float snappedTY = (float) Math.round(ty);
            float x0 = snappedTX - scaledW / 2f;
            float y0 = snappedTY - scaledH;

            float dy = findFreeYOffset(x0, y0, scaledW, scaledH, occupied);
            // Clamp excessive movement but never skip; near will cover if still overlapping
            float maxShift = Math.min(36f, Math.max(12f, scaledH * 0.6f));
            if (dy > maxShift) dy = maxShift;

            float finalY = baseY + dy;
            float snappedTY2 = (float) Math.round(finalY + scaledH);
            float y0Final = snappedTY2 - scaledH;

            occupied.add(new float[]{x0, y0Final, x0 + scaledW, y0Final + scaledH});
            plannedOffsetY.put(buildKey(d), dy);
        }

        // Painter order: draw far → near so nearer tags cover those behind
        tags.sort(Comparator.comparingDouble(t -> -t.distance));

        // Ensure font texture uses linear filtering to reduce shimmering
        int texId = font.textureId;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        RenderUtils.begin2DRendering();

        for (TagData data : tags) {
            renderSingleTag(data);
        }

        RenderUtils.end2DRendering();

        // Keep default linear filtering (explicit)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    private void renderSingleTag(TagData data) {
        ru.fuctorial.fuctorize.client.font.CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        // Distance-based scale with a higher floor to reduce blur
        float scale = 1.0f - (Math.max(0.0f, Math.min(data.distance, 64.0f)) / 128.0f);
        scale = Math.max(0.7f, scale);
        // Snap scale to 0.05 steps to reduce micro jitter on subpixel boundaries
        scale = (float) Math.round(scale * 20f) / 20f;
        if (data.scaleMul > 0f) {
            scale *= data.scaleMul;
        }

        String nameLine = data.nameLine;
        String infoLine = data.infoLine;
        if (data.healthPercent != -1) {
            infoLine = getHealthColor(data.healthPercent) + data.infoLine;
        }

        float nameWidth = font.getStringWidth(nameLine);
        float infoWidth = font.getStringWidth(net.minecraft.util.StringUtils.stripControlCodes(infoLine));
        float totalWidth = Math.max(nameWidth, infoWidth);

        float padding = 2.0f;
        float boxWidth = totalWidth + padding * 2;
        float boxHeight = font.getHeight() + (infoLine.isEmpty() ? 0 : font.getHeight()) + padding;

        // Compute scaled screen-space rect and resolve simple overlaps by vertical offset
        float scaledW = boxWidth * scale;
        float scaledH = boxHeight * scale;

        float baseX = data.screenCoords.x - scaledW / 2f;
        float baseY = data.screenCoords.y - scaledH; // strictly above anchor

        float finalX = baseX;
        float planned = 0f;
        Float plan = plannedOffsetY.get(buildKey(data));
        if (plan != null) planned = plan;
        float finalY = baseY + planned;

        GL11.glPushMatrix();
        // Pixel snapping: align translation to integer pixels to avoid texture shimmering
        float tx = finalX + scaledW / 2f;
        float ty = finalY + scaledH;
        float snappedTX = (float) Math.round(tx);
        float snappedTY = (float) Math.round(ty);
        GL11.glTranslatef(snappedTX, snappedTY, 0);
        GL11.glScalef(scale, scale, scale);

        float x0 = -boxWidth / 2f;
        float y0 = -boxHeight;

        // Background with slight outline for readability
        int bg = new Color(10, 10, 10, 170).getRGB();
        int outline = new Color(0, 0, 0, 200).getRGB();
        RenderUtils.drawRect(x0 - 0.5f, y0 - 0.5f, x0 + boxWidth + 0.5f, y0 + boxHeight + 0.5f, outline);
        RenderUtils.drawRect(x0, y0, x0 + boxWidth, y0 + boxHeight, bg);

        float nameX = x0 + (boxWidth - nameWidth) / 2f;
        float infoX = x0 + (boxWidth - infoWidth) / 2f;
        float currentY = y0 + padding / 2;

        font.drawString(nameLine, nameX, currentY, data.mainColor);
        if (!infoLine.isEmpty()) {
            currentY += font.getHeight();
            font.drawString(infoLine, infoX, currentY, Color.WHITE.getRGB());
        }

        GL11.glPopMatrix();
    }

    private float findFreeYOffset(float x, float y, float w, float h, List<float[]> occupied) {
        // Monotonic push-down layout: never move upward, only stack below previous targets
        float dy = 0f;
        final float gap = Math.max(2f, h * 0.08f);
        boolean changed;
        int guard = 0;
        do {
            changed = false;
            float x1 = x + w, y0 = y + dy, y1 = y0 + h;
            for (float[] r : occupied) {
                if (x < r[2] && x1 > r[0] && y0 < r[3] && y1 > r[1]) {
                    // push just below r
                    float needed = (r[3] + gap) - y;
                    if (needed > dy) {
                        dy = needed;
                        changed = true;
                        // update y0/y1 after change in next loop iteration
                    }
                }
            }
        } while (changed && guard++ < 32);
        return dy;
    }

    private boolean intersectsAny(float x, float y, float w, float h, List<float[]> occupied) {
        float x1 = x + w, y1 = y + h;
        for (float[] r : occupied) {
            if (x < r[2] && x1 > r[0] && y < r[3] && y1 > r[1]) return true;
        }
        return false;
    }

    private String getHealthColor(float healthPercent) {
        if (healthPercent > 0.75f) return "§a";
        if (healthPercent > 0.4f) return "§e";
        return "§c";
    }

    private String buildKey(TagData data) {
        // Prefer provided stable key when available
        if (data.key != null && !data.key.isEmpty()) return data.key;
        return data.nameLine + "|" + data.mainColor;
    }
}

