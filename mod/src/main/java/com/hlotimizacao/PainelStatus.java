package com.hlotimizacao;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.mixin.ErrosMixin;
import com.hlotimizacao.mixin.lag.LevelAccessor;
import com.hlotimizacao.recursos.Avisos;
import com.hlotimizacao.recursos.ChavesInfinityChest;
import com.hlotimizacao.recursos.Desempenho;
import com.hlotimizacao.recursos.DiagnosticoAmbiente;
import com.hlotimizacao.recursos.DiagnosticoLag;
import com.hlotimizacao.recursos.Estatisticas;
import com.hlotimizacao.recursos.LimpezaItens;
import com.hlotimizacao.recursos.RelatorioConflitos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paginas do /hl status. Cada metodo devolve as linhas prontas para o chat (cores, dica ao passar o
 * mouse, clique). Para o console (sem cores nem simbolos que o console do Windows pode nao mostrar),
 * use {@code console = true}.
 */
public final class PainelStatus {
    public static final Locale PT = Locale.forLanguageTag("pt-BR");
    /** Largura maxima (caracteres visiveis) de uma linha, para nao quebrar no chat padrao. */
    public static final int LARGURA = 55;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MinecraftServer server;
    private final boolean console;
    private final boolean podeAdmin;
    private final String prefixo;

    public PainelStatus(MinecraftServer server, boolean console, boolean podeAdmin, String prefixo) {
        this.server = server;
        this.console = console;
        this.podeAdmin = podeAdmin;
        this.prefixo = prefixo;
    }

    // ---------- simbolos e formatacao ----------

    private String ok() {
        return console ? "[ok]" : "✔";
    }

    private String nao() {
        return console ? "[x]" : "✘";
    }

    private String alerta() {
        return console ? "[!]" : "⚠";
    }

    private String ponto() {
        return console ? "-" : "·";
    }

    private MutableComponent titulo(String texto) {
        String barra = console ? "==========" : "━━━━━━━━━━";
        return Component.literal(barra + " " + texto + " " + barra).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private MutableComponent rodape() {
        return Component.literal(console ? "=".repeat(40) : "━".repeat(30)).withStyle(ChatFormatting.GOLD);
    }

    static String num(long n) {
        return String.format(PT, "%,d", n);
    }

    static String dec(double v) {
        return String.format(PT, "%.1f", v);
    }

    /** 1h 27min, 6min 12s, 45s. */
    static String tempo(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) {
            return String.format(PT, "%dh %02dmin", s / 3600, (s % 3600) / 60);
        }
        if (s >= 60) {
            return String.format(PT, "%dmin %02ds", s / 60, s % 60);
        }
        return s + "s";
    }

    /** 3,8 s ou 812 ms. */
    static String duracao(long ms) {
        return ms >= 1000 ? dec(ms / 1000.0) + " s" : ms + " ms";
    }

    /** Texto sem os caracteres que o console do Windows pode nao mostrar. */
    public static String paraConsole(String texto) {
        return texto.replace("·", "-").replace("…", "...").replace("–", "-").replace("✔", "[ok]").replace("✘", "[x]")
                .replace("⚠", "[!]").replace("━", "=");
    }

    static String cortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String preencher(String texto, int largura) {
        return texto.length() >= largura ? texto + " " : texto + " ".repeat(largura - texto.length());
    }

    private static MutableComponent t(String texto, ChatFormatting... estilo) {
        return Component.literal(texto).withStyle(estilo);
    }

    private MutableComponent hover(MutableComponent c, String dica) {
        if (dica == null || dica.isEmpty()) {
            return c;
        }
        return c.withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(dica))));
    }

    private MutableComponent clique(MutableComponent c, String subcomando, String dica) {
        String comando = "/" + prefixo + " " + subcomando;
        return c.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, comando))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal((dica == null ? "" : dica + "\n") + "Clique: " + comando))));
    }

    private static ChatFormatting corMspt(double ms) {
        return ms < 40 ? ChatFormatting.GREEN : ms <= 50 ? ChatFormatting.YELLOW : ChatFormatting.RED;
    }

    private static ChatFormatting corTps(double tps) {
        return tps >= 19.5 ? ChatFormatting.GREEN : tps >= 15 ? ChatFormatting.YELLOW : ChatFormatting.RED;
    }

    private static ChatFormatting corMemoria(double fracao) {
        return fracao < 0.70 ? ChatFormatting.GREEN : fracao <= 0.85 ? ChatFormatting.YELLOW : ChatFormatting.RED;
    }

    private static String versaoMod() {
        return ModList.get().getModContainerById(HLOtimizacao.MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    // ---------- paginas ----------

    public List<Component> visaoGeral() {
        List<Component> linhas = new ArrayList<>();
        linhas.add(titulo("HL Otimização " + versaoMod()));
        linhas.add(t(" Desempenho", ChatFormatting.WHITE, ChatFormatting.BOLD));
        Desempenho.Tempos tempos = Desempenho.tempos();
        double media = tempos.ticks() > 0 ? tempos.mediaMs() : server.getAverageTickTime();
        double tps = tempos.ticks() > 0 ? tempos.tps() : Math.min(20, 1000 / Math.max(50, media));
        linhas.add(hover(t("   TPS ", ChatFormatting.GRAY).append(t(dec(tps), corTps(tps)))
                        .append(t("  MSPT ", ChatFormatting.GRAY)).append(t(dec(media) + " ms", corMspt(media)))
                        .append(t("  pico " + Math.round(tempos.maxMs()) + " ms", ChatFormatting.GRAY)),
                "Últimos " + tempos.ticks() + " ticks. O pico não conta os ticks em que o mundo foi salvo ("
                        + tempos.ticksSalvamento() + " nesse intervalo); veja " + "/" + prefixo + " status desempenho."));
        Runtime rt = Runtime.getRuntime();
        long usada = rt.totalMemory() - rt.freeMemory();
        double fracao = (double) usada / rt.maxMemory();
        long sessao = Desempenho.sessaoMs();
        linhas.add(t("   Memória ", ChatFormatting.GRAY)
                .append(t(String.format(PT, "%.1f / %.1f GB (%d%%)", usada / 1e9, rt.maxMemory() / 1e9, Math.round(fracao * 100)), corMemoria(fracao)))
                .append(t(sessao >= 0 ? "  Sessão " + tempo(sessao) : "", ChatFormatting.GRAY)));

        int ligados = 0;
        int desligados = 0;
        for (Modulo m : Modulo.values()) {
            if (Config.ativo(m)) {
                ligados++;
            } else {
                desligados++;
            }
        }
        linhas.add(clique(t(" Módulos ", ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(t(ligados + (ligados == 1 ? " ligado " : " ligados ") + ponto() + " " + desligados
                        + (desligados == 1 ? " desligado" : " desligados"), ChatFormatting.GRAY)), "status modulos", null));

        if (Config.ativo(Modulo.LIMPEZA_ITENS)) {
            long[] chao = LimpezaItens.contarNoChao(server);
            long faltam = LimpezaItens.segundosParaProxima(server);
            String quando = LimpezaItens.limpandoAgora() ? "limpando agora" : faltam < 0 ? "não agendada" : "em " + tempo(faltam * 1000);
            MutableComponent l = t(" Limpeza ", ChatFormatting.WHITE, ChatFormatting.BOLD)
                    .append(t(quando + " " + ponto() + " " + num(chao[1]) + " itens no chão", ChatFormatting.GRAY));
            linhas.add(podeAdmin ? clique(l, "limpar status", null) : l);
        }

        if (Config.ativo(Modulo.DIAGNOSTICO_LAG)) {
            int n = (int) Estatisticas.LAG_REGISTROS.get();
            MutableComponent l = t(" Lag ", ChatFormatting.WHITE, ChatFormatting.BOLD);
            if (n == 0) {
                l.append(t("nenhum travamento na sessão", ChatFormatting.GREEN));
            } else {
                DiagnosticoLag.Registro pior = DiagnosticoLag.pior();
                l.append(t(n + " travamento" + (n == 1 ? "" : "s"), ChatFormatting.YELLOW));
                if (pior != null) {
                    l.append(t(" " + ponto() + " pior " + duracao(pior.duracaoMs()), ChatFormatting.GRAY));
                }
                String culpado = maisCulpado();
                if (culpado != null) {
                    l.append(t(" " + ponto() + " " + culpado, ChatFormatting.GRAY));
                }
            }
            linhas.add(clique(l, "status lag", null));
        }

        int nAvisos = contarAvisos();
        MutableComponent l = t(" Avisos ", ChatFormatting.WHITE, ChatFormatting.BOLD);
        if (nAvisos == 0) {
            l.append(t(ok() + " nenhum", ChatFormatting.GREEN));
        } else {
            l.append(t(alerta() + " " + nAvisos + " aviso" + (nAvisos == 1 ? "" : "s"), ChatFormatting.YELLOW));
            List<String> destaques = new ArrayList<>();
            if (!Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.isEmpty()) {
                destaques.add(Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.size() + " síncrona(s)");
            }
            if (DiagnosticoAmbiente.arquivosLentos() > 0) {
                destaques.add(DiagnosticoAmbiente.arquivosLentos() + " arquivo(s) lento(s)");
            }
            if (!destaques.isEmpty()) {
                l.append(t(" (" + String.join(", ", destaques) + ")", ChatFormatting.GRAY));
            }
        }
        linhas.add(clique(l, "status avisos", null));
        linhas.add(rodape());
        MutableComponent mais = t(" Mais: ", ChatFormatting.GRAY);
        String[] paginas = {"modulos", "desempenho", "lag", "avisos"};
        for (int i = 0; i < paginas.length; i++) {
            if (i > 0) {
                mais.append(t(" | ", ChatFormatting.DARK_GRAY));
            }
            mais.append(clique(t(paginas[i], ChatFormatting.AQUA), "status " + paginas[i], null));
        }
        linhas.add(mais);
        return linhas;
    }

    /** "avaritia 41%" pelo tempo de travamento atribuido a cada mod na sessao. */
    private static String maisCulpado() {
        long total = DiagnosticoLag.msTotal();
        if (total <= 0) {
            return null;
        }
        return DiagnosticoLag.msPorMod().entrySet().stream()
                .filter(e -> !e.getKey().equals(DiagnosticoLag.SEM_MOD))
                .max(Map.Entry.comparingByValue())
                .map(e -> cortar(e.getKey(), 16) + " " + Math.round(100.0 * e.getValue() / total) + "%")
                .orElse(null);
    }

    private int contarAvisos() {
        int n = Avisos.quantidade();
        if (!Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.isEmpty()) {
            n++;
        }
        if (DiagnosticoAmbiente.arquivosLentos() > 0) {
            n++;
        }
        if (Desempenho.pausasGcLongas() > 0) {
            n++;
        }
        return n;
    }

    public List<Component> modulos() {
        List<Component> linhas = new ArrayList<>();
        linhas.add(titulo("Módulos"));
        List<Modulo> ordem = new ArrayList<>(List.of(Modulo.values()));
        // Ligados primeiro, depois desligados, depois os "nao necessario".
        ordem.sort(Comparator.comparingInt(m -> Config.ativo(m) ? 0 : Config.naoNecessario(m) ? 2 : 1));
        boolean dedicado = FMLEnvironment.dist == Dist.DEDICATED_SERVER;
        for (Modulo m : ordem) {
            boolean ativo = Config.ativo(m);
            String simbolo = ativo ? ok() : Config.naoNecessario(m) ? (console ? "-" : "–") : nao();
            ChatFormatting cor = ativo ? ChatFormatting.GREEN : Config.naoNecessario(m) ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY;
            String nome = preencher(m.nomeAmigavel, 27);
            int espaco = LARGURA - 3 - nome.length();
            MutableComponent linha = t(" " + simbolo + " ", cor).append(t(nome, ativo ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            String detalhe;
            if (!ativo) {
                linha.append(t(cortar(Config.naoNecessario(m) ? "não necessário" : Config.motivoCurto(m), espaco), cor));
                detalhe = Config.motivo(m);
            } else if (m.soCliente && dedicado) {
                linha.append(t("(só no cliente)", ChatFormatting.GRAY));
                detalhe = "Este módulo só age no cliente; no servidor dedicado não há contadores.";
            } else {
                linha.append(t(cortar(resumoCurto(m), espaco), ChatFormatting.GRAY));
                detalhe = detalhe(m);
            }
            String dica = m.descricao + "\n\nConfig: modulo." + m.chave + "=" + Config.valor(m) + " (" + Config.motivo(m) + ")"
                    + (detalhe == null || detalhe.isEmpty() ? "" : "\n\n" + detalhe);
            linhas.add(hover(linha, dica));
        }
        return linhas;
    }

    /** Resumo de uma linha para a pagina de modulos. */
    static String resumoCurto(Modulo m) {
        return switch (m) {
            case DESPAWN_ITENS -> num(Estatisticas.DESPAWN_MOBS_LIBERADOS.get()) + " mobs · "
                    + num(Estatisticas.DESPAWN_ITENS_DEVOLVIDOS.get()) + " itens devolvidos";
            case SALVAMENTO_ASSINCRONO -> num(Estatisticas.SALVAMENTO_ASSINCRONOS.get()) + " em outra thread";
            case VAZAMENTO_GPU -> num(Estatisticas.GPU_FRAMEBUFFERS.get()) + " framebuffers · " + num(Estatisticas.GPU_TEXTURAS.get()) + " texturas";
            case TOOLTIP_SEGURO -> num(Estatisticas.TOOLTIP_ITENS.size()) + " itens corrigidos";
            case DIAGNOSTICO_LAG -> num(Estatisticas.LAG_REGISTROS.get()) + " travamentos registrados";
            case CORRECAO_INFINITY_CHEST -> num(ChavesInfinityChest.BAUS_CORRIGIDOS.get()) + " baús · "
                    + num(ChavesInfinityChest.CHAVES_CORRIGIDAS.get()) + " chaves";
            case LIMPEZA_ITENS -> num(LimpezaItens.LIMPEZAS.get()) + " limpezas · " + num(LimpezaItens.ITENS_REMOVIDOS.get()) + " itens";
            case RELATORIO_CONFLITOS -> num(RelatorioConflitos.avisosInicializacao) + " avisos na inicialização";
            case GATILHO_INVENTARIO, FPS_SEM_FOCO, TOASTS_VAZIOS, CORRECAO_FASTASYNCWORLDSAVE -> "ativo";
        };
    }

    /** Texto completo (hover) de um modulo ligado. */
    static String detalhe(Modulo m) {
        return switch (m) {
            case SALVAMENTO_ASSINCRONO -> String.format(PT, "%s arquivo(s) gravado(s) em outra thread, %s no thread principal, %s falha(s) regravada(s) depois.%s",
                    num(Estatisticas.SALVAMENTO_ASSINCRONOS.get()), num(Estatisticas.SALVAMENTO_SINCRONOS.get()), num(Estatisticas.SALVAMENTO_FALHAS.get()),
                    Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.isEmpty() ? ""
                            : "\nClasses gravadas no thread principal (save(File) próprio): " + String.join(", ", Estatisticas.SALVAMENTO_CLASSES_SINCRONAS));
            case TOOLTIP_SEGURO -> Estatisticas.TOOLTIP_ITENS.isEmpty() ? "Nenhum item com linha nula até agora."
                    : "Itens corrigidos: " + String.join(", ", Estatisticas.TOOLTIP_ITENS);
            case DESPAWN_ITENS -> "Mobs que pegaram itens e continuaram podendo desaparecer, e itens que eles devolveram ao desaparecer.";
            case CORRECAO_INFINITY_CHEST -> String.format(PT, "%s baú(s) corrigido(s) ao carregar o mundo, %s chave(s) consertada(s), %s entrada(s) "
                            + "duplicada(s) somada(s). Cópia do original: <arquivo>.dat.hlbak em data/avaritia/infinity_chest.",
                    num(ChavesInfinityChest.BAUS_CORRIGIDOS.get()), num(ChavesInfinityChest.CHAVES_CORRIGIDAS.get()),
                    num(ChavesInfinityChest.ENTRADAS_MESCLADAS.get()));
            case LIMPEZA_ITENS -> num(LimpezaItens.LIMPEZAS.get()) + " limpeza(s), " + num(LimpezaItens.ITENS_REMOVIDOS.get()) + " item(ns) removido(s).";
            default -> "";
        };
    }

    public List<Component> desempenho() {
        List<Component> linhas = new ArrayList<>();
        linhas.add(titulo("Desempenho"));
        Desempenho.Tempos tempos = Desempenho.tempos();
        linhas.add(t(" Servidor ", ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(t("TPS ", ChatFormatting.GRAY)).append(t(dec(tempos.tps()), corTps(tempos.tps())))
                .append(t(" " + ponto() + " MSPT ", ChatFormatting.GRAY)).append(t(dec(tempos.mediaMs()) + " ms", corMspt(tempos.mediaMs()))));
        linhas.add(hover(t(String.format(PT, "   mín %d ms %s máx %d ms (sem salvamento)", Math.round(tempos.minMs()), ponto(), Math.round(tempos.maxMs())),
                ChatFormatting.GRAY), "Últimos " + tempos.ticks() + " ticks; " + tempos.ticksSalvamento() + " deles tiveram salvamento do mundo."));
        if (Desempenho.ultimoSalvamentoHora() != null) {
            linhas.add(t("   último salvamento: " + duracao(Desempenho.ultimoSalvamentoMs()) + " às "
                    + Desempenho.ultimoSalvamentoHora().format(HORA), ChatFormatting.GRAY));
        }
        Runtime rt = Runtime.getRuntime();
        long usada = rt.totalMemory() - rt.freeMemory();
        linhas.add(t(" Memória ", ChatFormatting.WHITE, ChatFormatting.BOLD).append(t(String.format(PT, "usada %.1f GB", usada / 1e9),
                        corMemoria((double) usada / rt.maxMemory())))
                .append(t(String.format(PT, " %s alocada %.1f %s máx %.1f GB", ponto(), rt.totalMemory() / 1e9, ponto(), rt.maxMemory() / 1e9), ChatFormatting.GRAY)));
        int pausas = Desempenho.pausasGcLongas();
        linhas.add(t(pausas == 0 ? "   coleta de lixo: nenhuma pausa > 500 ms"
                        : String.format(PT, "   coleta de lixo: %d pausa(s) > 500 ms, maior %s", pausas, duracao(Desempenho.maiorPausaGcMs())),
                pausas == 0 ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        linhas.add(t(" Dimensões ", ChatFormatting.WHITE, ChatFormatting.BOLD).append(t("(só as com algo carregado)", ChatFormatting.GRAY)));
        int vazias = 0;
        for (ServerLevel level : server.getAllLevels()) {
            int jogadores = level.players().size();
            int chunks = level.getChunkSource().getLoadedChunksCount();
            int entidades = 0;
            for (Entity ignorada : level.getAllEntities()) {
                entidades++;
            }
            if (jogadores == 0 && chunks == 0 && entidades == 0) {
                vazias++;
                continue;
            }
            String nome = cortar(level.dimension().location().getPath(), 12);
            String be = Config.ativo(Modulo.DIAGNOSTICO_LAG)
                    ? " " + ponto() + " " + num(((LevelAccessor) level).hlotimizacao$blockEntityTickers().size()) + " BE" : "";
            linhas.add(hover(t(String.format(PT, "   %s %dj %s %s chunks %s %s ent%s", preencher(nome, 12), jogadores, ponto(), num(chunks), ponto(),
                            num(entidades), be), ChatFormatting.GRAY),
                    level.dimension().location() + ": " + jogadores + " jogador(es), " + chunks + " chunks carregados, " + entidades
                            + " entidades" + (be.isEmpty() ? "" : ", block entities que tickam")));
        }
        if (vazias > 0) {
            linhas.add(t("   +" + vazias + " dimensão(ões) vazia(s)", ChatFormatting.DARK_GRAY));
        }
        Runtime.Version v = Runtime.version();
        String fornecedor = cortar(System.getProperty("java.vendor", "?"), 18);
        MutableComponent java = t(" Java ", ChatFormatting.WHITE, ChatFormatting.BOLD).append(t(v.feature() + "." + v.interim() + "." + v.update()
                + " (" + fornecedor + ")", ChatFormatting.GRAY));
        if (!DiagnosticoAmbiente.temAjusteGc()) {
            java.append(hover(t(" " + ponto() + " GC: nenhum " + alerta(), ChatFormatting.YELLOW), DiagnosticoAmbiente.FLAGS_G1));
        }
        linhas.add(java);
        return linhas;
    }

    public List<Component> lag() {
        List<Component> linhas = new ArrayList<>();
        linhas.add(titulo("Lag (sessão)"));
        if (!Config.ativo(Modulo.DIAGNOSTICO_LAG)) {
            linhas.add(t(" O diagnóstico de lag está desligado.", ChatFormatting.GRAY));
            return linhas;
        }
        List<DiagnosticoLag.Registro> historico = DiagnosticoLag.historico();
        long n = Estatisticas.LAG_REGISTROS.get();
        if (n == 0) {
            linhas.add(t(" " + ok() + " Nenhum travamento registrado nesta sessão.", ChatFormatting.GREEN));
            return linhas;
        }
        DiagnosticoLag.Registro pior = DiagnosticoLag.pior();
        linhas.add(t(String.format(PT, " %s travamentos %s total %s", num(n), ponto(), duracao(DiagnosticoLag.msTotal())), ChatFormatting.YELLOW)
                .append(t(pior == null ? "" : " " + ponto() + " pior " + duracao(pior.duracaoMs()) + " às " + pior.hora().format(HORA), ChatFormatting.GRAY)));
        linhas.add(t(" Mods mais culpados:", ChatFormatting.WHITE, ChatFormatting.BOLD));
        long total = Math.max(1, DiagnosticoLag.msTotal());
        List<Map.Entry<String, Long>> ranking = new ArrayList<>(DiagnosticoLag.msPorMod().entrySet());
        ranking.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        int pos = 1;
        for (Map.Entry<String, Long> e : ranking) {
            if (pos > 3) {
                break;
            }
            boolean semMod = e.getKey().equals(DiagnosticoLag.SEM_MOD);
            String mod = semMod ? "Minecraft/Forge" : e.getKey();
            String frame = semMod ? "" : "(" + frameCurto(DiagnosticoLag.framePorMod(e.getKey())) + ")";
            linhas.add(hover(t(String.format(PT, "   %d. %s %3d%%  ", pos, preencher(cortar(mod, 14), 14), Math.round(100.0 * e.getValue() / total)),
                            ChatFormatting.GRAY).append(t(cortar(frame, 28), ChatFormatting.DARK_GRAY)),
                    mod + ": " + duracao(e.getValue()) + " de travamento atribuído\n" + DiagnosticoLag.framePorMod(e.getKey())));
            pos++;
        }
        linhas.add(t(" Últimos " + Math.min(3, historico.size()) + ":", ChatFormatting.WHITE, ChatFormatting.BOLD));
        for (int i = 0; i < Math.min(3, historico.size()); i++) {
            DiagnosticoLag.Registro r = historico.get(i);
            String mod = r.mod().equals(DiagnosticoLag.SEM_MOD) ? "Minecraft/Forge" : r.mod();
            linhas.add(hover(t(String.format(PT, "   %s %s %s %s %s", r.hora().format(HORA), preencher(duracao(r.duracaoMs()), 7),
                    preencher(cortar(r.tipo(), 14), 14), ponto(), cortar(mod, 14)), ChatFormatting.GRAY), r.tipo() + "\n" + mod + ": " + r.frame()));
        }
        linhas.add(t(" Detalhes: logs/latest.log (\"diagnostico_lag\")", ChatFormatting.DARK_GRAY));
        return linhas;
    }

    /** "committee.nova...chest.ChestHandler.updateItemKeys" -> "ChestHandler.updateItemKeys". */
    static String frameCurto(String frame) {
        int metodo = frame.lastIndexOf('.');
        if (metodo <= 0) {
            return frame;
        }
        int classe = frame.lastIndexOf('.', metodo - 1);
        String curto = frame.substring(classe + 1);
        int interna = curto.indexOf('$');
        return interna > 0 && interna < curto.indexOf('.') ? curto.substring(0, interna) + curto.substring(curto.indexOf('.')) : curto;
    }

    public List<Component> avisos() {
        List<Component> linhas = new ArrayList<>();
        linhas.add(titulo("Avisos"));
        int n = 0;
        for (Avisos.Aviso aviso : Avisos.todos()) {
            linhas.add(hover(t(" " + alerta() + " ", ChatFormatting.YELLOW).append(t(cortar(aviso.curto(), LARGURA - 4), ChatFormatting.WHITE)), aviso.detalhe()));
            n++;
        }
        if (!Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.isEmpty()) {
            int c = Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.size();
            linhas.add(hover(t(" " + alerta() + " ", ChatFormatting.YELLOW).append(t(c + " classe(s) gravando no thread principal", ChatFormatting.WHITE)),
                    "Estes dados têm save(File) próprio e são gravados do jeito do mod, no thread principal: "
                            + String.join(", ", Estatisticas.SALVAMENTO_CLASSES_SINCRONAS)
                            + "\nSe o save(File) da classe só chama o do Minecraft, ela pode ir para salvamento_assincrono.serializar_classes."));
            n++;
        }
        if (DiagnosticoAmbiente.arquivosLentos() > 0) {
            linhas.add(hover(t(" " + alerta() + " ", ChatFormatting.YELLOW)
                            .append(t(DiagnosticoAmbiente.arquivosLentos() + " arquivo(s) demoraram > 500 ms para abrir", ChatFormatting.WHITE)),
                    DiagnosticoAmbiente.DICA_ANTIVIRUS));
            n++;
        }
        if (Desempenho.pausasGcLongas() > 0) {
            linhas.add(hover(t(" " + alerta() + " ", ChatFormatting.YELLOW).append(t(Desempenho.pausasGcLongas()
                            + " pausa(s) da coleta de lixo > 500 ms", ChatFormatting.WHITE)),
                    "Maior pausa: " + duracao(Desempenho.maiorPausaGcMs()) + ". Nessas pausas o jogo inteiro para. " + DiagnosticoAmbiente.FLAGS_G1));
            n++;
        }
        if (ErrosMixin.FALHAS.isEmpty()) {
            linhas.add(t(" " + ok() + " Nenhum mixin falhou nesta inicialização", ChatFormatting.GREEN));
        }
        if (n == 0) {
            linhas.add(t(" " + ok() + " Nenhum aviso", ChatFormatting.GREEN));
        }
        return linhas;
    }

    /** Todas as paginas, para o teste de largura. */
    public List<List<Component>> todas() {
        return List.of(visaoGeral(), modulos(), desempenho(), lag(), avisos());
    }
}
