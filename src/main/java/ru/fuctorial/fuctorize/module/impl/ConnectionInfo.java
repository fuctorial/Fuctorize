package ru.fuctorial.fuctorize.module.impl;

import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.util.EnumChatFormatting;
import ru.fuctorial.fuctorize.FuctorizeClient;
import ru.fuctorial.fuctorize.event.PacketEvent;
import ru.fuctorial.fuctorize.manager.ModeratorManager;
import ru.fuctorial.fuctorize.module.Category;
import ru.fuctorial.fuctorize.module.Module;
import ru.fuctorial.fuctorize.utils.ChatUtils;
import ru.fuctorial.fuctorize.utils.Lang;

public class ConnectionInfo extends Module {

    public ConnectionInfo(FuctorizeClient client) {
        super(client);
    }

    @Override
    public void init() {
        // Добавьте ключи в ru_RU.lang и en_US.lang:
        // module.connectioninfo.name=Connection Info
        // module.connectioninfo.desc=Notifies about players joining or leaving. Special checks for Mods.
        // chat.connectioninfo.join=§7[§a+§7] §f%s
        // chat.connectioninfo.leave=§7[§c-§7] §f%s
        // chat.connectioninfo.leave_mod=§7[§c-§7] §c%s §7(Leave or Vanish)

        setMetadata("connectioninfo", Lang.get("module.connectioninfo.name"), Category.MISC);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.connectioninfo.desc");
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Игнорируем пакеты при заходе на сервер (первые 3 секунды), чтобы не спамить списком тех, кто уже онлайн
        if (mc.thePlayer.ticksExisted < 60) return;

        if (event.getPacket() instanceof S38PacketPlayerListItem) {
            S38PacketPlayerListItem packet = (S38PacketPlayerListItem) event.getPacket();

            String playerName = packet.func_149122_c(); // getPlayerName
            boolean isAdd = packet.func_149121_d();     // isAdd (true = join, false = leave)

            // Игнорируем самого себя и NPC (обычно у NPC latency = 0 или странные имена, но базовая проверка по имени обязательна)
            if (playerName == null || playerName.isEmpty() || playerName.equals(mc.thePlayer.getCommandSenderName())) {
                return;
            }

            if (isAdd) {
                // Игрок зашел
                String msg = Lang.format("chat.connectioninfo.join", playerName);
                // Если Lang не нашел ключ (старая версия файла), используем дефолт
                if (msg.equals("chat.connectioninfo.join")) {
                    msg = EnumChatFormatting.GRAY + "[" + EnumChatFormatting.GREEN + "+" + EnumChatFormatting.GRAY + "] " + EnumChatFormatting.WHITE + playerName;
                }
                ChatUtils.printMessage(msg);

            } else {
                // Игрок вышел (или скрылся в ванише)
                boolean isMod = ModeratorManager.isModerator(playerName);
                String msg;

                if (isMod) {
                    msg = Lang.format("chat.connectioninfo.leave_mod", playerName);
                    if (msg.equals("chat.connectioninfo.leave_mod")) {
                        msg = EnumChatFormatting.GRAY + "[" + EnumChatFormatting.RED + "-" + EnumChatFormatting.GRAY + "] " +
                                EnumChatFormatting.RED + playerName + EnumChatFormatting.GRAY + " (Leave or Vanish)";
                    }
                } else {
                    msg = Lang.format("chat.connectioninfo.leave", playerName);
                    if (msg.equals("chat.connectioninfo.leave")) {
                        msg = EnumChatFormatting.GRAY + "[" + EnumChatFormatting.RED + "-" + EnumChatFormatting.GRAY + "] " + EnumChatFormatting.WHITE + playerName;
                    }
                }
                ChatUtils.printMessage(msg);
            }
        }
    }
}