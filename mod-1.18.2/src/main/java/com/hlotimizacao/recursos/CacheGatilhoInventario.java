package com.hlotimizacao.recursos;

import net.minecraft.server.PlayerAdvancements;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Por jogador: ele tem alguma conquista pendente que pode ser obtida ao esvaziar uma pilha?
 * Invalidado sempre que o conjunto de conquistas pendentes do jogador muda. Usado so na thread do
 * servidor. WeakHashMap para nao segurar jogadores que sairam.
 */
public final class CacheGatilhoInventario {
    private static final Map<PlayerAdvancements, Boolean> CACHE = new WeakHashMap<>();

    private CacheGatilhoInventario() {
    }

    public static Boolean get(PlayerAdvancements advancements) {
        return CACHE.get(advancements);
    }

    public static void put(PlayerAdvancements advancements, boolean valor) {
        CACHE.put(advancements, valor);
    }

    public static void invalidar(PlayerAdvancements advancements) {
        CACHE.remove(advancements);
    }
}
