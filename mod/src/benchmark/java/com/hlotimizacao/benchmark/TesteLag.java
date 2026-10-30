package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.GravadorAssincrono;
import com.hlotimizacao.recursos.VerificadorMods;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/** Testes de diagnostico_lag, do verificador de nomes e da espera ao desligar. */
final class TesteLag {
    private TesteLag() {
    }

    /** Trava o tick do servidor por N ms. */
    static int travar(CommandContext<CommandSourceStack> ctx) {
        int ms = IntegerArgumentType.getInteger(ctx, "ms");
        long fim = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < fim) {
            Thread.onSpinWait();
        }
        HLBench.LOGGER.info("HLBENCH travar ms={}", ms);
        return 1;
    }

    /** Gera chunks nunca visitados de forma sincrona na thread do servidor (como alguns mods fazem). */
    static int gerar(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int raio = IntegerArgumentType.getInteger(ctx, "raio");
        int base = 6000 + new Random().nextInt(2000); // longe do spawn, cada execucao num lugar novo
        long inicio = System.nanoTime();
        int n = 0;
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                level.getChunkSource().getChunk(base + x, base + z, ChunkStatus.FULL, true);
                n++;
            }
        }
        HLBench.LOGGER.info("HLBENCH gerar chunks={} em_ms={}", n, (System.nanoTime() - inicio) / 1_000_000);
        return 1;
    }

    /** Nomes reais de arquivos do pack HL (amostra, com os casos dificeis). */
    private static final List<String> NOMES = List.of(
            "MergeEnchantments-FORGE-1.20.[0-4]-v4.3.jar",
            "ImmediatelyFast-Forge-1.5.5+1.20.4.jar",
            "gamemenuremovegfarb-forge-mc1.19.4-2.1.2.jar",
            "trashcans-1.1.2-forge-mc1.20.4.jar",
            "GPUTape-1.18x-1.21x-1.0.5.1-forge.jar",
            "packetfixer-3.3.2-1.18-1.20.4-merged.jar",
            "dynamic-fps-3.11.4+minecraft-1.20.0-forge.jar",
            "bellsandwhistles-0.4.5-1.20.x-Create6.0+.jar",
            "durabilitytooltip-1.2.0-forge-mc1.20.jar",
            "Icterine-forge-1.20.0-1-1.3.0.jar",
            "fixexperiencebug-1.20-46.2.2.jar",
            "fuelgoeshere-1.20.0-1.0.1.jar",
            "konkrete_forge_1.8.0_MC_1.20-1.20.1.jar",
            "Towns-and-Towers-1.12-Fabric+Forge.jar",
            "mekanismcovers-1.3-BETA+1.20.jar",
            "tlc_forge-1.0.3-R-1.20.X.jar",
            "ExtendedAE-1.20-1.4.22-forge.jar",
            "Apotheosis-1.20.1-7.4.8.jar",
            "embeddium-0.3.31+mc1.20.1.jar",
            "Ksyxis-1.4.6.jar",
            "AkashicTome-1.7-27.jar",
            "kubejs-forge-2001.6.5-build.26.jar",
            "Chunky-1.3.146.jar",
            "end_enchanting_table-1.1.2.jar",
            "DungeonsArise-1.20.x-2.1.58-release.jar",
            "goblintraders-forge-1.20.1-1.11.5.jar");

    static int nomes(CommandContext<CommandSourceStack> ctx) {
        for (String nome : NOMES) {
            VerificadorMods.AnaliseNome a = VerificadorMods.analisarNome(nome);
            HLBench.LOGGER.info("HLBENCH nome {} -> menciona={} compativel={} versoes={}", nome, a.mencionaVersao(), a.compativel(), a.versoes());
        }
        return 1;
    }

    /** Enfileira N gravacoes grandes e chama a rotina que o shutdown hook executa. */
    static int pendentes(CommandContext<CommandSourceStack> ctx) {
        int n = IntegerArgumentType.getInteger(ctx, "quantidade");
        int mb = IntegerArgumentType.getInteger(ctx, "mb");
        Path pasta = ctx.getSource().getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        Random random = new Random(1);
        for (int i = 0; i < n; i++) {
            long[] dados = new long[mb * 1024 * 1024 / 8];
            for (int j = 0; j < dados.length; j++) {
                dados[j] = random.nextLong();
            }
            CompoundTag tag = new CompoundTag();
            tag.put("dados", new LongArrayTag(dados));
            GravadorAssincrono.gravar(tag, pasta.resolve("hlbench_pendente_" + i + ".dat"), "hlbench_pendente_" + i, () -> {
            });
        }
        int naFila = GravadorAssincrono.pendentes();
        long inicio = System.nanoTime();
        int restantes = GravadorAssincrono.aoDesligar();
        HLBench.LOGGER.info("HLBENCH pendentes enfileiradas={} na_fila_ao_desligar={} restantes_depois={} espera_ms={}",
                n, naFila, restantes, (System.nanoTime() - inicio) / 1_000_000);
        return 1;
    }
}
