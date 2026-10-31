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
import java.util.Deque;
import java.util.ArrayDeque;
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

    public static final String SEM_MOD = "Minecraft/Forge (nenhum mod no topo da pilha)";

    /** Um travamento registrado, para o /hl status lag. */
    public record Registro(java.time.LocalTime hora, long duracaoMs, String tipo, String mod, String frame, String pilha, String dimensoes) {
    }

    private static final int MAX_HISTORICO = 20;
    private static final Deque<Registro> HISTORICO = new ArrayDeque<>();
    /** Tempo de travamento atribuido a cada mod na sessao (duracao x fracao das amostras). */
    private static final Map<String, Long> MS_POR_MOD = new ConcurrentHashMap<>();
    private static final Map<String, String> FRAME_POR_MOD = new ConcurrentHashMap<>();
    private static volatile long msTotal;
    private static volatile Registro pior;

    public static synchronized List<Registro> historico() {
        return new ArrayList<>(HISTORICO);
    }

    public static Map<String, Long> msPorMod() {
        return Map.copyOf(MS_POR_MOD);
    }

    public static String framePorMod(String mod) {
        return FRAME_POR_MOD.getOrDefault(mod, "?");
    }

    public static long msTotal() {
        return msTotal;
    }

    public static Registro pior() {
        return pior;
    }

    private static synchronized void guardar(Registro registro, Map<String, Integer> contagem, int amostras) {
        HISTORICO.addFirst(registro);
        while (HISTORICO.size() > MAX_HISTORICO) {
            HISTORICO.removeLast();
        }
        msTotal += registro.duracaoMs();
        if (pior == null || registro.duracaoMs() > pior.duracaoMs()) {
            pior = registro;
        }
        if (amostras == 0) {
            MS_POR_MOD.merge(SEM_MOD, registro.duracaoMs(), Long::sum);
            return;
        }
        contagem.forEach((mod, n) -> MS_POR_MOD.merge(mod, registro.duracaoMs() * n / amostras, Long::sum));
    }

    // Causas que nao sao um mod: padroes de espera reconhecidos pelo topo da pilha.
    public static final String CHUNK_SINCRONO = "carregamento sincrono de chunk";
    public static final String GRAVACAO_DISCO = "gravacao em disco";
    public static final String COMPRESSAO = "compressao ao salvar";
    public static final String CARREGANDO_CLASSES = "carregamento de classes";
    public static final String INSERCAO = "insercao em inventario grande";
    private static final Map<String, String> EXPLICACAO = Map.of(
            CHUNK_SINCRONO, "o servidor esperou um chunk ficar pronto; comum ao teleportar ou viajar rapido",
            GRAVACAO_DISCO, "abrindo ou gravando arquivo; se for frequente, veja o antivirus",
            COMPRESSAO, "comprimindo dados ao salvar",
            CARREGANDO_CLASSES, "acontece uma vez por classe, normalmente logo depois de entrar no mundo",
            INSERCAO, "uma maquina inserindo itens num inventario com muitos slots; o Forge percorre todos os slots para cada pilha");
    /** Frames de mod mais fundos que isso nao contam como causa (estao so "embrulhando" a chamada). */
    static final int PROFUNDIDADE_MAXIMA = 12;

    /** Causa de uma amostra: um mod, um padrao de espera ou Minecraft/Forge. */
    public record Causa(String chave, String detalhe) {
    }

    public static boolean ehPadrao(String chave) {
        return EXPLICACAO.containsKey(chave);
    }

    /** Nome curto para mostrar no /hl status (mods aparecem pelo id; padroes por um rotulo). */
    public static String rotulo(String chave) {
        return switch (chave) {
            case CHUNK_SINCRONO -> "chunk síncrono";
            case GRAVACAO_DISCO -> "gravação em disco";
            case COMPRESSAO -> "compressão";
            case CARREGANDO_CLASSES -> "carregar classes";
            case INSERCAO -> "inserção em inventário";
            case SEM_MOD -> "Minecraft/Forge";
            default -> chave;
        };
    }

    public static String explicacao(String chave) {
        return EXPLICACAO.getOrDefault(chave, "");
    }

    private static boolean metodo(StackTraceElement f, String classe, String... nomes) {
        if (!f.getClassName().equals(classe)) {
            return false;
        }
        for (String nome : nomes) {
            if (f.getMethodName().equals(nome)) {
                return true;
            }
        }
        return false;
    }

    private static String classeCurta(StackTraceElement f) {
        String classe = f.getClassName();
        return classe.substring(classe.lastIndexOf('.') + 1);
    }

    /** Um travamento esta sendo registrado agora (a vigia passou do limite)? */
    public static boolean travando() {
        return atual != null;
    }

    private static String nomeCurto(StackTraceElement f) {
        String classe = f.getClassName();
        String metodo = f.getMethodName();
        if (metodo.indexOf('$') > 0) {
            String[] partes = metodo.split("\\$");
            if (partes.length >= 4 && ehPrefixoMixin(partes[0])) {
                metodo = partes[partes.length - 1]; // handler$abc000$mod$metodo -> metodo
            }
        }
        return classe.substring(classe.lastIndexOf('.') + 1) + "." + metodo;
    }

    /** Handler de mixin da lista diagnostico_lag.ignorar_mixins ("mod:metodo"): so repassa a chamada. */
    static boolean mixinIgnorado(StackTraceElement f) {
        String metodo = f.getMethodName();
        if (metodo.indexOf('$') <= 0) {
            return false;
        }
        String[] partes = metodo.split("\\$");
        if (partes.length < 4 || !ehPrefixoMixin(partes[0])) {
            return false;
        }
        return Config.lagIgnorarMixins().contains(partes[2] + ":" + partes[partes.length - 1]);
    }

    /** Primeiro frame de mod a partir de "desde" (ignorando os mixins de passagem), ou null. */
    private static StackTraceElement primeiroDeMod(StackTraceElement[] pilha, int desde, int ate) {
        for (int i = desde; i < Math.min(pilha.length, ate); i++) {
            if (!mixinIgnorado(pilha[i]) && modDoFrame(pilha[i]) != null) {
                return pilha[i];
            }
        }
        return null;
    }

    private static Causa padrao(String chave, StackTraceElement[] pilha, int desde) {
        StackTraceElement pedido = primeiroDeMod(pilha, desde, desde + 25);
        return new Causa(chave, pedido == null ? "" : "pedido por: " + modDoFrame(pedido) + " (" + nomeCurto(pedido) + ")");
    }

    /**
     * Classifica uma amostra. Primeiro os padroes de espera (o topo da pilha diz o que o servidor estava
     * fazendo); depois o mod mais proximo do topo, ate PROFUNDIDADE_MAXIMA frames, ignorando os mixins
     * que so repassam a chamada (ex.: o Chrono, presente em todo tick de jogador).
     */
    public static Causa classificar(StackTraceElement[] pilha) {
        if (pilha.length == 0) {
            return new Causa(SEM_MOD, "");
        }
        // 1. Espera por chunk: managedBlock no topo, vindo de ServerChunkCache.getChunk.
        for (int i = 0; i < Math.min(pilha.length, 6); i++) {
            if (metodo(pilha[i], "net.minecraft.util.thread.BlockableEventLoop", "managedBlock", "m_18701_")
                    || metodo(pilha[i], "net.minecraft.server.level.ServerChunkCache$MainThreadExecutor", "managedBlock", "m_18701_")) {
                for (int j = i + 1; j < Math.min(pilha.length, i + 6); j++) {
                    if (metodo(pilha[j], "net.minecraft.server.level.ServerChunkCache", "getChunk", "m_7587_", "getChunkBlocking", "m_8373_")) {
                        return padrao(CHUNK_SINCRONO, pilha, j + 1);
                    }
                }
            }
        }
        // 2. Insercao de itens: ItemHandlerHelper.insertItemStacked/insertItem perto do topo. Onde = o mod acima
        //    dele (o inventario); quem = o primeiro mod abaixo (a maquina que despeja).
        for (int i = 0; i < Math.min(pilha.length, 15); i++) {
            if (pilha[i].getClassName().equals("net.minecraftforge.items.ItemHandlerHelper")
                    && (pilha[i].getMethodName().equals("insertItemStacked") || pilha[i].getMethodName().equals("insertItem"))) {
                StackTraceElement onde = primeiroDeMod(pilha, 0, i);
                StackTraceElement quem = primeiroDeMod(pilha, i + 1, i + 25);
                String detalhe = (quem == null ? "?" : modDoFrame(quem) + " (" + classeCurta(quem) + ")") + " -> "
                        + (onde == null ? "inventario" : modDoFrame(onde) + " (" + classeCurta(onde) + ")");
                return new Causa(INSERCAO, detalhe);
            }
        }
        // 3. Disco e compressao, pelo topo da pilha.
        for (int i = 0; i < Math.min(pilha.length, 6); i++) {
            String c = pilha[i].getClassName();
            String m = pilha[i].getMethodName();
            if (ABRIR_ARQUIVO.contains(c + "." + m) || (c.equals("java.io.FileOutputStream") && m.startsWith("write"))
                    || (c.equals("java.io.RandomAccessFile") && m.startsWith("write")) || c.equals("sun.nio.ch.FileDispatcherImpl")) {
                return padrao(GRAVACAO_DISCO, pilha, i + 1);
            }
            if (c.startsWith("java.util.zip.Deflater") || c.equals("java.util.zip.GZIPOutputStream")) {
                return padrao(COMPRESSAO, pilha, i + 1);
            }
        }
        // 3. Carregamento de classes.
        for (int i = 0; i < Math.min(pilha.length, 20); i++) {
            if ((pilha[i].getClassName().equals("java.lang.ClassLoader") && pilha[i].getMethodName().startsWith("defineClass"))
                    || pilha[i].getMethodName().equals("<clinit>")) {
                return padrao(CARREGANDO_CLASSES, pilha, i);
            }
        }
        // 4. Mod mais proximo do topo, ate a profundidade maxima.
        StackTraceElement perto = primeiroDeMod(pilha, 0, PROFUNDIDADE_MAXIMA);
        if (perto != null) {
            return new Causa(modDoFrame(perto), perto.getClassName() + "." + perto.getMethodName());
        }
        for (int i = PROFUNDIDADE_MAXIMA; i < pilha.length; i++) {
            if (!mixinIgnorado(pilha[i]) && modDoFrame(pilha[i]) != null) {
                return new Causa(SEM_MOD, "o mod mais proximo, " + modDoFrame(pilha[i]) + ", esta a " + i + " frames do topo");
            }
        }
        return new Causa(SEM_MOD, nomeCurto(pilha[0]));
    }

    /** Os primeiros frames de uma amostra, com os de mod marcados com "*" (para o detalhe na tela). */
    static String resumirPilha(StackTraceElement[] pilha) {
        StringBuilder sb = new StringBuilder();
        int limite = Math.min(pilha.length, 25);
        for (int i = 0; i < limite; i++) {
            String mod = modDoFrame(pilha[i]);
            sb.append(mod != null ? "* " : "  ").append(nomeCurto(pilha[i]));
            if (mod != null) {
                sb.append("  [").append(mod).append(']');
            }
            sb.append('\n');
        }
        if (pilha.length > limite) {
            sb.append("  ... mais ").append(pilha.length - limite).append(" frames\n");
        }
        return sb.toString();
    }

    /** Conta as amostras por causa (mod ou padrao de espera); exemplo = o detalhe de cada uma. */
    static Map<String, Integer> contarPorMod(List<StackTraceElement[]> amostras, Map<String, String> exemplo) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (StackTraceElement[] pilha : amostras) {
            Causa causa = classificar(pilha);
            contagem.merge(causa.chave(), 1, Integer::sum);
            exemplo.putIfAbsent(causa.chave(), causa.detalhe());
        }
        return contagem;
    }

    /** "pehkui 85% (virtuoel...ScaleData.getScale), avaritia 15% (...)", sobre todas as amostras. */
    static String provavelCausa(List<StackTraceElement[]> amostras) {
        if (amostras.isEmpty()) {
            return "sem amostras (travamento curto demais)";
        }
        Map<String, String> exemplo = new HashMap<>();
        Map<String, Integer> contagem = contarPorMod(amostras, exemplo);
        if (contagem.isEmpty()) {
            return "?";
        }
        int total = amostras.size();
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(e -> String.format("%s %d%% (%s)", e.getKey(), Math.round(100.0 * e.getValue() / total),
                        ehPadrao(e.getKey()) ? explicacao(e.getKey()) + (exemplo.get(e.getKey()).isEmpty() ? "" : "; " + exemplo.get(e.getKey()))
                                : exemplo.get(e.getKey())))
                .collect(Collectors.joining(", ")) + String.format(" [%d amostra(s)]", total);
    }

    private static final Set<String> ABRIR_ARQUIVO = Set.of(
            "java.io.FileOutputStream.open0", "java.io.FileInputStream.open0", "java.io.RandomAccessFile.open0",
            "sun.nio.fs.WindowsNativeDispatcher.CreateFile0", "sun.nio.fs.UnixNativeDispatcher.open0");

    /**
     * Classifica as amostras pelo topo da pilha: abrindo arquivo (disco ou antivirus) ou carregando
     * classes (acontece uma vez por classe). Null se nenhuma amostra se encaixa.
     */
    static String tipo(List<StackTraceElement[]> amostras) {
        int arquivo = 0;
        int classes = 0;
        String exemploArquivo = null;
        for (StackTraceElement[] pilha : amostras) {
            if (pilha.length == 0) {
                continue;
            }
            String topo = pilha[0].getClassName() + "." + pilha[0].getMethodName();
            if (ABRIR_ARQUIVO.contains(topo)) {
                arquivo++;
                if (exemploArquivo == null) {
                    for (StackTraceElement f : pilha) {
                        String dono = modDoFrame(f);
                        if (dono != null) {
                            exemploArquivo = dono;
                            break;
                        }
                    }
                }
                continue;
            }
            for (int i = 0; i < Math.min(pilha.length, 20); i++) {
                String c = pilha[i].getClassName();
                String m = pilha[i].getMethodName();
                if ((c.equals("java.lang.ClassLoader") && m.startsWith("defineClass")) || m.equals("<clinit>")) {
                    classes++;
                    break;
                }
            }
        }
        if (arquivo == 0 && classes == 0) {
            return null;
        }
        int total = amostras.size();
        StringBuilder sb = new StringBuilder();
        if (arquivo > 0) {
            sb.append(String.format("%d%% abrindo arquivos para gravar%s (se for frequente, provavelmente o antivirus)",
                    Math.round(100.0 * arquivo / total), exemploArquivo == null ? "" : " (" + exemploArquivo + ")"));
            DiagnosticoAmbiente.arquivoAberto(arquivo * AMOSTRA_MS, "durante um travamento");
        }
        if (classes > 0) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(String.format("%d%% carregando classes (acontece uma vez, normalmente logo depois de entrar no mundo)", Math.round(100.0 * classes / total)));
        }
        return sb.toString();
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
        Map<String, String> exemplos = new HashMap<>();
        Map<String, Integer> porMod = contarPorMod(amostras, exemplos);
        exemplos.forEach(FRAME_POR_MOD::putIfAbsent);
        String principal = porMod.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(SEM_MOD);
        String pilhaResumida = amostras.isEmpty() ? "(sem amostras)" : resumirPilha(amostras.get(0));
        int inicioDimensoes = sb.length();
        String insercoes = Insercao.relatorioDoTravamento();
        if (insercoes != null) {
            sb.append(insercoes);
        }
        String tipo = tipo(amostras);
        if (tipo != null) {
            sb.append("  Tipo: ").append(tipo).append('\n');
        }
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
        String dimensoesTexto = sb.substring(inicioDimensoes).stripTrailing();
        guardar(new Registro(java.time.LocalTime.now(), duracao,
                        t.salvando ? "salvamento" : t.onde.texto, principal, exemplos.getOrDefault(principal, "?"),
                        pilhaResumida, dimensoesTexto.length() > 1500 ? dimensoesTexto.substring(0, 1500) + "..." : dimensoesTexto),
                porMod, amostras.size());
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
        int itens = entidades.getOrDefault("minecraft:item", 0);
        int spawners = blocos.entrySet().stream().filter(e -> e.getKey().contains("spawner")).mapToInt(Map.Entry::getValue).sum();
        DimensoesPesadas.verificar(level.dimension().location().toString(), totalEntidades, itens, spawners);
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
