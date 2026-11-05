package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Msg;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * diagnostico_fps: o diagnostico_lag do cliente. Mostra a causa provavel dos picos de frame (as "travadinhas").
 * <ul>
 * <li>O mixin no Minecraft.runTick so grava System.nanoTime() no comeco e no fim do frame (e desconta o sono do
 *     limitador de FPS, que nao e trabalho).</li>
 * <li>Uma vigia so do cliente acorda a cada 5 ms. Quando o frame atual passa de limite_ms, tira amostras da pilha
 *     da thread de render enquanto ele durar.</li>
 * <li>No fim do frame lento, a vigia classifica as amostras pelo mod dono (o mesmo classificador do
 *     diagnostico_lag) e pela fase do Minecraft (entidades, block entities, chunks, particulas, GUI, tooltips,
 *     modelos/texturas, rede). Sem amostras e com pausa da JVM no intervalo, a causa e a pausa (monitor_gc).</li>
 * </ul>
 * Ignora frames fora do mundo (telas de carregamento) e com a janela sem foco.
 */
public final class DiagnosticoFps {
    private static final int AMOSTRA_MS = 5;
    private static final int AMOSTRA_LENTA_MS = 25;
    private static final int JANELA_FRAMES = 16384; // mais de 60 s a 240 FPS
    public static final int SEGUNDOS_HISTORICO = 300;
    private static final int MAX_PICOS = 30;
    private static final long LOG_INTERVALO_MS = 30_000;

    /** Um frame lento ja classificado. */
    public record Pico(LocalTime hora, long duracaoMs, String mod, String fase, String detalhe, String pilha, int amostras) {
    }

    // ---- estado do frame atual (thread de render escreve, vigia le) ----
    private static volatile long inicioNs;
    private static volatile long frameId;
    private static volatile Thread render;
    private static long limitadorInicioNs;
    private static long limitadorNs;
    private static long ultimoInicioNs;
    private static final List<StackTraceElement[]> AMOSTRAS = new ArrayList<>();
    private static long amostrasDoFrame = -1;

    // ---- estatisticas (thread de render escreve; leitura pela tela, tambem na thread de render) ----
    private static final float[] INTERVALOS_MS = new float[JANELA_FRAMES];
    private static final long[] QUANDO_MS = new long[JANELA_FRAMES];
    private static int indice;
    private static int preenchidos;
    private static final float[] MAX_POR_SEGUNDO = new float[SEGUNDOS_HISTORICO];
    private static long segundoAtual = -1;
    private static int segundoIndice;
    private static int segundosPreenchidos;
    private static float maxSegundo;
    private static long frames;
    private static long custoNs;
    private static long custoAmostras;

    // ---- picos (vigia escreve) ----
    private static final Deque<Object[]> PENDENTES = new ArrayDeque<>(); // {duracaoMs, List<amostras>, inicioMs}
    private static final Deque<Pico> PICOS = new ArrayDeque<>();
    private static final Deque<Long> HORARIOS_PICOS = new ArrayDeque<>();
    private static final Map<String, long[]> POR_CAUSA = new HashMap<>(); // "mod|fase" -> {ms, picos}
    public static volatile long picos;
    public static volatile long picosGrandes;
    public static volatile long piorMs;
    public static volatile String piorCausa = "";
    private static long ultimoLogMs;
    private static boolean iniciado;

    private DiagnosticoFps() {
    }

    public static synchronized void iniciar() {
        if (iniciado) {
            return;
        }
        iniciado = true;
        Thread vigia = new Thread(DiagnosticoFps::vigiar, "HLO - diagnostico_fps");
        vigia.setDaemon(true);
        vigia.setPriority(Thread.MAX_PRIORITY);
        vigia.start();
    }

    // ---------- chamados pelo mixin, na thread de render: precisam ser baratos ----------

    public static void inicioFrame() {
        long agora = System.nanoTime();
        if (render == null) {
            render = Thread.currentThread();
        }
        if (ultimoInicioNs != 0) {
            registrarIntervalo((agora - ultimoInicioNs) / 1e6f);
        }
        ultimoInicioNs = agora;
        limitadorNs = 0;
        frameId++;
        inicioNs = agora;
        custoNs += System.nanoTime() - agora;
    }

    public static void inicioLimitador() {
        limitadorInicioNs = System.nanoTime();
    }

    public static void fimLimitador() {
        if (limitadorInicioNs != 0) {
            limitadorNs += System.nanoTime() - limitadorInicioNs;
            limitadorInicioNs = 0;
        }
    }

    /** valido=false: frame fora do mundo ou com a janela sem foco (nao conta como pico). */
    public static void fimFrame(boolean valido) {
        long agora = System.nanoTime();
        long inicio = inicioNs;
        inicioNs = 0;
        if (inicio == 0) {
            return;
        }
        long trabalhoMs = (agora - inicio - limitadorNs) / 1_000_000;
        if (trabalhoMs > maxSegundo) {
            maxSegundo = trabalhoMs;
        }
        if (valido && trabalhoMs >= Config.fpsLimiteMs()) {
            List<StackTraceElement[]> amostras;
            synchronized (AMOSTRAS) {
                amostras = amostrasDoFrame == frameId ? new ArrayList<>(AMOSTRAS) : List.of();
                AMOSTRAS.clear();
                amostrasDoFrame = -1;
            }
            synchronized (PENDENTES) {
                PENDENTES.addLast(new Object[]{trabalhoMs, amostras, System.currentTimeMillis() - trabalhoMs});
            }
        } else if (amostrasDoFrame == frameId) {
            synchronized (AMOSTRAS) {
                AMOSTRAS.clear();
                amostrasDoFrame = -1;
            }
        }
        custoNs += System.nanoTime() - agora;
        custoAmostras++;
    }

    private static void registrarIntervalo(float ms) {
        long agoraMs = System.currentTimeMillis();
        INTERVALOS_MS[indice] = ms;
        QUANDO_MS[indice] = agoraMs;
        indice = (indice + 1) % JANELA_FRAMES;
        preenchidos = Math.min(JANELA_FRAMES, preenchidos + 1);
        frames++;
        long segundo = agoraMs / 1000;
        if (segundo != segundoAtual) {
            if (segundoAtual >= 0) {
                long pulados = Math.min(SEGUNDOS_HISTORICO, segundo - segundoAtual);
                for (long i = 0; i < pulados; i++) {
                    MAX_POR_SEGUNDO[segundoIndice] = i == 0 ? maxSegundo : ms; // segundos sem frame: o frame que terminou depois
                    segundoIndice = (segundoIndice + 1) % SEGUNDOS_HISTORICO;
                    segundosPreenchidos = Math.min(SEGUNDOS_HISTORICO, segundosPreenchidos + 1);
                }
            }
            segundoAtual = segundo;
            maxSegundo = 0;
        }
    }

    // ---------- vigia ----------

    private static void vigiar() {
        while (true) {
            try {
                // Picos o tempo todo (FPS baixo constante): amostra menos, para nao pesar mais num PC que ja sofre.
                Thread.sleep(picosRecentes() > 20 ? AMOSTRA_LENTA_MS : AMOSTRA_MS);
                long inicio = inicioNs;
                Thread r = render;
                if (inicio != 0 && r != null && (System.nanoTime() - inicio) / 1_000_000 >= Config.fpsLimiteMs()) {
                    long id = frameId;
                    StackTraceElement[] pilha = r.getStackTrace();
                    synchronized (AMOSTRAS) {
                        if (amostrasDoFrame != id) {
                            AMOSTRAS.clear();
                            amostrasDoFrame = id;
                        }
                        if (AMOSTRAS.size() < 400) {
                            AMOSTRAS.add(pilha);
                        }
                    }
                }
                Object[] pendente;
                while (true) {
                    synchronized (PENDENTES) {
                        pendente = PENDENTES.pollFirst();
                    }
                    if (pendente == null) {
                        break;
                    }
                    @SuppressWarnings("unchecked")
                    List<StackTraceElement[]> amostras = (List<StackTraceElement[]>) pendente[1];
                    classificar((Long) pendente[0], amostras, (Long) pendente[2]);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                Config.LOGGER.debug("[diagnostico_fps] Erro na vigia", t);
            }
        }
    }

    private static void classificar(long ms, List<StackTraceElement[]> amostras, long inicioMs) {
        String mod;
        String fase;
        String detalhe = "";
        String pilhaTexto = "";
        if (amostras.isEmpty()) {
            MonitorGc.Pausa pausa = MonitorGc.pausaEntre(inicioMs, inicioMs + ms);
            mod = pausa != null ? DiagnosticoLag.PAUSA_JVM : DiagnosticoLag.SEM_MOD;
            fase = pausa != null ? Msg.t("pausa da JVM") : Msg.t("curto demais para amostrar");
            detalhe = pausa != null ? pausa.descricao() : "";
        } else {
            Map<String, Integer> contagem = new HashMap<>();
            Map<String, String> exemplos = new HashMap<>();
            for (StackTraceElement[] p : amostras) {
                DiagnosticoLag.Causa c = DiagnosticoLag.classificar(p);
                String chave = c.chave() + "|" + fase(p);
                contagem.merge(chave, 1, Integer::sum);
                exemplos.putIfAbsent(chave, c.detalhe());
            }
            String principal = contagem.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
            mod = principal.substring(0, principal.indexOf('|'));
            fase = principal.substring(principal.indexOf('|') + 1);
            detalhe = exemplos.getOrDefault(principal, "");
            pilhaTexto = DiagnosticoLag.formatarPilha(Arrays.copyOf(amostras.get(amostras.size() / 2), Math.min(15, amostras.get(amostras.size() / 2).length)));
        }
        Pico pico = new Pico(LocalTime.now(), ms, mod, fase, detalhe, pilhaTexto, amostras.size());
        synchronized (PICOS) {
            PICOS.addFirst(pico);
            while (PICOS.size() > MAX_PICOS) {
                PICOS.removeLast();
            }
            HORARIOS_PICOS.addLast(System.currentTimeMillis());
            while (HORARIOS_PICOS.size() > 2000) {
                HORARIOS_PICOS.removeFirst();
            }
            long[] total = POR_CAUSA.computeIfAbsent(mod + "|" + fase, k -> new long[2]);
            total[0] += ms;
            total[1]++;
        }
        picos++;
        if (ms >= Config.fpsRegistroMs()) {
            picosGrandes++;
        }
        if (ms > piorMs) {
            piorMs = ms;
            piorCausa = DiagnosticoLag.rotulo(mod) + " (" + fase + ")";
        }
        long agora = System.currentTimeMillis();
        if (ms >= Config.fpsRegistroMs() && agora - ultimoLogMs >= LOG_INTERVALO_MS) {
            ultimoLogMs = agora;
            Config.LOGGER.warn("[diagnostico_fps] Frame lento: {} ms. Provavel causa: {} ({}){} [{} amostra(s)]{}", ms, DiagnosticoLag.rotulo(mod), fase,
                    detalhe.isEmpty() ? "" : " - " + detalhe, amostras.size(), pilhaTexto.isEmpty() ? "" : "\n" + pilhaTexto);
        }
    }

    /** Fase do Minecraft pela classe mais perto do topo da pilha que a identifica. */
    static String fase(StackTraceElement[] pilha) {
        for (StackTraceElement f : pilha) {
            String c = f.getClassName();
            String m = f.getMethodName();
            if (c.contains("Tooltip") || m.equals("getTooltipLines") || m.equals("m_41651_")) {
                return Msg.t("tooltips");
            }
            if (c.startsWith("net.minecraft.client.renderer.entity.") || c.contains("EntityRenderDispatcher")) {
                return Msg.t("render de entidades");
            }
            if (c.startsWith("net.minecraft.client.renderer.blockentity.") || c.contains("BlockEntityRenderDispatcher")) {
                return Msg.t("render de block entities");
            }
            if (c.startsWith("net.minecraft.client.renderer.chunk.") || c.contains(".render.chunk.") || c.contains("embeddium") && c.contains("chunk")) {
                return Msg.t("chunks (rebuild/upload)");
            }
            if (c.startsWith("net.minecraft.client.particle.")) {
                return Msg.t("particulas");
            }
            if (c.startsWith("net.minecraft.client.resources.model.") || c.startsWith("net.minecraft.client.renderer.texture.")
                    || c.startsWith("net.minecraftforge.client.model.") || c.contains("geckolib") && c.contains("Loader")) {
                return Msg.t("carregar modelo/textura");
            }
            if (c.startsWith("net.minecraft.client.gui.") || c.startsWith("net.minecraftforge.client.gui.")) {
                return Msg.t("GUI/HUD");
            }
            if (c.equals("net.minecraft.client.multiplayer.ClientPacketListener") || c.startsWith("net.minecraft.network.")
                    || c.startsWith("net.minecraftforge.network.")) {
                return Msg.t("rede (pacotes)");
            }
            if (c.equals("net.minecraft.client.multiplayer.ClientLevel") || c.equals("net.minecraft.client.Minecraft") && (m.equals("tick") || m.equals("m_91398_"))) {
                return Msg.t("tick do cliente");
            }
            if (c.equals("net.minecraft.client.renderer.LevelRenderer") || c.equals("net.minecraft.client.renderer.GameRenderer")) {
                return Msg.t("render do mundo");
            }
            if (c.equals("net.minecraft.client.server.IntegratedServer")) {
                return Msg.t("servidor interno");
            }
            if (c.equals("com.mojang.blaze3d.pipeline.RenderTarget") || c.equals("com.mojang.blaze3d.platform.Window")) {
                return Msg.t("esperando a placa de video (fim do frame)");
            }
        }
        return Msg.t("outro");
    }

    // ---------- leitura para a tela e o resumo ----------

    /** {fps medio, fps do 1% mais lento, fps do 0,1% mais lento} no ultimo minuto (0 sem dados). */
    public static float[] fpsUltimoMinuto() {
        long limite = System.currentTimeMillis() - 60_000;
        float[] valores = new float[preenchidos];
        int n = 0;
        double soma = 0;
        for (int i = 0; i < preenchidos; i++) {
            if (QUANDO_MS[i] >= limite) {
                valores[n++] = INTERVALOS_MS[i];
                soma += INTERVALOS_MS[i];
            }
        }
        if (n == 0) {
            return new float[3];
        }
        float[] v = Arrays.copyOf(valores, n);
        Arrays.sort(v);
        return new float[]{(float) (1000.0 * n / soma), 1000f / media(v, Math.max(1, n / 100)), 1000f / media(v, Math.max(1, n / 1000))};
    }

    /** Media dos k maiores (frames mais lentos) de um vetor ordenado. */
    private static float media(float[] ordenado, int k) {
        double s = 0;
        for (int i = ordenado.length - k; i < ordenado.length; i++) {
            s += ordenado[i];
        }
        return (float) (s / k);
    }

    /** Picos nos ultimos 10 s. */
    private static int picosRecentes() {
        long limite = System.currentTimeMillis() - 10_000;
        int n = 0;
        synchronized (PICOS) {
            var it = HORARIOS_PICOS.descendingIterator();
            while (it.hasNext() && it.next() >= limite) {
                n++;
            }
        }
        return n;
    }

    public static int picosUltimoMinuto() {
        long limite = System.currentTimeMillis() - 60_000;
        int n = 0;
        synchronized (PICOS) {
            for (Long h : HORARIOS_PICOS) {
                if (h >= limite) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Maior tempo de frame por segundo, do mais velho para o mais novo. */
    public static float[] historico() {
        float[] h = new float[segundosPreenchidos];
        int inicio = (segundoIndice - segundosPreenchidos + SEGUNDOS_HISTORICO) % SEGUNDOS_HISTORICO;
        for (int i = 0; i < segundosPreenchidos; i++) {
            h[i] = MAX_POR_SEGUNDO[(inicio + i) % SEGUNDOS_HISTORICO];
        }
        return h;
    }

    public static List<Pico> ultimosPicos() {
        synchronized (PICOS) {
            return new ArrayList<>(PICOS);
        }
    }

    /** {rotulo (fase), ms total, picos}, do maior para o menor. */
    public static List<Object[]> causas() {
        List<Object[]> lista = new ArrayList<>();
        synchronized (PICOS) {
            POR_CAUSA.forEach((chave, v) -> {
                int sep = chave.indexOf('|');
                lista.add(new Object[]{DiagnosticoLag.rotulo(chave.substring(0, sep)) + " (" + chave.substring(sep + 1) + ")", v[0], v[1]});
            });
        }
        lista.sort((a, b) -> Long.compare((Long) b[1], (Long) a[1]));
        return lista;
    }

    /** Custo medio dos ganchos do frame (microssegundos). */
    public static double custoUsPorFrame() {
        return custoAmostras == 0 ? 0 : custoNs / 1000.0 / custoAmostras;
    }

    public static long frames() {
        return frames;
    }

    public static String resumo() {
        return String.format(Msg.t("diagnostico_fps: %d pico(s) de frame acima de %d ms, %d acima de %d ms, pior %d ms (%s); custo %.1f us por frame"),
                picos, Config.fpsLimiteMs(), picosGrandes, Config.fpsRegistroMs(), piorMs, piorCausa.isEmpty() ? "-" : piorCausa, custoUsPorFrame());
    }
}
