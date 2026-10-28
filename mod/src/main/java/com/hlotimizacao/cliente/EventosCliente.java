package com.hlotimizacao.cliente;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.recursos.LiberadorGpu;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class EventosCliente {
    private EventosCliente() {
    }

    public static void registrar() {
        if (Config.ativo(Modulo.VAZAMENTO_GPU)) {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    LiberadorGpu.liberarPendentes();
                }
            });
        }
    }
}
