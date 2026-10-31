package com.hlotimizacao;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.recursos.LimpezaItens;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * Comando /hl (e /hlotimizacao como apelido). Registrado depois dos outros mods: se algum ja usa /hl,
 * fica so /hlotimizacao e o log avisa.
 */
public final class Comandos {
    private static boolean avisouHl;
    /** Prefixo em uso ("hl" ou "hlotimizacao"), para os cliques do /hl status. */
    private static volatile String prefixo = "hl";

    private Comandos() {
    }

    public static void registrar(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        boolean hlOcupado = dispatcher.getRoot().getChild("hl") != null;
        if (hlOcupado) {
            if (!avisouHl) {
                avisouHl = true;
                Config.LOGGER.warn("Outro mod ja registrou o comando /hl; os comandos do HL Otimizacao ficam em /hlotimizacao");
            }
            prefixo = HLOtimizacao.MOD_ID;
            dispatcher.register(arvore(HLOtimizacao.MOD_ID));
            return;
        }
        prefixo = "hl";
        LiteralCommandNode<CommandSourceStack> hl = dispatcher.register(arvore("hl"));
        dispatcher.register(Commands.literal(HLOtimizacao.MOD_ID).redirect(hl));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> arvore(String nome) {
        LiteralArgumentBuilder<CommandSourceStack> raiz = Commands.literal(nome)
                .then(Commands.literal("status").executes(ctx -> pagina(ctx, PainelStatus::visaoGeral))
                        .then(Commands.literal("modulos").executes(ctx -> pagina(ctx, PainelStatus::modulos)))
                        .then(Commands.literal("desempenho").executes(ctx -> pagina(ctx, PainelStatus::desempenho)))
                        .then(Commands.literal("lag").executes(ctx -> pagina(ctx, PainelStatus::lag)))
                        .then(Commands.literal("avisos").executes(ctx -> pagina(ctx, PainelStatus::avisos))))
                .then(Commands.literal("recarregar").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    Config.recarregarValores();
                    responder(ctx, "Configuracao relida (limites, listas e intervalos). Ligar ou desligar modulos exige reiniciar o jogo.");
                    return 1;
                }));
        if (Config.ativo(Modulo.LIMPEZA_ITENS)) {
            raiz.then(Commands.literal("limpar").requires(s -> s.hasPermission(2))
                    .executes(ctx -> {
                        LimpezaItens.limparAgora(ctx.getSource().getServer());
                        responder(ctx, "Limpeza dos itens no chao em 10 segundos.");
                        return 1;
                    })
                    .then(Commands.literal("cancelar").executes(ctx -> {
                        boolean ok = LimpezaItens.cancelar(ctx.getSource().getServer());
                        responder(ctx, ok ? "Proxima limpeza cancelada." : "Uma limpeza ja esta em andamento.");
                        return ok ? 1 : 0;
                    }))
                    .then(Commands.literal("status").executes(ctx -> {
                        LimpezaItens.status(ctx.getSource().getServer()).forEach(l -> responder(ctx, l));
                        return 1;
                    })));
        }
        return raiz;
    }

    private static void responder(CommandContext<CommandSourceStack> ctx, String texto) {
        ctx.getSource().sendSuccess(() -> Component.literal(texto), false);
    }

    private static int pagina(CommandContext<CommandSourceStack> ctx, java.util.function.Function<PainelStatus, List<Component>> pagina) {
        CommandSourceStack fonte = ctx.getSource();
        // Console: sem os simbolos que o console do Windows pode nao mostrar.
        PainelStatus painel = new PainelStatus(fonte.getServer(), !fonte.isPlayer(), fonte.hasPermission(2), prefixo);
        for (Component linha : pagina.apply(painel)) {
            Component saida = fonte.isPlayer() ? linha : Component.literal(PainelStatus.paraConsole(linha.getString()));
            fonte.sendSuccess(() -> saida, false);
        }
        return 1;
    }

}
