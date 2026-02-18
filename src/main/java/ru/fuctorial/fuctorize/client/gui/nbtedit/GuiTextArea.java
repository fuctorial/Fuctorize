 
package ru.fuctorial.fuctorize.client.gui.nbtedit;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiTextArea extends Gui {
    public int xPos;
    public int yPos;
    public int width;
    public int height;

    private String text = "";
    private boolean isFocused = false;
    private int cursorCounter;

    private int cursorPosition = 0;
    private int selectionEnd = 0;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private boolean isDraggingText = false;  
    private boolean isDraggingScroll = false;  

     
    private static final int SCROLLBAR_WIDTH = 6;
    private int scrollY = 0;
    private int scrollHeight = 0;
    private int initialClickY = 0;
    private int initialScrollOffset = 0;

    private final List<LineData> visibleLines = new ArrayList<>();

    private static class LineData {
        String content;
        int startIndex;

        LineData(String content, int startIndex) {
            this.content = content;
            this.startIndex = startIndex;
        }
    }

    public GuiTextArea(int x, int y, int w, int h) {
        this.xPos = x;
        this.yPos = y;
        this.width = w;
        this.height = h;
    }

    public boolean isFocused() { return isFocused; }
    public int getHeight() { return height; }

    public void setFocused(boolean focused) {
        isFocused = focused;
        if (focused) {
            cursorCounter = 0;
        }
    }

    public void updateCursorCounter() {
        ++this.cursorCounter;
    }

    public void setText(String newText) {
        this.text = newText;
        if (this.text == null) this.text = "";
        this.cursorPosition = 0;
        this.selectionEnd = 0;
        this.scrollOffset = 0;
        recalcLines();
    }

    public String getText() { return this.text; }

    public String getSelectedText() {
        int min = Math.min(cursorPosition, selectionEnd);
        int max = Math.max(cursorPosition, selectionEnd);
        if (min == max) return "";
        try { return text.substring(min, max); } catch (Exception e) { return ""; }
    }

    private void writeText(String str) {
        if (str == null) return;
        String filtered = ChatAllowedCharacters.filerAllowedCharacters(str);
        int min = Math.min(cursorPosition, selectionEnd);
        int max = Math.max(cursorPosition, selectionEnd);

        String before = text.substring(0, min);
        String after = text.substring(max);

        this.text = before + filtered + after;
        this.cursorPosition = min + filtered.length();
        this.selectionEnd = this.cursorPosition;
        recalcLines();
        scrollToCursor();
    }

    private void deleteSelection() {
        if (cursorPosition == selectionEnd) {
            if (cursorPosition > 0) {
                text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                cursorPosition--;
                selectionEnd = cursorPosition;
            }
        } else {
            int min = Math.min(cursorPosition, selectionEnd);
            int max = Math.max(cursorPosition, selectionEnd);
            text = text.substring(0, min) + text.substring(max);
            cursorPosition = min;
            selectionEnd = min;
        }
        recalcLines();
        scrollToCursor();
    }

    public void textboxKeyTyped(char typedChar, int keyCode) {
        if (!this.isFocused) return;

        if (GuiScreen.isCtrlKeyDown()) {
            if (keyCode == Keyboard.KEY_A) {
                this.cursorPosition = text.length();
                this.selectionEnd = 0;
                return;
            }
            if (keyCode == Keyboard.KEY_C) {
                GuiScreen.setClipboardString(getSelectedText());
                return;
            }
            if (keyCode == Keyboard.KEY_V) {
                writeText(GuiScreen.getClipboardString());
                return;
            }
            if (keyCode == Keyboard.KEY_X) {
                GuiScreen.setClipboardString(getSelectedText());
                deleteSelection();
                return;
            }
        }

        switch (keyCode) {
            case Keyboard.KEY_BACK:
                deleteSelection();
                return;
            case Keyboard.KEY_LEFT:
                moveCursor(-1, GuiScreen.isShiftKeyDown());
                return;
            case Keyboard.KEY_RIGHT:
                moveCursor(1, GuiScreen.isShiftKeyDown());
                return;
            case Keyboard.KEY_UP:
                moveLine(-1, GuiScreen.isShiftKeyDown());
                return;
            case Keyboard.KEY_DOWN:
                moveLine(1, GuiScreen.isShiftKeyDown());
                return;
            case Keyboard.KEY_RETURN:
                return;
            default:
                if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
                    writeText(Character.toString(typedChar));
                }
        }
    }

    private void moveCursor(int delta, boolean select) {
        if (delta != 0) {
            if (!select && cursorPosition != selectionEnd) {
                if (delta < 0) cursorPosition = Math.min(cursorPosition, selectionEnd);
                else cursorPosition = Math.max(cursorPosition, selectionEnd);
                selectionEnd = cursorPosition;
            } else {
                cursorPosition += delta;
            }
        }
        cursorPosition = Math.max(0, Math.min(text.length(), cursorPosition));
        if (!select) selectionEnd = cursorPosition;
        scrollToCursor();
    }

    private void moveLine(int lineDelta, boolean select) {
        if (visibleLines.isEmpty()) return;
        CustomFontRenderer font = getFont();
        if (font == null) return;

        int currentLineIdx = -1;
        int xInLine = 0;

        for (int i = 0; i < visibleLines.size(); i++) {
            LineData ld = visibleLines.get(i);
            if (cursorPosition >= ld.startIndex && cursorPosition <= ld.startIndex + ld.content.length()) {
                currentLineIdx = i;
                String sub = text.substring(ld.startIndex, cursorPosition);
                xInLine = font.getStringWidth(sub);
                break;
            }
        }

        if (currentLineIdx == -1) return;
        int targetLineIdx = Math.max(0, Math.min(visibleLines.size() - 1, currentLineIdx + lineDelta));

        LineData targetLine = visibleLines.get(targetLineIdx);
        String s = targetLine.content;
        int newLocalPos = font.trimStringToWidth(s, xInLine).length();

        if (newLocalPos < s.length()) {
            int w1 = font.getStringWidth(s.substring(0, newLocalPos));
            int w2 = font.getStringWidth(s.substring(0, newLocalPos + 1));
            if (Math.abs(xInLine - w2) < Math.abs(xInLine - w1)) newLocalPos++;
        }

        cursorPosition = targetLine.startIndex + newLocalPos;
        if (!select) selectionEnd = cursorPosition;
        scrollToCursor();
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        boolean wasClicked = mouseX >= this.xPos && mouseX < this.xPos + this.width &&
                mouseY >= this.yPos && mouseY < this.yPos + this.height;

        this.setFocused(wasClicked);

        if (wasClicked && button == 0) {
             
            if (maxScroll > 0 && mouseX >= xPos + width - SCROLLBAR_WIDTH) {
                int barY = yPos + scrollY;
                if (mouseY >= barY && mouseY <= barY + scrollHeight) {
                    isDraggingScroll = true;
                    initialClickY = mouseY;
                    initialScrollOffset = scrollOffset;
                } else {
                     
                     
                }
                return;
            }

             
            int index = getIndexAt(mouseX, mouseY);
            if (index != -1) {
                this.cursorPosition = index;
                this.selectionEnd = index;
                this.isDraggingText = true;
            }
        }
    }

    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton != 0) return;

        if (isDraggingScroll) {
            int totalTrackHeight = height - 2;  
            int trackHeight = totalTrackHeight - scrollHeight;
            if (trackHeight <= 0) return;

            int deltaY = mouseY - initialClickY;
            float ratio = (float) maxScroll / (float) trackHeight;
            int deltaScroll = Math.round(deltaY * ratio);

            scrollOffset = initialScrollOffset + deltaScroll;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return;
        }

        if (isDraggingText && isFocused) {
            int index = getIndexAt(mouseX, mouseY);
            if (index != -1) {
                this.cursorPosition = index;
                scrollToCursor();
            }
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            isDraggingText = false;
            isDraggingScroll = false;
        }
    }

    private int getIndexAt(int mouseX, int mouseY) {
        if (visibleLines.isEmpty()) return 0;
        if (getFont() == null) return -1;

        int lineHeight = getFont().getHeight() + 2;
        int relativeY = mouseY - (this.yPos + 4);
        int clickedLineIdx = relativeY / lineHeight;

        clickedLineIdx += scrollOffset;

        if (clickedLineIdx < 0) return 0;
        if (clickedLineIdx >= visibleLines.size()) return text.length();

        LineData line = visibleLines.get(clickedLineIdx);
        int relativeX = mouseX - (this.xPos + 4);

        String s = line.content;
        int charInLine = getFont().trimStringToWidth(s, relativeX).length();

        if (charInLine < s.length()) {
            int widthBefore = getFont().getStringWidth(s.substring(0, charInLine));
            int widthAfter = getFont().getStringWidth(s.substring(0, charInLine + 1));
            int charW = widthAfter - widthBefore;
            if (relativeX - widthBefore > charW / 2) charInLine++;
        }

        return line.startIndex + charInLine;
    }

    private void recalcLines() {
        visibleLines.clear();
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null) return;

        CustomFontRenderer font = getFont();
        int wrapWidth = width - 12 - SCROLLBAR_WIDTH;  

        String remaining = text;
        int currentGlobalIndex = 0;

        while (remaining.length() > 0) {
            String line = font.trimStringToWidth(remaining, wrapWidth);
            if (line.isEmpty()) line = remaining.substring(0, 1);

            visibleLines.add(new LineData(line, currentGlobalIndex));
            currentGlobalIndex += line.length();
            remaining = remaining.substring(line.length());
        }

        if (visibleLines.isEmpty()) visibleLines.add(new LineData("", 0));

        int lineHeight = font.getHeight() + 2;
        int maxDisplayLines = (height - 8) / lineHeight;
        this.maxScroll = Math.max(0, visibleLines.size() - maxDisplayLines);

         
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    private void scrollToCursor() {
        if (visibleLines.isEmpty()) return;

        int cursorLine = 0;
        for (int i = 0; i < visibleLines.size(); i++) {
            LineData ld = visibleLines.get(i);
            if (cursorPosition >= ld.startIndex && cursorPosition <= ld.startIndex + ld.content.length()) {
                cursorLine = i;
                break;
            }
        }

        CustomFontRenderer font = getFont();
        int lineHeight = font.getHeight() + 2;
        int visibleLinesCount = (height - 8) / lineHeight;

        if (cursorLine < scrollOffset) {
            scrollOffset = cursorLine;
        } else if (cursorLine >= scrollOffset + visibleLinesCount) {
            scrollOffset = cursorLine - visibleLinesCount + 1;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    public void handleMouseInput() {
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (dWheel > 0) scrollOffset--;
            else scrollOffset++;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }

    public void drawTextBox() {
        if (visibleLines.isEmpty() && !text.isEmpty()) recalcLines();
        if (visibleLines.isEmpty() && text.isEmpty()) visibleLines.add(new LineData("", 0));

         
        drawRect(xPos - 1, yPos - 1, xPos + width + 1, yPos + height + 1, Theme.COMPONENT_BG.getRGB());
        drawRect(xPos, yPos, xPos + width, yPos + height, 0xFF151515);

         
        int borderColor = isFocused ? Theme.ORANGE.getRGB() : Theme.COMPONENT_BG.darker().getRGB();
        drawRect(xPos - 1, yPos - 1, xPos + width + 1, yPos, borderColor);
        drawRect(xPos - 1, yPos + height, xPos + width + 1, yPos + height + 1, borderColor);
        drawRect(xPos - 1, yPos, xPos, yPos + height, borderColor);
        drawRect(xPos + width, yPos, xPos + width + 1, yPos + height, borderColor);

        CustomFontRenderer font = getFont();
        if (font == null) return;

        int lineHeight = font.getHeight() + 2;
        int maxLines = (height - 8) / lineHeight;

         
        int minSel = Math.min(cursorPosition, selectionEnd);
        int maxSel = Math.max(cursorPosition, selectionEnd);

        for (int i = 0; i < Math.min(visibleLines.size(), maxLines); i++) {
            int lineIdx = i + scrollOffset;
            if (lineIdx >= visibleLines.size()) break;

            LineData ld = visibleLines.get(lineIdx);
            String line = ld.content;

            float yRender = yPos + 4 + (i * lineHeight);
            float xRender = xPos + 4;

             
            if (minSel != maxSel) {
                int lineStartGlobal = ld.startIndex;
                int lineEndGlobal = lineStartGlobal + line.length();

                if (maxSel > lineStartGlobal && minSel < lineEndGlobal) {
                    int localSelStart = Math.max(0, minSel - lineStartGlobal);
                    int localSelEnd = Math.min(line.length(), maxSel - lineStartGlobal);

                    float x1 = xRender + font.getStringWidth(line.substring(0, localSelStart));
                    float x2 = xRender + font.getStringWidth(line.substring(0, localSelEnd));

                    drawRect((int)x1, (int)yRender - 1, (int)x2, (int)yRender + font.getHeight() + 1, 0xFF0000AA);
                }
            }

             
            font.drawString(line, xRender, yRender, 0xFFFFFFFF);

             
            if (isFocused && cursorCounter / 6 % 2 == 0) {
                if (cursorPosition >= ld.startIndex && cursorPosition <= ld.startIndex + line.length()) {
                    int localCursorPos = cursorPosition - ld.startIndex;
                    String beforeCursor = line.substring(0, localCursorPos);
                    float xCursor = xRender + font.getStringWidth(beforeCursor);
                    drawRect((int)xCursor, (int)yRender - 1, (int)xCursor + 1, (int)yRender + font.getHeight() + 1, 0xFFFFFFFF);
                }
            }
        }

         
        if (maxScroll > 0) {
            int totalTrackHeight = height - 2;
            int contentHeightInLines = visibleLines.size();
            int viewableLines = maxLines;

             
            float ratio = (float) viewableLines / (float) contentHeightInLines;
            scrollHeight = Math.max(10, (int) (totalTrackHeight * ratio));

             
            float scrollProgress = (float) scrollOffset / (float) maxScroll;
            int trackEffectiveHeight = totalTrackHeight - scrollHeight;
            scrollY = 1 + (int) (trackEffectiveHeight * scrollProgress);

            int barX = xPos + width - SCROLLBAR_WIDTH;
            int barY = yPos + scrollY;

             
            drawRect(barX, yPos + 1, barX + SCROLLBAR_WIDTH - 1, yPos + height - 1, 0xFF202020);
             
            int barColor = isDraggingScroll ? Theme.ORANGE.brighter().getRGB() : Theme.ORANGE.getRGB();
            drawRect(barX, barY, barX + SCROLLBAR_WIDTH - 1, barY + scrollHeight, barColor);
        }
    }

    private CustomFontRenderer getFont() {
        if (FuctorizeClient.INSTANCE == null || FuctorizeClient.INSTANCE.fontManager == null) return null;
        return FuctorizeClient.INSTANCE.fontManager.regular_18;
    }
}