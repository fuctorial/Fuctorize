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
         
         
         
         
         
         

        setMetadata("connectioninfo", Lang.get("module.connectioninfo.name"), Category.MISC);
    }

    @Override
    public String getDescription() {
        return Lang.get("module.connectioninfo.desc");
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;

         
        if (mc.thePlayer.ticksExisted < 60) return;

        if (event.getPacket() instanceof S38PacketPlayerListItem) {
            S38PacketPlayerListItem packet = (S38PacketPlayerListItem) event.getPacket();

            String playerName = packet.func_149122_c();  
            boolean isAdd = packet.func_149121_d();      

             
            if (playerName == null || playerName.isEmpty() || playerName.equals(mc.thePlayer.getCommandSenderName())) {
                return;
            }

            if (isAdd) {
                 
                String msg = Lang.format("chat.connectioninfo.join", playerName);
                 
                if (msg.equals("chat.connectioninfo.join")) {
                    msg = EnumChatFormatting.GRAY + "[" + EnumChatFormatting.GREEN + "+" + EnumChatFormatting.GRAY + "] " + EnumChatFormatting.WHITE + playerName;
                }
                ChatUtils.printMessage(msg);

            } else {
                 
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