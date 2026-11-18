package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Msg;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * relatorio_inicializacao: junta o que atrasou a abertura do jogo, a partir do proprio log da sessao (logs/latest.log),
 * com os formatos conferidos no codigo de quem escreve as linhas:
 * <ul>
 * <li>ModernFix: "Posting dynamic ModelEvent.ModifyBakingResult to mods took 22.51 s, breakdown below:" seguido de
 *     "    lantea: 16.27 s" (Stopwatch da Guava); "Game took N seconds to start"; "Time from main menu to in-game was N
 *     seconds"; "Total time to load game and open world was N seconds".</li>
 * <li>JEI: "Registering recipes: jei:minecraft took 17.91 seconds" (por plugin), "Registering recipes took 35.20 seconds"
 *     (fase) e "Starting JEI took 1.490 minutes" (4 algarismos significativos; a unidade e o nome do TimeUnit em
 *     minusculas: milliseconds, seconds, minutes...). Sem a linha final, o tempo vai do "Starting JEI..." ate a ultima
 *     linha de "Sending Runtime" (marcado "medido pelo log").</li>
 * </ul>
 * O latest.log do Forge abrevia o nome do logger ({@code %c{2.}}: "mezz.jei.library.load.PluginCaller" vira
 * "me.je.li.lo.PluginCaller"); a 0.5.0 procurava "[mezz.jei" e nunca achava nada no log real. Agora a linha e
 * separada em hora, logger e mensagem, e o JEI e reconhecido pelo logger abreviado ou completo.
 * O Forge 47 nao registra o tempo das fases de carregamento (CONSTRUCT, COMMON_SETUP...) por mod, entao elas nao
 * entram. So le o log; nada e mudado. Lido uma vez, numa thread de fundo, depois de entrar no mundo.
 */
public final class RelatorioInicializacao {
    public record Item(String rotulo, long ms) {
    }

    private static final Pattern DURACAO = Pattern.compile(
            "([0-9]+(?:[.,][0-9]+)?)\\s*(nanoseconds|microseconds|milliseconds|seconds|minutes|hours|days|ns|μs|us|ms|sec|min|s|h|d)\\b");
    private static final Pattern MODELOS = Pattern.compile("Posting dynamic ModelEvent\\.ModifyBakingResult to mods took (.+?), breakdown");
    private static final Pattern MODELO_MOD = Pattern.compile("^\\s{2,}([a-z0-9_.-]+): (.+)$");
    /**
     * "[03:39:21] [Render thread/INFO] [me.je.co.ut.LoggedTimer/]: mensagem" (log4j do Forge) ou
     * "[03:39:21] [Render thread/INFO]: mensagem" (log4j do launcher vanilla, usado pelo CurseForge e outros, sem o
     * nome do logger) -> hora, logger (pode faltar), mensagem.
     */
    private static final Pattern LINHA = Pattern.compile("^\\[([0-9]{1,2}):([0-9]{2}):([0-9]{2})(?:\\.[0-9]+)?\\] \\[[^\\]]*\\](?: \\[([^\\]]*)\\])?: (.*)$");
    /** Logger do JEI: "mezz.jei.(...)" completo ou "me.je.(...)" abreviado. */
    private static final Pattern LOGGER_JEI = Pattern.compile("^(mezz\\.jei\\.|me\\.je\\.|[^/]*jei)", Pattern.CASE_INSENSITIVE);
    /** Sem o nome do logger: so as mensagens que o JEI escreve (LoggedTimer e PluginCaller). */
    private static final Pattern MENSAGEM_JEI = Pattern.compile(
            "^(Starting JEI(\\.\\.\\.)?|Sending Runtime.*|(Starting JEI|Registering [A-Za-z ]+|Sending Runtime|Building [A-Za-z ]+|Rebuilding ingredient filter)(: [a-z0-9_.-]+:[a-z0-9_/.-]+)? took .+)$");
    private static final Pattern JEI_TOOK = Pattern.compile("^(.+?) took (.+)$");
    private static final Pattern JEI_PLUGIN = Pattern.compile("^(.+?): ([a-z0-9_.-]+:[a-z0-9_/.-]+)$");
    private static final Pattern JOGO = Pattern.compile("Game took ([0-9.]+) seconds to start");
    private static final Pattern MENU = Pattern.compile("Time from main menu to in-game was ([0-9.]+) seconds");
    private static final Pattern TOTAL = Pattern.compile("Total time to load game and open world was ([0-9.]+) seconds");

    private static volatile List<Item> top = List.of();
    public static volatile long jogoMs = -1;
    public static volatile long menuMs = -1;
    public static volatile long totalMs = -1;
    public static volatile long jeiMs = -1;
    /** O tempo do JEI veio do intervalo entre linhas (sem a linha "Starting JEI took"). */
    public static volatile boolean jeiMedido;
    private static volatile boolean lido;

    private RelatorioInicializacao() {
    }

    /** Converte "16.27 s", "1.490 min", "923.4 ms" para ms; -1 se nao entender. */
    public static long ms(String texto) {
        Matcher m = DURACAO.matcher(texto.trim());
        if (!m.find()) {
            return -1;
        }
        double v = Double.parseDouble(m.group(1).replace(',', '.'));
        return switch (m.group(2)) {
            case "ns", "nanoseconds" -> 0;
            case "μs", "us", "microseconds" -> Math.round(v / 1000);
            case "ms", "milliseconds" -> Math.round(v);
            case "s", "sec", "seconds" -> Math.round(v * 1000);
            case "min", "minutes" -> Math.round(v * 60_000);
            case "h", "hours" -> Math.round(v * 3_600_000);
            default -> Math.round(v * 86_400_000);
        };
    }

    /** Le o log e monta o top 10 (thread de fundo). Chamado uma vez depois de entrar no mundo. */
    public static void lerEmSegundoPlano(Path log) {
        if (lido) {
            return;
        }
        lido = true;
        Thread t = new Thread(() -> {
            try {
                analisar(log);
            } catch (Throwable e) {
                Config.LOGGER.debug("[relatorio_inicializacao] Nao foi possivel ler o log", e);
            }
        }, "HLO - relatorio_inicializacao");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** Analisa as linhas (publico para o teste com um log de exemplo). */
    public static List<Item> analisar(Path log) throws IOException {
        Map<String, Long> itens = new HashMap<>();
        jogoMs = menuMs = totalMs = jeiMs = -1;
        jeiMedido = false;
        long jeiInicio = -1;
        long jeiFim = -1;
        boolean noBreakdown = false;
        try (BufferedReader r = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String linha;
            while ((linha = r.readLine()) != null) {
                Matcher partes = LINHA.matcher(linha);
                boolean temCabecalho = partes.matches();
                String mensagem = temCabecalho ? partes.group(5) : linha;
                if (noBreakdown) {
                    Matcher mm = MODELO_MOD.matcher(mensagem);
                    if (mm.matches() && ms(mm.group(2)) >= 0) {
                        itens.merge("ModelEvent.ModifyBakingResult: " + mm.group(1), ms(mm.group(2)), Math::max);
                        continue;
                    }
                    noBreakdown = false;
                }
                Matcher m;
                boolean jei = temCabecalho && (partes.group(4) != null ? LOGGER_JEI.matcher(partes.group(4)).find() : MENSAGEM_JEI.matcher(mensagem).matches());
                if (jei) {
                    long hora = (Long.parseLong(partes.group(1)) * 3600 + Long.parseLong(partes.group(2)) * 60 + Long.parseLong(partes.group(3))) * 1000;
                    if (mensagem.equals("Starting JEI...")) {
                        jeiInicio = hora;
                        jeiFim = -1;
                    } else if (mensagem.startsWith("Sending Runtime") && !mensagem.startsWith("Sending Runtime Unavailable") && jeiInicio >= 0) {
                        jeiFim = hora;
                    }
                    if ((m = JEI_TOOK.matcher(mensagem)).matches()) {
                        long v = ms(m.group(2));
                        Matcher plugin = JEI_PLUGIN.matcher(m.group(1));
                        if (m.group(1).equals("Starting JEI")) {
                            jeiMs = Math.max(jeiMs, v);
                        } else if (plugin.matches()) {
                            itens.merge("JEI " + plugin.group(1) + ": " + plugin.group(2), v, Math::max);
                        } else {
                            itens.merge("JEI " + m.group(1) + " " + Msg.t("(total)"), v, Math::max);
                        }
                    }
                } else if ((m = MODELOS.matcher(mensagem)).find()) {
                    itens.merge("ModelEvent.ModifyBakingResult " + Msg.t("(total)"), ms(m.group(1)), Math::max);
                    noBreakdown = true;
                } else if ((m = JOGO.matcher(mensagem)).find()) {
                    jogoMs = Math.round(Double.parseDouble(m.group(1)) * 1000);
                } else if ((m = MENU.matcher(mensagem)).find()) {
                    menuMs = Math.round(Double.parseDouble(m.group(1)) * 1000);
                } else if ((m = TOTAL.matcher(mensagem)).find()) {
                    totalMs = Math.round(Double.parseDouble(m.group(1)) * 1000);
                }
            }
        }
        if (jeiMs < 0 && jeiInicio >= 0 && jeiFim >= 0) {
            jeiMs = jeiFim >= jeiInicio ? jeiFim - jeiInicio : jeiFim + 86_400_000L - jeiInicio; // passou da meia-noite
            jeiMedido = true;
        }
        List<Item> lista = new ArrayList<>();
        itens.forEach((k, v) -> {
            if (v > 0) {
                lista.add(new Item(k, v));
            }
        });
        lista.sort((a, b) -> Long.compare(b.ms(), a.ms()));
        top = List.copyOf(lista.subList(0, Math.min(10, lista.size())));
        registrar();
        return top;
    }

    /** "63,8 s" ou "63,8 s (medido pelo log)" ou "?". */
    public static String jeiTexto() {
        return jeiMs < 0 ? "?" : s(jeiMs) + (jeiMedido ? Msg.t(" (medido pelo log)") : "");
    }

    private static String s(long ms) {
        return ms < 0 ? "?" : String.format(Msg.locale(), "%.1f s", ms / 1000.0);
    }

    private static void registrar() {
        if (top.isEmpty() && totalMs < 0 && jeiMs < 0) {
            return;
        }
        StringBuilder sb = new StringBuilder(Msg.f("[relatorio_inicializacao] Abertura: jogo {}, menu ate o mundo {}, total {}, JEI {}. Mais lentos:",
                s(jogoMs), s(menuMs), s(totalMs), jeiTexto()));
        for (Item i : top) {
            sb.append("\n  ").append(i.rotulo()).append(": ").append(s(i.ms()));
        }
        Config.LOGGER.info(sb.toString());
        for (Item i : top) {
            // Um unico mod (nao um total) acima de 10 s numa fase: candidato a reportar ao autor.
            // jei:minecraft e o proprio JEI registrando as receitas do vanilla: nao ha autor de mod a avisar.
            if (i.ms() >= 10_000 && !i.rotulo().contains(Msg.t("(total)")) && !i.rotulo().contains(": jei:")) {
                Avisos.substituir("inicializacao:" + i.rotulo(), Msg.f("Abertura: {} levou {}", i.rotulo(), s(i.ms())),
                        Msg.f("{} levou {} na abertura do jogo (e de novo a cada recarga de recursos, F3+T). Um unico mod acima de 10 s numa fase e candidato a reportar ao autor.",
                                i.rotulo(), s(i.ms())));
            }
        }
        if (jeiMs > 30_000) {
            Avisos.substituir("inicializacao:jei", Msg.f("JEI levou {} para abrir", jeiTexto()),
                    Msg.t("Em config/jei/jei-client.ini, tooltipSearchMode = DISABLED pula o indice de busca por tooltip. Os plugins mais lentos estao no topo do relatorio de inicializacao (aba FPS)."));
        }
    }

    public static List<Item> top() {
        return top;
    }
}
