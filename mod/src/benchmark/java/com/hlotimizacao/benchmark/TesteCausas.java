package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.DiagnosticoLag;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.List;

/** Pilhas sinteticas para conferir a "provavel causa" do diagnostico_lag em cada caso. */
final class TesteCausas {
    private TesteCausas() {
    }

    private static StackTraceElement f(String classe, String metodo) {
        return new StackTraceElement(classe, metodo, null, -1);
    }

    private static StackTraceElement[] pilha(StackTraceElement... frames) {
        return frames;
    }

    private record Caso(String nome, StackTraceElement[] pilha, String esperado, String detalheContem) {
    }

    static int verificar(CommandContext<CommandSourceStack> ctx) {
        List<StackTraceElement> tickJogador = List.of(
                f("net.minecraft.world.entity.LivingEntity", "baseTick"),
                f("net.minecraft.world.entity.LivingEntity", "tick"),
                f("net.minecraft.world.entity.player.Player", "tick"),
                f("net.minecraft.server.level.ServerPlayer", "doTick"),
                f("net.minecraft.server.network.ServerGamePacketListenerImpl", "redirect$dei000$chrono$scalePlayerGameplayTick"),
                f("net.minecraft.server.network.ServerGamePacketListenerImpl", "tick"),
                f("net.minecraft.network.Connection", "tick"),
                f("net.minecraft.server.MinecraftServer", "tickChildren"));
        List<Caso> casos = new ArrayList<>();

        // 1. O caso do log das 08:59:47: esperando chunk depois de teleportar, Chrono no fundo.
        List<StackTraceElement> espera = new ArrayList<>(List.of(
                f("jdk.internal.misc.Unsafe", "park"),
                f("java.util.concurrent.locks.LockSupport", "parkNanos"),
                f("net.minecraft.util.thread.BlockableEventLoop", "managedBlock"),
                f("net.minecraft.server.level.ServerChunkCache", "getChunk"),
                f("net.minecraft.world.level.Level", "getChunk"),
                f("net.minecraft.world.level.Level", "getBlockState"),
                f("net.minecraft.world.entity.Entity", "handler$zcb000$shetiphiancore$getLandingPos"),
                f("net.minecraft.world.entity.Entity", "getOnPos")));
        espera.addAll(tickJogador);
        casos.add(new Caso("espera de chunk com Chrono no fundo", espera.toArray(new StackTraceElement[0]),
                DiagnosticoLag.CHUNK_SINCRONO, "shetiphiancore"));

        // 2. Os mesmos nomes de producao (SRG).
        casos.add(new Caso("espera de chunk (nomes SRG)", pilha(
                f("java.util.concurrent.locks.LockSupport", "parkNanos"),
                f("net.minecraft.util.thread.BlockableEventLoop", "m_18701_"),
                f("net.minecraft.server.level.ServerChunkCache", "m_7587_"),
                f("net.minecraft.world.level.Level", "m_6325_")), DiagnosticoLag.CHUNK_SINCRONO, ""));

        // 3. Tick de jogador normal, sem espera: o Chrono so repassa e nao pode ser o culpado.
        List<StackTraceElement> soChrono = new ArrayList<>(List.of(f("net.minecraft.world.entity.Entity", "move")));
        soChrono.addAll(tickJogador);
        casos.add(new Caso("Chrono de passagem", soChrono.toArray(new StackTraceElement[0]), DiagnosticoLag.SEM_MOD, ""));

        // 4. Gravacao em disco (ConduitSavedData do EnderIO).
        casos.add(new Caso("gravacao em disco", pilha(
                f("java.io.FileOutputStream", "open0"),
                f("java.io.FileOutputStream", "open"),
                f("java.io.FileOutputStream", "<init>"),
                f("net.minecraft.nbt.NbtIo", "writeCompressed"),
                f("net.minecraft.world.level.saveddata.SavedData", "save"),
                f("net.minecraft.world.level.storage.DimensionDataStorage", "handler$zza000$enderio$saveConduits"),
                f("net.minecraft.server.level.ServerLevel", "save")), DiagnosticoLag.GRAVACAO_DISCO, "enderio"));

        // 5. Compressao ao salvar.
        casos.add(new Caso("compressao", pilha(
                f("java.util.zip.Deflater", "deflateBytesBytes"),
                f("java.util.zip.Deflater", "deflate"),
                f("java.util.zip.DeflaterOutputStream", "deflate"),
                f("java.util.zip.GZIPOutputStream", "write"),
                f("net.minecraft.nbt.NbtIo", "writeCompressed")), DiagnosticoLag.COMPRESSAO, ""));

        // 6. Carregamento de classes.
        casos.add(new Caso("carregamento de classes", pilha(
                f("java.lang.ClassLoader", "defineClass1"),
                f("java.lang.ClassLoader", "defineClass"),
                f("cpw.mods.cl.ModuleClassLoader", "loadClass"),
                f("net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase", "handler$zzz000$canary$initFlags")),
                DiagnosticoLag.CARREGANDO_CLASSES, "canary"));

        // 7. Mod de verdade no topo.
        casos.add(new Caso("mod real no topo", pilha(
                f("net.minecraft.world.entity.Entity", "handler$zab000$pehkui$getScale"),
                f("net.minecraft.world.entity.Entity", "getBbWidth"),
                f("net.minecraft.world.entity.LivingEntity", "tick")), "pehkui", "getScale"));

        // 8. Mod fundo demais (so embrulha a chamada).
        List<StackTraceElement> fundo = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            fundo.add(f("net.minecraft.world.level.Level", "metodo" + i));
        }
        fundo.add(f("net.minecraft.server.MinecraftServer", "handler$zzc000$algummod$tickServer"));
        casos.add(new Caso("mod a 15 frames", fundo.toArray(new StackTraceElement[0]), DiagnosticoLag.SEM_MOD, "algummod, esta a 15 frames"));

        int falhas = 0;
        for (Caso caso : casos) {
            DiagnosticoLag.Causa causa = DiagnosticoLag.classificar(caso.pilha());
            boolean ok = causa.chave().equals(caso.esperado()) && causa.detalhe().contains(caso.detalheContem());
            if (!ok) {
                falhas++;
            }
            HLBench.LOGGER.info("HLBENCH causa {} [{}] -> {} ({})", ok ? "ok" : "FALHA", caso.nome(), causa.chave(), causa.detalhe());
        }
        HLBench.LOGGER.info("HLBENCH causas casos={} falhas={}", casos.size(), falhas);
        return 1;
    }
}
