package com.hlotimizacao.cliente;

import com.hlotimizacao.cliente.tela.TelaHL;
import com.hlotimizacao.rede.DadosPainel;
import com.hlotimizacao.rede.RedeHL;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/** Lado do cliente da tela do /hl: tecla de atalho e pacotes recebidos do servidor. */
public final class ClienteHL {
    public static final KeyMapping ABRIR = new KeyMapping("key.hlotimizacao.abrir", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8,
            "key.categories.hlotimizacao");

    private ClienteHL() {
    }

    public static void registrarTeclas(RegisterKeyMappingsEvent event) {
        event.register(ABRIR);
    }

    public static void aoTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (ABRIR.consumeClick()) {
            pedirAbrir();
        }
    }

    /** F8: pede os dados ao servidor; ele responde abrindo a tela. */
    public static void pedirAbrir() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            return;
        }
        if (RedeHL.CANAL.isRemotePresent(mc.getConnection().getConnection())) {
            RedeHL.CANAL.sendToServer(new RedeHL.C2SAbrir());
        } else {
            mc.player.displayClientMessage(Component.translatable("hlotimizacao.tela.sem_servidor"), false);
        }
    }

    public static void receberDados(DadosPainel dados, boolean abrir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TelaHL tela) {
            tela.atualizar(dados);
        } else if (abrir) {
            mc.setScreen(new TelaHL(dados));
        }
    }

    public static void receberResultado(boolean ok, String texto) {
        Minecraft mc = Minecraft.getInstance();
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.translatable(ok ? "hlotimizacao.tela.toast" : "hlotimizacao.tela.toast_erro"), Component.literal(texto));
        if (mc.screen instanceof TelaHL tela) {
            tela.mensagem(ok, texto);
        }
    }

    public static void receberTexto(String titulo, String texto) {
        if (Minecraft.getInstance().screen instanceof TelaHL tela) {
            tela.mostrarTexto(titulo, texto);
        }
    }
}
