package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Msg;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Recomendacao de memoria a partir de dados (monitor_gc): quanto o jogo usa de verdade, quanto esta reservado (-Xmx)
 * e quanto sugerir, mais a RAM livre do sistema. So sugere: o mod nunca muda os argumentos da JVM.
 * <ul>
 * <li>Uso real (conjunto vivo): heap logo depois de cada coleta completa (Full); vale o maximo da sessao. Sem nenhuma delas, o minimo depois das coletas Young dos ultimos 5 min,
 *     marcado como estimado.</li>
 * <li>Sugestao: ver {@link #calcular}.</li>
 * <li>RAM livre do sistema a cada 5 s (5 min de historico); log uma vez por sessao abaixo de 1 GB.</li>
 * </ul>
 */
public final class Memoria {
    public static final long LIVRE_BAIXA_MB = 1024;
    private static final long JANELA_YOUNG_MS = 5 * 60_000;
    public static final int AMOSTRAS_RAM = 60; // 5 min a cada 5 s

    private static volatile long vivoMaxMb = -1;
    private static final Deque<long[]> POS_YOUNG = new ArrayDeque<>(); // {quando, MB}
    private static final float[] RAM_LIVRE = new float[AMOSTRAS_RAM];
    private static int ramIndice;
    private static int ramPreenchidos;
    private static volatile long ramLivreMb = -1;
    private static long proximaAmostraMs;
    private static boolean avisouRamBaixa;
    public static volatile int congelamentosComRamBaixa;
    private static volatile int amostrasRam;
    private static volatile int amostrasRamBaixa;
    private static volatile long menorRamLivreMb = Long.MAX_VALUE;
    private static volatile String primeiraRamBaixa;
    private static volatile long primeiraRamBaixaMb;

    private Memoria() {
    }

    /**
     * Depois de cada coleta que para a JVM (MonitorGc). So a coleta completa (Full) mede o conjunto vivo: depois de uma
     * Young o heap ainda tem o lixo da geracao velha (na sessao de 16/11: 4.103 MB depois de Young, 3.559 depois da
     * Full). O G1 tambem libera objetos humongous (geracao velha) em coletas Young, entao "a geracao velha diminuiu"
     * nao identifica uma coleta mista; por isso a 0.5.1 nao tenta mais.
     */
    static void aposColeta(boolean completa, long depoisMb, long agoraMs) {
        if (completa) {
            vivoMaxMb = Math.max(vivoMaxMb, depoisMb);
            return;
        }
        synchronized (POS_YOUNG) {
            POS_YOUNG.addLast(new long[]{agoraMs, depoisMb});
            while (!POS_YOUNG.isEmpty() && agoraMs - POS_YOUNG.peekFirst()[0] > JANELA_YOUNG_MS) {
                POS_YOUNG.removeFirst();
            }
        }
    }

    /** {MB, 1 = estimado} ou {-1, 0} sem dados. Com alguma coleta completa, so ela conta (nunca mistura). */
    public static long[] usoReal() {
        if (vivoMaxMb > 0) {
            return new long[]{vivoMaxMb, 0};
        }
        long minimo = -1;
        synchronized (POS_YOUNG) {
            for (long[] v : POS_YOUNG) {
                minimo = minimo < 0 ? v[1] : Math.min(minimo, v[1]);
            }
        }
        return new long[]{minimo, minimo > 0 ? 1 : 0};
    }

    /** Necessidade sem arredondar: vivo x 1,6. */
    public static long necessidadeMb(long vivoMb) {
        return Math.round(vivoMb * 1.6);
    }

    public static long tetoMb(long ramTotalMb, boolean gpuIntegrada) {
        return Math.round(ramTotalMb * (gpuIntegrada ? 0.50 : 0.65));
    }

    /**
     * A recomendacao de -Xmx.
     * @param sugeridoMb valor final (nunca acima do teto; com "manter", igual ao reservado)
     * @param manter     a diferenca para o -Xmx atual e menor que 512 MB: nao vale mexer
     * @param noLimite   a necessidade (vivo x 1,6) passa do teto da RAM
     * @param ramBaixa   mais de 30% dos congelamentos com a RAM livre do sistema abaixo de 1 GB: nunca sugere aumentar
     */
    public record Recomendacao(long vivoMb, boolean estimado, long reservadoMb, long sugeridoMb, long tetoMb, boolean manter,
                               boolean noLimite, boolean ramBaixa, int congelamentos, int congelamentosRamBaixa, long ramTotalMb, boolean gpuIntegrada) {
    }

    /**
     * Regras (relatorio 0.5.1): vivo x 1,6 arredondado para cima em 512 MB, no minimo 4 GB; depois o teto (50% da RAM
     * com GPU integrada, 65% sem), aplicado DEPOIS do arredondamento, entao o valor final nunca passa do teto. Com mais
     * de 30% dos congelamentos com pouca RAM livre, o problema e falta de RAM do sistema: o valor final e no maximo o
     * atual. Diferenca menor que 512 MB do atual: "manter".
     */
    public static Recomendacao calcular(long vivoMb, boolean estimado, long reservadoMb, long ramTotalMb, boolean gpuIntegrada,
                                        int congelamentos, int congelamentosRamBaixa) {
        long necessidade = necessidadeMb(vivoMb);
        long sugerido = Math.max(4096, (necessidade + 511) / 512 * 512);
        long teto = ramTotalMb > 0 ? tetoMb(ramTotalMb, gpuIntegrada) : Long.MAX_VALUE;
        boolean noLimite = necessidade > teto;
        sugerido = Math.min(sugerido, teto);
        boolean ramBaixa = congelamentos > 0 && congelamentosRamBaixa * 10L > congelamentos * 3L;
        if (ramBaixa && reservadoMb > 0) {
            sugerido = Math.min(sugerido, reservadoMb);
        }
        boolean manter = reservadoMb > 0 && Math.abs(reservadoMb - sugerido) < 512;
        if (manter) {
            sugerido = reservadoMb;
        }
        return new Recomendacao(vivoMb, estimado, reservadoMb, sugerido, teto, manter, noLimite, ramBaixa, congelamentos, congelamentosRamBaixa,
                ramTotalMb, gpuIntegrada);
    }

    /** A recomendacao com os dados desta sessao, ou null sem nenhuma coleta medida. */
    public static Recomendacao atual() {
        long[] uso = usoReal();
        if (uso[0] <= 0) {
            return null;
        }
        return calcular(uso[0], uso[1] == 1, Runtime.getRuntime().maxMemory() >> 20, MonitorGc.ramTotal() >> 20, MonitorGc.gpuIntegrada,
                MonitorGc.congelamentos(), congelamentosComRamBaixa);
    }

    /** Texto da recomendacao (painel e resumo). */
    public static String texto(Recomendacao r) {
        String texto = Msg.f("usa ~{} GB{}, reservado {} GB, sugerido {} GB{} (RAM {} GB{})", gb(r.vivoMb()), r.estimado() ? Msg.t(" (estimado)") : "",
                gb(r.reservadoMb()), gb(r.sugeridoMb()), r.manter() ? Msg.t(" (manter)") : "",
                r.ramTotalMb() > 0 ? gb(r.ramTotalMb()) : "?", r.gpuIntegrada() ? Msg.t(", GPU integrada") : "");
        if (r.ramBaixa()) {
            texto += Msg.f(" - {} de {} congelamentos com a RAM livre abaixo de 1 GB: feche outros programas (navegador, Discord) enquanto joga",
                    r.congelamentosRamBaixa(), r.congelamentos());
        } else if (r.noLimite()) {
            texto += Msg.t(" - no limite da RAM: feche outros programas ou reduza mods");
        }
        return texto;
    }

    // ---------- RAM livre do sistema (chamado pela thread do monitor_gc) ----------

    static void amostrarRam(long agoraMs) {
        if (agoraMs < proximaAmostraMs) {
            return;
        }
        proximaAmostraMs = agoraMs + 5000;
        long livre = livreSistemaMb();
        if (livre < 0) {
            return;
        }
        ramLivreMb = livre;
        amostrasRam++;
        if (livre < LIVRE_BAIXA_MB) {
            amostrasRamBaixa++;
            menorRamLivreMb = Math.min(menorRamLivreMb, livre);
            if (primeiraRamBaixa == null) {
                primeiraRamBaixa = java.time.LocalTime.now().withNano(0).toString();
                primeiraRamBaixaMb = livre;
            }
        }
        synchronized (RAM_LIVRE) {
            RAM_LIVRE[ramIndice] = livre;
            ramIndice = (ramIndice + 1) % AMOSTRAS_RAM;
            ramPreenchidos = Math.min(AMOSTRAS_RAM, ramPreenchidos + 1);
        }
        if (livre < LIVRE_BAIXA_MB && !avisouRamBaixa) {
            avisouRamBaixa = true;
            Config.LOGGER.warn("[monitor_gc] RAM livre do sistema abaixo de 1 GB ({} MB): o Windows vai comecar a paginar o jogo para o disco", livre);
        }
    }

    /** Congelamento do processo sem GC: guarda se foi com a RAM livre baixa. */
    static void aoCongelar() {
        if (ramLivreMb >= 0 && ramLivreMb < LIVRE_BAIXA_MB) {
            congelamentosComRamBaixa++;
        }
    }

    static long livreSistemaMb() {
        try {
            if (java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                return os.getFreeMemorySize() >> 20;
            }
        } catch (Throwable ignorado) {
            // sem a API
        }
        return -1;
    }

    public static long ramLivreMb() {
        return ramLivreMb;
    }

    /** RAM livre (MB) a cada 5 s, do mais velho para o mais novo. */
    public static float[] historicoRamLivre() {
        synchronized (RAM_LIVRE) {
            float[] h = new float[ramPreenchidos];
            int inicio = (ramIndice - ramPreenchidos + AMOSTRAS_RAM) % AMOSTRAS_RAM;
            for (int i = 0; i < ramPreenchidos; i++) {
                h[i] = RAM_LIVRE[(inicio + i) % AMOSTRAS_RAM];
            }
            return h;
        }
    }

    private static String gb(long mb) {
        return String.format(Msg.locale(), "%.1f", mb / 1024.0);
    }

    /** "usa ~3,6 GB, reservado 6,0 GB, sugerido 6,0 GB (manter) (RAM 11,8 GB, GPU integrada) - ..." ou null sem dados. */
    public static String resumo() {
        Recomendacao r = atual();
        return r == null ? null : texto(r);
    }

    /** Sugestao curta para mensagens: "6,0 GB" ou null. */
    public static String sugestaoCurta() {
        Recomendacao r = atual();
        return r == null ? null : gb(r.sugeridoMb()) + " GB";
    }

    /** "RAM livre do sistema abaixo de 1 GB em 40% da sessao (primeira vez as 03:35:19, 810 MB)" ou null. */
    public static String resumoRamLivre() {
        if (amostrasRam == 0 || amostrasRamBaixa == 0) {
            return null;
        }
        return Msg.f("RAM livre do sistema abaixo de 1 GB em {} da sessao (primeira vez as {}, {} MB; menor {} MB)",
                Math.round(amostrasRamBaixa * 100.0 / amostrasRam) + "%", primeiraRamBaixa, primeiraRamBaixaMb, menorRamLivreMb);
    }
}
