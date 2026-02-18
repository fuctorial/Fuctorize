 
package ru.fuctorial.fuctorize.client.font;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;

public class CustomFontRenderer {

    private static final int[] colorCode = new int[32];

    static {
        for (int i = 0; i < 32; ++i) {
            int j = (i >> 3 & 1) * 85;
            int k = (i >> 2 & 1) * 170 + j;
            int l = (i >> 1 & 1) * 170 + j;
            int i1 = (i & 1) * 170 + j;
            if (i == 6) k += 85;
            if (i >= 16) {
                k /= 4;
                l /= 4;
                i1 /= 4;
            }
            colorCode[i] = (k & 255) << 16 | (l & 255) << 8 | i1 & 255;
        }
    }

    private static final int ATLAS_WIDTH = 2048;
    private static final int ATLAS_HEIGHT = 2048;
     
    private static final String glyph_string = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя.,:;'\"!?/\\|-_+*@#$€%^&(){}[]<>~`§ ★☆";
    private final Font font;
    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private int fontHeight;
    public int textureId;

    public CustomFontRenderer(Font font) {
        this.font = font;
        generateAtlas();
    }

     
 
 
    public void destroy() {
        if (this.textureId != 0) {
            GL11.glDeleteTextures(this.textureId);
            this.textureId = 0;
        }
    }

    private void generateAtlas() {
        BufferedImage atlas = new BufferedImage(ATLAS_WIDTH, ATLAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = (Graphics2D) atlas.getGraphics();
        g2d.setFont(this.font);
        g2d.setColor(Color.WHITE);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics fontMetrics = g2d.getFontMetrics();
        this.fontHeight = fontMetrics.getHeight();
        final int padding = 4;
        int currentX = padding;
        int currentY = padding;
        int rowHeight = 0;
        for (char ch : glyph_string.toCharArray()) {
            int charWidth = fontMetrics.charWidth(ch);
            int charHeight = fontMetrics.getHeight();
            if (currentX + charWidth + padding >= ATLAS_WIDTH) {
                currentX = padding;
                currentY += rowHeight;
                rowHeight = 0;
            }
            g2d.drawString(String.valueOf(ch), currentX, currentY + fontMetrics.getAscent());
            glyphs.put(ch, new Glyph(currentX, currentY, charWidth, charHeight));
            currentX += charWidth + padding;
            if (charHeight + padding > rowHeight) {
                rowHeight = charHeight + padding;
            }
        }
        g2d.dispose();
        this.textureId = TextureUtil.glGenTextures();
        TextureUtil.uploadTextureImage(this.textureId, atlas);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    private void drawGlyph(Glyph glyph, double x, double y) {
        float u1 = (float) glyph.x / ATLAS_WIDTH;
        float v1 = (float) glyph.y / ATLAS_HEIGHT;
        float u2 = (float) (glyph.x + glyph.width) / ATLAS_WIDTH;
        float v2 = (float) (glyph.y + glyph.height) / ATLAS_HEIGHT;
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2d(x, y);
        GL11.glTexCoord2f(u1, v2);
        GL11.glVertex2d(x, y + glyph.height);
        GL11.glTexCoord2f(u2, v2);
        GL11.glVertex2d(x + glyph.width, y + glyph.height);
        GL11.glTexCoord2f(u2, v1);
        GL11.glVertex2d(x + glyph.width, y);
    }

    public void renderString(String text, double x, double y, int defaultColor, boolean boldShadow) {
        if (text == null) return;
        int currentColor = defaultColor;
        boolean bold = false;

         
         
         
        int initialAlpha = (defaultColor >> 24) & 0xFF;
        if (initialAlpha == 0 && (defaultColor & 0x00FFFFFF) != 0) {
            initialAlpha = 255;
        }
        float a = (float) initialAlpha / 255.0F;
         

        for (int i = 0; i < text.length(); ++i) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) {
                int colorIndex = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));
                if (colorIndex < 16) {
                    bold = false;
                    currentColor = colorCode[colorIndex];
                } else if (colorIndex == 17) {
                    bold = true;
                } else if (colorIndex == 20) {
                    bold = false;
                    currentColor = defaultColor;
                }
                i++;
                continue;
            }
            Glyph glyph = glyphs.get(ch);
            if (glyph != null) {
                float r = (float) (currentColor >> 16 & 255) / 255.0F;
                float g = (float) (currentColor >> 8 & 255) / 255.0F;
                float b = (float) (currentColor & 255) / 255.0F;

                if (boldShadow) {
                    int shadowColor = (defaultColor & 0xFCFCFC) >> 2 | defaultColor & 0xFF000000;
                    float sr = (float) (shadowColor >> 16 & 255) / 255.0F;
                    float sg = (float) (shadowColor >> 8 & 255) / 255.0F;
                    float sb = (float) (shadowColor & 255) / 255.0F;
                     
                    GL11.glColor4f(sr, sg, sb, a);
                    GL11.glBegin(GL11.GL_QUADS);
                    drawGlyph(glyph, x + 2.0, y + 2.0);
                    GL11.glEnd();
                }

                 
                GL11.glColor4f(r, g, b, a);
                GL11.glBegin(GL11.GL_QUADS);
                drawGlyph(glyph, x, y);
                if (bold) {
                    drawGlyph(glyph, x + 2.0, y);
                }
                GL11.glEnd();
                x += glyph.width;
            }
        }
    }

    public void drawString(String text, double x, double y, int defaultColor) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glScalef(0.25F, 0.25F, 0.25F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
        renderString(text, x * 4, y * 4, defaultColor, false);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    public int getStringWidth(String text) {
        if (text == null) return 0;
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                int colorIndex = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));
                if (colorIndex == 17) bold = true;
                else if (colorIndex < 16 || colorIndex == 20) bold = false;
                i++;
                continue;
            }
            Glyph glyph = glyphs.get(c);
            if (glyph != null) {
                width += glyph.width;
                if (bold) width += 2;
            }
        }
        return width / 4;
    }

    public int getHeight() {
        return fontHeight / 4;
    }

    private static class Glyph {
        public final int x, y, width, height;

        public Glyph(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public void drawSplitString(String text, double x, double y, int wrapWidth, int color) {
        List<String> lines = wrapText(text, wrapWidth);
        for (String line : lines) {
            this.drawString(line, x, y, color);
            y += this.getHeight() + 1;
        }
    }

    public List<String> wrapText(String text, int wrapWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || wrapWidth <= 0) {
            return lines;
        }

        int scaledWrapWidth = wrapWidth * 4;
        String[] paragraphs = text.split("\n");
        String lastFormatting = "";

        for (String paragraph : paragraphs) {
            if (getStringWidthInternal(lastFormatting + paragraph) <= scaledWrapWidth) {
                lines.add(lastFormatting + paragraph);
                lastFormatting = getFormatting(lastFormatting + paragraph);
                continue;
            }

            String[] words = paragraph.split(" ");
            StringBuilder currentLine = new StringBuilder();
            currentLine.append(lastFormatting);

            for (String word : words) {
                if (getStringWidthInternal(currentLine.toString() + " " + word) > scaledWrapWidth) {
                    if (currentLine.length() > lastFormatting.length()) {
                        lines.add(currentLine.toString());
                        currentLine.setLength(0);
                        currentLine.append(lastFormatting);
                    }
                    if (getStringWidthInternal(word) > scaledWrapWidth) {
                        String remainingWord = word;
                        while (getStringWidthInternal(remainingWord) > scaledWrapWidth) {
                            int splitIndex = findSplitIndex(remainingWord, scaledWrapWidth);
                            String part = remainingWord.substring(0, splitIndex);
                            lines.add(lastFormatting + part);
                            remainingWord = remainingWord.substring(splitIndex);
                            lastFormatting = getFormatting(lastFormatting + part);
                            remainingWord = lastFormatting + remainingWord;
                        }
                        currentLine.append(remainingWord);
                    } else {
                        currentLine.append(word);
                    }
                } else {
                    if (currentLine.length() > lastFormatting.length()) {
                        currentLine.append(" ");
                    }
                    currentLine.append(word);
                }
                lastFormatting = getFormatting(currentLine.toString());
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }


    private int getStringWidthInternal(String text) {
        if (text == null) return 0;
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                int colorIndex = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));
                if (colorIndex == 17) bold = true;
                else if (colorIndex < 16 || colorIndex == 20) bold = false;
                i++;
                continue;
            }
            Glyph glyph = glyphs.get(c);
            if (glyph != null) {
                width += glyph.width;
                if (bold) width += 2;
            }
        }
        return width;
    }

    private static String getFormatting(String text) {
        String lastColor = "";
        StringBuilder styles = new StringBuilder();
        boolean styleReset = false;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                char formatChar = Character.toLowerCase(text.charAt(i + 1));

                if ("0123456789abcdef".indexOf(formatChar) != -1) {
                    lastColor = "§" + formatChar;
                    styles.setLength(0);
                    styleReset = false;
                } else if ("klmno".indexOf(formatChar) != -1) {
                    if (!styleReset) {
                        String styleCode = "§" + formatChar;
                        if (styles.indexOf(styleCode) == -1) {
                            styles.append(styleCode);
                        }
                    }
                } else if (formatChar == 'r') {
                    lastColor = "";
                    styles.setLength(0);
                    styleReset = true;
                }
                i++;
            }
        }
        return lastColor + styles.toString();
    }


    private int findSplitIndex(String text, int wrapWidth) {
        int currentWidth = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                int colorIndex = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));
                if (colorIndex == 17) bold = true;
                else if (colorIndex < 16 || colorIndex == 20) bold = false;
                i++;
                continue;
            }
            Glyph g = glyphs.get(c);
            if (g != null) {
                currentWidth += g.width;
                if (bold) currentWidth += 2;
            }

            if (currentWidth > wrapWidth) {
                return i > 0 ? i : 1;
            }
        }
        return text.length();
    }

     
    public String trimStringToWidth(String text, int width) {
        return this.trimStringToWidth(text, width, false);
    }

     
    public String trimStringToWidth(String text, int width, boolean reverse) {
        int scaledWidth = width * 4;
        if (getStringWidthInternal(text) <= scaledWidth) {
            return text;
        }

        if (reverse) {
            int currentWidth = 0;
            int i = text.length() - 1;
            for (; i >= 0; --i) {
                char c = text.charAt(i);
                 
                Glyph glyph = glyphs.get(c);
                if (glyph != null) {
                    currentWidth += glyph.width;
                }
                if (currentWidth > scaledWidth) {
                    break;
                }
            }
            return text.substring(i + 1);
        } else {
            StringBuilder sb = new StringBuilder();
            int currentWidth = 0;
            boolean bold = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '§' && i + 1 < text.length()) {
                    int colorIndex = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(i + 1)));
                    if (colorIndex == 17) bold = true;
                    else if (colorIndex < 16 || colorIndex == 20) bold = false;
                    sb.append(c).append(text.charAt(i + 1));
                    i++;
                    continue;
                }
                Glyph glyph = glyphs.get(c);
                if (glyph != null) {
                    currentWidth += glyph.width;
                    if (bold) currentWidth += 2;
                }
                if (currentWidth > scaledWidth) {
                    return sb.toString();
                }
                sb.append(c);
            }
        }
        return text;
    }
}