package com.hlotimizacao.recursos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Avisos do relatorio_conflitos, guardados para o /hl status avisos (alem de irem para o log).
 * Cada aviso tem um texto curto (uma linha do chat) e o detalhe com a correcao (hover).
 */
public final class Avisos {
    public record Aviso(String curto, String detalhe) {
    }

    private static final Map<String, Aviso> AVISOS = new LinkedHashMap<>();

    private Avisos() {
    }

    /** Repetir o mesmo texto curto (ex.: ao abrir o mundo de novo) so atualiza o detalhe. */
    public static synchronized void adicionar(String curto, String detalhe) {
        AVISOS.put(curto, new Aviso(curto, detalhe));
    }

    public static synchronized List<Aviso> todos() {
        return new ArrayList<>(AVISOS.values());
    }

    public static synchronized int quantidade() {
        return AVISOS.size();
    }
}
