package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.mixin.lag.ChunkMapAccessor;
import com.hlotimizacao.mixin.lag.LevelAccessor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;

/**
 * Registra travamentos do servidor.
 *
 * Uma thread vigia o tempo do tick atual (e o tempo parado entre ticks, quando o servidor executa
 * tarefas de chunk enquanto espera o proximo tick). Se passar do limite, ela registra a pilha da
 * thread do servidor enquanto ele ainda esta travado: isso mostra qual codigo esta segurando o tick.
 * Ler a pilha de outra thread e seguro; o estado do mundo, nao. Por isso o relatorio com as
 * contagens e feito depois, na propria thread do servidor, quando o tick termina.
 *
 * No maximo um registro a cada diagnostico_lag.intervalo_s segundos.
 */
public final class DiagnosticoLag {
    private static final int FRAMES_NA_PILHA = 45;
    private static final int TOP = 5;

    private static volatile Thread threadServidor;
    private static volatile long inicioTickNs;
    private static volatile long fimTickNs;
    private static volatile boolean emTick;
    /** Quando foi o ultimo registro (ms), para o limite de frequencia. */
    private static volatile long ultimoRegistroMs;
    /** Ja registrou a pilha deste travamento? Evita repetir enquanto o mesmo travamento continua. */
    private static volatile boolean pilhaRegistrada;
    /** Este travamento deve ganhar relatorio completo quando acabar. */
    private static volatile boolean relatorioPendente;
    private static volatile String descricaoPendente = "";
    private static Thread vigia;
    /**
     * Chunks que terminaram de carregar desde o fim do tick anterior, por dimensao:
     * [0] = todos, [1] = gerados agora (chunks novos).
     */
    private static final Map<String, AtomicIntegerArray> CHUNKS_DESDE_ULTIMO_TICK = new ConcurrentHashMap<>();
    /** Amostras seguidas em que a thread do servidor estava executando fora de um tick. */
    private static int amostrasOcupadoEntreTicks;

    private DiagnosticoLag() {
    }

    public static synchronized void iniciar() {
        if (vigia != null) {
            return;
        }
        vigia = new Thread(DiagnosticoLag::vigiar, "HL Otimizacao - diagnostico de lag");
        vigia.setDaemon(true);
        vigia.start();
        Config.LOGGER.info("[diagnostico_lag] Ativo: registra ticks acima de {} ms (no maximo 1 registro a cada {} s)",
                Config.lagLimiteMs(), Config.lagIntervaloS());
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
            if (relatorioPendente && !emTick && fimTickNs != 0) {
                // O travamento foi entre ticks (tarefas de chunk na espera do proximo tick).
                relatorio(event.getServer(), (agora - fimTickNs) / 1_000_000, "entre ticks");
            }
            inicioTickNs = agora;
            emTick = true;
        } else {
            emTick = false;
            fimTickNs = agora;
            long duracaoMs = (agora - inicioTickNs) / 1_000_000;
            if (relatorioPendente || (duracaoMs >= Config.lagLimiteMs() && podeRegistrar())) {
                relatorio(event.getServer(), duracaoMs, "dentro do tick");
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
                Thread.sleep(250);
                Thread servidor = threadServidor;
                if (servidor == null || !servidor.isAlive()) {
                    continue;
                }
                long referencia = emTick ? inicioTickNs : fimTickNs;
                if (referencia == 0) {
                    continue;
                }
                long paradoMs = (System.nanoTime() - referencia) / 1_000_000;
                if (paradoMs < Config.lagLimiteMs()) {
                    pilhaRegistrada = false;
                    amostrasOcupadoEntreTicks = 0;
                    continue;
                }
                if (!emTick) {
                    // Fora do tick, a thread so esta travada se estiver trabalhando. Parada esperando
                    // (jogo pausado no singleplayer, servidor ocioso) nao conta.
                    amostrasOcupadoEntreTicks = servidor.getState() == Thread.State.RUNNABLE ? amostrasOcupadoEntreTicks + 1 : 0;
                    if (amostrasOcupadoEntreTicks < 3) {
                        continue;
                    }
                }
                if (pilhaRegistrada || relatorioPendente || !podeRegistrar()) {
                    continue;
                }
                pilhaRegistrada = true;
                relatorioPendente = true;
                ultimoRegistroMs = System.currentTimeMillis();
                descricaoPendente = emTick ? "dentro do tick" : "entre ticks";
                Config.LOGGER.warn("[diagnostico_lag] Servidor travado ha {} ms ({}). Onde a thread do servidor esta agora:\n{}",
                        paradoMs, descricaoPendente, pilha(servidor));
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                Config.LOGGER.debug("[diagnostico_lag] Erro na thread vigia", t);
            }
        }
    }

    private static String pilha(Thread thread) {
        StackTraceElement[] frames = thread.getStackTrace();
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

    /** Roda na thread do servidor. */
    private static void relatorio(MinecraftServer server, long duracaoMs, String onde) {
        relatorioPendente = false;
        ultimoRegistroMs = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[diagnostico_lag] Tick lento: %d ms (%s). Estado por dimensao:%n", duracaoMs, onde));
        for (ServerLevel level : server.getAllLevels()) {
            try {
                int total = 0;
                int prontos = 0;
                for (ChunkHolder holder : ((ChunkMapAccessor) level.getChunkSource().chunkMap).hlotimizacao$getChunks()) {
                    total++;
                    if (holder.getFullChunk() != null) {
                        prontos++;
                    }
                }
                AtomicIntegerArray noTick = CHUNKS_DESDE_ULTIMO_TICK.getOrDefault(level.dimension().location().toString(), new AtomicIntegerArray(2));
                Map<String, Integer> entidades = new HashMap<>();
                int totalEntidades = 0;
                for (Entity entidade : level.getAllEntities()) {
                    totalEntidades++;
                    entidades.merge(idEntidade(entidade.getType()), 1, Integer::sum);
                }
                Map<String, Integer> blocos = new HashMap<>();
                List<TickingBlockEntity> tickers = ((LevelAccessor) level).hlotimizacao$blockEntityTickers();
                for (TickingBlockEntity ticker : tickers) {
                    blocos.merge(ticker.getType(), 1, Integer::sum);
                }
                sb.append(String.format("  %s: jogadores=%d, chunks prontos=%d, chunks parciais (borda ou em geracao)=%d, tarefas pendentes=%d%n",
                        level.dimension().location(), level.players().size(), prontos, total - prontos, level.getChunkSource().getPendingTasksCount()));
                sb.append(String.format("    durante o travamento: %d chunks carregados, dos quais %d gerados agora (novos)%n",
                        noTick.get(0), noTick.get(1)));
                sb.append(String.format("    entidades=%d, mais comuns: %s%n", totalEntidades, top(entidades)));
                sb.append(String.format("    block entities que tickam=%d, mais comuns: %s%n", tickers.size(), top(blocos)));
            } catch (Throwable t) {
                sb.append("  ").append(level.dimension().location()).append(": erro ao coletar (").append(t).append(")\n");
            }
        }
        Config.LOGGER.warn(sb.toString().stripTrailing());
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
