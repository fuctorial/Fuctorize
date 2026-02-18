package ru.fuctorial.fuctorize.event;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Packet;

 
@Cancelable
public class PrePacketReceiveEvent extends Event {
    public Packet packet;  
    public ChannelHandlerContext context;  

     
     
    public PrePacketReceiveEvent() {}
     

    public PrePacketReceiveEvent(Packet packet, ChannelHandlerContext context) {
        this.packet = packet;
        this.context = context;
    }
}