package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.Memoria;
import com.hlotimizacao.recursos.RelatorioInicializacao;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 0.5.0: conta da memoria sugerida com os numeros da sessao de 15/11 e leitura de um log de abertura de exemplo. */
final class TesteMemoria {
    private TesteMemoria() {
    }

    static int memoria(CommandContext<CommandSourceStack> ctx) {
        long vivo = 3725;
        long ram = 12119;
        HLBench.LOGGER.info("HLBENCH memoria vivo={} ram={} igpu=true necessidade={} sugerido={} teto={} no_limite={} (esperado: sugerido 6144 = 6 GB, fora do limite)",
                vivo, ram, Memoria.necessidadeMb(vivo), Memoria.sugeridoMb(vivo), Memoria.tetoMb(ram, true), Memoria.noLimite(vivo, ram, true));
        HLBench.LOGGER.info("HLBENCH memoria casos: vivo 1500 -> {} (minimo 4 GB); vivo 7000 ram 12119 igpu -> no_limite={}; vivo 3725 sem igpu teto={}",
                Memoria.sugeridoMb(1500), Memoria.noLimite(7000, ram, true), Memoria.tetoMb(ram, false));
        HLBench.LOGGER.info("HLBENCH memoria sessao_atual: {}", Memoria.resumo());
        return 1;
    }

    static int inicializacao(CommandContext<CommandSourceStack> ctx) {
        try {
            return inicializacaoIo();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static int inicializacaoIo() throws java.io.IOException {
        Path log = Files.createTempFile("hlo_log_exemplo", ".log");
        Files.write(log, List.of(
                "[21:44:01] [Render thread/WARN] [ModernFix/]: Game took 211.2 seconds to start",
                "[21:43:10] [Worker-ResourceReload-3/WARN] [ModernFix/]: Posting dynamic ModelEvent.ModifyBakingResult to mods took 22.51 s, breakdown below:",
                "[21:43:10] [Worker-ResourceReload-3/WARN] [ModernFix/]:     lantea: 16.27 s",
                "[21:43:10] [Worker-ResourceReload-3/WARN] [ModernFix/]:     framedblocks: 1.602 s",
                "[21:43:10] [Worker-ResourceReload-3/WARN] [ModernFix/]:     modernfix: 1.411 s",
                "[21:43:10] [Worker-ResourceReload-3/WARN] [ModernFix/]:     ae2: 312.4 ms",
                "[21:43:11] [Render thread/INFO] [minecraft/Minecraft]: Reloading ResourceManager: Default",
                "[21:56:20] [Render thread/INFO] [mezz.jei.library.load.PluginCaller/]: Registering recipes: jei:minecraft took 17.91 seconds",
                "[21:56:24] [Render thread/INFO] [mezz.jei.library.load.PluginCaller/]: Registering recipes: industrialforegoing:default took 3.812 seconds",
                "[21:56:28] [Render thread/INFO] [mezz.jei.library.load.PluginCaller/]: Registering recipes: create:jei_plugin took 3.799 seconds",
                "[21:56:30] [Render thread/INFO] [mezz.jei.library.load.PluginCaller/]: Registering recipes took 35.20 seconds",
                "[21:56:45] [Render thread/INFO] [mezz.jei.library.ingredients.IngredientFilter/]: Building ingredient filter took 14.95 seconds",
                "[21:57:00] [Render thread/INFO] [mezz.jei.common.util.LoggedTimer/]: Starting JEI took 1.490 minutes",
                "[21:57:20] [Render thread/WARN] [ModernFix/]: Time from main menu to in-game was 132.0 seconds",
                "[21:57:20] [Render thread/WARN] [ModernFix/]: Total time to load game and open world was 343.2 seconds"));
        var top = RelatorioInicializacao.analisar(log);
        HLBench.LOGGER.info("HLBENCH inicializacao jogo={} menu={} total={} jei={} top={}", RelatorioInicializacao.jogoMs, RelatorioInicializacao.menuMs,
                RelatorioInicializacao.totalMs, RelatorioInicializacao.jeiMs, top);
        Files.deleteIfExists(log);
        return 1;
    }
}
