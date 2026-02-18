package ru.fuctorial.fuctorize.client.gui.sender;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.Unpooled;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.font.CustomFontRenderer;
import ru.fuctorial.fuctorize.client.gui.clickgui.Theme;
import ru.fuctorial.fuctorize.client.gui.widgets.StyledButton;
import ru.fuctorial.fuctorize.utils.ChatUtils;
import ru.fuctorial.fuctorize.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

public class GuiPacketSender extends GuiScreen {

    private final GuiScreen parentScreen;
    private int x, y, panelWidth, panelHeight;
    
    private GuiTextField channelField;
    private GuiTextField dataField;
    private StyledButton sendButton;
    private String statusMessage = "";
    private long statusTime = 0;
    private int statusColor = -1;

    public GuiPacketSender(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        this.panelWidth = 400;
        this.panelHeight = 250;
        this.x = (this.width - this.panelWidth) / 2;
        this.y = (this.height - this.panelHeight) / 2;

        Keyboard.enableRepeatEvents(true);

        int fieldWidth = panelWidth - 40;
        
        this.channelField = new GuiTextField(mc.fontRenderer, x + 20, y + 50, fieldWidth, 20);
        this.channelField.setMaxStringLength(100);
        this.channelField.setFocused(true);
        
        this.dataField = new GuiTextField(mc.fontRenderer, x + 20, y + 100, fieldWidth, 20);
        this.dataField.setMaxStringLength(32767);

        this.buttonList.clear();
        this.sendButton = new StyledButton(1, x + 20, y + 150, fieldWidth, 20, "Отправить");
        this.buttonList.add(sendButton);
        
        this.buttonList.add(new StyledButton(0, x + 20, y + panelHeight - 30, fieldWidth, 20, "Назад"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            this.mc.displayGuiScreen(parentScreen);
        } else if (button.id == 1) {
            sendPacket();
        }
    }

    private void sendPacket() {
        String channel = channelField.getText().trim();
        String hexData = dataField.getText().trim();

        if (channel.isEmpty()) {
            setStatus("Ошибка: Канал не может быть пустым", 0xFFFF5555);
            return;
        }

        try {
            byte[] payload = hexStringToByteArray(hexData);
            
             
             
            FMLProxyPacket packet = new FMLProxyPacket(Unpooled.wrappedBuffer(payload), channel);
            
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(packet);
                setStatus("Пакет отправлен! (" + payload.length + " байт)", 0xFF55FF55);
            } else {
                setStatus("Ошибка: Нет соединения с сервером", 0xFFFF5555);
            }
            
        } catch (IllegalArgumentException e) {
            setStatus("Ошибка: Неверный формат HEX", 0xFFFF5555);
        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Ошибка: " + e.getMessage(), 0xFFFF5555);
        }
    }

    private void setStatus(String msg, int color) {
        this.statusMessage = msg;
        this.statusColor = color;
        this.statusTime = System.currentTimeMillis();
    }

    public static byte[] hexStringToByteArray(String s) {
        s = s.replace(" ", "");  
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int digit1 = Character.digit(s.charAt(i), 16);
            int digit2 = Character.digit(s.charAt(i + 1), 16);
            if (digit1 == -1 || digit2 == -1) throw new IllegalArgumentException("Invalid hex char");
            data[i / 2] = (byte) ((digit1 << 4) + digit2);
        }
        return data;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parentScreen);
            return;
        }
        if (channelField.isFocused()) {
            channelField.textboxKeyTyped(typedChar, keyCode);
        } else if (dataField.isFocused()) {
            dataField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        channelField.mouseClicked(mouseX, mouseY, mouseButton);
        dataField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (FuctorizeClient.INSTANCE.fontManager == null || !FuctorizeClient.INSTANCE.fontManager.isReady()) return;

         
        RenderUtils.drawRect(x, y, x + panelWidth, y + panelHeight, Theme.CATEGORY_BG.getRGB());
        RenderUtils.drawRect(x - 1, y - 1, x + panelWidth + 1, y, Theme.BORDER.getRGB());
        RenderUtils.drawRect(x - 1, y - 1, x, y + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(x + panelWidth, y - 1, x + panelWidth + 1, y + panelHeight + 1, Theme.BORDER.getRGB());
        RenderUtils.drawRect(x - 1, y + panelHeight, x + panelWidth + 1, y + panelHeight + 1, Theme.BORDER.getRGB());

        CustomFontRenderer titleFont = FuctorizeClient.INSTANCE.fontManager.bold_22;
        CustomFontRenderer font = FuctorizeClient.INSTANCE.fontManager.regular_18;

        String title = "Packet Sender";
        titleFont.drawString(title, x + (panelWidth - titleFont.getStringWidth(title)) / 2f, y + 8, Theme.ORANGE.getRGB());

        font.drawString("Channel Name:", x + 20, y + 35, -1);
        channelField.drawTextBox();

        font.drawString("Hex Data (e.g. 00 0A FF):", x + 20, y + 85, -1);
        dataField.drawTextBox();

         
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusTime < 3000) {
            font.drawString(statusMessage, x + (panelWidth - font.getStringWidth(statusMessage)) / 2f, y + 130, statusColor);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
