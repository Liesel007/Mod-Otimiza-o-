package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.SalvamentoSeletivo;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.io.File;

/**
 * 0.5.0: salvamento_dimensoes_vazias. Um dado de mod "sempre alterado" com save(File) proprio que leva 150 ms (como o
 * ConduitSavedData do Ender IO) em cada dimensao; salva tres vezes pelo caminho da pausa e mede. Depois muda o dado de
 * verdade numa dimensao sem jogador: o fechamento do mundo tem que grava-lo.
 */
final class TesteDimensoes {
    static final class SempreSujo extends SavedData {
        int valor;
        final String dimensao;

        SempreSujo(String dimensao) {
            this.dimensao = dimensao;
        }

        @Override
        public boolean isDirty() {
            return true; // como o ConduitSavedData: sempre marcado como alterado
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("valor", valor);
            return tag;
        }

        @Override
        public void save(File arquivo) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignorado) {
                Thread.currentThread().interrupt();
            }
            super.save(arquivo);
            HLBench.LOGGER.info("HLBENCH dimensoes gravou dim={} valor={}", dimensao, valor);
        }
    }

    private TesteDimensoes() {
    }

    private static SempreSujo dados(ServerLevel level) {
        String dim = level.dimension().location().toString();
        return level.getDataStorage().computeIfAbsent(t -> {
            SempreSujo d = new SempreSujo(dim);
            d.valor = t.getInt("valor");
            return d;
        }, () -> new SempreSujo(dim), "hlbench_sempre_sujo");
    }

    static int pausa(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int dims = 0;
        for (ServerLevel level : server.getAllLevels()) {
            dados(level);
            dims++;
        }
        for (int i = 1; i <= 3; i++) {
            long t0 = System.nanoTime();
            server.saveEverything(false, false, false); // o mesmo caminho do "Saving and pausing game"
            long ms = (System.nanoTime() - t0) / 1_000_000;
            HLBench.LOGGER.info("HLBENCH dimensoes pausa {} de {} dims: {} ms · {} · {}", i, dims, ms, SalvamentoSeletivo.dimensoes(), SalvamentoSeletivo.porClasse());
        }
        return 1;
    }

    static int mudar(CommandContext<CommandSourceStack> ctx) {
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            if (level.dimension() == net.minecraft.world.level.Level.END) {
                SempreSujo d = dados(level);
                d.valor = 4242;
                HLBench.LOGGER.info("HLBENCH dimensoes mudou dim=end valor=4242 jogadores={} chunks={}", level.players().size(),
                        level.getChunkSource().getLoadedChunksCount());
            }
        }
        return 1;
    }
}
