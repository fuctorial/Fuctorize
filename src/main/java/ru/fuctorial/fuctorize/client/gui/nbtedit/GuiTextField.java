 
package ru.fuctorial.fuctorize.client.gui.nbtedit;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.module.impl.SmartSpammer;
import ru.fuctorial.fuctorize.utils.CharacterFilter;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuiTextField extends Gui {

     
     
    public int xPos;
    public int yPos;
    public int width;
    public final int height;
    private String text = "";
    private int maxStringLength = 256;
    private int cursorCounter;
    private boolean isFocused = false;
    private boolean isEnabled = true;
    private int lineScrollOffset = 0;
    private int cursorPosition = 0;
    private int selectionEnd = 0;
    private int enabledColor = Theme.TEXT_WHITE.getRGB();
    private int disabledColor = Theme.TEXT_GRAY.getRGB();
    private boolean visible = true;
    private final boolean allowSection;
    private String placeholder;
     
    private boolean syntaxHighlighting = false;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(\\{[^}]+})");

     
    public GuiTextField(int x, int y, int w, int h, boolean allowSection) {
        this.xPos = x;
        this.yPos = y;
        this.width = w;
        this.height = h;
        this.allowSection = allowSection;
    }
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

     
    public void setSyntaxHighlighting(boolean enable) {
        this.syntaxHighlighting = enable;
    }


     
    private CustomFontRenderer getFont() {
        return FuctorizeClient.INSTANCE.fontManager.regular_18;
    }

    public void updateCursorCounter() {
        ++this.cursorCounter;
    }

    public void setText(String par1Str) {
        this.text = par1Str.length() > this.maxStringLength ? par1Str.substring(0, this.maxStringLength) : par1Str;
        this.setCursorPositionEnd();
    }

    public String getText() {
        return this.text;
    }

    public String getSelectedtext() {
        int start = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int end = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        return this.text.substring(start, end);
    }

    public void writeText(String par1Str) {
        String newText = "";
        String filtered = CharacterFilter.filerAllowedCharacters(par1Str, this.allowSection);
        int start = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int end = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        int available = this.maxStringLength - this.text.length() - (start - end);

        if (this.text.length() > 0) {
            newText += this.text.substring(0, start);
        }

        int insertedLength;
        if (available < filtered.length()) {
            newText += filtered.substring(0, available);
            insertedLength = available;
        } else {
            newText += filtered;
            insertedLength = filtered.length();
        }

        if (this.text.length() > 0 && end < this.text.length()) {
            newText += this.text.substring(end);
        }

        this.text = newText;
        this.moveCursorBy(start - this.selectionEnd + insertedLength);
    }

    public void deleteWords(int par1) {
        if (this.text.length() == 0) return;
        if (this.selectionEnd != this.cursorPosition) {
            this.writeText("");
        } else {
            this.deleteFromCursor(this.getNthWordFromCursor(par1) - this.cursorPosition);
        }
    }

    public void deleteFromCursor(int par1) {
        if (this.text.length() == 0) return;
        if (this.selectionEnd != this.cursorPosition) {
            this.writeText("");
        } else {
            boolean isNegative = par1 < 0;
            int start = isNegative ? this.cursorPosition + par1 : this.cursorPosition;
            int end = isNegative ? this.cursorPosition : this.cursorPosition + par1;
            String result = "";
            if (start >= 0) {
                result = this.text.substring(0, start);
            }
            if (end < this.text.length()) {
                result += this.text.substring(end);
            }
            this.text = result;
            if (isNegative) {
                this.moveCursorBy(par1);
            }
        }
    }

    public void drawTextBox() {
        if (!this.getVisible() || getFont() == null) return;

        RenderUtils.drawRect(xPos - 1, yPos - 1, xPos + width + 1, yPos + height + 1, Theme.COMPONENT_BG.getRGB());

        int borderColor = Theme.COMPONENT_BG.darker().getRGB();
        if (this.isFocused()) {
            borderColor = Theme.ORANGE.getRGB();
        }
        RenderUtils.drawRect(xPos - 2, yPos - 2, xPos + width + 2, yPos - 1, borderColor);
        RenderUtils.drawRect(xPos - 2, yPos + height + 1, xPos + width + 2, yPos + height + 2, borderColor);
        RenderUtils.drawRect(xPos - 2, yPos - 1, xPos - 1, yPos + height + 1, borderColor);
        RenderUtils.drawRect(xPos + width + 1, yPos - 1, xPos + width + 2, yPos + height + 1, borderColor);

        int textColor = this.isEnabled ? this.enabledColor : this.disabledColor;
        int selectionStart = this.cursorPosition - this.lineScrollOffset;
        int selectionEndPos = this.selectionEnd - this.lineScrollOffset;
        String visibleText = getFont().trimStringToWidth(this.text.substring(this.lineScrollOffset), this.getWidth());

        boolean cursorVisible = this.isFocused && this.cursorCounter / 6 % 2 == 0;
        float textX = xPos + 4;
        float textY = yPos + (this.height - getFont().getHeight()) / 2f;

        if (!this.isFocused() && this.text.isEmpty() && this.placeholder != null) {
            getFont().drawString(this.placeholder, textX, textY, Theme.TEXT_GRAY.getRGB());
        } else {
             
            if (syntaxHighlighting && !visibleText.isEmpty()) {
                drawHighlightedText(visibleText, textX, textY, textColor);
            } else if (visibleText.length() > 0) {
                getFont().drawString(visibleText, textX, textY, textColor);
            }
        }

        boolean isCursorInBounds = selectionStart >= 0 && selectionStart <= visibleText.length();
        int cursorX = (int) (textX + getFont().getStringWidth(visibleText.substring(0, Math.min(selectionStart, visibleText.length()))));

        if (cursorVisible) {
            if (isCursorInBounds) {
                RenderUtils.drawRect(cursorX, (int)textY, cursorX + 1, (int)(textY + getFont().getHeight()), -3092272);
            } else {
                getFont().drawString("_", cursorX, textY, textColor);
            }
        }

        if (selectionEndPos != selectionStart) {
            int selStartVisible = Math.min(selectionStart, selectionEndPos);
            int selEndVisible = Math.max(selectionStart, selectionEndPos);
            selStartVisible = Math.max(0, selStartVisible);
            selEndVisible = Math.min(visibleText.length(), selEndVisible);

            int selStartX = (int) (textX + getFont().getStringWidth(visibleText.substring(0, selStartVisible)));
            int selEndX = (int) (textX + getFont().getStringWidth(visibleText.substring(0, selEndVisible)));

            this.drawSelectionBox(selStartX, (int) textY, selEndX, (int) (textY + getFont().getHeight()));
        }
    }

    private void drawHighlightedText(String text, float x, float y, int defaultColor) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        int lastEnd = 0;
        float currentX = x;

        while (matcher.find()) {
             
            String precedingText = text.substring(lastEnd, matcher.start());
            if (!precedingText.isEmpty()) {
                getFont().drawString(precedingText, currentX, y, defaultColor);
                currentX += getFont().getStringWidth(precedingText);
            }
             
            String placeholderText = matcher.group(1);
             
            boolean isValid = SmartSpammer.isValidPlaceholderSyntax(placeholderText);
             
            int placeholderColor = isValid ? Theme.ORANGE.getRGB() : 0xFFFF5555;

            getFont().drawString(placeholderText, currentX, y, placeholderColor);
            currentX += getFont().getStringWidth(placeholderText);

            lastEnd = matcher.end();
        }

         
        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd);
            getFont().drawString(remainingText, currentX, y, defaultColor);
        }
    }

    private void drawSelectionBox(int x1, int y1, int x2, int y2) {
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        RenderUtils.drawRect(left, top, right, bottom, 0x8000A0FF);
    }

    public void setCursorPosition(int pos) {
        this.cursorPosition = pos;
        int len = this.text.length();
        this.cursorPosition = Math.max(0, Math.min(len, this.cursorPosition));
        this.setSelectionPos(this.cursorPosition);
    }

    public void setSelectionPos(int pos) {
        int len = this.text.length();
        if (pos > len) pos = len;
        if (pos < 0) pos = 0;
        this.selectionEnd = pos;

        if (getFont() != null) {
            if (this.lineScrollOffset > len) {
                this.lineScrollOffset = len;
            }
            int visibleWidth = this.getWidth();
            String visibleText = getFont().trimStringToWidth(this.text.substring(this.lineScrollOffset), visibleWidth);
            int endOfVisible = visibleText.length() + this.lineScrollOffset;
            if (pos == this.lineScrollOffset) {
                this.lineScrollOffset -= getFont().trimStringToWidth(this.text, visibleWidth, true).length();
            }
            if (pos > endOfVisible) {
                this.lineScrollOffset += pos - endOfVisible;
            } else if (pos <= this.lineScrollOffset) {
                this.lineScrollOffset -= this.lineScrollOffset - pos;
            }
            this.lineScrollOffset = Math.max(0, Math.min(len, this.lineScrollOffset));
        }
    }

    public int getWidth() {
        return this.width - 8;
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        boolean wasClicked = mouseX >= this.xPos && mouseX < this.xPos + this.width &&
                mouseY >= this.yPos && mouseY < this.yPos + this.height;

         
         
         
        if (this.isEnabled) {
            this.setFocused(wasClicked);
        }

        if (this.isFocused && button == 0 && getFont() != null) {
            int clickPos = mouseX - this.xPos - 4;
            String textBeforeClick = getFont().trimStringToWidth(this.text.substring(this.lineScrollOffset), clickPos);
            this.setCursorPosition(textBeforeClick.length() + this.lineScrollOffset);
        }
    }

    public int getNthWordFromCursor(int par1) {
        return this.getNthWordFromPos(par1, this.getCursorPosition());
    }

    public int getCursorPosition() {
        return this.cursorPosition;
    }

    public boolean getVisible() {
        return this.visible;
    }

    public boolean isFocused() {
        return this.isFocused;
    }

    public void setFocused(boolean focused) {
        this.isFocused = focused;
        if (!focused) this.cursorCounter = 0;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void func_82265_c(boolean enabled) {
        this.isEnabled = enabled;
    }

    public void setMaxStringLength(int len) {
        this.maxStringLength = len;
    }

    public int getMaxStringLength() {
        return this.maxStringLength;
    }

    public void setCursorPositionZero() {
        this.setCursorPosition(0);
    }

    public void setCursorPositionEnd() {
        this.setCursorPosition(this.text.length());
    }

    public void moveCursorBy(int amount) {
        this.setCursorPosition(this.selectionEnd + amount);
    }

    public int getNthWordFromPos(int par1, int par2) {
        return this.func_73798_a(par1, this.getCursorPosition(), true);
    }

    public int func_73798_a(int par1, int par2, boolean par3) {
        int var4 = par2;
        boolean var5 = par1 < 0;
        int var6 = Math.abs(par1);
        for (int var7 = 0; var7 < var6; ++var7) {
            if (var5) {
                while (par3 && var4 > 0 && this.text.charAt(var4 - 1) == ' ') {
                    --var4;
                }
                while (var4 > 0 && this.text.charAt(var4 - 1) != ' ') {
                    --var4;
                }
            } else {
                int var8 = this.text.length();
                var4 = this.text.indexOf(32, var4);
                if (var4 == -1) {
                    var4 = var8;
                } else {
                    while (par3 && var4 < var8 && this.text.charAt(var4) == ' ') {
                        ++var4;
                    }
                }
            }
        }
        return var4;
    }

    public void setTextColor(int par1) {
        this.enabledColor = par1;
    }

    public boolean textboxKeyTyped(char par1, int par2) {
        if (!this.isEnabled || !this.isFocused) {
            return false;
        }
        switch (par1) {
            case 1:
                this.setCursorPositionEnd();
                this.setSelectionPos(0);
                return true;
            case 3:
                GuiScreen.setClipboardString(this.getSelectedtext());
                return true;
            case 22:
                this.writeText(GuiScreen.getClipboardString());
                return true;
            case 24:
                GuiScreen.setClipboardString(this.getSelectedtext());
                this.writeText("");
                return true;
            default:
                switch (par2) {
                    case 14:
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.deleteWords(-1);
                        } else {
                            this.deleteFromCursor(-1);
                        }
                        return true;
                    case 199:
                        if (GuiScreen.isShiftKeyDown()) {
                            this.setSelectionPos(0);
                        } else {
                            this.setCursorPositionZero();
                        }
                        return true;
                    case 203:
                        if (GuiScreen.isShiftKeyDown()) {
                            if (GuiScreen.isCtrlKeyDown()) {
                                this.setSelectionPos(this.getNthWordFromPos(-1, this.selectionEnd));
                            } else {
                                this.setSelectionPos(this.selectionEnd - 1);
                            }
                        } else if (GuiScreen.isCtrlKeyDown()) {
                            this.setCursorPosition(this.getNthWordFromCursor(-1));
                        } else {
                            this.moveCursorBy(-1);
                        }
                        return true;
                    case 205:
                        if (GuiScreen.isShiftKeyDown()) {
                            if (GuiScreen.isCtrlKeyDown()) {
                                this.setSelectionPos(this.getNthWordFromPos(1, this.selectionEnd));
                            } else {
                                this.setSelectionPos(this.selectionEnd + 1);
                            }
                        } else if (GuiScreen.isCtrlKeyDown()) {
                            this.setCursorPosition(this.getNthWordFromCursor(1));
                        } else {
                            this.moveCursorBy(1);
                        }
                        return true;
                    case 207:
                        if (GuiScreen.isShiftKeyDown()) {
                            this.setSelectionPos(this.text.length());
                        } else {
                            this.setCursorPositionEnd();
                        }
                        return true;
                    case 211:
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.deleteWords(1);
                        } else {
                            this.deleteFromCursor(1);
                        }
                        return true;
                    default:
                        if (ChatAllowedCharacters.isAllowedCharacter(par1)) {
                            this.writeText(Character.toString(par1));
                            return true;
                        } else {
                            return false;
                        }
                }
        }
    }
}