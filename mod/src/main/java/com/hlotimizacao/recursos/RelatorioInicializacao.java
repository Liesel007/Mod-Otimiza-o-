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
 * <li>JEI: "Registering recipes: jei:minecraft took 17.91 s" (por plugin), "Registering recipes took 35.20 s" (fase) e
 *     "Starting JEI took 1.490 min" (4 algarismos significativos; ns, us, ms, s, min, h).</li>
 * </ul>
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
    /** Linha de um logger do JEI ([mezz.jei...]): "titulo took duracao". */
    private static final Pattern JEI = Pattern.compile("\\[mezz\\.jei[^\\]]*\\]: (.+?) took (.+)$");
    private static final Pattern JEI_PLUGIN = Pattern.compile("^(.+?): ([a-z0-9_.-]+:[a-z0-9_/.-]+)$");
    private static final Pattern JOGO = Pattern.compile("Game took ([0-9.]+) seconds to start");
    private static final Pattern MENU = Pattern.compile("Time from main menu to in-game was ([0-9.]+) seconds");
    private static final Pattern TOTAL = Pattern.compile("Total time to load game and open world was ([0-9.]+) seconds");

    private static volatile List<Item> top = List.of();
    public static volatile long jogoMs = -1;
    public static volatile long menuMs = -1;
    public static volatile long totalMs = -1;
    public static volatile long jeiMs = -1;
    private static volatile boolean lido;

    private RelatorioInicializacao() {
    }

    /** Converte "16.27 s", "1.490 min", "923.4 ms" para ms; -1 se nao entender. */
    static long ms(String texto) {
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
        boolean noBreakdown = false;
        try (BufferedReader r = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String linha;
            while ((linha = r.readLine()) != null) {
                if (noBreakdown) {
                    String depoisDoColchete = linha.contains("]: ") ? linha.substring(linha.indexOf("]: ") + 3) : linha;
                    Matcher mm = MODELO_MOD.matcher(depoisDoColchete);
                    if (mm.matches() && ms(mm.group(2)) >= 0) {
                        itens.merge("ModelEvent.ModifyBakingResult: " + mm.group(1), ms(mm.group(2)), Math::max);
                        continue;
                    }
                    noBreakdown = false;
                }
                Matcher m;
                if ((m = MODELOS.matcher(linha)).find()) {
                    itens.merge("ModelEvent.ModifyBakingResult " + Msg.t("(total)"), ms(m.group(1)), Math::max);
                    noBreakdown = true;
                } else if ((m = JEI.matcher(linha)).find()) {
                    long v = ms(m.group(2));
                    Matcher plugin = JEI_PLUGIN.matcher(m.group(1));
                    if (m.group(1).equals("Starting JEI")) {
                        jeiMs = Math.max(jeiMs, v);
                    } else if (plugin.matches()) {
                        itens.merge("JEI " + plugin.group(1) + ": " + plugin.group(2), v, Math::max);
                    } else {
                        itens.merge("JEI " + m.group(1) + " " + Msg.t("(total)"), v, Math::max);
                    }
                } else if ((m = JOGO.matcher(linha)).find()) {
                    jogoMs = Math.round(Double.parseDouble(m.group(1)) * 1000);
                } else if ((m = MENU.matcher(linha)).find()) {
                    menuMs = Math.round(Double.parseDouble(m.group(1)) * 1000);
                } else if ((m = TOTAL.matcher(linha)).find()) {
                    totalMs = Math.round(Double.parseDouble(m.group(1)) * 1000);
                }
            }
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

    private static String s(long ms) {
        return ms < 0 ? "?" : String.format(Msg.locale(), "%.1f s", ms / 1000.0);
    }

    private static void registrar() {
        if (top.isEmpty() && totalMs < 0 && jeiMs < 0) {
            return;
        }
        StringBuilder sb = new StringBuilder(Msg.f("[relatorio_inicializacao] Abertura: jogo {}, menu ate o mundo {}, total {}, JEI {}. Mais lentos:",
                s(jogoMs), s(menuMs), s(totalMs), s(jeiMs)));
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
            Avisos.substituir("inicializacao:jei", Msg.f("JEI levou {} para abrir", s(jeiMs)),
                    Msg.t("Em config/jei/jei-client.ini, tooltipSearchMode = DISABLED pula o indice de busca por tooltip. Os plugins mais lentos estao no topo do relatorio de inicializacao (aba FPS)."));
        }
    }

    public static List<Item> top() {
        return top;
    }
}
