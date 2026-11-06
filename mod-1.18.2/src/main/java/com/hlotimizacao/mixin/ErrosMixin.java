package com.hlotimizacao.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Anota os mixins de qualquer mod que falham ao aplicar (o Mixin so escreve "Mixin apply failed" no
 * meio do log). O relatorio_conflitos mostra a lista com uma explicacao e, quando conhecida, a correcao.
 * Nao muda a decisao do Mixin (devolve a acao recebida).
 */
public class ErrosMixin implements IMixinErrorHandler {
    public record Falha(String config, String mixin, String alvo, String motivo) {
    }

    public static final List<Falha> FALHAS = new CopyOnWriteArrayList<>();

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable erro, IMixinInfo mixin, ErrorAction acao) {
        anotar(config == null ? "?" : config.getName(), mixin, "-", erro);
        return acao;
    }

    @Override
    public ErrorAction onApplyError(String alvo, Throwable erro, IMixinInfo mixin, ErrorAction acao) {
        anotar(mixin == null || mixin.getConfig() == null ? "?" : mixin.getConfig().getName(), mixin, alvo, erro);
        return acao;
    }

    private static void anotar(String config, IMixinInfo mixin, String alvo, Throwable erro) {
        StringBuilder motivo = new StringBuilder();
        for (Throwable t = erro; t != null && motivo.length() < 600; t = t.getCause()) {
            if (motivo.length() > 0) {
                motivo.append(" <- ");
            }
            motivo.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        }
        FALHAS.add(new Falha(config, mixin == null ? "?" : mixin.getClassName(), alvo, motivo.toString()));
    }
}
