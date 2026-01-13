// C:\Fuctorize\src\main\java\ru\fuctorial\fuctorize\event\PacketEvent.java
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

    // Defensive override: guarantee cancelability regardless of annotation lookup.
    @Override
    public boolean isCancelable() {
        return true;
    }

    // A nested class for outgoing packets.
    // FIX: Add @Cancelable directly to the concrete event class that is being posted.
    // This is the most reliable way to ensure FML recognizes it as cancelable.
    @Cancelable
    public static class Send extends PacketEvent {
        public Send(Packet packet) {
            super(packet);
        }
    }

    // A nested class for incoming packets.
    // FIX: Also add @Cancelable here for consistency and future use.
    @Cancelable
    public static class Receive extends PacketEvent {
        public Receive(Packet packet) {
            super(packet);
        }
    }
}
