package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.config.Msg;
import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * monitor_gc: pausas da JVM inteira, que congelam o render e o tick juntos (o "stuttering" que nenhum
 * diagnostico de thread explica, porque a propria vigia tambem fica parada).
 * <ul>
 * <li>Pausas de coleta de lixo (GarbageCollectionNotificationInfo): coletor, causa, duracao, heap antes/depois.
 *     Guarda as >= 100 ms, loga as >= 500 ms.</li>
 * <li>Soluços (como o jHiccup): uma thread dorme 20 ms em loop e mede quanto dormiu a mais. 200 ms a mais sem
 *     nenhuma coleta no intervalo = o sistema operacional congelou o processo (troca de memoria com o disco,
 *     antivirus, economia de energia).</li>
 * <li>O diagnostico_lag pergunta aqui o que aconteceu durante um travamento sem amostras.</li>
 * <li>Sugestoes de argumentos da JVM (so texto, o mod nunca muda a JVM): System.gc() frequente, coleta completa,
 *     -Xmx grande demais com placa de video integrada.</li>
 * </ul>
 * Custo: uma thread que acorda a cada 20 ms e um ouvinte chamado so quando ha coleta.
 */
public final class MonitorGc {
    public static final long GUARDAR_MS = 100;
    public static final long LOGAR_MS = 500;
    public static final long SOLUCO_MS = 200;
    private static final int DORMIR_MS = 20;
    private static final int MAX_EVENTOS = 200;

    /** Uma pausa da JVM. gc=false: soluço sem coleta (processo congelado pelo sistema). Horarios em ms de relogio. */
    public record Pausa(long inicioMs, long fimMs, boolean gc, String coletor, String causa, long heapAntesMb, long heapDepoisMb) {
        public long duracaoMs() {
            return fimMs - inicioMs;
        }

        public String descricao() {
            if (!gc) {
                return Msg.f("processo inteiro congelado pelo sistema por {} ms, sem coleta de lixo (troca de memoria com o disco, antivirus ou economia de energia)", duracaoMs());
            }
            if (heapAntesMb == 0 && heapDepoisMb == 0) {
                return Msg.f("JVM inteira parada {} ms pela coleta de lixo (detalhes na linha [monitor_gc] do log)", duracaoMs());
            }
            return Msg.f("JVM inteira parada {} ms pela coleta de lixo ({}, causa: {}, heap {} MB -> {} MB)", duracaoMs(), coletor, causa, heapAntesMb, heapDepoisMb);
        }
    }

    private static final Deque<Pausa> PAUSAS = new ArrayDeque<>();
    public static final AtomicInteger PAUSAS_GC = new AtomicInteger();
    public static final AtomicInteger PAUSAS_COMPLETAS = new AtomicInteger();
    public static final AtomicInteger GC_EXPLICITOS = new AtomicInteger();
    public static final AtomicInteger SOLUCOS = new AtomicInteger();
    public static final AtomicInteger YOUNG_LONGAS = new AtomicInteger();
    public static volatile String origemDiagnostico = "";
    private static volatile long maiorMs;
    private static volatile String maiorDescricao = "";
    private static long ultimoLogSolucoMs;
    private static boolean iniciado;
    /** Cliente: placa de video integrada (preenchido na primeira tela). */
    public static volatile boolean gpuIntegrada;
    public static volatile String placaDeVideo = "";
    private static long inicioJvmMs;

    private MonitorGc() {
    }

    /** Ouvinte de GC sempre (o painel mostra as pausas longas); soluços, logs e avisos so com o modulo ligado. */
    public static synchronized void iniciar() {
        if (iniciado) {
            return;
        }
        iniciado = true;
        inicioJvmMs = ManagementFactory.getRuntimeMXBean().getStartTime();
        try {
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (gc instanceof NotificationEmitter emissor) {
                    emissor.addNotificationListener((notificacao, dado) -> {
                        if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notificacao.getType())) {
                            aoColetar(GarbageCollectionNotificationInfo.from((CompositeData) notificacao.getUserData()));
                        }
                    }, null, null);
                }
            }
        } catch (Throwable t) {
            Config.LOGGER.debug("Nao foi possivel acompanhar as pausas de coleta de lixo", t);
        }
        if (Config.ativo(Modulo.MONITOR_GC)) {
            Thread t = new Thread(MonitorGc::vigiarSolucos, "HLO - monitor_gc");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            t.start();
        }
    }

    private static void aoColetar(GarbageCollectionNotificationInfo info) {
        String nome = info.getGcName();
        // "G1 Concurrent GC" e parecidos rodam junto com o jogo: nao param as threads.
        if (nome.contains("Concurrent")) {
            return;
        }
        long ms = info.getGcInfo().getDuration();
        Desempenho.registrarPausaGc(ms);
        // Uso real da memoria (Memoria): heap depois das coletas completas (so elas medem o conjunto vivo).
        long depoisTotal = 0;
        for (Map.Entry<String, MemoryUsage> e : info.getGcInfo().getMemoryUsageAfterGc().entrySet()) {
            depoisTotal += e.getValue().getUsed();
        }
        boolean completaGc = info.getGcAction().contains("major") || nome.contains("Old") || nome.contains("MarkSweep");
        Memoria.aposColeta(completaGc, depoisTotal >> 20, System.currentTimeMillis());
        String causa = info.getGcCause();
        if (Config.ativo(Modulo.MONITOR_GC) && (causa.contains("Diagnostic Command") || causa.contains("Heap Inspection"))) {
            origemDiagnostico(ms, causa);
        }
        boolean explicito = info.getGcCause().contains("System.gc");
        if (explicito) {
            GC_EXPLICITOS.incrementAndGet();
        }
        boolean completa = info.getGcAction().contains("major") || nome.contains("Old") || nome.contains("MarkSweep");
        if (ms < GUARDAR_MS) {
            return;
        }
        PAUSAS_GC.incrementAndGet();
        if (completa) {
            PAUSAS_COMPLETAS.incrementAndGet();
        }
        long antes = 0;
        long depois = 0;
        for (Map.Entry<String, MemoryUsage> e : info.getGcInfo().getMemoryUsageBeforeGc().entrySet()) {
            antes += e.getValue().getUsed();
        }
        for (Map.Entry<String, MemoryUsage> e : info.getGcInfo().getMemoryUsageAfterGc().entrySet()) {
            depois += e.getValue().getUsed();
        }
        String coletor = nome + (completa ? Msg.t(" (coleta completa)") : "");
        Pausa p = new Pausa(inicioJvmMs + info.getGcInfo().getStartTime(), inicioJvmMs + info.getGcInfo().getEndTime(), true,
                coletor, info.getGcCause(), antes >> 20, depois >> 20);
        guardar(p);
        if (!Config.ativo(Modulo.MONITOR_GC)) {
            return;
        }
        long maximo = Runtime.getRuntime().maxMemory();
        if (!completa && ms >= 1000 && antes < maximo * 0.8) {
            // Coleta Young de segundos com o heap longe de cheio: o tempo foi o sistema trazendo o heap de volta do disco.
            YOUNG_LONGAS.incrementAndGet();
            Memoria.Recomendacao r = Memoria.atual();
            if (r != null && (r.manter() || r.sugeridoMb() >= r.reservadoMb())) {
                // Nunca mandar aumentar o heap: o problema e RAM livre do sistema.
                Config.LOGGER.warn("[monitor_gc] Coleta Young levou {} ms: e o sistema paginando o heap para o disco, nao um problema do GC. Feche outros programas; nao aumente o -Xmx (o atual, {}, serve).",
                        ms, Memoria.sugestaoCurta());
            } else {
                String sugestao = Memoria.sugestaoCurta();
                Config.LOGGER.warn("[monitor_gc] Coleta Young levou {} ms: e o sistema paginando o heap para o disco, nao um problema do GC. Reduza o -Xmx (sugerido: {}) ou feche outros programas.",
                        ms, sugestao == null ? "?" : sugestao);
            }
        } else if (ms >= LOGAR_MS) {
            Config.LOGGER.warn("[monitor_gc] Pausa de {} ms: {}, causa: {}, heap {} MB -> {} MB", ms, coletor, info.getGcCause(), antes >> 20, depois >> 20);
        }
        atualizarAvisos();
    }

    private static void guardar(Pausa p) {
        synchronized (PAUSAS) {
            PAUSAS.addLast(p);
            while (PAUSAS.size() > MAX_EVENTOS) {
                PAUSAS.removeFirst();
            }
        }
        if (p.duracaoMs() > maiorMs) {
            maiorMs = p.duracaoMs();
            maiorDescricao = p.descricao();
        }
    }

    private static void vigiarSolucos() {
        long anterior = System.nanoTime();
        while (true) {
            try {
                Thread.sleep(DORMIR_MS);
            } catch (InterruptedException e) {
                return;
            }
            long agora = System.nanoTime();
            Memoria.amostrarRam(System.currentTimeMillis());
            long extraMs = (agora - anterior) / 1_000_000 - DORMIR_MS;
            anterior = agora;
            if (extraMs >= SOLUCO_MS) {
                long fim = System.currentTimeMillis();
                long inicio = fim - extraMs;
                // A notificacao da coleta chega um pouco depois do fim da pausa: espera ela antes de decidir.
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    return;
                }
                anterior = System.nanoTime();
                if (pausaGcEntre(inicio - 50, fim + 50) == null) {
                    SOLUCOS.incrementAndGet();
                    Memoria.aoCongelar();
                    Pausa p = new Pausa(inicio, fim, false, "", "", 0, 0);
                    guardar(p);
                    atualizarAvisos();
                    if (fim - ultimoLogSolucoMs >= 30_000) {
                        ultimoLogSolucoMs = fim;
                        Config.LOGGER.warn("[monitor_gc] Processo inteiro congelado por {} ms sem coleta de lixo: o sistema operacional parou a JVM (troca de memoria com o disco, antivirus ou economia de energia)", extraMs);
                    }
                }
            }
        }
    }

    private static Pausa pausaGcEntre(long inicioMs, long fimMs) {
        synchronized (PAUSAS) {
            for (Pausa p : PAUSAS) {
                if (p.gc() && p.fimMs() >= inicioMs && p.inicioMs() <= fimMs) {
                    return p;
                }
            }
        }
        return null;
    }

    private static volatile List<GarbageCollectorMXBean> coletoresComPausa;

    /**
     * Tempo total (ms) que a JVM passou parada em coletas desde que abriu. Sincrono, diferente da notificacao, que chega
     * um pouco depois do fim da pausa: o diagnostico_lag le no comeco e no fim do tick.
     */
    public static long tempoGcMs() {
        List<GarbageCollectorMXBean> lista = coletoresComPausa;
        if (lista == null) {
            lista = ManagementFactory.getGarbageCollectorMXBeans().stream().filter(b -> !b.getName().contains("Concurrent")).toList();
            coletoresComPausa = lista;
        }
        long total = 0;
        for (GarbageCollectorMXBean b : lista) {
            total += Math.max(0, b.getCollectionTime());
        }
        return total;
    }

    /** A maior pausa (GC ou soluço) que cruza o intervalo, ou null. Para o diagnostico_lag. */
    public static Pausa pausaEntre(long inicioMs, long fimMs) {
        Pausa maior = null;
        synchronized (PAUSAS) {
            for (Pausa p : PAUSAS) {
                if (p.fimMs() >= inicioMs && p.inicioMs() <= fimMs && (maior == null || p.duracaoMs() > maior.duracaoMs())) {
                    maior = p;
                }
            }
        }
        return maior;
    }

    /** As ultimas n pausas guardadas, da mais nova para a mais velha. */
    public static List<Pausa> ultimas(int n) {
        List<Pausa> lista = new ArrayList<>();
        synchronized (PAUSAS) {
            var it = PAUSAS.descendingIterator();
            while (it.hasNext() && lista.size() < n) {
                lista.add(it.next());
            }
        }
        return lista;
    }

    public static long maiorMs() {
        return maiorMs;
    }

    static int congelamentos() {
        return SOLUCOS.get();
    }

    public static String maiorDescricao() {
        return maiorDescricao;
    }

    public static final String FLAGS_G1_PACK = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions "
            + "-XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 "
            + "-XX:InitiatingHeapOccupancyPercent=15 -XX:+AlwaysPreTouch";

    private static void atualizarAvisos() {
        Memoria.Recomendacao r = Memoria.atual();
        if (r != null && r.ramBaixa()) {
            // Falta RAM no sistema: aumentar o -Xmx piora a paginacao. A mensagem principal e fechar programas.
            Avisos.remover("monitor_gc:xmx");
            Avisos.substituir("monitor_gc:ram", Msg.f("RAM: feche outros programas, o Windows esta sem RAM livre ({} de {} congelamentos)", r.congelamentosRamBaixa(), r.congelamentos()),
                    Msg.f("{} de {} congelamentos do processo aconteceram com menos de 1 GB de RAM livre no sistema: o Windows esta tirando o jogo da memoria. "
                            + "Feche o navegador, o Discord e outros programas enquanto joga. Nao aumente o -Xmx: isso piora. {}",
                            r.congelamentosRamBaixa(), r.congelamentos(), Memoria.texto(r)));
        } else if (r != null && (SOLUCOS.get() > 0 || YOUNG_LONGAS.get() > 0) && !r.manter() && r.sugeridoMb() < r.reservadoMb()) {
            Avisos.substituir("monitor_gc:xmx", Msg.f("RAM: -Xmx {} GB, sugerido {}", Math.round(r.reservadoMb() / 102.4) / 10.0, Memoria.sugestaoCurta()),
                    Msg.f("O jogo usa de verdade ~{} MB (medido depois das coletas), mas reserva {} MB. Com o sistema congelando o processo ({} vez(es)) e coletas Young longas ({}), o heap grande esta sendo paginado para o disco. "
                            + "Sugestao: -Xmx{}M nos argumentos da JVM (no CurseForge: Configuracoes > Minecraft > memoria). {}",
                            r.vivoMb(), r.reservadoMb(), SOLUCOS.get(), YOUNG_LONGAS.get(), r.sugeridoMb(), Memoria.texto(r)));
        }
        if (GC_EXPLICITOS.get() >= 2) {
            Avisos.substituir("monitor_gc:explicito", Msg.f("GC: {} coleta(s) pedida(s) por System.gc()", GC_EXPLICITOS.get()),
                    Msg.t("Algum mod pede coleta de lixo manual (System.gc()), que para o jogo inteiro. Causa classica de travada no carregamento. "
                            + "Sugestao: acrescente -XX:+DisableExplicitGC aos argumentos da JVM (no CurseForge: Configuracoes > Minecraft > argumentos da JVM). "
                            + "O AllTheLeaks contorna essa opcao ao fechar o mundo (usa a API de diagnostico), entao essa coleta continua."));
        }
        if (PAUSAS_COMPLETAS.get() >= 1 || maiorMs >= 1000) {
            Avisos.substituir("monitor_gc:g1", Msg.f("GC: {} coleta(s) completa(s), maior pausa {} ms", PAUSAS_COMPLETAS.get(), maiorMs),
                    Msg.t("Coletas completas (Full GC) param o jogo por segundos. Argumentos do G1 para modpacks grandes (copie para os argumentos da JVM): ")
                            + FLAGS_G1_PACK + "\n" + Msg.t("MaxGCPauseMillis: alvo de pausa; G1NewSizePercent/MaxNewSizePercent: geracao nova maior, menos lixo chega a velha; "
                            + "InitiatingHeapOccupancyPercent=15: comeca a limpar a geracao velha cedo, sem esperar encher; DisableExplicitGC: ignora System.gc() dos mods; "
                            + "AlwaysPreTouch: so use com -Xms igual ao -Xmx (reserva a memoria toda na abertura)."));
        }
    }

    /**
     * Cliente: placa de video integrada (Intel, Radeon de APU) divide a RAM com o sistema. Com -Xmx acima de ~50% da
     * RAM, o resto nao cabe e o Windows usa o disco (soluços). Chamado uma vez com o GL_VENDOR/GL_RENDERER.
     */
    public static void verificarGpuIntegrada(String fabricante, String placa) {
        String texto = (fabricante + " " + placa).toLowerCase(java.util.Locale.ROOT);
        boolean integrada = texto.contains("intel") || texto.contains("radeon(tm) graphics") || texto.contains("radeon graphics")
                || texto.contains("vega 8") || texto.contains("vega 6") || texto.contains("vega 3");
        placaDeVideo = placa;
        if (!integrada) {
            return;
        }
        gpuIntegrada = true;
        long ram = ramTotal();
        long xmx = Runtime.getRuntime().maxMemory();
        Config.LOGGER.info("[monitor_gc] Placa de video integrada: {} (RAM total {} MB, -Xmx {} MB)", placa, ram >> 20, xmx >> 20);
        if (ram > 0 && xmx > ram / 2) {
            Avisos.substituir("monitor_gc:igpu", Msg.f("RAM: -Xmx {} GB com placa de video integrada e {} GB de RAM", Math.round(xmx / 1e8) / 10.0, Math.round(ram / 1e8) / 10.0),
                    Msg.f("A placa de video integrada ({}) usa a mesma RAM do sistema. Com o jogo reservando mais da metade da RAM, o resto nao cabe e o Windows passa a usar o disco, o que causa travadas. Sugestao: -Xmx de no maximo {} GB.",
                            placa, Math.max(4, ram / 2 / 1_000_000_000L)));
        }
    }

    static long ramTotal() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                return os.getTotalMemorySize();
            }
        } catch (Throwable ignorado) {
            // sem a API: nao avisa
        }
        return -1;
    }

    /** Linha do resumo ao fechar. */
    public static String resumo() {
        String texto = String.format(Msg.t("monitor_gc: %d pausa(s) de GC >= 100 ms (%d completa(s), %d por System.gc()), %d congelamento(s) do processo sem GC, maior pausa %d ms"),
                PAUSAS_GC.get(), PAUSAS_COMPLETAS.get(), GC_EXPLICITOS.get(), SOLUCOS.get(), maiorMs);
        if (SOLUCOS.get() > 0 && Memoria.ramLivreMb() >= 0) {
            texto += Msg.f("; {} de {} congelamentos com a RAM livre do sistema abaixo de 1 GB", Memoria.congelamentosComRamBaixa, SOLUCOS.get());
        }
        String memoria = Memoria.resumo();
        if (memoria != null) {
            texto += "\n  " + Msg.f("monitor_gc: memoria: {}", memoria);
        }
        String ramLivre = Memoria.resumoRamLivre();
        if (ramLivre != null) {
            texto += "\n  " + "monitor_gc: " + ramLivre;
        }
        return texto;
    }

    /**
     * Coleta completa pedida pela API de diagnostico (DiagnosticCommand GC.run, histograma, heap dump): procura quem
     * pediu nas pilhas de todas as threads que nao estao esperando. A coleta ja terminou quando a notificacao chega,
     * entao quem pediu pode ja ter saido; nesse caso, so o AllTheLeaks instalado e indicado como provavel.
     */
    private static void origemDiagnostico(long ms, String causa) {
        String mod = null;
        String onde = null;
        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
            Thread.State estado = e.getKey().getState();
            if (e.getKey() == Thread.currentThread() || estado == Thread.State.WAITING || estado == Thread.State.TIMED_WAITING) {
                continue;
            }
            boolean diagnostico = false;
            for (StackTraceElement f : e.getValue()) {
                String c = f.getClassName();
                if (c.contains("DiagnosticCommand") || c.contains("HotSpotDiagnostic") || f.getMethodName().equals("gcRun")) {
                    diagnostico = true;
                    continue;
                }
                if (diagnostico) {
                    String m = DiagnosticoLag.modDoModulo(f.getModuleName());
                    if (m != null) {
                        mod = m;
                        onde = c.substring(c.lastIndexOf('.') + 1) + "." + f.getMethodName();
                        break;
                    }
                }
            }
            if (mod != null) {
                break;
            }
        }
        // 2. Ninguem mais dentro da API: threads em execucao com codigo de mod perto do topo (provavel quem pediu e seguiu).
        String provavel = null;
        if (mod == null) {
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                Thread th = e.getKey();
                if (th == Thread.currentThread() || th.getName().startsWith("HLO") || th.getState() != Thread.State.RUNNABLE) {
                    continue;
                }
                StackTraceElement[] pilha = e.getValue();
                for (int i = 0; i < Math.min(12, pilha.length); i++) {
                    String m = DiagnosticoLag.modDoModulo(pilha[i].getModuleName());
                    if (m != null && !m.equals("hlotimizacao")) {
                        String c = pilha[i].getClassName();
                        provavel = m + " (" + c.substring(c.lastIndexOf('.') + 1) + "." + pilha[i].getMethodName() + ", " + th.getName() + ")";
                        break;
                    }
                }
                if (provavel != null) {
                    break;
                }
            }
        }
        boolean atl = net.minecraftforge.fml.loading.LoadingModList.get() != null
                && net.minecraftforge.fml.loading.LoadingModList.get().getModFileById("alltheleaks") != null;
        if (mod != null) {
            origemDiagnostico = mod + " (" + onde + ")";
            Config.LOGGER.warn("[monitor_gc] Coleta completa de {} ms pedida pela API de diagnostico por {} ({}), causa: {}", ms, mod, onde, causa);
        } else if (provavel != null) {
            origemDiagnostico = Msg.f("provavelmente {}", provavel);
            Config.LOGGER.warn("[monitor_gc] Coleta completa de {} ms pedida pela API de diagnostico ({}); quem pediu ja tinha saido da chamada, provavelmente {}{}", ms, causa, provavel,
                    atl ? " " + Msg.t("(o AllTheLeaks tambem faz isso ao parar o servidor)") : "");
        } else {
            origemDiagnostico = atl ? Msg.t("provavelmente o AllTheLeaks") : "?";
            Config.LOGGER.warn("[monitor_gc] Coleta completa de {} ms pedida pela API de diagnostico ({}); quem pediu ja tinha terminado. {}", ms, causa,
                    atl ? Msg.t("Provavelmente o AllTheLeaks, que faz isso ao parar o servidor.") : "");
        }
        if (atl && (mod == null || mod.equals("alltheleaks"))) {
            Avisos.substituir("monitor_gc:atl", Msg.f("AllTheLeaks: coleta completa forcada de {} ms ao fechar o mundo", ms),
                    Msg.t("O AllTheLeaks (1.20.1) forca uma coleta completa toda vez que o servidor para (ServerStoppedEvent), e com -XX:+DisableExplicitGC ele usa a API de diagnostico (GC.run) para contornar a opcao. "
                            + "A config dele (memoryUsageWarningPercentage, logIntervalInMinutes...) nao tem opcao que desligue isso. A pausa fica longa quando o heap foi paginado para o disco: "
                            + "o remedio e o -Xmx sugerido pelo monitor_gc. Se continuar, vale reportar ao autor do AllTheLeaks."));
        }
    }
}
