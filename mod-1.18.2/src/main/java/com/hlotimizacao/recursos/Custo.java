package com.hlotimizacao.recursos;

import java.util.concurrent.atomic.LongAdder;

/**
 * Quanto tempo o proprio HLO gasta por tick no thread do servidor (mixins e eventos), para o
 * "custo do HLO" no /hl. Caminhos chamados milhares de vezes por tick (uma vez por entidade ou por
 * insercao) medem so 1 chamada a cada AMOSTRA e multiplicam; eventos raros medem sempre.
 */
public final class Custo {
    private static final int AMOSTRA = 32;
    private static final int TICKS_MEDIA = 100;
    private static final LongAdder NS = new LongAdder();
    private static int contador;
    private static long somaNs;
    private static int ticks;
    private static volatile double msPorTick = -1;

    private Custo() {
    }

    /** Comeco de um caminho quente: devolve 0 quando esta chamada nao e amostrada. */
    public static long inicio() {
        return (++contador & (AMOSTRA - 1)) == 0 ? System.nanoTime() : 0L;
    }

    public static void fim(long inicio) {
        if (inicio != 0L) {
            NS.add((System.nanoTime() - inicio) * AMOSTRA);
        }
    }

    /** Tempo medido inteiro (eventos). */
    public static void somar(long ns) {
        NS.add(ns);
    }

    /** Fim de um tick do servidor: fecha a media a cada 100 ticks. */
    public static void fimDoTick() {
        somaNs += NS.sumThenReset();
        if (++ticks >= TICKS_MEDIA) {
            msPorTick = somaNs / 1e6 / ticks;
            somaNs = 0;
            ticks = 0;
        }
    }

    /** Media dos ultimos 100 ticks, ou -1 antes da primeira medida. */
    public static double msPorTick() {
        return msPorTick;
    }
}
