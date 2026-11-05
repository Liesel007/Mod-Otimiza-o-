package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.SalvamentoGradual;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.4.0: salvamento_gradual. Marca como alterados todos os chunks carregados perto do spawn, chama o autosave pelo
 * mesmo caminho do vanilla (saveEverything(true, false, false)) e mede o tick do autosave; depois espera a fila
 * esvaziar e confere que nenhum chunk ficou sem salvar.
 */
final class TesteSalvamentoGradual {
    private static final List<LevelChunk> MARCADOS = new ArrayList<>();
    private static boolean ouvindo;
    private static long maiorTickMs;
    private static int ticksEsperando;
    private static long inicioTickNs;
    private static int filaVaziaEm = -1;
    private static long semSalvarNaHora;

    private TesteSalvamentoGradual() {
    }

    static int rodar(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = server.overworld();
        ChunkPos spawn = new ChunkPos(level.getSharedSpawnPos());
        MARCADOS.clear();
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                LevelChunk c = level.getChunkSource().getChunkNow(spawn.x + dx, spawn.z + dz);
                if (c != null) {
                    c.setUnsaved(true);
                    MARCADOS.add(c);
                }
            }
        }
        // So no teste: zera o intervalo de 10 s entre gravacoes do mesmo chunk, para os dois modos salvarem tudo.
        try {
            var campo = net.minecraft.server.level.ChunkMap.class.getDeclaredField("chunkSaveCooldowns");
            campo.setAccessible(true);
            ((it.unimi.dsi.fastutil.longs.Long2LongMap) campo.get(level.getChunkSource().chunkMap)).clear();
        } catch (ReflectiveOperationException e) {
            HLBench.LOGGER.warn("HLBENCH gradual sem acesso aos cooldowns", e);
        }
        long t0 = System.nanoTime();
        server.saveEverything(true, false, false);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        int pendentes = SalvamentoGradual.pendentes(server);
        HLBench.LOGGER.info("HLBENCH gradual chunks_alterados={} autosave_ms={} na_fila={}", MARCADOS.size(), ms, pendentes);
        maiorTickMs = 0;
        ticksEsperando = 0;
        filaVaziaEm = -1;
        inicioTickNs = 0;
        if (!ouvindo) {
            ouvindo = true;
            MinecraftForge.EVENT_BUS.addListener(TesteSalvamentoGradual::aoTick);
        }
        return 1;
    }

    private static void aoTick(TickEvent.ServerTickEvent event) {
        if (MARCADOS.isEmpty()) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            inicioTickNs = System.nanoTime();
            return;
        }
        if (inicioTickNs == 0) {
            return;
        }
        ticksEsperando++;
        if (filaVaziaEm < 0) {
            maiorTickMs = Math.max(maiorTickMs, (System.nanoTime() - inicioTickNs) / 1_000_000);
            if (SalvamentoGradual.pendentes(event.getServer()) == 0) {
                filaVaziaEm = ticksEsperando;
                semSalvarNaHora = MARCADOS.stream().filter(LevelChunk::isUnsaved).count();
            }
        } else if (ticksEsperando == filaVaziaEm + 300) { // 15 s: o salvamento aos poucos do proprio vanilla pega o resto
            long semSalvar = MARCADOS.stream().filter(LevelChunk::isUnsaved).count();
            HLBench.LOGGER.info("HLBENCH gradual fila_vazia_em_ticks={} maior_tick_durante_fila_ms={} sem_salvar_quando_esvaziou={} sem_salvar_15s_depois={} maior_passo_ms={}",
                    filaVaziaEm, maiorTickMs, semSalvarNaHora, semSalvar, SalvamentoGradual.maiorPassoMs());
            MARCADOS.clear();
        }
    }
}
