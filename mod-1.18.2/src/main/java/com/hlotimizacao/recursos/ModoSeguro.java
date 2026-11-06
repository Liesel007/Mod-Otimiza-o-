package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.config.Msg;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;

/**
 * Modo seguro: um modulo que lanca excecao num ponto de entrada (mixin ou evento) deixa o jogo seguir
 * pelo caminho original naquela chamada; depois de LIMITE erros ele fica desligado ate reiniciar o jogo.
 */
public final class ModoSeguro {
    public static final int LIMITE = 3;
    private static final boolean[] DESLIGADO = new boolean[Modulo.values().length];
    private static final AtomicIntegerArray FALHAS = new AtomicIntegerArray(Modulo.values().length);

    /** So para testes: -Dhlotimizacao.teste.falhar=<modulo> faz esse modulo lancar excecao nos pontos de entrada. */
    private static final String TESTE = System.getProperty("hlotimizacao.teste.falhar", "");

    private ModoSeguro() {
    }

    /** Ponto de teste do modo seguro (nao faz nada sem a propriedade). */
    public static void testar(Modulo modulo) {
        if (!TESTE.isEmpty() && TESTE.equals(modulo.chave)) {
            throw new IllegalStateException("hlotimizacao.teste.falhar=" + modulo.chave);
        }
    }

    /** Leitura barata para caminhos quentes. */
    public static boolean desligado(Modulo modulo) {
        return DESLIGADO[modulo.ordinal()];
    }

    public static int falhas(Modulo modulo) {
        return FALHAS.get(modulo.ordinal());
    }

    public static void falhou(Modulo modulo, Throwable erro) {
        int n = FALHAS.incrementAndGet(modulo.ordinal());
        if (n > LIMITE) {
            return;
        }
        Config.LOGGER.error("[modo_seguro] Erro no modulo {} ({} de {})", modulo.chave, n, LIMITE, erro);
        if (n == LIMITE) {
            DESLIGADO[modulo.ordinal()] = true;
            String motivo = Msg.f("desligado pelo modo seguro depois de {} erros (ate reiniciar)", LIMITE);
            Config.desligarEmExecucao(modulo, motivo, Msg.t("modo seguro: desligado ate reiniciar"));
            Config.LOGGER.error("[modo_seguro] Modulo {} desligado ate reiniciar o jogo depois de {} erros. Mande o log para o autor do HLO; para nao ligar de novo, use modulo.{}=off.",
                    modulo.chave, LIMITE, modulo.chave);
            Avisos.adicionar(Msg.f("{}: desligado pelo modo seguro", modulo.chave),
                    Msg.f("O modulo {} deu {} erros e foi desligado ate reiniciar o jogo; o jogo segue sem ele. O erro esta no log (procure por modo_seguro). Ultimo erro: {}",
                            modulo.chave, LIMITE, String.valueOf(erro)));
        }
    }

    /**
     * Registra um ouvinte de evento que mede o proprio custo e, com modulo != null, passa pelo modo seguro.
     * O tipo do evento e explicito porque o EventBus nao descobre o tipo de um Consumer generico.
     */
    public static <E extends Event> void ouvir(Modulo modulo, Class<E> tipo, Consumer<E> ouvinte) {
        ouvir(modulo, EventPriority.NORMAL, tipo, ouvinte);
    }

    public static <E extends Event> void ouvir(Modulo modulo, EventPriority prioridade, Class<E> tipo, Consumer<E> ouvinte) {
        MinecraftForge.EVENT_BUS.addListener(prioridade, false, tipo, evento -> {
            if (modulo != null && DESLIGADO[modulo.ordinal()]) {
                return;
            }
            long inicio = System.nanoTime();
            try {
                if (modulo != null) {
                    testar(modulo);
                }
                ouvinte.accept(evento);
            } catch (RuntimeException | LinkageError e) {
                if (modulo == null) {
                    throw e;
                }
                falhou(modulo, e);
            } finally {
                Custo.somar(System.nanoTime() - inicio);
            }
        });
    }
}
