package com.hlotimizacao.benchmark;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.world.level.saveddata.SavedData;

import java.io.File;

/** Testes da 0.1.3: pausa simulada, travamento entre ticks, salvamento lento com save(File) proprio. */
final class TesteLag013 {
    private TesteLag013() {
    }

    private static void girar(long ms) {
        long fim = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < fim) {
            Thread.onSpinWait();
        }
    }

    /** Enfileira para rodar entre ticks (execute() rodaria na hora, dentro do tick atual). */
    private static void entreTicks(MinecraftServer server, Runnable tarefa) {
        server.tell(new TickTask(server.getTickCount(), tarefa));
    }

    /**
     * Simula o jogo pausado: entre ticks, a thread do servidor fica parada (como esperando o proximo
     * tick) por idle_ms e depois trabalha por busy_ms (como o salvamento ao pausar ou tarefas de chunk).
     */
    static int pausa(CommandContext<CommandSourceStack> ctx) {
        int parado = IntegerArgumentType.getInteger(ctx, "parado_ms");
        int ocupado = IntegerArgumentType.getInteger(ctx, "ocupado_ms");
        entreTicks(ctx.getSource().getServer(), () -> {
            try {
                Thread.sleep(parado);
            } catch (InterruptedException ignorado) {
                Thread.currentThread().interrupt();
            }
            girar(ocupado);
            HLBench.LOGGER.info("HLBENCH pausa parado_ms={} ocupado_ms={}", parado, ocupado);
        });
        return 1;
    }

    /** Trabalho pesado entre ticks (tarefa executada enquanto o servidor espera o proximo tick). */
    static int ocupado(CommandContext<CommandSourceStack> ctx) {
        int ms = IntegerArgumentType.getInteger(ctx, "ms");
        entreTicks(ctx.getSource().getServer(), () -> {
            girar(ms);
            HLBench.LOGGER.info("HLBENCH ocupado ms={}", ms);
        });
        return 1;
    }

    static volatile int lentoMs;

    /** SavedData com save(File) proprio e lento (como o ConduitSavedData do EnderIO). */
    public static final class DadosLentos extends SavedData {
        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("t", System.currentTimeMillis());
            return tag;
        }

        @Override
        public void save(File arquivo) {
            girar(lentoMs);
            super.save(arquivo);
        }
    }

    /** save(File) proprio que so chama o do Minecraft: pode ir para a lista serializar_classes. */
    public static final class DadosPermitidos extends SavedData {
        static volatile int chamadasSaveArquivo;

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("t", System.currentTimeMillis());
            return tag;
        }

        @Override
        public void save(File arquivo) {
            chamadasSaveArquivo++;
            super.save(arquivo);
        }
    }

    static int salvarLento(CommandContext<CommandSourceStack> ctx) {
        lentoMs = IntegerArgumentType.getInteger(ctx, "ms");
        MinecraftServer server = ctx.getSource().getServer();
        var storage = server.overworld().getDataStorage();
        storage.computeIfAbsent(t -> new DadosLentos(), DadosLentos::new, "hlbench_lento").setDirty();
        storage.computeIfAbsent(t -> new DadosPermitidos(), DadosPermitidos::new, "hlbench_permitido").setDirty();
        HLBench.LOGGER.info("HLBENCH salvarlento preparado ms={}", lentoMs);
        return 1;
    }

    static int conferirPermitido(CommandContext<CommandSourceStack> ctx) {
        File arquivo = ctx.getSource().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("hlbench_permitido.dat").toFile();
        HLBench.LOGGER.info("HLBENCH permitido chamadas_save_file={} arquivo_existe={}", DadosPermitidos.chamadasSaveArquivo, arquivo.exists());
        return 1;
    }
}
