package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Msg;
import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.mixin.lag.LevelAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraftforge.event.TickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aviso para dimensoes pesadas (muitas entidades ou itens no chao), com o maior valor visto na sessao.
 * Amostra a cada 60 s e tambem recebe as contagens do relatorio do diagnostico_lag.
 */
public final class DimensoesPesadas {
    static final int LIMITE_ENTIDADES = 300;
    static final int LIMITE_ITENS = 200;
    private static final long INTERVALO_MS = 60_000;

    private record Pico(int entidades, int itens, int spawners) {
    }

    private static final Map<String, Pico> PICOS = new ConcurrentHashMap<>();
    private static long proximaAmostraMs;

    private DimensoesPesadas() {
    }

    public static void aoTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || System.currentTimeMillis() < proximaAmostraMs) {
            return;
        }
        proximaAmostraMs = System.currentTimeMillis() + INTERVALO_MS;
        amostrar(event.getServer());
    }

    static void amostrar(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            int entidades = 0;
            int itens = 0;
            for (Entity e : level.getAllEntities()) {
                entidades++;
                if (e instanceof ItemEntity) {
                    itens++;
                }
            }
            int spawners = -1;
            if (Config.ativo(Modulo.DIAGNOSTICO_LAG)) {
                spawners = 0;
                for (TickingBlockEntity ticker : ((LevelAccessor) level).hlotimizacao$blockEntityTickers()) {
                    if (ticker.getType().contains("spawner")) {
                        spawners++;
                    }
                }
            }
            verificar(level.dimension().location().toString(), entidades, itens, spawners);
        }
    }

    /** spawners < 0: desconhecido. */
    public static void verificar(String dimensao, int entidades, int itens, int spawners) {
        if (entidades <= LIMITE_ENTIDADES && itens <= LIMITE_ITENS) {
            return;
        }
        Pico novo = PICOS.merge(dimensao, new Pico(entidades, itens, spawners),
                (a, b) -> new Pico(Math.max(a.entidades(), b.entidades()), Math.max(a.itens(), b.itens()), Math.max(a.spawners(), b.spawners())));
        String nome = dimensao.startsWith("minecraft:") ? dimensao.substring("minecraft:".length()) : dimensao;
        if (nome.length() > 22) {
            nome = nome.substring(0, 21) + "…";
        }
        String curto = Msg.f("{}: {} entid., {} itens", nome, novo.entidades(), novo.itens())
                + (novo.spawners() > 0 ? Msg.f(", {} spawners", novo.spawners()) : "");
        Avisos.substituir("dimensao:" + dimensao, curto,
                Msg.f("{}: {} entidades, {} itens no chao", dimensao, novo.entidades(), novo.itens())
                        + (novo.spawners() >= 0 ? Msg.f(", {} spawners ativos", novo.spawners()) : "") + " "
                        + Msg.f("(maior quantidade vista nesta sessao). Entidades e itens demais numa dimensao deixam o servidor lento, mesmo sem jogador por perto se os chunks estiverem carregados. Use /hl limpar para os itens no chao e remova spawners que nao usa. Limite do aviso: {} entidades ou {} itens.",
                        LIMITE_ENTIDADES, LIMITE_ITENS));
    }
}
