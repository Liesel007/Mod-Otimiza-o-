package com.hlotimizacao;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.recursos.GravadorAssincrono;
import com.hlotimizacao.recursos.DiagnosticoLag;
import com.hlotimizacao.recursos.Desempenho;
import com.hlotimizacao.recursos.Estatisticas;
import com.hlotimizacao.recursos.LimpezaItens;
import com.hlotimizacao.recursos.RelatorioConflitos;
import com.hlotimizacao.recursos.VerificadorMods;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;

@Mod(HLOtimizacao.MOD_ID)
public class HLOtimizacao {
    public static final String MOD_ID = "hlotimizacao";

    public HLOtimizacao() {
        Config.carregar();
        // So o cliente ou so o servidor podem ter o mod: nao exige o mesmo mod dos dois lados.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (remote, isServer) -> true));

        MinecraftForge.EVENT_BUS.addListener(this::aoIniciarServidor);
        MinecraftForge.EVENT_BUS.addListener(this::aoPararServidor);
        // Prioridade mais baixa: registra depois dos outros mods, para saber se /hl ja esta em uso.
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, Comandos::registrar);
        MinecraftForge.EVENT_BUS.addListener(Desempenho::aoTick);
        MinecraftForge.EVENT_BUS.addListener(Desempenho::aoSalvar);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> Desempenho.aoIniciarServidor());
        Desempenho.iniciarGc();
        if (Config.ativo(Modulo.LIMPEZA_ITENS)) {
            MinecraftForge.EVENT_BUS.addListener(LimpezaItens::aoTick);
            MinecraftForge.EVENT_BUS.addListener(LimpezaItens::aoMorrer);
            MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> LimpezaItens.aoIniciarServidor(e.getServer()));
        }
        if (Config.ativo(Modulo.RELATORIO_CONFLITOS)) {
            int avisos = VerificadorMods.verificar();
            Config.LOGGER.info("Verificacao da lista de mods: {} aviso(s)", avisos);
        }
        if (Config.ativo(Modulo.DIAGNOSTICO_LAG)) {
            MinecraftForge.EVENT_BUS.addListener(DiagnosticoLag::aoTick);
            MinecraftForge.EVENT_BUS.addListener(DiagnosticoLag::aoCarregarChunk);
            MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> DiagnosticoLag.aoIniciarServidor(e.getServer()));
            MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> DiagnosticoLag.aoPararServidor());
            MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> DiagnosticoLag.aoEntrarJogador());
            DiagnosticoLag.iniciar();
        }
        if (Config.ativo(Modulo.CORRECAO_FASTASYNCWORLDSAVE)) {
            // Tambem no fechamento sem ServerStoppedEvent (crash, System.exit).
            Runtime.getRuntime().addShutdownHook(new Thread(() -> esperarFastAsyncWorldSave(10), "HL Otimizacao - FAWS ao desligar"));
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.hlotimizacao.cliente.EventosCliente.registrar();
        }
    }

    private void aoIniciarServidor(ServerStartedEvent event) {
        if (Config.ativo(Modulo.RELATORIO_CONFLITOS)) {
            RelatorioConflitos.registrar(event.getServer());
        }
    }

    private void aoPararServidor(ServerStoppedEvent event) {
        Estatisticas.registrarResumo();
        if (Config.ativo(Modulo.SALVAMENTO_ASSINCRONO)) {
            GravadorAssincrono.esperarPendentes();
        }
        if (Config.ativo(Modulo.CORRECAO_FASTASYNCWORLDSAVE)) {
            esperarFastAsyncWorldSave(60);
        }
    }

    /**
     * O Fast Async World Save grava numa thread daemon e nunca espera ela terminar: se o jogo fecha
     * logo depois de salvar, a ultima gravacao pode se perder. Aqui esperamos a fila dele esvaziar.
     */
    private static void esperarFastAsyncWorldSave(int segundos) {
        try {
            Class<?> classe = Class.forName("com.fastasyncworldsave.FastAsyncWorldSave");
            Field campo = classe.getField("threadPool");
            if (campo.get(null) instanceof ExecutorService executor) {
                GravadorAssincrono.esperar(executor, "Fast Async World Save", segundos);
                Config.LOGGER.info("Gravacoes pendentes do Fast Async World Save concluidas");
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            Config.LOGGER.warn("Nao foi possivel acessar a fila do Fast Async World Save", e);
        }
    }
}
