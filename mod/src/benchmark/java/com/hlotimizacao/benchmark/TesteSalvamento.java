package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.GravadorAssincrono;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Testes do modulo salvamento_assincrono:
 * - nome que o java.nio rejeita (NUL no Linux, ":" no Windows) vai para o save vanilla, sem excecao
 *   na thread de gravacao;
 * - falha na gravacao assincrona remarca o dado como sujo, e o proximo save grava;
 * - nome normal continua indo pela gravacao assincrona.
 */
final class TesteSalvamento {
    static final String NORMAL = "hlbench_normal";
    static final String FALHA = "hlbench_falha";
    static final String INVALIDO = "hlbench\u0000invalido";

    private TesteSalvamento() {
    }

    static final class Dados extends SavedData {
        int valor;

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("valor", valor);
            return tag;
        }
    }

    private static Dados obter(DimensionDataStorage storage, String nome) {
        return storage.computeIfAbsent(tag -> {
            Dados d = new Dados();
            d.valor = tag.getInt("valor");
            return d;
        }, Dados::new, nome);
    }

    private static Path pastaDados(ServerLevel level) {
        return level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
    }

    /** Passo 1: suja os tres dados, cria o obstaculo para a gravacao falhar e salva. */
    static int preparar(CommandContext<CommandSourceStack> ctx) {
        try {
            return prepararIo(ctx);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static int prepararIo(CommandContext<CommandSourceStack> ctx) throws IOException {
        ServerLevel level = ctx.getSource().getServer().overworld();
        DimensionDataStorage storage = level.getDataStorage();
        Path pasta = pastaDados(level);
        // Uma pasta nao vazia no lugar do arquivo: o move final da gravacao assincrona falha.
        Path obstaculo = pasta.resolve(FALHA + ".dat");
        Files.createDirectories(obstaculo);
        Files.writeString(obstaculo.resolve("bloqueio.txt"), "x");

        for (String nome : new String[]{NORMAL, FALHA, INVALIDO}) {
            Dados d = obter(storage, nome);
            d.valor = 111;
            d.setDirty();
        }
        storage.save();
        HLBench.LOGGER.info("HLBENCH salvamento passo1 sujo_logo_apos_save normal={} falha={} invalido={}",
                obter(storage, NORMAL).isDirty(), obter(storage, FALHA).isDirty(), obter(storage, INVALIDO).isDirty());
        GravadorAssincrono.esperarPendentes();
        return 1;
    }

    /** Passo 2 (ticks depois): a falha deve ter remarcado o dado; tira o obstaculo e salva de novo. */
    static int verificar(CommandContext<CommandSourceStack> ctx) {
        try {
            return verificarIo(ctx);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static int verificarIo(CommandContext<CommandSourceStack> ctx) throws IOException {
        ServerLevel level = ctx.getSource().getServer().overworld();
        DimensionDataStorage storage = level.getDataStorage();
        Path pasta = pastaDados(level);
        boolean falhaRemarcada = obter(storage, FALHA).isDirty();
        boolean normalGravado = Files.isRegularFile(pasta.resolve(NORMAL + ".dat"));

        Path obstaculo = pasta.resolve(FALHA + ".dat");
        Files.deleteIfExists(obstaculo.resolve("bloqueio.txt"));
        Files.deleteIfExists(pasta.resolve(FALHA + ".dat.hltmp"));
        Files.deleteIfExists(obstaculo);
        storage.save();
        GravadorAssincrono.esperarPendentes();

        boolean falhaGravadaDepois = Files.isRegularFile(obstaculo);
        long temporarios;
        try (var arquivos = Files.list(pasta)) {
            temporarios = arquivos.filter(p -> p.getFileName().toString().endsWith(".hltmp")).count();
        }
        HLBench.LOGGER.info("HLBENCH salvamento passo2 falha_remarcada_como_sujo={} normal_gravado={} falha_gravada_no_save_seguinte={} sujo_apos_regravar={} temporarios_sobrando={}",
                falhaRemarcada, normalGravado, falhaGravadaDepois, obter(storage, FALHA).isDirty(), temporarios);
        return 1;
    }
}
