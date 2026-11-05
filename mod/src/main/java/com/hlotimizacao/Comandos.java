package com.hlotimizacao;

import com.hlotimizacao.config.Msg;
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
                Config.LOGGER.warn("Outro mod ja registrou o comando /hl; os comandos do HLO ficam em /hlotimizacao");
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
                // /hl sozinho: abre a tela se o cliente tiver o mod; senao (cliente vanilla, console) mostra o texto.
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer jogador
                            && com.hlotimizacao.rede.RedeHL.temCanal(jogador)) {
                        com.hlotimizacao.rede.RedeHL.abrir(jogador);
                        return 1;
                    }
                    return pagina(ctx, PainelStatus::visaoGeral);
                })
                .then(Commands.literal("status").executes(ctx -> pagina(ctx, PainelStatus::visaoGeral))
                        .then(Commands.literal("modulos").executes(ctx -> pagina(ctx, PainelStatus::modulos)))
                        .then(Commands.literal("desempenho").executes(ctx -> pagina(ctx, PainelStatus::desempenho)))
                        .then(Commands.literal("lag").executes(ctx -> pagina(ctx, PainelStatus::lag)))
                        .then(Commands.literal("avisos").executes(ctx -> pagina(ctx, PainelStatus::avisos))))
                .then(Commands.literal("recarregar").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    Config.recarregarValores();
                    responder(ctx, Msg.t("Configuracao relida (limites, listas e intervalos). Ligar ou desligar modulos exige reiniciar o jogo."));
                    return 1;
                }));
        raiz.then(Commands.literal("entidades").requires(Comandos::podeAdministrar).executes(Comandos::entidades));
        if (Config.ativo(Modulo.LIMITE_ENXAME)) {
            com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> tipos = (c, b) ->
                    net.minecraft.commands.SharedSuggestionProvider.suggest(Config.enxameLimites().keySet(), b);
            raiz.then(Commands.literal("enxame").requires(s -> s.hasPermission(2))
                    .then(Commands.literal("reduzir").then(Commands.argument("tipo", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .suggests(tipos).executes(ctx -> reduzirEnxame(ctx, false)))));
        }
        if (Config.ativo(Modulo.LIMPEZA_ITENS)) {
            raiz.then(Commands.literal("limpar").requires(s -> s.hasPermission(2))
                    .executes(ctx -> {
                        LimpezaItens.limparAgora(ctx.getSource().getServer());
                        responder(ctx, Msg.t("Limpeza dos itens no chao em 10 segundos."));
                        return 1;
                    })
                    .then(Commands.literal("cancelar").executes(ctx -> {
                        boolean ok = LimpezaItens.cancelar(ctx.getSource().getServer());
                        responder(ctx, Msg.t(ok ? "Proxima limpeza cancelada." : "Uma limpeza ja esta em andamento."));
                        return ok ? 1 : 0;
                    }))
                    .then(Commands.literal("status").executes(ctx -> {
                        LimpezaItens.status(ctx.getSource().getServer()).forEach(l -> responder(ctx, l));
                        return 1;
                    })));
        }
        return raiz;
    }

    /** Operador (nivel 2) ou o dono do mundo no singleplayer. */
    private static boolean podeAdministrar(CommandSourceStack fonte) {
        return fonte.hasPermission(2) || (fonte.getEntity() instanceof net.minecraft.server.level.ServerPlayer jogador
                && fonte.getServer().isSingleplayerOwner(jogador.getGameProfile()));
    }

    /** /hl entidades: as 10 chunks com mais entidades, com coordenadas e o tipo mais comum. */
    private static int entidades(CommandContext<CommandSourceStack> ctx) {
        record Chave(net.minecraft.server.level.ServerLevel level, long chunk) {
        }
        java.util.Map<Chave, java.util.Map<String, Integer>> porChunk = new java.util.HashMap<>();
        int total = 0;
        for (net.minecraft.server.level.ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                total++;
                net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
                porChunk.computeIfAbsent(new Chave(level, e.chunkPosition().toLong()), k -> new java.util.HashMap<>())
                        .merge(id == null ? "?" : id.toString(), 1, Integer::sum);
            }
        }
        boolean console = !ctx.getSource().isPlayer();
        responder(ctx, (console ? "==========" : "━━━━━━━━━━") + " " + Msg.f("Entidades ({} no total)", PainelStatus.num(total)) + " " + (console ? "==========" : "━━━━━━━━━━"));
        java.util.List<java.util.Map.Entry<Chave, java.util.Map<String, Integer>>> lista = new java.util.ArrayList<>(porChunk.entrySet());
        lista.sort((a, b) -> Integer.compare(b.getValue().values().stream().mapToInt(Integer::intValue).sum(),
                a.getValue().values().stream().mapToInt(Integer::intValue).sum()));
        for (int i = 0; i < Math.min(10, lista.size()); i++) {
            var e = lista.get(i);
            net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(e.getKey().chunk());
            int soma = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
            var comum = e.getValue().entrySet().stream().max(java.util.Map.Entry.comparingByValue()).get();
            String dim = e.getKey().level().dimension().location().toString();
            int x = chunk.getMiddleBlockX();
            int z = chunk.getMiddleBlockZ();
            String tipo = comum.getKey().startsWith("minecraft:") ? comum.getKey().substring(10) : comum.getKey();
            String texto = String.format(Msg.locale(), " %d. %s (%d, %d): %s · %s x%d", i + 1,
                    PainelStatus.cortar(dim.startsWith("minecraft:") ? dim.substring(10) : dim, 14), x, z, PainelStatus.num(soma),
                    PainelStatus.cortar(tipo, 18), comum.getValue());
            String tp = "/execute in " + dim + " run tp @s " + x + " ~ " + z;
            Component linha = Component.literal(console ? PainelStatus.paraConsole(texto) : texto).withStyle(st -> st
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, tp))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            Component.literal(dim + " chunk " + chunk.x + ", " + chunk.z + "\n" + e.getValue() + "\n" + Msg.f("Clique: {}", tp)))));
            ctx.getSource().sendSuccess(() -> linha, false);
        }
        return 1;
    }

    /** /hl enxame reduzir <tipo> [confirmar]: sem "confirmar" so mostra quantos sairiam. */
    private static int reduzirEnxame(CommandContext<CommandSourceStack> ctx, boolean ignorado) {
        String arg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "tipo").trim();
        boolean confirmar = arg.endsWith(" confirmar") || arg.endsWith(" confirm");
        String tipo = confirmar ? arg.substring(0, arg.lastIndexOf(' ')).trim() : arg;
        int n = com.hlotimizacao.recursos.LimiteEnxame.reduzir(ctx.getSource().getServer(), tipo, confirmar);
        if (n < 0) {
            responder(ctx, Msg.f("{} nao esta em limite_enxame.lista", tipo));
            return 0;
        }
        if (!confirmar) {
            responder(ctx, Msg.f("{} {} sairiam (sem drops), deixando no maximo {} por regiao. Para remover: /hl enxame reduzir {} confirmar",
                    PainelStatus.num(n), tipo, Config.enxameLimites().get(tipo), tipo));
            return 1;
        }
        Config.LOGGER.info("[limite_enxame] /hl enxame reduzir {}: {} removido(s) por {}", tipo, n, ctx.getSource().getTextName());
        responder(ctx, Msg.f("{} {} removido(s).", PainelStatus.num(n), tipo));
        return 1;
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
