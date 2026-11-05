package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.sun.management.GarbageCollectionNotificationInfo;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tempos de tick para o /hl status, separando os ticks em que o mundo foi salvo (o autosave pode
 * levar segundos e faria um unico pico parecer um problema), pausas longas da coleta de lixo e
 * o tempo de sessao.
 */
public final class Desempenho {
    private static final int TICKS = 100;
    private static final long PAUSA_GC_LONGA_MS = 500;

    private static final long[] DURACAO_NS = new long[TICKS];
    private static final boolean[] SALVOU = new boolean[TICKS];
    private static int indice;
    private static int preenchidos;
    private static long inicioTickNs;
    private static volatile boolean emTick;
    private static volatile boolean salvouNesteTick;

    private static volatile long ultimoSalvamentoMs = -1;
    private static volatile LocalTime ultimoSalvamentoHora;
    private static volatile long sessaoDesdeMs = -1;

    /** Historico para o grafico da tela: MSPT medio de cada segundo, nos ultimos 5 minutos. */
    public static final int SEGUNDOS_HISTORICO = 300;
    private static final float[] HISTORICO_MSPT = new float[SEGUNDOS_HISTORICO];
    private static final boolean[] HISTORICO_SALVOU = new boolean[SEGUNDOS_HISTORICO];
    private static int historicoIndice;
    private static int historicoPreenchido;
    private static long segundoAtual = -1;
    private static long somaSegundoNs;
    private static int ticksSegundo;
    private static boolean salvouSegundo;

    private static final AtomicInteger PAUSAS_GC_LONGAS = new AtomicInteger();
    private static volatile long maiorPausaGcMs;
    private static boolean gcRegistrado;

    private Desempenho() {
    }

    /** Pausas da coleta de lixo chegam do MonitorGc (que tem o ouvinte da JVM). */
    public static void iniciarGc() {
        MonitorGc.iniciar();
    }

    static void registrarPausaGc(long ms) {
        if (ms >= PAUSA_GC_LONGA_MS) {
            PAUSAS_GC_LONGAS.incrementAndGet();
            maiorPausaGcMs = Math.max(maiorPausaGcMs, ms);
        }
    }

    public static void aoIniciarServidor() {
        sessaoDesdeMs = System.currentTimeMillis();
        synchronized (HISTORICO_MSPT) {
            historicoPreenchido = 0;
            historicoIndice = 0;
            segundoAtual = -1;
        }
        synchronized (DURACAO_NS) {
            preenchidos = 0;
            indice = 0;
        }
    }

    public static void aoSalvar(LevelEvent.Save event) {
        if (emTick && !event.getLevel().isClientSide()) {
            salvouNesteTick = true;
        }
    }

    public static void aoTick(TickEvent.ServerTickEvent event) {
        long agora = System.nanoTime();
        if (event.phase == TickEvent.Phase.START) {
            inicioTickNs = agora;
            salvouNesteTick = false;
            emTick = true;
            return;
        }
        emTick = false;
        Custo.fimDoTick();
        long duracao = agora - inicioTickNs;
        synchronized (DURACAO_NS) {
            DURACAO_NS[indice] = duracao;
            SALVOU[indice] = salvouNesteTick;
            indice = (indice + 1) % TICKS;
            preenchidos = Math.min(TICKS, preenchidos + 1);
        }
        if (salvouNesteTick) {
            ultimoSalvamentoMs = duracao / 1_000_000;
            ultimoSalvamentoHora = LocalTime.now();
        }
        long segundo = System.currentTimeMillis() / 1000;
        synchronized (HISTORICO_MSPT) {
            if (segundo != segundoAtual) {
                if (segundoAtual >= 0 && ticksSegundo > 0) {
                    // Segundos sem nenhum tick (travamento longo) ficam com o valor do tick que terminou depois.
                    long pulados = Math.min(SEGUNDOS_HISTORICO, segundo - segundoAtual) - 1;
                    float media = (float) (somaSegundoNs / 1e6 / ticksSegundo);
                    gravarSegundo(media, salvouSegundo);
                    for (long i = 0; i < pulados; i++) {
                        gravarSegundo(media, salvouSegundo);
                    }
                }
                segundoAtual = segundo;
                somaSegundoNs = 0;
                ticksSegundo = 0;
                salvouSegundo = false;
            }
            somaSegundoNs += duracao;
            ticksSegundo++;
            salvouSegundo |= salvouNesteTick;
        }
    }

    private static void gravarSegundo(float mspt, boolean salvou) {
        HISTORICO_MSPT[historicoIndice] = mspt;
        HISTORICO_SALVOU[historicoIndice] = salvou;
        historicoIndice = (historicoIndice + 1) % SEGUNDOS_HISTORICO;
        historicoPreenchido = Math.min(SEGUNDOS_HISTORICO, historicoPreenchido + 1);
    }

    /** MSPT de cada segundo, do mais antigo para o mais recente; salvou[i] = houve salvamento naquele segundo. */
    public static float[] historicoMspt(boolean[] salvouSaida) {
        synchronized (HISTORICO_MSPT) {
            float[] valores = new float[historicoPreenchido];
            int inicio = (historicoIndice - historicoPreenchido + SEGUNDOS_HISTORICO) % SEGUNDOS_HISTORICO;
            for (int i = 0; i < historicoPreenchido; i++) {
                int j = (inicio + i) % SEGUNDOS_HISTORICO;
                valores[i] = HISTORICO_MSPT[j];
                if (salvouSaida != null && i < salvouSaida.length) {
                    salvouSaida[i] = HISTORICO_SALVOU[j];
                }
            }
            return valores;
        }
    }

    /** Tempos dos ultimos 100 ticks. Media inclui todos; min e max ignoram os ticks de salvamento. */
    public record Tempos(int ticks, double mediaMs, double minMs, double maxMs, int ticksSalvamento) {
        public double tps() {
            return ticks == 0 ? 20 : Math.min(20, 1000 / Math.max(50, mediaMs));
        }
    }

    public static Tempos tempos() {
        synchronized (DURACAO_NS) {
            if (preenchidos == 0) {
                return new Tempos(0, 0, 0, 0, 0);
            }
            long soma = 0;
            long min = Long.MAX_VALUE;
            long max = 0;
            int salvamentos = 0;
            for (int i = 0; i < preenchidos; i++) {
                soma += DURACAO_NS[i];
                if (SALVOU[i]) {
                    salvamentos++;
                    continue;
                }
                min = Math.min(min, DURACAO_NS[i]);
                max = Math.max(max, DURACAO_NS[i]);
            }
            if (min == Long.MAX_VALUE) {
                min = 0;
            }
            return new Tempos(preenchidos, soma / 1e6 / preenchidos, min / 1e6, max / 1e6, salvamentos);
        }
    }

    public static long ultimoSalvamentoMs() {
        return ultimoSalvamentoMs;
    }

    public static LocalTime ultimoSalvamentoHora() {
        return ultimoSalvamentoHora;
    }

    /** Milissegundos desde que o mundo abriu, ou -1. */
    public static long sessaoMs() {
        return sessaoDesdeMs < 0 ? -1 : System.currentTimeMillis() - sessaoDesdeMs;
    }

    public static int pausasGcLongas() {
        return PAUSAS_GC_LONGAS.get();
    }

    public static long maiorPausaGcMs() {
        return maiorPausaGcMs;
    }
}
