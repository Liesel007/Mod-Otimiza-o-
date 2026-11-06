package com.hlotimizacao.cliente;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Msg;
import com.hlotimizacao.recursos.Avisos;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le do log o tempo de abertura do JEI ("Starting JEI took 58.30 seconds") e os plugins do JEI que falharam. So olha
 * os loggers do JEI (mezz.jei): o resto do log passa direto. So diagnostico: nada e mudado na config do JEI.
 */
public final class TempoJei {
    private static final Pattern TEMPO = Pattern.compile("Starting JEI took ([0-9.,]+)\\s*(ms|s|seconds)?");
    private static final Pattern PLUGIN = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_/.-]+)");
    private static volatile long ms = -1;
    private static final Set<String> PLUGINS_COM_ERRO = ConcurrentHashMap.newKeySet();

    private TempoJei() {
    }

    public static long ms() {
        return ms;
    }

    public static void instalar() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            AbstractAppender ouvinte = new AbstractAppender("HLO-JEI", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent e) {
                    String logger = e.getLoggerName();
                    if (logger == null || !logger.startsWith("mezz.jei")) {
                        return;
                    }
                    String texto = e.getMessage().getFormattedMessage();
                    if (ms < 0 && texto.startsWith("Starting JEI took")) {
                        lerTempo(texto);
                    } else if (e.getLevel().isMoreSpecificThan(Level.ERROR) && texto.toLowerCase(java.util.Locale.ROOT).contains("plugin")) {
                        Matcher m = PLUGIN.matcher(texto);
                        String id = m.find() ? m.group(1) : texto.length() > 80 ? texto.substring(0, 80) + "…" : texto;
                        if (PLUGINS_COM_ERRO.add(id)) {
                            Avisos.adicionar(Msg.f("JEI: o plugin {} falhou", id),
                                    Msg.f("O JEI registrou um erro no plugin {} (do proprio mod, nao do HLO). As receitas ou integracoes desse plugin podem faltar no JEI. Atualize o mod ou avise o autor; o erro completo esta no log.", id));
                        }
                    }
                }
            };
            ouvinte.start();
            ctx.getConfiguration().getRootLogger().addAppender(ouvinte, Level.INFO, null);
            ctx.updateLoggers();
        } catch (Throwable t) {
            Config.LOGGER.debug("Nao foi possivel acompanhar o log do JEI", t);
        }
    }

    private static void lerTempo(String texto) {
        Matcher m = TEMPO.matcher(texto);
        if (!m.find()) {
            return;
        }
        double valor = Double.parseDouble(m.group(1).replace(',', '.'));
        ms = "ms".equals(m.group(2)) ? Math.round(valor) : Math.round(valor * 1000);
        if (ms > 20_000) {
            Config.LOGGER.info("[relatorio_conflitos] JEI levou {} s para abrir", ms / 1000);
            Avisos.adicionar(Msg.f("JEI levou {} s para abrir", ms / 1000),
                    Msg.t("A maior parte costuma ser montar o indice de busca por tooltip ($) de todos os itens. Em config/jei/jei-client.ini, "
                            + "tooltipSearchMode = DISABLED deixa a entrada no mundo bem mais rapida (a busca por $ deixa de existir). "
                            + "lowMemorySlowSearchEnabled = true so ajuda se a RAM estiver curta."));
        }
    }
}
