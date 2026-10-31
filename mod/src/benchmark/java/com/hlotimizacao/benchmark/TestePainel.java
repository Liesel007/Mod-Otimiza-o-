package com.hlotimizacao.benchmark;

import com.hlotimizacao.PainelStatus;
import com.hlotimizacao.recursos.Avisos;
import com.hlotimizacao.recursos.ChavesInfinityChest;
import com.hlotimizacao.recursos.Estatisticas;
import com.hlotimizacao.recursos.LimpezaItens;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Monta todas as paginas do /hl status com contadores grandes e confere largura, nulos, cliques e dicas. */
final class TestePainel {
    private TestePainel() {
    }

    private static final int MAX = 60;

    static int verificar(CommandContext<CommandSourceStack> ctx) {
        // Contadores falsos e grandes, para o pior caso de largura.
        Estatisticas.DESPAWN_MOBS_LIBERADOS.set(123_456);
        Estatisticas.DESPAWN_ITENS_DEVOLVIDOS.set(7_654_321);
        Estatisticas.SALVAMENTO_ASSINCRONOS.set(1_234_567);
        Estatisticas.SALVAMENTO_SINCRONOS.set(4_321);
        Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.add("com.enderio.conduits.common.network.ConduitSavedData");
        Estatisticas.TOOLTIP_ITENS.add("silentgear:blueprint_package");
        Estatisticas.GPU_FRAMEBUFFERS.set(99_999);
        Estatisticas.GPU_TEXTURAS.set(199_998);
        ChavesInfinityChest.BAUS_CORRIGIDOS.set(12);
        ChavesInfinityChest.CHAVES_CORRIGIDAS.set(21_234);
        ChavesInfinityChest.ENTRADAS_MESCLADAS.set(9_876);
        LimpezaItens.LIMPEZAS.set(1_234);
        LimpezaItens.ITENS_REMOVIDOS.set(98_765_432);
        Avisos.adicionar("ImmediatelyFast-Forge-1.5.5+1.20.4-um-nome-muito-comprido-mesmo: feito para 1.20.4",
                "detalhe de teste com a correcao");
        int linhas = 0;
        int problemas = 0;
        int cliques = 0;
        int dicas = 0;
        for (boolean console : new boolean[]{false, true}) {
            PainelStatus painel = new PainelStatus(ctx.getSource().getServer(), console, true, "hl");
            String[] nomes = {"visao geral", "modulos", "desempenho", "lag", "avisos"};
            List<List<Component>> paginas = painel.todas();
            for (int p = 0; p < paginas.size(); p++) {
                for (Component linha : paginas.get(p)) {
                    linhas++;
                    if (linha == null) {
                        problemas++;
                        HLBench.LOGGER.error("HLBENCH painel linha nula na pagina {}", nomes[p]);
                        continue;
                    }
                    String texto = linha.getString();
                    if (texto.contains("null")) {
                        problemas++;
                        HLBench.LOGGER.error("HLBENCH painel 'null' na pagina {}: {}", nomes[p], texto);
                    }
                    if (texto.length() > MAX) {
                        problemas++;
                        HLBench.LOGGER.error("HLBENCH painel linha com {} caracteres na pagina {}: {}", texto.length(), nomes[p], texto);
                    }
                    if (console && (texto.contains("✔") || texto.contains("✘") || texto.contains("⚠") || texto.contains("━"))) {
                        problemas++;
                        HLBench.LOGGER.error("HLBENCH painel simbolo no console na pagina {}: {}", nomes[p], texto);
                    }
                    for (Component parte : linha.toFlatList()) {
                        if (parte.getStyle().getClickEvent() != null) {
                            cliques++;
                        }
                        if (parte.getStyle().getHoverEvent() != null) {
                            dicas++;
                        }
                    }
                    if (!console) {
                        HLBench.LOGGER.info("HLBENCH painel [{}] {}", nomes[p], texto);
                    }
                }
            }
        }
        HLBench.LOGGER.info("HLBENCH painel linhas={} problemas={} partes_clicaveis={} partes_com_dica={}", linhas, problemas, cliques, dicas);
        return 1;
    }
}
