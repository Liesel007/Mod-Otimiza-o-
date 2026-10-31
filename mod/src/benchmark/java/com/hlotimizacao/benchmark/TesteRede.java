package com.hlotimizacao.benchmark;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.rede.ColetorStatus;
import com.hlotimizacao.rede.DadosPainel;
import com.hlotimizacao.rede.RedeHL;
import com.mojang.brigadier.context.CommandContext;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

/** Testes da rede da tela: tamanho e ida e volta do DadosPainel, e pacote forjado sem permissao. */
final class TesteRede {
    private TesteRede() {
    }

    private static final int LIMITE = 8 * 1024;

    private static int tamanho(DadosPainel d) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        d.escrever(buf);
        int n = buf.readableBytes();
        DadosPainel lido = DadosPainel.ler(buf);
        if (buf.readableBytes() != 0 || lido.aba != d.aba || lido.ligados != d.ligados) {
            throw new IllegalStateException("ida e volta diferente na aba " + d.aba);
        }
        return n;
    }

    static int verificar(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int falhas = 0;
        // 1. Dados reais de cada aba.
        for (DadosPainel.Aba aba : DadosPainel.Aba.values()) {
            int n = tamanho(ColetorStatus.coletar(server, aba, true));
            if (n > LIMITE) {
                falhas++;
            }
            HLBench.LOGGER.info("HLBENCH rede aba={} bytes={}", aba, n);
        }
        // 2. Valores-limite: 300 pontos de MSPT, nomes longos, listas cheias e listas vazias.
        String longo = "um-nome-de-mod-muito-comprido-".repeat(20);
        DadosPainel d = ColetorStatus.coletar(server, DadosPainel.Aba.GERAL, true);
        d.maisCulpado = longo;
        d.desempenho = new DadosPainel.Desempenho();
        d.desempenho.historico = new float[300];
        d.desempenho.salvou = new boolean[300];
        for (int i = 0; i < 300; i++) {
            d.desempenho.historico[i] = i * 7.3f;
            d.desempenho.salvou[i] = i % 60 == 0;
        }
        for (int i = 0; i < 12; i++) {
            d.desempenho.dimensoes.add(new DadosPainel.Dimensao(longo, 3, 9999, 99999, 9999));
        }
        int nDesempenho = tamanho(d);
        d.desempenho = null;
        d.lag = new DadosPainel.Lag();
        for (int i = 0; i < 5; i++) {
            d.lag.causas.add(new DadosPainel.Causa(longo, 20, longo));
        }
        for (int i = 0; i < 20; i++) {
            d.lag.registros.add(new DadosPainel.Registro(i, "23:59:59", 123456, "dentro do tick", longo, longo));
        }
        int nLag = tamanho(d);
        d.lag = null;
        d.listaAvisos = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            d.listaAvisos.add(new DadosPainel.Aviso(longo, longo));
        }
        int nAvisos = tamanho(d);
        d.listaAvisos = new ArrayList<>();
        d.modulos = new ArrayList<>();
        d.config = new ArrayList<>();
        int nVazio = tamanho(d);
        for (int n : new int[]{nDesempenho, nLag, nAvisos, nVazio}) {
            if (n > LIMITE) {
                falhas++;
            }
        }
        HLBench.LOGGER.info("HLBENCH rede limite desempenho_300pts={} lag_20_registros={} avisos_15_longos={} listas_vazias={}",
                nDesempenho, nLag, nAvisos, nVazio);

        // 3. Pacote forjado: um jogador sem permissao manda as acoes "na mao".
        ServerPlayer comum = HLBench.jogadorDeTeste(server.overworld(), "SemPermissao");
        String antes = Config.valorTexto("limpeza_itens.intervalo_min");
        for (RedeHL.Acao acao : new RedeHL.Acao[]{RedeHL.Acao.LIMPAR, RedeHL.Acao.CANCELAR, RedeHL.Acao.RECARREGAR,
                RedeHL.Acao.IR_ITENS, RedeHL.Acao.MODULO, RedeHL.Acao.CONFIG}) {
            String arg = switch (acao) {
                case IR_ITENS -> "minecraft:overworld";
                case MODULO -> "limpeza_itens=off";
                case CONFIG -> "limpeza_itens.intervalo_min=1";
                default -> "";
            };
            RedeHL.Resultado r = RedeHL.executar(comum, acao, arg);
            if (r.ok()) {
                falhas++;
            }
            HLBench.LOGGER.info("HLBENCH rede forjado sem_permissao acao={} aceito={} resposta={}", acao, r.ok(), r.texto());
        }
        String depois = Config.valorTexto("limpeza_itens.intervalo_min");
        HLBench.LOGGER.info("HLBENCH rede config_intacta={} ({} -> {})", antes.equals(depois), antes, depois);
        if (!antes.equals(depois)) {
            falhas++;
        }
        RedeHL.Resultado detalhe = RedeHL.executar(comum, RedeHL.Acao.DETALHE_LAG, "0");
        HLBench.LOGGER.info("HLBENCH rede detalhe_lag_sem_permissao (so leitura, permitido) ok={}", detalhe.ok() || detalhe.texto().contains("encontrado"));

        // 4. O mesmo jogador como operador.
        server.getPlayerList().op(comum.getGameProfile());
        RedeHL.Resultado config = RedeHL.executar(comum, RedeHL.Acao.CONFIG, "limpeza_itens.intervalo_min=7");
        RedeHL.Resultado erro = RedeHL.executar(comum, RedeHL.Acao.CONFIG, "limpeza_itens.intervalo_min=abc");
        RedeHL.Resultado proibida = RedeHL.executar(comum, RedeHL.Acao.CONFIG, "modulo.qualquer=on");
        server.getPlayerList().deop(comum.getGameProfile());
        HLBench.LOGGER.info("HLBENCH rede operador config={} ({}) intervalo_agora={} | valor_invalido_recusado={} ({}) | chave_fora_da_lista_recusada={}",
                config.ok(), config.texto(), Config.limpezaIntervaloMin(), !erro.ok(), erro.texto(), !proibida.ok());
        if (!config.ok() || Config.limpezaIntervaloMin() != 7 || erro.ok() || proibida.ok()) {
            falhas++;
        }
        RedeHL.executar(comum, RedeHL.Acao.CONFIG, "x=y"); // sem permissao de novo: so para conferir que voltou a recusar
        HLBench.LOGGER.info("HLBENCH rede falhas={}", falhas);
        return 1;
    }
}
