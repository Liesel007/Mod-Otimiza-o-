package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.mixin.lag.ChunkMapAccessor;
import com.hlotimizacao.mixin.lag.LevelAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Registra travamentos do servidor.
 *
 * Uma thread vigia o tempo do tick atual. Se passar do limite, ela registra a pilha da thread do
 * servidor e continua tirando uma amostra a cada 250 ms (no maximo {@value #MAX_AMOSTRAS}) enquanto
 * o travamento durar. No fim, as amostras sao agregadas pelo mod dono do primeiro frame que nao e do
 * Minecraft, do Forge ou do Java ("provavel causa"). Ler a pilha de outra thread e seguro; o estado do
 * mundo, nao. Por isso o relatorio com as contagens e feito na propria thread do servidor.
 *
 * Fora do tick (tarefas de chunk na espera do proximo tick, salvamento ao pausar), so conta o tempo
 * em que a thread esta de fato trabalhando: com o jogo pausado ou o servidor ocioso ela fica parada
 * esperando, e isso nao e travamento. Entre o ServerStoppingEvent e o ServerStartedEvent (abrir e
 * fechar o mundo) e durante uma tolerancia depois de abrir, nada e registrado.
 *
 * No maximo um registro a cada diagnostico_lag.intervalo_s segundos.
 */
public final class DiagnosticoLag {
    private static final int FRAMES_NA_PILHA = 45;
    private static final int TOP = 5;
    private static final int MAX_AMOSTRAS = 20;
    private static final long AMOSTRA_MS = 250;
    /** Ate quando depois de abrir o mundo o primeiro jogador entrando ainda conta como "abrindo". */
    private static final long JANELA_PRIMEIRO_JOGADOR_MS = 120_000;
    private static final Set<String> MODS_BASE = Set.of("minecraft", "forge");

    private static volatile Thread threadServidor;
    private static volatile MinecraftServer servidor;
    private static volatile long inicioTickNs;
    private static volatile boolean emTick;
    /** Falso enquanto o mundo abre ou fecha. */
    private static volatile boolean servidorRodando;
    private static volatile long iniciadoEmMs;
    private static volatile long toleranciaAteMs;
    private static volatile boolean primeiroJogadorVisto;
    private static volatile long ultimoRegistroMs;
    private static volatile Travamento atual;
    /** So no cliente: o jogo esta pausado (menu aberto no singleplayer). */
    private static volatile BooleanSupplier detectorPausa = () -> false;
    private static Thread vigia;
    /** Usado so pela thread vigia: desde quando a thread do servidor esta ocupada fora de um tick. */
    private static long ocupadoDesdeNs;
    private static Map<String, String> modPorModulo;

    /**
     * Chunks que terminaram de carregar desde o fim do tick anterior, por dimensao:
     * [0] = todos, [1] = gerados agora (chunks novos).
     */
    private static final Map<String, AtomicIntegerArray> CHUNKS_DESDE_ULTIMO_TICK = new ConcurrentHashMap<>();

    private enum Onde {
        DENTRO("dentro do tick"), ENTRE("entre ticks");
        final String texto;

        Onde(String texto) {
            this.texto = texto;
        }
    }

    private static final class Travamento {
        final Onde onde;
        /** inicioTickNs (dentro do tick) ou ocupadoDesdeNs (entre ticks). */
        final long referenciaNs;
        final List<StackTraceElement[]> amostras = new ArrayList<>();
        volatile long duracaoMs;
        volatile boolean salvando;
        volatile boolean pausado;
        volatile boolean encerrado;

        Travamento(Onde onde, long referenciaNs) {
            this.onde = onde;
            this.referenciaNs = referenciaNs;
        }
    }

    private DiagnosticoLag() {
    }

    public static synchronized void iniciar() {
        if (vigia != null) {
            return;
        }
        vigia = new Thread(DiagnosticoLag::vigiar, "HL Otimizacao - diagnostico de lag");
        vigia.setDaemon(true);
        vigia.start();
        Config.LOGGER.info("[diagnostico_lag] Ativo: registra ticks acima de {} ms (no maximo 1 registro a cada {} s; "
                + "ignora os primeiros {} s depois de abrir o mundo)", Config.lagLimiteMs(), Config.lagIntervaloS(), Config.lagToleranciaS());
    }

    /** Chamado pelo lado do cliente para os registros feitos com o jogo pausado serem marcados. */
    public static void definirDetectorPausa(BooleanSupplier detector) {
        detectorPausa = detector;
    }

    public static void aoIniciarServidor(MinecraftServer server) {
        servidor = server;
        atual = null;
        emTick = false;
        primeiroJogadorVisto = false;
        iniciadoEmMs = System.currentTimeMillis();
        toleranciaAteMs = iniciadoEmMs + Config.lagToleranciaS() * 1000L;
        servidorRodando = true;
    }

    public static void aoPararServidor() {
        servidorRodando = false;
        atual = null;
    }

    /** O primeiro jogador entrando logo depois de abrir o mundo ainda carrega classes e chunks: estende a tolerancia. */
    public static void aoEntrarJogador() {
        long agora = System.currentTimeMillis();
        if (!primeiroJogadorVisto && agora - iniciadoEmMs < JANELA_PRIMEIRO_JOGADOR_MS) {
            primeiroJogadorVisto = true;
            toleranciaAteMs = Math.max(toleranciaAteMs, agora + Config.lagToleranciaS() * 1000L);
        }
    }

    private static boolean vigiando() {
        return servidorRodando && System.currentTimeMillis() >= toleranciaAteMs;
    }

    public static void aoCarregarChunk(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AtomicIntegerArray contagem = CHUNKS_DESDE_ULTIMO_TICK.computeIfAbsent(level.dimension().location().toString(), k -> new AtomicIntegerArray(2));
            contagem.incrementAndGet(0);
            if (event.isNewChunk()) {
                contagem.incrementAndGet(1);
            }
        }
    }

    public static void aoTick(TickEvent.ServerTickEvent event) {
        long agora = System.nanoTime();
        if (event.phase == TickEvent.Phase.START) {
            threadServidor = Thread.currentThread();
            servidor = event.getServer();
            Travamento t = atual;
            if (t != null) {
                // Travamento entre ticks (ou um que a vigia comecou a registrar no fim do tick anterior).
                relatorio(event.getServer(), t, t.onde == Onde.ENTRE ? t.duracaoMs : -1);
            }
            inicioTickNs = agora;
            emTick = true;
        } else {
            emTick = false;
            long duracaoMs = (agora - inicioTickNs) / 1_000_000;
            Travamento t = atual;
            if (vigiando()) {
                if (t != null && t.onde == Onde.DENTRO) {
                    relatorio(event.getServer(), t, duracaoMs);
                } else if (t == null && duracaoMs >= Config.lagLimiteMs() && podeRegistrar()) {
                    // Lento, mas curto demais para a vigia (que olha a cada 250 ms) pegar.
                    Travamento curto = new Travamento(Onde.DENTRO, inicioTickNs);
                    curto.salvando = event.getServer().isCurrentlySaving();
                    relatorio(event.getServer(), curto, duracaoMs);
                }
            } else {
                atual = null;
            }
            CHUNKS_DESDE_ULTIMO_TICK.clear();
        }
    }

    private static boolean podeRegistrar() {
        return System.currentTimeMillis() - ultimoRegistroMs >= Config.lagIntervaloS() * 1000L;
    }

    private static void vigiar() {
        while (true) {
            try {
                Thread.sleep(AMOSTRA_MS);
                amostrar();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                Config.LOGGER.debug("[diagnostico_lag] Erro na thread vigia", t);
            }
        }
    }

    private static void amostrar() {
        Thread thread = threadServidor;
        if (thread == null || !thread.isAlive() || !vigiando()) {
            ocupadoDesdeNs = 0;
            return;
        }
        long agora = System.nanoTime();
        boolean dentro = emTick;
        long referencia;
        if (dentro) {
            ocupadoDesdeNs = 0;
            referencia = inicioTickNs;
        } else if (thread.getState() == Thread.State.RUNNABLE) {
            // Fora do tick a thread so esta travada se estiver trabalhando. Parada esperando o proximo
            // tick (servidor ocioso, jogo pausado no singleplayer) nao conta, e zera a contagem.
            if (ocupadoDesdeNs == 0) {
                ocupadoDesdeNs = agora;
            }
            referencia = ocupadoDesdeNs;
        } else {
            ocupadoDesdeNs = 0;
            Travamento t = atual;
            if (t != null && t.onde == Onde.ENTRE) {
                t.encerrado = true; // relatorio no proximo tick
            }
            return;
        }
        long paradoMs = (agora - referencia) / 1_000_000;
        Onde onde = dentro ? Onde.DENTRO : Onde.ENTRE;
        Travamento t = atual;
        if (t != null) {
            if (!t.encerrado && t.onde == onde && t.referenciaNs == referencia) {
                adicionarAmostra(t, thread, paradoMs);
            } else {
                t.encerrado = true;
            }
            return;
        }
        if (paradoMs < Config.lagLimiteMs() || !podeRegistrar()) {
            return;
        }
        t = new Travamento(onde, referencia);
        ultimoRegistroMs = System.currentTimeMillis();
        StackTraceElement[] pilha = adicionarAmostra(t, thread, paradoMs);
        atual = t;
        Config.LOGGER.warn("[diagnostico_lag] Servidor travado ha {} ms ({}{}). Onde a thread do servidor esta agora "
                        + "(continua tirando amostras enquanto durar):\n{}",
                paradoMs, onde.texto, marcaSalvamento(t), formatarPilha(pilha));
    }

    private static StackTraceElement[] adicionarAmostra(Travamento t, Thread thread, long paradoMs) {
        t.duracaoMs = paradoMs;
        MinecraftServer server = servidor;
        if (server != null && server.isCurrentlySaving()) {
            t.salvando = true;
        }
        try {
            if (detectorPausa.getAsBoolean()) {
                t.pausado = true;
            }
        } catch (Throwable ignorado) {
            // so serve para marcar o registro
        }
        StackTraceElement[] pilha = thread.getStackTrace();
        synchronized (t.amostras) {
            if (t.amostras.size() < MAX_AMOSTRAS) {
                t.amostras.add(pilha);
            }
        }
        return pilha;
    }

    private static String marcaSalvamento(Travamento t) {
        if (t.salvando && t.pausado) {
            return ", durante o salvamento ao pausar (\"Saving and pausing game\"): provavelmente gravacao em disco";
        }
        if (t.salvando) {
            return ", durante um salvamento do mundo: provavelmente gravacao em disco";
        }
        return "";
    }

    private static String formatarPilha(StackTraceElement[] frames) {
        StringBuilder sb = new StringBuilder();
        int limite = Math.min(frames.length, FRAMES_NA_PILHA);
        for (int i = 0; i < limite; i++) {
            sb.append("    at ").append(frames[i]).append('\n');
        }
        if (frames.length > limite) {
            sb.append("    ... mais ").append(frames.length - limite).append(" frames\n");
        }
        return sb.toString();
    }

    /**
     * Mod dono do frame, ou null se for do Minecraft, do Forge, do Java ou de uma biblioteca.
     * Handlers de mixin injetados em classes do Minecraft tem o id no nome
     * (handler$abc000$idmod$metodo), entao contam para o mod do mixin.
     */
    static String modDoFrame(StackTraceElement frame) {
        String metodo = frame.getMethodName();
        if (metodo.indexOf('$') > 0) {
            String[] partes = metodo.split("\\$");
            if (partes.length >= 4 && ehPrefixoMixin(partes[0])) {
                return MODS_BASE.contains(partes[2]) ? null : partes[2];
            }
        }
        String modulo = frame.getModuleName();
        if (modulo == null) {
            return null;
        }
        String mod = modsPorModulo().get(modulo);
        return mod == null || MODS_BASE.contains(mod) ? null : mod;
    }

    private static boolean ehPrefixoMixin(String prefixo) {
        return switch (prefixo) {
            case "handler", "redirect", "modify", "localvar", "constant", "wrapOperation", "wrapWithCondition",
                    "modifyExpressionValue", "modifyReturnValue", "modifyReceiver", "wrapMethod" -> true;
            default -> prefixo.startsWith("md") || prefixo.startsWith("mixinextras");
        };
    }

    private static synchronized Map<String, String> modsPorModulo() {
        if (modPorModulo == null) {
            Map<String, String> mapa = new HashMap<>();
            try {
                for (IModFileInfo arquivo : ModList.get().getModFiles()) {
                    List<IModInfo> mods = arquivo.getMods();
                    if (!mods.isEmpty()) {
                        mapa.put(arquivo.getFile().getSecureJar().name(), mods.get(0).getModId());
                    }
                }
            } catch (Throwable t) {
                Config.LOGGER.debug("[diagnostico_lag] Nao foi possivel montar a lista de modulos dos mods", t);
            }
            modPorModulo = mapa;
        }
        return modPorModulo;
    }

    /** "pehkui 85% (virtuoel...ScaleData.getScale), avaritia 15% (...)", sobre todas as amostras. */
    static String provavelCausa(List<StackTraceElement[]> amostras) {
        if (amostras.isEmpty()) {
            return "sem amostras (travamento curto demais)";
        }
        Map<String, Integer> contagem = new LinkedHashMap<>();
        Map<String, String> exemplo = new HashMap<>();
        for (StackTraceElement[] pilha : amostras) {
            String mod = "Minecraft/Forge (nenhum mod no topo da pilha)";
            String frameExemplo = pilha.length > 0 ? pilha[0].getClassName() + "." + pilha[0].getMethodName() : "?";
            for (StackTraceElement frame : pilha) {
                String dono = modDoFrame(frame);
                if (dono != null) {
                    mod = dono;
                    frameExemplo = frame.getClassName() + "." + frame.getMethodName();
                    break;
                }
            }
            contagem.merge(mod, 1, Integer::sum);
            exemplo.putIfAbsent(mod, frameExemplo);
        }
        int total = amostras.size();
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(e -> String.format("%s %d%% (%s)", e.getKey(), Math.round(100.0 * e.getValue() / total), exemplo.get(e.getKey())))
                .collect(Collectors.joining(", ")) + String.format(" [%d amostra(s)]", total);
    }

    /** Roda na thread do servidor. duracaoMs < 0: desconhecida (usa a da vigia). */
    private static void relatorio(MinecraftServer server, Travamento t, long duracaoMs) {
        atual = null;
        ultimoRegistroMs = System.currentTimeMillis();
        Estatisticas.LAG_REGISTROS.incrementAndGet();
        List<StackTraceElement[]> amostras;
        synchronized (t.amostras) {
            amostras = new ArrayList<>(t.amostras);
        }
        long duracao = duracaoMs >= 0 ? duracaoMs : t.duracaoMs;
        StringBuilder sb = new StringBuilder();
        if (t.onde == Onde.ENTRE) {
            sb.append(String.format("[diagnostico_lag] Tick lento: servidor ocupado por pelo menos %d ms entre ticks%s.%n", duracao, marcaSalvamento(t)));
        } else {
            sb.append(String.format("[diagnostico_lag] Tick lento: %d ms (dentro do tick%s).%n", duracao, marcaSalvamento(t)));
        }
        sb.append("  Provavel causa: ").append(provavelCausa(amostras)).append('\n');
        int vazias = 0;
        for (ServerLevel level : server.getAllLevels()) {
            try {
                if (!dimensao(level, sb)) {
                    vazias++;
                }
            } catch (Throwable e) {
                sb.append("  ").append(level.dimension().location()).append(": erro ao coletar (").append(e).append(")\n");
            }
        }
        if (vazias > 0) {
            sb.append(String.format("  +%d dimensao(oes) vazia(s) (sem jogadores, chunks ou entidades)%n", vazias));
        }
        Config.LOGGER.warn(sb.toString().stripTrailing());
    }

    /** Escreve o estado da dimensao; devolve false (sem escrever) se ela estiver vazia. */
    private static boolean dimensao(ServerLevel level, StringBuilder sb) {
        int total = 0;
        int prontos = 0;
        for (ChunkHolder holder : ((ChunkMapAccessor) level.getChunkSource().chunkMap).hlotimizacao$getChunks()) {
            total++;
            if (holder.getFullChunk() != null) {
                prontos++;
            }
        }
        Map<String, Integer> entidades = new HashMap<>();
        int totalEntidades = 0;
        for (Entity entidade : level.getAllEntities()) {
            totalEntidades++;
            entidades.merge(idEntidade(entidade.getType()), 1, Integer::sum);
        }
        int jogadores = level.players().size();
        if (jogadores == 0 && total == 0 && totalEntidades == 0) {
            return false;
        }
        Map<String, Integer> blocos = new HashMap<>();
        int dormindo = 0;
        List<TickingBlockEntity> tickers = ((LevelAccessor) level).hlotimizacao$blockEntityTickers();
        for (TickingBlockEntity ticker : tickers) {
            String tipo = ticker.getType();
            if (tipo.startsWith("<")) {
                // Ex.: "<canary_sleeping>": o Canary troca o ticker de block entities paradas por um
                // "dormindo". Mostra o tipo real e conta a parte.
                dormindo++;
                tipo = tipoReal(level, ticker, tipo);
            }
            blocos.merge(tipo, 1, Integer::sum);
        }
        AtomicIntegerArray noTick = CHUNKS_DESDE_ULTIMO_TICK.get(level.dimension().location().toString());
        sb.append(String.format("  %s: jogadores=%d, chunks prontos=%d, parciais (borda ou em geracao)=%d, tarefas pendentes=%d%n",
                level.dimension().location(), jogadores, prontos, total - prontos, level.getChunkSource().getPendingTasksCount()));
        if (noTick != null && noTick.get(0) > 0) {
            sb.append(String.format("    durante o travamento: %d chunks carregados, dos quais %d gerados agora (novos)%n", noTick.get(0), noTick.get(1)));
        }
        sb.append(String.format("    entidades=%d, mais comuns: %s%n", totalEntidades, top(entidades)));
        sb.append(String.format("    block entities que tickam=%d%s, mais comuns: %s%n", tickers.size(),
                dormindo > 0 ? " (" + dormindo + " dormindo)" : "", top(blocos)));
        return true;
    }

    private static String tipoReal(ServerLevel level, TickingBlockEntity ticker, String padrao) {
        try {
            if (!level.isLoaded(ticker.getPos())) {
                return padrao;
            }
            BlockEntity be = level.getBlockEntity(ticker.getPos());
            if (be != null) {
                ResourceLocation id = BlockEntityType.getKey(be.getType());
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Throwable ignorado) {
            // fica com o nome do ticker
        }
        return padrao;
    }

    private static String idEntidade(EntityType<?> tipo) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(tipo);
        return id == null ? tipo.toString() : id.toString();
    }

    private static String top(Map<String, Integer> contagem) {
        if (contagem.isEmpty()) {
            return "-";
        }
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP)
                .map(e -> e.getKey() + " x" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
