package com.hlotimizacao.benchmark;

import com.hlotimizacao.cliente.ClienteHL;
import com.hlotimizacao.cliente.tela.TelaHL;
import com.hlotimizacao.recursos.DiagnosticoFps;
import com.hlotimizacao.rede.DadosPainel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Teste do diagnostico_fps (HLBENCH_FPS=1): no mundo, mede o custo dos ganchos por frame, depois cria dois frames
 * de 300 ms dentro do render do mundo (35 s entre eles, por causa do limite de 1 log a cada 30 s). O pico deve sair
 * no log com o hlbench como causa. Por fim abre a aba FPS e tira uma captura.
 */
final class TesteFps {
    private static int ticks;
    private static volatile int travarFrames;

    private TesteFps() {
    }

    static void registrar() {
        MinecraftForge.EVENT_BUS.addListener(TesteFps::aoTick);
        MinecraftForge.EVENT_BUS.addListener(TesteFps::aoRender);
    }

    private static void aoRender(RenderLevelStageEvent event) {
        if (travarFrames > 0 && event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            travarFrames--;
            framePesado();
        }
    }

    /** Trabalho de verdade (nao sleep): a amostra precisa ver este metodo no topo da pilha. */
    private static void framePesado() {
        long fim = System.nanoTime() + 300_000_000L;
        long x = 0;
        while (System.nanoTime() < fim) {
            x += System.identityHashCode(new Object());
        }
        if (x == 42) {
            HLBench.LOGGER.info("improvavel");
        }
    }

    private static void aoTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        ticks++;
        if (ticks == 200) {
            HLBench.LOGGER.info("HLBENCH fps antes frames={} picos={} custo_us_por_frame={}", DiagnosticoFps.frames(), DiagnosticoFps.picos,
                    String.format("%.2f", DiagnosticoFps.custoUsPorFrame()));
            travarFrames = 1;
        }
        if (ticks == 900) {
            travarFrames = 1;
        }
        if (ticks == 1000) {
            ClienteHL.pedirAbrir();
        }
        if (ticks == 1060 && mc.screen instanceof TelaHL tela) {
            tela.selecionarAba(DadosPainel.Aba.FPS);
        }
        if (ticks == 1100) {
            Screenshot.grab(mc.gameDirectory, "hl_fps.png", mc.getMainRenderTarget(), msg -> HLBench.LOGGER.info("HLBENCH fps captura: {}", msg.getString()));
            var picos = DiagnosticoFps.ultimosPicos();
            HLBench.LOGGER.info("HLBENCH fps depois frames={} picos={} grandes={} pior={} causa={} custo_us_por_frame={} fps={}",
                    DiagnosticoFps.frames(), DiagnosticoFps.picos, DiagnosticoFps.picosGrandes, DiagnosticoFps.piorMs, DiagnosticoFps.piorCausa,
                    String.format("%.2f", DiagnosticoFps.custoUsPorFrame()), java.util.Arrays.toString(DiagnosticoFps.fpsUltimoMinuto()));
            for (var p : picos) {
                HLBench.LOGGER.info("HLBENCH fps pico {} ms mod={} fase={} amostras={}", p.duracaoMs(), p.mod(), p.fase(), p.amostras());
            }
        }
        if (ticks == 1140) {
            mc.stop();
        }
    }
}
