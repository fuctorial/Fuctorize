// C:\Fuctorize\src\main\java\ru.fuctorial\fuctorize\module\impl\CoordTP.java
package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import ru.fuctorial.fuctorize.module.settings.TextSetting;
import ru.fuctorial.fuctorize.utils.Lang;
import ru.fuctorial.fuctorize.utils.NetUtils;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

public class CoordTP extends Module {

    private TextSetting xSetting, ySetting, zSetting;

    public CoordTP(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("coordtp", Lang.get("module.coordtp.name"), Category.EXPLOIT, ActivationType.SINGLE);

        xSetting = new TextSetting(Lang.get("module.coordtp.setting.coord_x"), "0");
        ySetting = new TextSetting(Lang.get("module.coordtp.setting.coord_y"), "120");
        zSetting = new TextSetting(Lang.get("module.coordtp.setting.coord_z"), "0");

        addSetting(xSetting);
        addSetting(ySetting);
        addSetting(zSetting);

        addSetting(new BindSetting(Lang.get("module.coordtp.setting.bind"), Keyboard.KEY_NONE));
    }

    @Override
    public String getDescription() {
        return Lang.get("module.coordtp.desc");
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            toggle();
            return;
        }

        double finalX, finalY, finalZ;
        try {
            finalX = Double.parseDouble(xSetting.text);
            finalY = Double.parseDouble(ySetting.text);
            finalZ = Double.parseDouble(zSetting.text);
        } catch (NumberFormatException e) {
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + Lang.get("chat.coordtp.invalid_coords")));
            toggle();
            return;
        }

        double startX = mc.thePlayer.posX;
        double startZ = mc.thePlayer.posZ;

        final double eyeHeight = 1.62D;
        double highY = 260.0;
        double highStance = highY + eyeHeight;

        NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(startX, highY, highStance, startZ, false));
        NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(finalX, highY, highStance, finalZ, false));
        NetUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(finalX, finalY, finalY + eyeHeight, finalZ, true));

        mc.thePlayer.fallDistance = 0f;
        mc.thePlayer.setPosition(finalX, finalY, finalZ);

        toggle();
    }
}