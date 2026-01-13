package ru.fuctorial.fuctorize.event;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Packet;

/**
 * Высокоприоритетное, синхронное событие, которое срабатывает в PacketHandler
 * немедленно при получении пакета, ДО того, как он будет передан в основную
 * шину событий Forge. Позволяет мгновенно реагировать на пакеты и, при необходимости,
 * отменять их дальнейшую обработку.
 */
@Cancelable
public class PrePacketReceiveEvent extends Event {
    public Packet packet; // Поля больше не final
    public ChannelHandlerContext context; // Поля больше не final

    // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
    // Добавляем публичный конструктор без аргументов, который требует FML.
    public PrePacketReceiveEvent() {}
    // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

    public PrePacketReceiveEvent(Packet packet, ChannelHandlerContext context) {
        this.packet = packet;
        this.context = context;
    }
}