package com.hlotimizacao.cliente;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.recursos.DiagnosticoLag;
import com.hlotimizacao.recursos.LiberadorGpu;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class EventosCliente {
    private EventosCliente() {
    }

    public static void registrar() {
        if (Config.ativo(Modulo.DIAGNOSTICO_LAG)) {
            // Para marcar os travamentos que acontecem no salvamento ao pausar o singleplayer.
            DiagnosticoLag.definirDetectorPausa(() -> {
                Minecraft mc = Minecraft.getInstance();
                return mc != null && mc.hasSingleplayerServer() && mc.isPaused();
            });
        }
        if (Config.ativo(Modulo.TOOLTIP_SEGURO)) {
            TooltipSeguro.registrar();
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
