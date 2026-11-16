package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Msg;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Recomendacao de memoria a partir de dados (monitor_gc): quanto o jogo usa de verdade, quanto esta reservado (-Xmx)
 * e quanto sugerir, mais a RAM livre do sistema. So sugere: o mod nunca muda os argumentos da JVM.
 * <ul>
 * <li>Uso real (conjunto vivo): heap logo depois de cada coleta completa ou mista (Mixed, quando a geracao velha
 *     diminui); vale o maximo da sessao. Sem nenhuma delas, o minimo depois das coletas Young dos ultimos 5 min,
 *     marcado como estimado.</li>
 * <li>Sugestao: vivo x 1,6 arredondado para cima em 512 MB, no minimo 4 GB. Teto: 50% da RAM total com placa de video
 *     integrada (ela usa a mesma RAM), 65% sem ela. O teto e comparado com a necessidade antes do arredondamento.</li>
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

    private Memoria() {
    }

    /** Depois de cada coleta que para a JVM (MonitorGc). */
    static void aposColeta(boolean completaOuMista, long depoisMb, long agoraMs) {
        if (completaOuMista) {
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

    /** {MB, 1 = estimado} ou {-1, 0} sem dados. */
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

    /** Sugestao de -Xmx: vivo x 1,6 arredondado para cima em 512 MB, no minimo 4 GB. */
    public static long sugeridoMb(long vivoMb) {
        long n = necessidadeMb(vivoMb);
        return Math.max(4096, (n + 511) / 512 * 512);
    }

    public static long tetoMb(long ramTotalMb, boolean gpuIntegrada) {
        return Math.round(ramTotalMb * (gpuIntegrada ? 0.50 : 0.65));
    }

    /** O PC esta no limite: a necessidade (antes de arredondar) passa do teto. */
    public static boolean noLimite(long vivoMb, long ramTotalMb, boolean gpuIntegrada) {
        return ramTotalMb > 0 && necessidadeMb(vivoMb) > tetoMb(ramTotalMb, gpuIntegrada);
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

    /** "usa ~3,7 GB, reservado 7,5 GB, sugerido 6 GB (RAM 11,8 GB, GPU integrada)" ou null sem dados. */
    public static String resumo() {
        long[] uso = usoReal();
        if (uso[0] <= 0) {
            return null;
        }
        long ram = MonitorGc.ramTotal() >> 20;
        long reservado = Runtime.getRuntime().maxMemory() >> 20;
        long sugerido = sugeridoMb(uso[0]);
        boolean igpu = MonitorGc.gpuIntegrada;
        String texto = Msg.f("usa ~{} GB{}, reservado {} GB, sugerido {} GB (RAM {} GB{})", gb(uso[0]), uso[1] == 1 ? Msg.t(" (estimado)") : "",
                gb(reservado), gb(sugerido), ram > 0 ? gb(ram) : "?", igpu ? Msg.t(", GPU integrada") : "");
        if (ram > 0 && noLimite(uso[0], ram, igpu)) {
            texto += Msg.t(" - no limite da RAM: feche outros programas ou reduza mods");
        }
        return texto;
    }

    /** Sugestao curta para mensagens: "6 GB" ou null. */
    public static String sugestaoCurta() {
        long[] uso = usoReal();
        return uso[0] <= 0 ? null : gb(sugeridoMb(uso[0])) + " GB";
    }
}
