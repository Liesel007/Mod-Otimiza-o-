package com.hlotimizacao.compat;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Porte 1.18.2: no 1.18 ainda nao existem Component.literal/translatable/empty. */
public final class C {
    private C() {
    }

    public static MutableComponent lit(String texto) {
        return new TextComponent(texto);
    }

    public static MutableComponent tr(String chave, Object... args) {
        return new TranslatableComponent(chave, args);
    }

    public static MutableComponent empty() {
        return new TextComponent("");
    }
}
