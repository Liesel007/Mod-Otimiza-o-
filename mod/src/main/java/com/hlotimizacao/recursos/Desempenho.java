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

    private static final AtomicInteger PAUSAS_GC_LONGAS = new AtomicInteger();
    private static volatile long maiorPausaGcMs;
    private static boolean gcRegistrado;

    private Desempenho() {
    }

    public static synchronized void iniciarGc() {
        if (gcRegistrado) {
            return;
        }
        gcRegistrado = true;
        try {
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (gc instanceof NotificationEmitter emissor) {
                    emissor.addNotificationListener((notificacao, dado) -> {
                        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notificacao.getType())) {
                            return;
                        }
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notificacao.getUserData());
                        long ms = info.getGcInfo().getDuration();
                        // "Concurrent" roda junto com o jogo e nao para as threads.
                        if (ms >= PAUSA_GC_LONGA_MS && !info.getGcName().contains("Concurrent")) {
                            PAUSAS_GC_LONGAS.incrementAndGet();
                            maiorPausaGcMs = Math.max(maiorPausaGcMs, ms);
                        }
                    }, null, null);
                }
            }
        } catch (Throwable t) {
            Config.LOGGER.debug("Nao foi possivel acompanhar as pausas de coleta de lixo", t);
        }
    }

    public static void aoIniciarServidor() {
        sessaoDesdeMs = System.currentTimeMillis();
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
