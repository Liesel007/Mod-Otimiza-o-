package com.hlotimizacao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log do mod: traduz o modelo da mensagem (Msg) antes de registrar. Os modelos ficam no codigo em
 * portugues; em ingles por padrao, em portugues se o Java estiver em portugues ou com log.idioma=pt_br.
 */
public final class Log {
    private final Logger logger;

    Log(String nome) {
        this.logger = LoggerFactory.getLogger(nome);
    }

    public void info(String modelo, Object... args) {
        logger.info(Msg.t(modelo), args);
    }

    public void warn(String modelo, Object... args) {
        logger.warn(Msg.t(modelo), args);
    }

    public void error(String modelo, Object... args) {
        logger.error(Msg.t(modelo), args);
    }

    public void debug(String modelo, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(Msg.t(modelo), args);
        }
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }
}
