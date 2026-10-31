package com.hlotimizacao.benchmark;

import com.hlotimizacao.cliente.ClienteHL;
import com.hlotimizacao.cliente.tela.TelaHL;
import com.hlotimizacao.rede.DadosPainel;
import com.hlotimizacao.rede.RedeHL;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;

import java.util.Random;

/**
 * Teste da tela no cliente (HLBENCH_TELA=1): entra no mundo, cria itens e um travamento, abre o painel
 * pelo mesmo caminho da tecla F8, passa por todas as abas tirando capturas de tela, abre o detalhe de
 * um travamento e confere que os pacotes param depois de fechar a tela.
 */
final class TesteTela {
    private static int ticks;
    private static int etapa;
    private static long enviadosAoFechar = -1;
    private static final DadosPainel.Aba[] ABAS = DadosPainel.Aba.values();

    private TesteTela() {
    }

    static void aoTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        ticks++;
        if (ticks == 100) {
            var server = mc.getSingleplayerServer();
            var pos = mc.player.blockPosition();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                Random r = new Random(3);
                for (int i = 0; i < 300; i++) {
                    ItemEntity item = new ItemEntity(level, pos.getX() + r.nextInt(30) - 15, pos.getY() + 1, pos.getZ() + r.nextInt(30) - 15,
                            new ItemStack(Items.COBBLESTONE, 1 + r.nextInt(16)));
                    level.addFreshEntity(item);
                }
                long fim = System.currentTimeMillis() + 2500; // um travamento para a aba Lag
                while (System.currentTimeMillis() < fim) {
                    Thread.onSpinWait();
                }
                HLBench.LOGGER.info("HLBENCH tela itens e travamento criados");
            });
        }
        if (ticks == 220) {
            ClienteHL.pedirAbrir(); // o mesmo que a tecla F8 faz
        }
        if (ticks < 280) {
            return;
        }
        int passo = (ticks - 280) / 50;
        int dentro = (ticks - 280) % 50;
        if (passo < ABAS.length) {
            if (dentro == 0 && mc.screen instanceof TelaHL tela) {
                tela.selecionarAba(ABAS[passo]);
            }
            if (dentro == 40) {
                captura(mc, ABAS[passo].name().toLowerCase());
            }
            return;
        }
        int depois = ticks - 280 - ABAS.length * 50;
        if (depois == 0 && mc.screen instanceof TelaHL tela) {
            tela.selecionarAba(DadosPainel.Aba.LAG);
        }
        if (depois == 20) {
            RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.DETALHE_LAG, "0")); // o botao [ver]
        }
        if (depois == 50) {
            captura(mc, "lag_detalhe");
            HLBench.LOGGER.info("HLBENCH tela detalhe_aberto={}", mc.screen instanceof TelaHL t && t.modalAberto());
        }
        if (depois == 60) {
            HLBench.LOGGER.info("HLBENCH tela inscritos_com_tela_aberta={} enviados={}", RedeHL.inscritos(), RedeHL.ENVIADOS.get());
            mc.setScreen(null);
        }
        if (depois == 80) {
            enviadosAoFechar = RedeHL.ENVIADOS.get();
            HLBench.LOGGER.info("HLBENCH tela fechada inscritos={} enviados={}", RedeHL.inscritos(), enviadosAoFechar);
        }
        if (depois == 220) {
            HLBench.LOGGER.info("HLBENCH tela 7s_depois_de_fechar inscritos={} pacotes_novos={}", RedeHL.inscritos(),
                    RedeHL.ENVIADOS.get() - enviadosAoFechar);
            mc.stop();
        }
    }

    private static void captura(Minecraft mc, String nome) {
        Screenshot.grab(mc.gameDirectory, "hl_" + nome + ".png", mc.getMainRenderTarget(),
                msg -> HLBench.LOGGER.info("HLBENCH tela captura {}: {}", nome, msg.getString()));
    }
}
