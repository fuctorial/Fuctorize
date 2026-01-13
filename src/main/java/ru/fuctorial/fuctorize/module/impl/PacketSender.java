package ru.fuctorial.fuctorize.module.impl;

import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.client.gui.sender.GuiPacketSender;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.module.settings.BindSetting;
import org.lwjgl.input.Keyboard;

public class PacketSender extends Module {

    public PacketSender(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        setMetadata("packetsender", "Packet Sender", Category.EXPLOIT, ActivationType.SINGLE);
        addSetting(new BindSetting("Open GUI", Keyboard.KEY_NONE));
        setShowInHud(false);
    }

    @Override
    public String getDescription() {
        return "Отправка кастомных пакетов (FML/CustomPayload) по hex-данным.";
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            mc.displayGuiScreen(new GuiPacketSender(mc.currentScreen));
        }
        toggle();
    }
}
