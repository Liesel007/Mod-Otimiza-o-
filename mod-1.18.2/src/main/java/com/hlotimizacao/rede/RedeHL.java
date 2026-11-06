package com.hlotimizacao.rede;

import com.hlotimizacao.config.Msg;
import com.hlotimizacao.HLOtimizacao;
import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.recursos.LimpezaItens;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Canal de rede da tela do /hl. O cliente pede para abrir e se inscreve na aba visivel; o servidor
 * manda os dados a cada 1 s so enquanto a tela estiver aberta (sem sinal do cliente por 5 s, para).
 * Toda acao que muda algo e conferida no servidor (nivel de permissao 2): esconder o botao no
 * cliente e so aparencia.
 */
public final class RedeHL {
    /**
     * Canal da tela. Aceita qualquer versao (e a falta do canal): o Forge nunca recusa a conexao por causa do HLO.
     * A compatibilidade e conferida pelo DadosPainel.PROTOCOLO, que vai no comeco de cada pacote de dados: com
     * protocolos diferentes a tela avisa "atualize o HLO" em vez de decodificar. Regras para manter isso valendo:
     * mensagens novas so no fim de registrar() (os ids nao mudam), e as mensagens do cliente para o servidor nao mudam
     * de formato. (0.2.x usava o canal "painel" e a 0.3.0 o "painel2"; para elas, este canal simplesmente nao existe.)
     */
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HLOtimizacao.MOD_ID, "tela"), () -> Integer.toString(DadosPainel.PROTOCOLO),
            versao -> true, versao -> true);

    private static final long INTERVALO_MS = 1000;
    private static final long SEM_SINAL_MS = 5000;
    /** Pacotes de dados enviados (para o teste conferir que param ao fechar a tela). */
    public static final AtomicLong ENVIADOS = new AtomicLong();

    private record Inscricao(DadosPainel.Aba aba, long sinalMs) {
    }

    private static final Map<UUID, Inscricao> INSCRITOS = new ConcurrentHashMap<>();
    private static long proximoEnvioMs;

    public enum Acao {
        LIMPAR(true), CANCELAR(true), RECARREGAR(true), IR_ITENS(true), MODULO(true), CONFIG(true), DETALHE_LAG(false);

        final boolean exigeOperador;

        Acao(boolean exigeOperador) {
            this.exigeOperador = exigeOperador;
        }
    }

    private RedeHL() {
    }

    public static void registrar() {
        int id = 0;
        CANAL.messageBuilder(C2SAbrir.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {
                }).decoder(b -> new C2SAbrir()).consumer(naThread(RedeHL::aoAbrir)).add();
        CANAL.messageBuilder(C2SInscrever.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> b.writeEnum(m.aba())).decoder(b -> new C2SInscrever(b.readEnum(DadosPainel.Aba.class)))
                .consumer(naThread(RedeHL::aoInscrever)).add();
        CANAL.messageBuilder(C2SDesinscrever.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {
                }).decoder(b -> new C2SDesinscrever()).consumer(naThread(RedeHL::aoDesinscrever)).add();
        CANAL.messageBuilder(C2SAcao.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {
                    b.writeEnum(m.acao());
                    b.writeUtf(m.argumento(), 2048);
                }).decoder(b -> new C2SAcao(b.readEnum(Acao.class), b.readUtf(2048)))
                .consumer(naThread(RedeHL::aoAcao)).add();
        CANAL.messageBuilder(S2CDados.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> {
                    b.writeBoolean(m.abrir());
                    m.dados().escrever(b);
                }).decoder(b -> new S2CDados(b.readBoolean(), DadosPainel.ler(b)))
                .consumer(naThread((m, ctx) -> paraCliente(() -> () -> com.hlotimizacao.cliente.ClienteHL.receberDados(m.dados(), m.abrir())))).add();
        CANAL.messageBuilder(S2CResultado.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> {
                    b.writeBoolean(m.ok());
                    b.writeUtf(m.texto(), 512);
                }).decoder(b -> new S2CResultado(b.readBoolean(), b.readUtf(512)))
                .consumer(naThread((m, ctx) -> paraCliente(() -> () -> com.hlotimizacao.cliente.ClienteHL.receberResultado(m.ok(), m.texto())))).add();
        CANAL.messageBuilder(S2CTexto.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> {
                    b.writeUtf(m.titulo(), 128);
                    b.writeUtf(m.texto(), 8192);
                }).decoder(b -> new S2CTexto(b.readUtf(128), b.readUtf(8192)))
                .consumer(naThread((m, ctx) -> paraCliente(() -> () -> com.hlotimizacao.cliente.ClienteHL.receberTexto(m.titulo(), m.texto())))).add();
    }

    /** Porte 1.18.2: o MessageBuilder ainda nao tem consumerMainThread; faz o mesmo (roda na thread principal). */
    private static <M> java.util.function.BiConsumer<M, Supplier<NetworkEvent.Context>> naThread(
            java.util.function.BiConsumer<M, Supplier<NetworkEvent.Context>> tratar) {
        return (m, ctx) -> {
            ctx.get().enqueueWork(() -> tratar.accept(m, ctx));
            ctx.get().setPacketHandled(true);
        };
    }

    private static void paraCliente(Supplier<Runnable> codigo) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, codigo);
    }

    // ---------- mensagens ----------

    public record C2SAbrir() {
    }

    public record C2SInscrever(DadosPainel.Aba aba) {
    }

    public record C2SDesinscrever() {
    }

    public record C2SAcao(Acao acao, String argumento) {
    }

    public record S2CDados(boolean abrir, DadosPainel dados) {
    }

    public record S2CResultado(boolean ok, String texto) {
    }

    public record S2CTexto(String titulo, String texto) {
    }

    // ---------- servidor ----------

    /**
     * Pode mudar coisas pela tela: operador (nivel 2) ou o dono do mundo no singleplayer (que nao e
     * operador quando os cheats estao desligados, mas e o dono da propria configuracao).
     */
    public static boolean podeAdministrar(ServerPlayer jogador) {
        return jogador.hasPermissions(2) || jogador.server.isSingleplayerOwner(jogador.getGameProfile());
    }

    /** O cliente deste jogador tem o mod (e o canal)? */
    public static boolean temCanal(ServerPlayer jogador) {
        return jogador.connection != null && CANAL.isRemotePresent(jogador.connection.connection);
    }

    /** /hl: abre a tela no cliente do jogador. */
    public static void abrir(ServerPlayer jogador) {
        INSCRITOS.put(jogador.getUUID(), new Inscricao(DadosPainel.Aba.GERAL, System.currentTimeMillis()));
        enviar(jogador, DadosPainel.Aba.GERAL, true);
    }

    private static void enviar(ServerPlayer jogador, DadosPainel.Aba aba, boolean abrir) {
        DadosPainel dados = ColetorStatus.coletar(jogador.server, aba, podeAdministrar(jogador));
        CANAL.send(PacketDistributor.PLAYER.with(() -> jogador), new S2CDados(abrir, dados));
        ENVIADOS.incrementAndGet();
    }

    private static void aoAbrir(C2SAbrir m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer jogador = ctx.get().getSender();
        if (jogador != null) {
            abrir(jogador);
        }
    }

    private static void aoInscrever(C2SInscrever m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer jogador = ctx.get().getSender();
        if (jogador == null) {
            return;
        }
        Inscricao anterior = INSCRITOS.put(jogador.getUUID(), new Inscricao(m.aba(), System.currentTimeMillis()));
        if (anterior == null || anterior.aba() != m.aba()) {
            enviar(jogador, m.aba(), false); // trocou de aba: manda na hora
        }
    }

    private static void aoDesinscrever(C2SDesinscrever m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer jogador = ctx.get().getSender();
        if (jogador != null) {
            INSCRITOS.remove(jogador.getUUID());
        }
    }

    public static int inscritos() {
        return INSCRITOS.size();
    }

    public static void aoTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || INSCRITOS.isEmpty()) {
            return;
        }
        long agora = System.currentTimeMillis();
        if (agora < proximoEnvioMs) {
            return;
        }
        proximoEnvioMs = agora + INTERVALO_MS;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (Map.Entry<UUID, Inscricao> e : new HashMap<>(INSCRITOS).entrySet()) {
            ServerPlayer jogador = server.getPlayerList().getPlayer(e.getKey());
            if (jogador == null || agora - e.getValue().sinalMs() > SEM_SINAL_MS) {
                INSCRITOS.remove(e.getKey());
                Config.LOGGER.debug("[painel] Parou de mandar dados para {} (tela fechada ou sem sinal)", e.getKey());
                continue;
            }
            enviar(jogador, e.getValue().aba(), false);
        }
    }

    private static void aoAcao(C2SAcao m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer jogador = ctx.get().getSender();
        if (jogador == null) {
            return;
        }
        Resultado r = executar(jogador, m.acao(), m.argumento());
        if (r.texto() != null && !r.texto().isEmpty()) {
            if (r.titulo() != null) {
                CANAL.send(PacketDistributor.PLAYER.with(() -> jogador), new S2CTexto(r.titulo(), r.texto()));
            } else {
                CANAL.send(PacketDistributor.PLAYER.with(() -> jogador), new S2CResultado(r.ok(), r.texto()));
            }
        }
        Inscricao inscricao = INSCRITOS.get(jogador.getUUID());
        if (inscricao != null && r.ok()) {
            enviar(jogador, inscricao.aba(), false); // mostra o efeito na hora
        }
    }

    /** Resultado de uma acao; titulo != null = abrir uma janela de texto. */
    public record Resultado(boolean ok, String texto, String titulo) {
        static Resultado ok(String texto) {
            return new Resultado(true, texto, null);
        }

        static Resultado erro(String texto) {
            return new Resultado(false, texto, null);
        }
    }

    /**
     * Executa uma acao pedida pela tela. Publico para o teste de pacote forjado no HLBench: a
     * permissao e conferida aqui, no servidor, para qualquer pacote que chegue.
     */
    public static Resultado executar(ServerPlayer jogador, Acao acao, String argumento) {
        if (acao.exigeOperador && !podeAdministrar(jogador)) {
            Config.LOGGER.warn("[painel] {} tentou {} sem permissao", jogador.getGameProfile().getName(), acao);
            return Resultado.erro(Msg.t("Sem permissão: só operadores podem fazer isso."));
        }
        MinecraftServer server = jogador.server;
        switch (acao) {
            case LIMPAR -> {
                if (!Config.ativo(Modulo.LIMPEZA_ITENS)) {
                    return Resultado.erro(Msg.t("A limpeza de itens está desligada."));
                }
                LimpezaItens.limparAgora(server);
                return Resultado.ok(Msg.t("Limpeza dos itens no chão em 10 segundos."));
            }
            case CANCELAR -> {
                if (!Config.ativo(Modulo.LIMPEZA_ITENS)) {
                    return Resultado.erro(Msg.t("A limpeza de itens está desligada."));
                }
                return LimpezaItens.cancelar(server) ? Resultado.ok(Msg.t("Próxima limpeza cancelada.")) : Resultado.erro(Msg.t("Uma limpeza já está em andamento."));
            }
            case RECARREGAR -> {
                Config.recarregarValores();
                return Resultado.ok(Msg.t("Configuração relida."));
            }
            case IR_ITENS -> {
                return irAteItens(jogador, argumento);
            }
            case MODULO, CONFIG -> {
                int igual = argumento.indexOf('=');
                if (igual <= 0) {
                    return Resultado.erro(Msg.t("Pedido inválido."));
                }
                String chave = argumento.substring(0, igual);
                if (acao == Acao.MODULO) {
                    chave = "modulo." + chave;
                }
                String erro = Config.gravarValor(chave, argumento.substring(igual + 1));
                if (erro != null) {
                    return Resultado.erro(erro);
                }
                return Resultado.ok(acao == Acao.MODULO ? Msg.t("Gravado. Mudanças nos módulos valem depois de reiniciar o jogo.") : Msg.f("Gravado e relido: {}", chave));
            }
            case DETALHE_LAG -> {
                try {
                    String texto = ColetorStatus.detalheLag(Integer.parseInt(argumento));
                    return texto == null ? Resultado.erro(Msg.t("Registro não encontrado.")) : new Resultado(true, texto, Msg.t("Travamento"));
                } catch (NumberFormatException e) {
                    return Resultado.erro(Msg.t("Pedido inválido."));
                }
            }
            default -> {
                return Resultado.erro(Msg.t("Ação desconhecida."));
            }
        }
    }

    /** Teleporta o jogador para o chunk com mais itens no chao da dimensao. */
    private static Resultado irAteItens(ServerPlayer jogador, String dimensao) {
        ResourceLocation id = ResourceLocation.tryParse(dimensao);
        ServerLevel level = id == null ? null : jogador.server.getLevel(ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, id));
        if (level == null) {
            return Resultado.erro(Msg.f("Dimensão não encontrada: {}", dimensao));
        }
        Map<Long, Integer> porChunk = new HashMap<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof ItemEntity) {
                porChunk.merge(ChunkPos.asLong(e.blockPosition()), 1, Integer::sum);
            }
        }
        if (porChunk.isEmpty()) {
            return Resultado.erro(Msg.f("Não há itens no chão em {}.", dimensao));
        }
        ChunkPos chunk = new ChunkPos(porChunk.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey());
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1;
        jogador.teleportTo(level, x + 0.5, y, z + 0.5, jogador.getYRot(), jogador.getXRot());
        return Resultado.ok(Msg.f("Teleportado para {} em {} ({} pilhas de itens).", new BlockPos(x, y, z).toShortString(), dimensao, porChunk.get(chunk.toLong())));
    }
}
