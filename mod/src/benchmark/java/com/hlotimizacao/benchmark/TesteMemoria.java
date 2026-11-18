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
        // Sessao de 16/11 (relatorio 0.5.1): Full GC deixou 3.559 MB, -Xmx 6144, RAM 11.800, GPU integrada, 27 de 38 congelamentos com pouca RAM.
        Memoria.Recomendacao r = Memoria.calcular(3559, false, 6144, 11800, true, 38, 27);
        HLBench.LOGGER.info("HLBENCH memoria sessao_16_11: sugerido={} manter={} ram_baixa={} no_limite={} teto={} (esperado: 6144, manter, ram_baixa) | {}",
                r.sugeridoMb(), r.manter(), r.ramBaixa(), r.noLimite(), r.tetoMb(), Memoria.texto(r));
        // O valor errado da 0.5.0 (pos-Young 4.103): mesmo assim nunca pode passar do teto nem do atual com RAM baixa.
        r = Memoria.calcular(4103, true, 6144, 11800, true, 38, 27);
        HLBench.LOGGER.info("HLBENCH memoria young_4103: sugerido={} manter={} (esperado: nunca acima de 6144) | {}", r.sugeridoMb(), r.manter(), Memoria.texto(r));
        r = Memoria.calcular(4103, true, 4096, 11800, true, 0, 0);
        HLBench.LOGGER.info("HLBENCH memoria teto: vivo 4103 Xmx 4096 sem congelamentos -> sugerido={} teto={} no_limite={} (esperado: = teto 5900, no limite)",
                r.sugeridoMb(), r.tetoMb(), r.noLimite());
        r = Memoria.calcular(3725, false, 7504, 12119, true, 35, 5);
        HLBench.LOGGER.info("HLBENCH memoria sessao_15_11: sugerido={} manter={} (esperado: 6060 = teto, reduzir de 7,3 GB) | {}", r.sugeridoMb(), r.manter(), Memoria.texto(r));
        r = Memoria.calcular(1500, false, 8192, 32768, false, 0, 0);
        HLBench.LOGGER.info("HLBENCH memoria minimo: vivo 1500 -> sugerido={} (esperado 4096)", r.sugeridoMb());
        HLBench.LOGGER.info("HLBENCH memoria sessao_atual: {} | {}", Memoria.resumo(), Memoria.resumoRamLivre());
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
        // Formato real do latest.log do Forge: logger abreviado (%c{2.}) e o JEI em "minutes" acima de 60 s.
        Path log = Files.createTempFile("hlo_log_exemplo", ".log");
        Files.write(log, List.of(
                "[03:36:01] [Render thread/WARN] [ModernFix/]: Game took 286.1 seconds to start",
                "[03:35:10] [Worker-ResourceReload-3/WARN] [ModernFix/]: Posting dynamic ModelEvent.ModifyBakingResult to mods took 22.51 s, breakdown below:",
                "[03:35:10] [Worker-ResourceReload-3/WARN] [ModernFix/]:     lantea: 16.27 s",
                "[03:35:10] [Worker-ResourceReload-3/WARN] [ModernFix/]:     framedblocks: 1.602 s",
                "[03:35:10] [Worker-ResourceReload-3/WARN] [ModernFix/]:     ae2: 312.4 ms",
                "[03:38:17] [Render thread/INFO] [me.je.li.st.JeiStarter/]: Starting JEI...",
                "[03:38:20] [Render thread/INFO] [me.je.li.lo.PluginCallerTimerRunnable/]: Registering categories: industrialforegoing:default took 2.104 seconds",
                "[03:38:50] [Render thread/INFO] [me.je.li.lo.PluginCallerTimerRunnable/]: Registering recipes: jei:minecraft took 17.91 seconds",
                "[03:38:54] [Render thread/INFO] [me.je.li.lo.PluginCallerTimerRunnable/]: Registering recipes: create:jei_plugin took 3.799 seconds",
                "[03:38:55] [Render thread/INFO] [me.je.li.lo.PluginCaller/]: Registering recipes took 35.20 seconds",
                "[03:39:20] [Render thread/INFO] [me.je.li.lo.PluginCaller/]: Sending Runtime...",
                "[03:39:21] [Render thread/INFO] [me.je.li.lo.PluginCaller/]: Sending Runtime took 812.3 milliseconds",
                "[03:39:21] [Render thread/INFO] [me.je.co.ut.LoggedTimer/]: Starting JEI took 1.063 minutes",
                "[03:40:59] [Render thread/WARN] [ModernFix/]: Time from main menu to in-game was 98.5 seconds",
                "[03:40:59] [Render thread/WARN] [ModernFix/]: Total time to load game and open world was 384.7 seconds"));
        var top = RelatorioInicializacao.analisar(log);
        HLBench.LOGGER.info("HLBENCH inicializacao jogo={} menu={} total={} jei={} (esperado 63780) top={}", RelatorioInicializacao.jogoMs, RelatorioInicializacao.menuMs,
                RelatorioInicializacao.totalMs, RelatorioInicializacao.jeiTexto(), top);
        // Sem a linha "Starting JEI took": mede do "Starting JEI..." ate a ultima linha de "Sending Runtime".
        List<String> linhas = Files.readAllLines(log);
        linhas.removeIf(l -> l.contains("Starting JEI took"));
        Files.write(log, linhas);
        RelatorioInicializacao.analisar(log);
        HLBench.LOGGER.info("HLBENCH inicializacao sem_linha_final jei={} (esperado 64,0 s medido pelo log)", RelatorioInicializacao.jeiTexto());
        // Formato do launcher vanilla/CurseForge: sem o nome do logger.
        Files.write(log, List.of(
                "[03:38:17] [Render thread/INFO]: Starting JEI...",
                "[03:38:50] [Render thread/INFO]: Registering recipes: jei:minecraft took 17.91 seconds",
                "[03:38:52] [Render thread/INFO]: Registering categories: industrialforegoing:default took 2.104 seconds",
                "[03:38:55] [Render thread/INFO]: Loading took 99.0 seconds",
                "[03:39:21] [Render thread/INFO]: Starting JEI took 1.063 minutes",
                "[03:40:59] [Render thread/WARN]: Total time to load game and open world was 384.7 seconds"));
        top = RelatorioInicializacao.analisar(log);
        HLBench.LOGGER.info("HLBENCH inicializacao formato_vanilla jei={} total={} top={} (esperado 63,8 s, sem o 'Loading took')", RelatorioInicializacao.jeiTexto(),
                RelatorioInicializacao.totalMs, top);
        Files.deleteIfExists(log);
        return 1;
    }
}
