package com.hlotimizacao;

import com.hlotimizacao.config.Msg;
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

    /** Locale dos numeros: o do idioma do mod (1.204,5 em portugues; 1,204.5 em ingles). */
    public static Locale loc() {
        return Msg.locale();
    }

    private static String m(String modelo, Object... args) {
        return Msg.f(modelo, args);
    }
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

    public static String num(long n) {
        return String.format(loc(), "%,d", n);
    }

    public static String dec(double v) {
        return String.format(loc(), "%.1f", v);
    }

    /** 1h 27min, 6min 12s, 45s. */
    public static String tempo(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) {
            return String.format(loc(), "%dh %02dmin", s / 3600, (s % 3600) / 60);
        }
        if (s >= 60) {
            return String.format(loc(), "%dmin %02ds", s / 60, s % 60);
        }
        return s + "s";
    }

    /** 3,8 s ou 812 ms. */
    public static String duracao(long ms) {
        return ms >= 1000 ? dec(ms / 1000.0) + " s" : ms + " ms";
    }

    /** Texto sem os caracteres que o console do Windows pode nao mostrar. */
    public static String paraConsole(String texto) {
        return texto.replace("·", "-").replace("…", "...").replace("–", "-").replace("✔", "[ok]").replace("✘", "[x]")
                .replace("⚠", "[!]").replace("━", "=");
    }

    public static String cortar(String texto, int max) {
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
        linhas.add(titulo("HLO " + versaoMod()));
        linhas.add(t(m(" Preset: {} ({} mods)", Config.preset().nome(), Config.modsCarregados()), ChatFormatting.DARK_GRAY));
        linhas.add(t(m(" Desempenho"), ChatFormatting.WHITE, ChatFormatting.BOLD));
        Desempenho.Tempos tempos = Desempenho.tempos();
        double media = tempos.ticks() > 0 ? tempos.mediaMs() : server.getAverageTickTime();
        double tps = tempos.ticks() > 0 ? tempos.tps() : Math.min(20, 1000 / Math.max(50, media));
        linhas.add(hover(t("   TPS ", ChatFormatting.GRAY).append(t(dec(tps), corTps(tps)))
                        .append(t("  MSPT ", ChatFormatting.GRAY)).append(t(dec(media) + " ms", corMspt(media)))
                        .append(t(m("  pico {} ms", Math.round(tempos.maxMs())), ChatFormatting.GRAY)),
                m("Últimos {} ticks. O pico não conta os ticks em que o mundo foi salvo ({} nesse intervalo); veja /{} status desempenho.",
                        tempos.ticks(), tempos.ticksSalvamento(), prefixo)));
        Runtime rt = Runtime.getRuntime();
        long usada = rt.totalMemory() - rt.freeMemory();
        double fracao = (double) usada / rt.maxMemory();
        long sessao = Desempenho.sessaoMs();
        linhas.add(t(m("   Memória "), ChatFormatting.GRAY)
                .append(t(String.format(loc(), "%.1f / %.1f GB (%d%%)", usada / 1e9, rt.maxMemory() / 1e9, Math.round(fracao * 100)), corMemoria(fracao)))
                .append(t(sessao >= 0 ? m("  Sessão {}", tempo(sessao)) : "", ChatFormatting.GRAY)));

        int ligados = 0;
        int desligados = 0;
        for (Modulo m : Modulo.values()) {
            if (Config.ativo(m)) {
                ligados++;
            } else {
                desligados++;
            }
        }
        linhas.add(clique(t(m(" Módulos "), ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(t(m(ligados == 1 ? "{} ligado" : "{} ligados", ligados) + " " + ponto() + " "
                        + m(desligados == 1 ? "{} desligado" : "{} desligados", desligados), ChatFormatting.GRAY)), "status modulos", null));

        if (Config.ativo(Modulo.LIMPEZA_ITENS)) {
            long[] chao = LimpezaItens.contarNoChao(server);
            long faltam = LimpezaItens.segundosParaProxima(server);
            String quando = LimpezaItens.limpandoAgora() ? m("limpando agora") : faltam < 0 ? m("não agendada") : m("em {}", tempo(faltam * 1000));
            MutableComponent l = t(m(" Limpeza "), ChatFormatting.WHITE, ChatFormatting.BOLD)
                    .append(t(quando + " " + ponto() + " " + m("{} itens no chão", num(chao[1])), ChatFormatting.GRAY));
            linhas.add(podeAdmin ? clique(l, "limpar status", null) : l);
        }

        if (Config.ativo(Modulo.DIAGNOSTICO_LAG)) {
            int n = (int) Estatisticas.LAG_REGISTROS.get();
            MutableComponent l = t(" Lag ", ChatFormatting.WHITE, ChatFormatting.BOLD);
            if (n == 0) {
                l.append(t(m("nenhum travamento na sessão"), ChatFormatting.GREEN));
            } else {
                DiagnosticoLag.Registro pior = DiagnosticoLag.pior();
                l.append(t(m(n == 1 ? "{} travamento" : "{} travamentos", n), ChatFormatting.YELLOW));
                if (pior != null) {
                    l.append(t(" " + ponto() + " " + m("pior {}", duracao(pior.duracaoMs())), ChatFormatting.GRAY));
                }
                String culpado = maisCulpado();
                if (culpado != null) {
                    l.append(t(" " + ponto() + " " + culpado, ChatFormatting.GRAY));
                }
            }
            linhas.add(clique(l, "status lag", null));
        }

        int nAvisos = contarAvisos();
        MutableComponent l = t(m(" Avisos "), ChatFormatting.WHITE, ChatFormatting.BOLD);
        if (nAvisos == 0) {
            l.append(t(ok() + " " + m("nenhum"), ChatFormatting.GREEN));
        } else {
            l.append(t(alerta() + " " + m(nAvisos == 1 ? "{} aviso" : "{} avisos", nAvisos), ChatFormatting.YELLOW));
            List<String> destaques = new ArrayList<>();
            if (!Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.isEmpty()) {
                destaques.add(m("{} síncrona(s)", Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.size()));
            }
            if (DiagnosticoAmbiente.arquivosLentos() > 0) {
                destaques.add(m("{} arquivo(s) lento(s)", DiagnosticoAmbiente.arquivosLentos()));
            }
            if (!destaques.isEmpty()) {
                l.append(t(" (" + String.join(", ", destaques) + ")", ChatFormatting.GRAY));
            }
        }
        linhas.add(clique(l, "status avisos", null));
        linhas.add(rodape());
        MutableComponent mais = t(m(" Mais: "), ChatFormatting.GRAY);
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
    public static String maisCulpado() {
        long total = DiagnosticoLag.msTotal();
        if (total <= 0) {
            return null;
        }
        return DiagnosticoLag.msPorMod().entrySet().stream()
                .filter(e -> !e.getKey().equals(DiagnosticoLag.SEM_MOD))
                .max(Map.Entry.comparingByValue())
                .map(e -> cortar(DiagnosticoLag.rotulo(e.getKey()), 16) + " " + Math.round(100.0 * e.getValue() / total) + "%")
                .orElse(null);
    }

    private int contarAvisos() {
        return todosOsAvisos().size();
    }

    /** Avisos da inicializacao mais os que aparecem durante o jogo; usado pelo chat e pela tela. */
    public static List<Avisos.Aviso> todosOsAvisos() {
        List<Avisos.Aviso> lista = new ArrayList<>(Avisos.todos());
        if (!Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.isEmpty()) {
            lista.add(new Avisos.Aviso(m("{} classe(s) gravando no thread principal", Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.size()),
                    m("Estes dados têm save(File) próprio e são gravados do jeito do mod, no thread principal: {}\nSe o save(File) da classe só chama o do Minecraft, ela pode ir para salvamento_assincrono.serializar_classes.",
                            String.join(", ", Estatisticas.SALVAMENTO_CLASSES_SINCRONAS))));
        }
        if (DiagnosticoAmbiente.arquivosLentos() > 0) {
            lista.add(new Avisos.Aviso(m("{} arquivo(s) demoraram > 500 ms para abrir", DiagnosticoAmbiente.arquivosLentos()), Msg.t(DiagnosticoAmbiente.DICA_ANTIVIRUS)));
        }
        if (Desempenho.pausasGcLongas() > 0) {
            lista.add(new Avisos.Aviso(m("{} pausa(s) da coleta de lixo > 500 ms", Desempenho.pausasGcLongas()),
                    m("Maior pausa: {}. Nessas pausas o jogo inteiro para. {}", duracao(Desempenho.maiorPausaGcMs()), Msg.t(DiagnosticoAmbiente.FLAGS_G1))));
        }
        return lista;
    }

    public List<Component> modulos() {
        List<Component> linhas = new ArrayList<>();
        linhas.add(titulo(m("Módulos")));
        List<Modulo> ordem = new ArrayList<>(List.of(Modulo.values()));
        // Ligados primeiro, depois desligados, depois os "nao necessario".
        ordem.sort(Comparator.comparingInt(m -> Config.ativo(m) ? 0 : Config.naoNecessario(m) ? 2 : 1));
        boolean dedicado = FMLEnvironment.dist == Dist.DEDICATED_SERVER;
        for (Modulo m : ordem) {
            boolean ativo = Config.ativo(m);
            String simbolo = ativo ? ok() : Config.naoNecessario(m) ? (console ? "-" : "–") : nao();
            ChatFormatting cor = ativo ? ChatFormatting.GREEN : Config.naoNecessario(m) ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY;
            String nome = preencher(m.nome(), 27);
            int espaco = LARGURA - 3 - nome.length();
            MutableComponent linha = t(" " + simbolo + " ", cor).append(t(nome, ativo ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            String detalhe;
            if (!ativo) {
                linha.append(t(cortar(Config.naoNecessario(m) ? m("não necessário") : Config.motivoCurto(m), espaco), cor));
                detalhe = Config.motivo(m);
            } else if (m.soCliente && dedicado) {
                linha.append(t(m("(só no cliente)"), ChatFormatting.GRAY));
                detalhe = m("Este módulo só age no cliente; no servidor dedicado não há contadores.");
            } else {
                linha.append(t(cortar(resumoCurto(m), espaco), ChatFormatting.GRAY));
                detalhe = detalhe(m);
            }
            String dica = m.texto() + "\n\nConfig: modulo." + m.chave + "=" + Config.valor(m) + " (" + Config.motivo(m) + ")"
                    + (detalhe == null || detalhe.isEmpty() ? "" : "\n\n" + detalhe);
            linhas.add(hover(linha, dica));
        }
        return linhas;
    }

    /** Resumo de uma linha para a pagina de modulos. */
    public static String resumoCurto(Modulo m) {
        return switch (m) {
            case DESPAWN_ITENS -> m("{} mobs · {} itens devolvidos", num(Estatisticas.DESPAWN_MOBS_LIBERADOS.get()),
                    num(Estatisticas.DESPAWN_ITENS_DEVOLVIDOS.get()));
            case SALVAMENTO_ASSINCRONO -> m("{} em outra thread", num(Estatisticas.SALVAMENTO_ASSINCRONOS.get()));
            case VAZAMENTO_GPU -> m("{} framebuffers · {} texturas", num(Estatisticas.GPU_FRAMEBUFFERS.get()), num(Estatisticas.GPU_TEXTURAS.get()));
            case TOOLTIP_SEGURO -> m("{} itens corrigidos", num(Estatisticas.TOOLTIP_ITENS.size()));
            case DIAGNOSTICO_LAG -> m(Estatisticas.LAG_REGISTROS.get() == 1 ? "{} travamento registrado" : "{} travamentos registrados", num(Estatisticas.LAG_REGISTROS.get()));
            case CORRECAO_INFINITY_CHEST -> m("{} baús · {} chaves", num(ChavesInfinityChest.BAUS_CORRIGIDOS.get()),
                    num(ChavesInfinityChest.CHAVES_CORRIGIDAS.get()));
            case LIMPEZA_ITENS -> m("{} limpezas · {} itens", num(LimpezaItens.LIMPEZAS.get()), num(LimpezaItens.ITENS_REMOVIDOS.get()));
            case RELATORIO_CONFLITOS -> m(RelatorioConflitos.avisosInicializacao == 1 ? "{} aviso na inicialização" : "{} avisos na inicialização", num(RelatorioConflitos.avisosInicializacao));
            case ALCANCE_ATIVACAO -> m("{} ticks pulados", num(com.hlotimizacao.recursos.AlcanceAtivacao.TICKS_PULADOS.get()));
            case LIMITE_ENXAME -> m("{} spawns bloqueados", num(com.hlotimizacao.recursos.LimiteEnxame.BLOQUEADOS.get()));
            case DIAGNOSTICO_INSERCAO -> m("ativo nos travamentos");
            case MONITOR_GC -> m("{} pausas · {} congelamentos", num(com.hlotimizacao.recursos.MonitorGc.PAUSAS_GC.get()),
                    num(com.hlotimizacao.recursos.MonitorGc.SOLUCOS.get()));
            case INSERCAO_RAPIDA -> m("{} pelo cache · {} varreduras", num(com.hlotimizacao.recursos.Insercao.RAPIDAS.get()),
                    num(com.hlotimizacao.recursos.Insercao.VARREDURAS.get()));
            case GATILHO_INVENTARIO, FPS_SEM_FOCO, TOASTS_VAZIOS, CORRECAO_FASTASYNCWORLDSAVE -> m("ativo");
        };
    }

    /** Texto completo (hover) de um modulo ligado. */
    public static String detalhe(Modulo m) {
        return switch (m) {
            case SALVAMENTO_ASSINCRONO -> m("{} arquivo(s) gravado(s) em outra thread, {} no thread principal, {} falha(s) regravada(s) depois.",
                    num(Estatisticas.SALVAMENTO_ASSINCRONOS.get()), num(Estatisticas.SALVAMENTO_SINCRONOS.get()), num(Estatisticas.SALVAMENTO_FALHAS.get()))
                    + (Estatisticas.SALVAMENTO_CLASSES_SINCRONAS.isEmpty() ? ""
                    : "\n" + m("Classes gravadas no thread principal (save(File) próprio): {}", String.join(", ", Estatisticas.SALVAMENTO_CLASSES_SINCRONAS)));
            case TOOLTIP_SEGURO -> Estatisticas.TOOLTIP_ITENS.isEmpty() ? m("Nenhum item com linha nula até agora.")
                    : m("Itens corrigidos: {}", String.join(", ", Estatisticas.TOOLTIP_ITENS));
            case DESPAWN_ITENS -> m("Mobs que pegaram itens e continuaram podendo desaparecer, e itens que eles devolveram ao desaparecer.");
            case CORRECAO_INFINITY_CHEST -> m("{} baú(s) corrigido(s) ao carregar o mundo, {} chave(s) consertada(s), {} entrada(s) duplicada(s) somada(s), {} inserção(ões) sem varrer os slots. Cópia do original: <arquivo>.dat.hlbak em data/avaritia/infinity_chest.",
                    num(ChavesInfinityChest.BAUS_CORRIGIDOS.get()), num(ChavesInfinityChest.CHAVES_CORRIGIDAS.get()),
                    num(ChavesInfinityChest.ENTRADAS_MESCLADAS.get()), num(com.hlotimizacao.recursos.Insercao.ATALHO_AVARITIA.get()));
            case LIMPEZA_ITENS -> m("{} limpeza(s), {} item(ns) removido(s).", num(LimpezaItens.LIMPEZAS.get()), num(LimpezaItens.ITENS_REMOVIDOS.get()));
            case ALCANCE_ATIVACAO -> m("{} tick(s) de entidade pulado(s) e {} processado(s) longe dos jogadores. Alcance: {} blocos na horizontal, {} na vertical; longe, 1 tick a cada {}.",
                    num(com.hlotimizacao.recursos.AlcanceAtivacao.TICKS_PULADOS.get()), num(com.hlotimizacao.recursos.AlcanceAtivacao.TICKS_PROCESSADOS_LONGE.get()),
                    Config.ativacaoHorizontal(), Config.ativacaoVertical(), Config.ativacaoIntervalo());
            case LIMITE_ENXAME -> m("Spawns bloqueados: {}", num(com.hlotimizacao.recursos.LimiteEnxame.BLOQUEADOS.get()))
                    + (com.hlotimizacao.recursos.LimiteEnxame.porTipo().isEmpty() ? "" : " (" + com.hlotimizacao.recursos.LimiteEnxame.porTipo() + ")")
                    + "\n" + m("Limites: {}", Config.enxameLimites());
            case MONITOR_GC -> com.hlotimizacao.recursos.MonitorGc.resumo()
                    + (com.hlotimizacao.recursos.MonitorGc.maiorMs() > 0 ? "\n" + m("Maior: {}", com.hlotimizacao.recursos.MonitorGc.maiorDescricao()) : "");
            case INSERCAO_RAPIDA -> m("{} inserção(ões) pelo cache, {} varredura(s) completa(s), {} recusada(s) pelo orçamento.",
                    num(com.hlotimizacao.recursos.Insercao.RAPIDAS.get()), num(com.hlotimizacao.recursos.Insercao.VARREDURAS.get()),
                    num(com.hlotimizacao.recursos.Insercao.RECUSADAS.get()));
            default -> "";
        };
    }

    public List<Component> desempenho() {
        List<Component> linhas = new ArrayList<>();
        linhas.add(titulo(m("Desempenho")));
        Desempenho.Tempos tempos = Desempenho.tempos();
        linhas.add(t(m(" Servidor "), ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(t("TPS ", ChatFormatting.GRAY)).append(t(dec(tempos.tps()), corTps(tempos.tps())))
                .append(t(" " + ponto() + " MSPT ", ChatFormatting.GRAY)).append(t(dec(tempos.mediaMs()) + " ms", corMspt(tempos.mediaMs()))));
        linhas.add(hover(t(m("   mín {} ms {} máx {} ms (sem salvamento)", Math.round(tempos.minMs()), ponto(), Math.round(tempos.maxMs())),
                ChatFormatting.GRAY), m("Últimos {} ticks; {} deles tiveram salvamento do mundo.", tempos.ticks(), tempos.ticksSalvamento())));
        if (Desempenho.ultimoSalvamentoHora() != null) {
            linhas.add(t(m("   último salvamento: {} às {}", duracao(Desempenho.ultimoSalvamentoMs()), Desempenho.ultimoSalvamentoHora().format(HORA)),
                    ChatFormatting.GRAY));
        }
        double custo = com.hlotimizacao.recursos.Custo.msPorTick();
        linhas.add(hover(t(custo < 0 ? m("   custo do HLO: medindo...") : m("   custo do HLO: {} ms/tick", String.format(loc(), "%.3f", custo)), ChatFormatting.GRAY),
                m("Tempo medio gasto por tick nos mixins e eventos do proprio HLO (medido por amostragem).")));
        Runtime rt = Runtime.getRuntime();
        long usada = rt.totalMemory() - rt.freeMemory();
        linhas.add(t(m(" Memória "), ChatFormatting.WHITE, ChatFormatting.BOLD).append(t(m("usada {} GB", dec(usada / 1e9)),
                        corMemoria((double) usada / rt.maxMemory())))
                .append(t(" " + ponto() + " " + m("alocada {}", dec(rt.totalMemory() / 1e9)) + " " + ponto() + " " + m("máx {} GB", dec(rt.maxMemory() / 1e9)),
                        ChatFormatting.GRAY)));
        int pausas = Desempenho.pausasGcLongas();
        linhas.add(t(pausas == 0 ? m("   coleta de lixo: nenhuma pausa > 500 ms")
                        : m("   coleta de lixo: {} pausa(s) > 500 ms, maior {}", pausas, duracao(Desempenho.maiorPausaGcMs())),
                pausas == 0 ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        linhas.add(t(m(" Dimensões "), ChatFormatting.WHITE, ChatFormatting.BOLD).append(t(m("(só as com algo carregado)"), ChatFormatting.GRAY)));
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
            linhas.add(hover(t(String.format(loc(), "   %s %d%s %s %s chunks %s %s ent%s", preencher(nome, 12), jogadores, m("j"), ponto(), num(chunks), ponto(),
                            num(entidades), be), ChatFormatting.GRAY),
                    m("{}: {} jogador(es), {} chunks carregados, {} entidades", level.dimension().location(), jogadores, chunks, entidades)));
        }
        if (vazias > 0) {
            linhas.add(t(m("   +{} dimensão(ões) vazia(s)", vazias), ChatFormatting.DARK_GRAY));
        }
        Runtime.Version v = Runtime.version();
        String fornecedor = cortar(System.getProperty("java.vendor", "?"), 18);
        MutableComponent java = t(" Java ", ChatFormatting.WHITE, ChatFormatting.BOLD).append(t(v.feature() + "." + v.interim() + "." + v.update()
                + " (" + fornecedor + ")", ChatFormatting.GRAY));
        if (!DiagnosticoAmbiente.temAjusteGc()) {
            java.append(hover(t(" " + ponto() + " " + m("GC: nenhum") + " " + alerta(), ChatFormatting.YELLOW), Msg.t(DiagnosticoAmbiente.FLAGS_G1)));
        }
        linhas.add(java);
        return linhas;
    }

    public List<Component> lag() {
        List<Component> linhas = new ArrayList<>();
        linhas.add(titulo(m("Lag (sessão)")));
        if (!Config.ativo(Modulo.DIAGNOSTICO_LAG)) {
            linhas.add(t(m(" O diagnóstico de lag está desligado."), ChatFormatting.GRAY));
            return linhas;
        }
        List<DiagnosticoLag.Registro> historico = DiagnosticoLag.historico();
        long n = Estatisticas.LAG_REGISTROS.get();
        if (n == 0) {
            linhas.add(t(" " + ok() + " " + m("Nenhum travamento registrado nesta sessão."), ChatFormatting.GREEN));
            return linhas;
        }
        DiagnosticoLag.Registro pior = DiagnosticoLag.pior();
        linhas.add(t(" " + m(n == 1 ? "{} travamento" : "{} travamentos", num(n)) + " " + ponto() + " " + m("total {}", duracao(DiagnosticoLag.msTotal())), ChatFormatting.YELLOW)
                .append(t(pior == null ? "" : " " + ponto() + " " + m("pior {} às {}", duracao(pior.duracaoMs()), pior.hora().format(HORA)), ChatFormatting.GRAY)));
        linhas.add(t(m(" Principais causas:"), ChatFormatting.WHITE, ChatFormatting.BOLD));
        long total = Math.max(1, DiagnosticoLag.msTotal());
        List<Map.Entry<String, Long>> ranking = new ArrayList<>(DiagnosticoLag.msPorMod().entrySet());
        ranking.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        int pos = 1;
        for (Map.Entry<String, Long> e : ranking) {
            if (pos > 3) {
                break;
            }
            boolean semMod = e.getKey().equals(DiagnosticoLag.SEM_MOD);
            boolean padrao = DiagnosticoLag.ehPadrao(e.getKey());
            String mod = DiagnosticoLag.rotulo(e.getKey());
            String frame = semMod || padrao ? "" : "(" + frameCurto(DiagnosticoLag.framePorMod(e.getKey())) + ")";
            linhas.add(hover(t(String.format(loc(), "   %d. %s %3d%%  ", pos, preencher(cortar(mod, 14), 14), Math.round(100.0 * e.getValue() / total)),
                            ChatFormatting.GRAY).append(t(cortar(frame, 28), ChatFormatting.DARK_GRAY)),
                    m("{}: {} de travamento atribuído", mod, duracao(e.getValue())) + "\n"
                            + (padrao ? DiagnosticoLag.explicacao(e.getKey()) + "\n" : "") + DiagnosticoLag.framePorMod(e.getKey())));
            pos++;
        }
        linhas.add(t(m(" Últimos {}:", Math.min(3, historico.size())), ChatFormatting.WHITE, ChatFormatting.BOLD));
        for (int i = 0; i < Math.min(3, historico.size()); i++) {
            DiagnosticoLag.Registro r = historico.get(i);
            String mod = DiagnosticoLag.rotulo(r.mod());
            linhas.add(hover(t(String.format(loc(), "   %s %s %s %s %s", r.hora().format(HORA), preencher(duracao(r.duracaoMs()), 7),
                    preencher(cortar(Msg.t(r.tipo()), 14), 14), ponto(), cortar(mod, 14)), ChatFormatting.GRAY), Msg.t(r.tipo()) + "\n" + mod + ": " + r.frame()));
        }
        linhas.add(t(m(" Detalhes: logs/latest.log (\"diagnostico_lag\")"), ChatFormatting.DARK_GRAY));
        return linhas;
    }

    /** "committee.nova...chest.ChestHandler.updateItemKeys" -> "ChestHandler.updateItemKeys". */
    public static String frameCurto(String frame) {
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
        linhas.add(titulo(m("Avisos")));
        List<Avisos.Aviso> lista = todosOsAvisos();
        for (Avisos.Aviso aviso : lista) {
            linhas.add(hover(t(" " + alerta() + " ", ChatFormatting.YELLOW).append(t(cortar(aviso.curto(), LARGURA - 4), ChatFormatting.WHITE)), aviso.detalhe()));
        }
        if (ErrosMixin.FALHAS.isEmpty()) {
            linhas.add(t(" " + ok() + " " + m("Nenhum mixin falhou nesta inicialização"), ChatFormatting.GREEN));
        }
        if (lista.isEmpty()) {
            linhas.add(t(" " + ok() + " " + m("Nenhum aviso"), ChatFormatting.GREEN));
        }
        return linhas;
    }

    /** Todas as paginas, para o teste de largura. */
    public List<List<Component>> todas() {
        return List.of(visaoGeral(), modulos(), desempenho(), lag(), avisos());
    }
}
