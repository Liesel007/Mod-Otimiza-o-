package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contadores dos modulos, para o resumo ao fechar o mundo e o comando /hlotimizacao status.
 * Contam desde que o jogo abriu.
 */
public final class Estatisticas {
    public static final AtomicLong DESPAWN_MOBS_LIBERADOS = new AtomicLong();
    public static final AtomicLong DESPAWN_ITENS_DEVOLVIDOS = new AtomicLong();
    public static final AtomicLong GPU_FRAMEBUFFERS = new AtomicLong();
    public static final AtomicLong GPU_TEXTURAS = new AtomicLong();
    public static final Set<String> TOOLTIP_ITENS = ConcurrentHashMap.newKeySet();
    public static final AtomicLong SALVAMENTO_ASSINCRONOS = new AtomicLong();
    public static final AtomicLong SALVAMENTO_SINCRONOS = new AtomicLong();
    public static final AtomicLong SALVAMENTO_FALHAS = new AtomicLong();
    public static final Set<String> SALVAMENTO_CLASSES_SINCRONAS = ConcurrentHashMap.newKeySet();
    public static final AtomicLong LAG_REGISTROS = new AtomicLong();
    public static final AtomicLong AVARITIA_SAVES_EVITADOS = new AtomicLong();
    public static final AtomicLong AVARITIA_BAUS_GRAVADOS = new AtomicLong();
    public static final AtomicLong AVARITIA_BAUS_PULADOS = new AtomicLong();
    public static final AtomicLong AVARITIA_CHAVES_CONSERTADAS = new AtomicLong();

    private Estatisticas() {
    }

    /** Uma linha por modulo ligado. */
    public static List<String> resumo() {
        List<String> linhas = new ArrayList<>();
        if (Config.ativo(Modulo.DESPAWN_ITENS)) {
            linhas.add(String.format("despawn_itens: %d mob(s) que pegaram itens continuaram podendo desaparecer; %d item(ns) devolvido(s) ao desaparecer",
                    DESPAWN_MOBS_LIBERADOS.get(), DESPAWN_ITENS_DEVOLVIDOS.get()));
        }
        if (Config.ativo(Modulo.VAZAMENTO_GPU)) {
            linhas.add(String.format("vazamento_gpu: %d framebuffer(s) e %d textura(s) esquecidos por mods foram liberados",
                    GPU_FRAMEBUFFERS.get(), GPU_TEXTURAS.get()));
        }
        if (Config.ativo(Modulo.TOOLTIP_SEGURO)) {
            linhas.add(String.format("tooltip_seguro: corrigiu tooltips com linha nula em %d item(ns)%s",
                    TOOLTIP_ITENS.size(), TOOLTIP_ITENS.isEmpty() ? "" : " " + TOOLTIP_ITENS));
        }
        if (Config.ativo(Modulo.SALVAMENTO_ASSINCRONO)) {
            linhas.add(String.format("salvamento_assincrono: %d arquivo(s) gravado(s) fora do thread principal, %d no thread principal "
                            + "(%d classe(s) sincrona(s)%s), %d falha(s) regravada(s) depois",
                    SALVAMENTO_ASSINCRONOS.get(), SALVAMENTO_SINCRONOS.get(), SALVAMENTO_CLASSES_SINCRONAS.size(),
                    SALVAMENTO_CLASSES_SINCRONAS.isEmpty() ? "" : ": " + String.join(", ", SALVAMENTO_CLASSES_SINCRONAS),
                    SALVAMENTO_FALHAS.get()));
        }
        if (Config.ativo(Modulo.DIAGNOSTICO_LAG)) {
            linhas.add(String.format("diagnostico_lag: %d travamento(s) registrado(s)", LAG_REGISTROS.get()));
        }
        if (Config.ativo(Modulo.CORRECAO_INFINITY_CHEST)) {
            linhas.add(String.format("correcao_infinity_chest: %d Infinity Chest(s) gravado(s) fora do thread principal, %d sem mudancas "
                            + "nao regravado(s), %d salvamento(s) repetido(s) por dimensao evitado(s), %d chave(s) consertada(s)",
                    AVARITIA_BAUS_GRAVADOS.get(), AVARITIA_BAUS_PULADOS.get(), AVARITIA_SAVES_EVITADOS.get(), AVARITIA_CHAVES_CONSERTADAS.get()));
        }
        return linhas;
    }

    public static void registrarResumo() {
        List<String> linhas = resumo();
        if (!linhas.isEmpty()) {
            Config.LOGGER.info("Resumo desde que o jogo abriu:\n  {}", String.join("\n  ", linhas));
        }
    }
}
