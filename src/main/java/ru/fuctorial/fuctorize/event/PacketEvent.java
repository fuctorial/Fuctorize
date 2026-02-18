 
package ru.fuctorial.fuctorize.event;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.network.Packet;

@Cancelable
public class PacketEvent extends Event {
    private final Packet packet;

    public PacketEvent(Packet packet) {
        this.packet = packet;
    }

    public Packet getPacket() {
        return packet;
    }

     
    @Override
    public boolean isCancelable() {
        return true;
    }

     
     
     
    @Cancelable
    public static class Send extends PacketEvent {
        public Send(Packet packet) {
            super(packet);
        }
    }

     
     
    @Cancelable
    public static class Receive extends PacketEvent {
        public Receive(Packet packet) {
            super(packet);
        }
    }
}
