package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;

import java.util.concurrent.atomic.AtomicLong;

/**
 * salvamento_gradual (como o max-auto-save-chunks-per-tick do Paper): no autosave de 5 em 5 minutos o vanilla
 * transforma em NBT todos os chunks alterados de todas as dimensoes no mesmo tick (a escrita no disco ja e
 * assincrona; a serializacao nao). Aqui o autosave so poe os chunks numa fila por dimensao, e cada tick salva o que
 * couber em salvamento_gradual.orcamento_ms.
 * <p>
 * So o autosave (saveEverything sem log, sem flush, nao forcado) e com o jogo rodando. Fechar o mundo, /save-all,
 * /save-all flush e o salvamento ao pausar o singleplayer (sem ticks depois dele) continuam completos e imediatos,
 * e um salvamento completo esvazia a fila.
 */
public final class SalvamentoGradual {
    /** Implementado pelo mixin no ChunkMap: a fila fica no proprio mapa de chunks. */
    public interface MapaGradual {
        void hlotimizacao$enfileirar();

        void hlotimizacao$limparFila();

        /** Salva da fila ate o horario (nanoTime); devolve quantos chunks foram salvos. */
        int hlotimizacao$passo(long ateNs);

        int hlotimizacao$pendentes();
    }

    private static boolean autosave;
    public static final AtomicLong AUTOSAVES = new AtomicLong();
    public static final AtomicLong CHUNKS = new AtomicLong();
    private static volatile long maiorPassoMs;

    private SalvamentoGradual() {
    }

    public static void inicioSaveEverything(MinecraftServer server, boolean semLog, boolean flush, boolean forcado) {
        // O salvamento ao pausar o singleplayer chama saveEverything(false, ...): com log, nunca entra aqui.
        autosave = semLog && !flush && !forcado && server.isRunning();
        if (autosave) {
            AUTOSAVES.incrementAndGet();
        }
    }

    public static void fimSaveEverything() {
        autosave = false;
    }

    /** Chamado no ChunkMap.saveAllChunks: true = so enfileirou (o vanilla nao roda). */
    public static boolean interceptar(MapaGradual mapa, boolean flush) {
        if (flush || !autosave || ModoSeguro.desligado(com.hlotimizacao.config.Modulo.SALVAMENTO_GRADUAL)) {
            mapa.hlotimizacao$limparFila(); // salvamento completo: tudo vai ser salvo agora
            return false;
        }
        mapa.hlotimizacao$enfileirar();
        return true;
    }

    public static void aoTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long inicio = System.nanoTime();
        long ate = inicio + Config.salvamentoGradualOrcamentoMs() * 1_000_000L;
        int salvos = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            MapaGradual mapa = (MapaGradual) level.getChunkSource().chunkMap;
            if (mapa.hlotimizacao$pendentes() == 0) {
                continue;
            }
            salvos += mapa.hlotimizacao$passo(ate);
            if (System.nanoTime() >= ate) {
                break;
            }
        }
        if (salvos > 0) {
            CHUNKS.addAndGet(salvos);
            maiorPassoMs = Math.max(maiorPassoMs, (System.nanoTime() - inicio) / 1_000_000);
        }
    }

    public static int pendentes(MinecraftServer server) {
        int n = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().chunkMap instanceof MapaGradual mapa) { // sem o modulo o mixin nao e aplicado
                n += mapa.hlotimizacao$pendentes();
            }
        }
        return n;
    }

    public static long maiorPassoMs() {
        return maiorPassoMs;
    }
}
