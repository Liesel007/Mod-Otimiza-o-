package com.hlotimizacao.config;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Presets por tamanho de modpack. O preset so define valores padrao: o que estiver escrito no
 * hlotimizacao.properties continua tendo prioridade.
 */
public enum Preset {
    /** Ate 120 mods: so correcoes e diagnostico, nada que mude o comportamento do jogo. */
    LEVE,
    /** De 121 a 250 mods. */
    PADRAO,
    /** Mais de 250 mods: valores mais firmes e a insercao rapida ligada. */
    AGRESSIVO,
    /** Sem valores de preset: cada modulo em "auto" se comporta como no padrao. */
    PERSONALIZADO;

    public static final int LIMITE_LEVE = 120;
    public static final int LIMITE_PADRAO = 250;

    /** Modulos que mudam o comportamento do jogo: desligados no preset leve (em "auto"). */
    public static final Set<Modulo> MUDAM_COMPORTAMENTO = EnumSet.of(Modulo.ALCANCE_ATIVACAO, Modulo.LIMITE_ENXAME, Modulo.LIMPEZA_ITENS,
            Modulo.INSERCAO_RAPIDA);

    public String chave() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Nome para mostrar, no idioma do mod (a config aceita os dois). */
    public String nome() {
        if (Msg.portugues()) {
            return chave();
        }
        return switch (this) {
            case LEVE -> "light";
            case PADRAO -> "default";
            case AGRESSIVO -> "aggressive";
            case PERSONALIZADO -> "custom";
        };
    }

    public static Preset porQuantidade(int mods) {
        return mods <= LIMITE_LEVE ? LEVE : mods <= LIMITE_PADRAO ? PADRAO : AGRESSIVO;
    }

    /** null = "auto" ou valor desconhecido. */
    public static Preset deTexto(String texto) {
        if (texto == null) {
            return null;
        }
        return switch (texto.trim().toLowerCase(Locale.ROOT)) {
            case "leve", "light" -> LEVE;
            case "padrao", "default", "standard" -> PADRAO;
            case "agressivo", "aggressive" -> AGRESSIVO;
            case "personalizado", "custom" -> PERSONALIZADO;
            default -> null;
        };
    }

    /** Valor padrao de uma chave numerica conforme o preset. */
    public int valor(int padrao, int agressivo) {
        return this == AGRESSIVO ? agressivo : padrao;
    }
}
