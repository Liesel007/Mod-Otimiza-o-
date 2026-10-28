package com.hlotimizacao.benchmark;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;

/** Conexao de teste que descarta todos os pacotes (o jogador de teste nao tem cliente). */
final class ConexaoMuda extends ServerGamePacketListenerImpl {
    ConexaoMuda(ServerPlayer jogador) {
        super(jogador.getServer(), new Connection(PacketFlow.CLIENTBOUND), jogador);
    }

    @Override
    public void send(Packet<?> packet) {
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
    }
}
