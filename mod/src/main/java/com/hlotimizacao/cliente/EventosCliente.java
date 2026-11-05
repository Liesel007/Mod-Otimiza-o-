package com.hlotimizacao.cliente;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.recursos.DiagnosticoLag;
import com.hlotimizacao.recursos.LiberadorGpu;
import com.hlotimizacao.recursos.LimpezaItens;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class EventosCliente {
    private EventosCliente() {
    }

    public static void registrar() {
        // Tela do /hl: tecla F8 (configuravel em Controles) e pacotes do servidor.
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(ClienteHL::registrarTeclas);
        MinecraftForge.EVENT_BUS.addListener(ClienteHL::aoTick);
        if (Config.ativo(Modulo.RELATORIO_CONFLITOS)) {
            TempoJei.instalar();
        }
        if (Config.ativo(Modulo.DIAGNOSTICO_LAG)) {
            // Para marcar os travamentos que acontecem no salvamento ao pausar o singleplayer.
            DiagnosticoLag.definirDetectorPausa(() -> {
                Minecraft mc = Minecraft.getInstance();
                return mc != null && mc.hasSingleplayerServer() && mc.isPaused();
            });
        }
        if (Config.ativo(Modulo.DIAGNOSTICO_FPS)) {
            com.hlotimizacao.recursos.DiagnosticoFps.iniciar();
            // Resumo ao sair do mundo (tambem no multiplayer, onde o resumo do servidor nao e deste PC).
            MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> {
                if (com.hlotimizacao.recursos.DiagnosticoFps.frames() > 0) {
                    Config.LOGGER.info("[diagnostico_fps] {}", com.hlotimizacao.recursos.DiagnosticoFps.resumo());
                }
            });
        }
        if (Config.ativo(Modulo.TOOLTIP_SEGURO)) {
            TooltipSeguro.registrar();
        }
        if (Config.ativo(Modulo.LIMPEZA_ITENS)) {
            // Com o singleplayer pausado o servidor integrado nao roda ticks; o relogio da limpeza tambem
            // precisa parar, senao ela dispara logo depois de o jogador voltar do menu.
            long[] ultimo = {System.currentTimeMillis()};
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase != TickEvent.Phase.END) {
                    return;
                }
                long agora = System.currentTimeMillis();
                Minecraft mc = Minecraft.getInstance();
                if (mc.hasSingleplayerServer() && mc.isPaused()) {
                    LimpezaItens.adiar(agora - ultimo[0]);
                }
                ultimo[0] = agora;
            });
        }
        if (Config.ativo(Modulo.VAZAMENTO_GPU)) {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    LiberadorGpu.liberarPendentes();
                }
            });
        }
    }
}
