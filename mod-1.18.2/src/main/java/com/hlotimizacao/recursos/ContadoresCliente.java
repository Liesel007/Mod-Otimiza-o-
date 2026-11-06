package com.hlotimizacao.recursos;

import java.util.Map;

/**
 * Contadores dos modulos de cliente que o painel e o /hl leem. Ficam aqui (codigo comum) porque o PainelStatus
 * tambem roda no servidor dedicado, onde as classes de render do cliente nao existem.
 */
public final class ContadoresCliente {
    public static volatile int enxameDesenhadas;
    public static volatile int enxameExistentes;
    /** tipo -> {existentes, escondidas}. */
    public static volatile Map<String, int[]> enxamePorTipo = Map.of();
    /** Desenhos de block entity pulados pela distancia (acumulado; so a thread de render escreve). */
    public static long blockEntitiesPulados;

    private ContadoresCliente() {
    }
}
