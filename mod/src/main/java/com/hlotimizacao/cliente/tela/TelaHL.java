package com.hlotimizacao.cliente.tela;

import com.hlotimizacao.PainelStatus;
import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.rede.DadosPainel;
import com.hlotimizacao.rede.DadosPainel.Aba;
import com.hlotimizacao.rede.RedeHL;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tela do /hl (e da tecla F8): abas com tudo o que o mod mostra. Os dados vem do servidor
 * (DadosPainel) a cada 1 s, so da aba aberta. Nao pausa o jogo. Os botoes de acao so aparecem
 * ativos para operadores, mas a permissao e conferida de novo no servidor.
 * Tudo e desenhado aqui (abas, botoes, barras, controles deslizantes), sem os botoes cinza do Minecraft.
 */
public class TelaHL extends Screen {
    private static final int LARGURA_MAX = 440;
    private static final int ALTURA_MAX = 270;
    private static final int LINHA = 12;
    private static final int TOPO = 36;
    private static final int RODAPE = 16;

    // cores do texto (RGB) e da moldura (ARGB)
    private static final int VERDE = 0x5BE37D;
    private static final int AMARELO = 0xFFD54F;
    private static final int VERMELHO = 0xFF6B6B;
    private static final int CINZA = 0xA4ABB5;
    private static final int CINZA_ESCURO = 0x5F6670;
    private static final int BRANCO = 0xECEFF4;
    private static final int OURO = 0xFFB547;
    private static final int AZUL = 0x6CB8FF;
    private static final int FUNDO = 0xF2121519;
    private static final int FUNDO_BARRA = 0xFF1A1E25;
    private static final int FUNDO_CAMPO = 0xFF0C0E12;
    private static final int BORDA = 0xFF343A45;
    private static final int BORDA_OURO = 0xFF7A5C24;
    private static final int LISTRA = 0x0AFFFFFF;
    private static final int REALCE = 0x1CFFFFFF;

    private DadosPainel dados;
    private Aba aba;
    private int x0;
    private int y0;
    private int w;
    private int h;
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private double rolagem;
    private int alturaConteudo;
    private int ticks;
    private final List<Linha> linhas = new ArrayList<>();
    private final Set<Integer> avisosAbertos = new HashSet<>();
    private final Map<String, ControleConfig> controles = new LinkedHashMap<>();
    private final List<Object[]> abas = new ArrayList<>(); // {Aba, int[] retangulo}
    private String mensagem;
    private int corMensagem = BRANCO;
    private long mensagemAte;
    private String tituloModal;
    private List<FormattedCharSequence> linhasModal;
    private String textoModal;
    private double rolagemModal;
    private ControleConfig arrastando;
    private int[] trilhoArrastado;
    private boolean arrastandoBarra;
    private final Map<String, int[]> zonasRodape = new HashMap<>();

    /** Um botao dentro de uma linha ("Limpar agora"). cor = cor do texto (0 = padrao). */
    private record Acao(Component rotulo, Runnable executar, boolean ativa, Component confirmar, int cor) {
        Acao(Component rotulo, Runnable executar, boolean ativa, Component confirmar) {
            this(rotulo, executar, ativa, confirmar, 0);
        }
    }

    private enum Tipo {
        TEXTO, BARRA, GRAFICO, SEPARADOR, ESPACO, TITULO, CONTROLE
    }

    private static final class Linha {
        Tipo tipo = Tipo.TEXTO;
        Component texto = Component.empty();
        Component texto2;
        int coluna2;
        List<Component> dica;
        Runnable clique;
        final List<Acao> acoes = new ArrayList<>();
        float fracao;
        int cor;
        boolean listra;
        ControleConfig controle;
        FormattedCharSequence textoFormatado;
        int altura = LINHA;
        // calculado ao desenhar
        int yTela;
        final List<int[]> retangulosAcoes = new ArrayList<>();
        final Map<String, int[]> zonas = new HashMap<>();
    }

    public TelaHL(DadosPainel dados) {
        super(Component.translatable("hlotimizacao.tela.titulo"));
        this.dados = dados;
        this.aba = dados.aba;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        w = Math.min(LARGURA_MAX, width - 16);
        h = Math.min(ALTURA_MAX, height - 16);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        cx = x0 + 7;
        cy = y0 + TOPO + 2;
        cw = w - 18; // sobra espaco para a barra de rolagem
        ch = h - TOPO - RODAPE - 4;
        montarAbas();
        controles.values().stream().filter(c -> c.caixa != null).forEach(c -> addWidget(c.caixa));
        RedeHL.CANAL.sendToServer(new RedeHL.C2SInscrever(aba));
        reconstruir();
    }

    /** Abas desenhadas aqui; se nao couberem, o espaco entre elas diminui antes de cortar o texto. */
    private void montarAbas() {
        abas.clear();
        Aba[] todas = Aba.values();
        int disponivel = w - 10;
        int texto = 0;
        for (Aba a : todas) {
            texto += font.width(rotuloAba(a));
        }
        int margem = Math.max(2, Math.min(8, (disponivel - texto - (todas.length - 1) * 2) / (todas.length * 2)));
        int x = x0 + 5;
        for (Aba a : todas) {
            int largura = font.width(rotuloAba(a)) + margem * 2;
            abas.add(new Object[]{a, new int[]{x, y0 + 19, x + largura, y0 + 33}});
            x += largura + 2;
        }
    }

    private static Component rotuloAba(Aba a) {
        return Component.translatable("hlotimizacao.tela.aba." + a.name().toLowerCase());
    }

    @Override
    public void removed() {
        RedeHL.CANAL.sendToServer(new RedeHL.C2SDesinscrever());
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 40 == 0) {
            RedeHL.CANAL.sendToServer(new RedeHL.C2SInscrever(aba)); // sinal de vida: sem ele o servidor para em 5 s
        }
        controles.values().stream().filter(c -> c.caixa != null).forEach(c -> c.caixa.tick());
    }

    private void trocarAba(Aba nova) {
        if (nova == aba) {
            return;
        }
        aba = nova;
        rolagem = 0;
        arrastando = null;
        if (nova != Aba.CONFIG) {
            controles.values().stream().filter(c -> c.caixa != null).forEach(c -> removeWidget(c.caixa));
            controles.clear();
        }
        RedeHL.CANAL.sendToServer(new RedeHL.C2SInscrever(nova));
        rebuildWidgets();
    }

    /** Para testes e atalhos: troca de aba como se a aba fosse clicada. */
    public void selecionarAba(Aba nova) {
        trocarAba(nova);
    }

    public boolean modalAberto() {
        return tituloModal != null;
    }

    public void fecharModal() {
        tituloModal = null;
    }

    public void atualizar(DadosPainel novos) {
        this.dados = novos;
        reconstruir();
    }

    public void mensagem(boolean ok, String texto) {
        mensagem = texto;
        corMensagem = ok ? VERDE : VERMELHO;
        mensagemAte = System.currentTimeMillis() + 5000;
    }

    public void mostrarTexto(String titulo, String texto) {
        tituloModal = titulo;
        textoModal = texto;
        linhasModal = new ArrayList<>();
        for (String linha : texto.split("\n", -1)) {
            linhasModal.addAll(font.split(Component.literal(linha.isEmpty() ? " " : linha), w - 40));
        }
        rolagemModal = 0;
    }

    // ---------- montagem das linhas de cada aba ----------

    private static String t(String chave, Object... args) {
        return Component.translatable("hlotimizacao.tela." + chave, args).getString();
    }

    private Linha texto(Component c) {
        Linha l = new Linha();
        l.texto = c;
        linhas.add(l);
        return l;
    }

    private Linha texto(String s, int cor) {
        return texto(Component.literal(s).withStyle(st -> st.withColor(cor)));
    }

    private void espaco() {
        Linha l = new Linha();
        l.tipo = Tipo.ESPACO;
        l.altura = 5;
        linhas.add(l);
    }

    private void separador() {
        Linha l = new Linha();
        l.tipo = Tipo.SEPARADOR;
        l.altura = 7;
        linhas.add(l);
    }

    /** Titulo de secao: texto dourado com um filete ate a direita. */
    private void titulo(String s) {
        Linha l = new Linha();
        l.tipo = Tipo.TITULO;
        l.texto = Component.literal(s);
        l.altura = linhas.isEmpty() ? 13 : 17;
        linhas.add(l);
    }

    private void barra(String rotulo, float fracao, int cor, String valor) {
        Linha l = new Linha();
        l.tipo = Tipo.BARRA;
        l.texto = Component.literal(rotulo);
        l.texto2 = Component.literal(valor).withStyle(st -> st.withColor(cor));
        l.fracao = Math.max(0, Math.min(1, fracao));
        l.cor = cor;
        l.altura = 15;
        linhas.add(l);
    }

    /** "1 travamento", "3 travamentos" (chaves <nome> e <nome>s nos arquivos de idioma). */
    private static String plural(long n, String chave) {
        return t(n == 1 ? chave : chave + "s", PainelStatus.num(n));
    }

    private static int corMspt(double ms) {
        return ms < 40 ? VERDE : ms <= 50 ? AMARELO : VERMELHO;
    }

    private static int corTps(double tps) {
        return tps >= 19.5 ? VERDE : tps >= 15 ? AMARELO : VERMELHO;
    }

    private static int corMemoria(double f) {
        return f < 0.70 ? VERDE : f <= 0.85 ? AMARELO : VERMELHO;
    }

    private static String gb(long bytes) {
        return PainelStatus.dec(bytes / 1e9);
    }

    private void reconstruir() {
        linhas.clear();
        if (dados == null) {
            return;
        }
        switch (aba) {
            case GERAL -> geral();
            case MODULOS -> modulos();
            case DESEMPENHO -> desempenho();
            case LAG -> lag();
            case LIMPEZA -> limpeza();
            case AVISOS -> avisos();
            case CONFIG -> config();
        }
        alturaConteudo = linhas.stream().mapToInt(l -> l.altura).sum() + 4;
        rolagem = Math.max(0, Math.min(rolagem, Math.max(0, alturaConteudo - ch)));
    }

    private boolean carregando(Object secao) {
        if (secao == null) {
            texto(t("carregando"), CINZA);
            return true;
        }
        return false;
    }

    private void geral() {
        DadosPainel d = dados;
        barra("TPS", d.tps / 20f, corTps(d.tps), PainelStatus.dec(d.tps));
        barra("MSPT", d.msptMedio / 50f, corMspt(d.msptMedio), PainelStatus.dec(d.msptMedio) + " ms  (" + t("pico") + " " + Math.round(d.msptPico) + " ms)");
        double memoria = d.memMax == 0 ? 0 : (double) d.memUsada / d.memMax;
        barra("RAM", (float) memoria, corMemoria(memoria), gb(d.memUsada) + " / " + gb(d.memMax) + " GB (" + Math.round(memoria * 100) + "%)");
        espaco();
        Linha sessao = texto((d.sessaoMs >= 0 ? t("sessao") + ": " + PainelStatus.tempo(d.sessaoMs) + "      " : "")
                + t("modulos_resumo", plural(d.ligados, "ligado"), plural(d.desligados, "desligado")), BRANCO);
        sessao.clique = () -> trocarAba(Aba.MODULOS);
        if (!d.preset.isEmpty()) {
            texto(t("preset", d.preset, d.mods), CINZA);
        }
        if (d.limpezaAtiva) {
            String quando = d.limpando ? t("limpando") : d.limpezaSegundos < 0 ? "-" : PainelStatus.tempo(d.limpezaSegundos * 1000);
            Linha l = texto(t("proxima_limpeza") + ": " + quando + " · " + PainelStatus.num(d.itensNoChao) + " " + t("itens_chao"), BRANCO);
            l.clique = () -> trocarAba(Aba.LIMPEZA);
        }
        if (d.lagAtivo) {
            String lag = d.lagRegistros == 0 ? t("sem_lag")
                    : t("lag_resumo", plural(d.lagRegistros, "travamento"), PainelStatus.duracao(d.lagPiorMs))
                    + (d.maisCulpado.isEmpty() ? "" : " · " + t("mais_culpado") + ": " + d.maisCulpado);
            Linha l = texto(t("lag_sessao") + ": " + lag, d.lagRegistros == 0 ? VERDE : BRANCO);
            l.clique = () -> trocarAba(Aba.LAG);
        }
        Linha avisos = texto(t("avisos") + ": " + (d.avisos == 0 ? "✔ " + t("nenhum") : "⚠ " + d.avisos + "   (" + t("clique_ver") + ")"),
                d.avisos == 0 ? VERDE : AMARELO);
        avisos.clique = () -> trocarAba(Aba.AVISOS);
    }

    private void modulos() {
        if (carregando(dados.modulos)) {
            return;
        }
        if (dados.mudancaPendente) {
            texto("⟳ " + t("reiniciar"), AMARELO);
            espaco();
        }
        boolean listra = false;
        for (DadosPainel.Modulo m : dados.modulos) {
            boolean ligado = m.estado() == DadosPainel.Modulo.LIGADO;
            boolean naoNecessario = m.estado() == DadosPainel.Modulo.NAO_NECESSARIO;
            int cor = ligado ? VERDE : naoNecessario ? CINZA_ESCURO : VERMELHO;
            String resumo = m.resumo();
            Modulo local = moduloLocal(m.chave());
            if (ligado && m.soCliente() && local != null) {
                resumo = PainelStatus.resumoCurto(local); // contadores deste cliente (GPU, tooltip)
            }
            Linha l = texto(Component.literal("● ").withStyle(st -> st.withColor(cor))
                    .append(Component.literal(m.nome()).withStyle(st -> st.withColor(ligado ? BRANCO : CINZA))));
            l.texto2 = Component.literal(resumo).withStyle(st -> st.withColor(ligado ? CINZA : CINZA_ESCURO));
            l.coluna2 = 150;
            l.listra = listra;
            listra = !listra;
            l.dica = new ArrayList<>(List.of(Component.literal(m.nome()).withStyle(ChatFormatting.GOLD), Component.literal(m.descricao()),
                    Component.literal("modulo." + m.chave() + "=" + m.valor()).withStyle(ChatFormatting.GRAY)));
            if (!m.detalhe().isEmpty()) {
                l.dica.add(Component.literal(m.detalhe()).withStyle(ChatFormatting.DARK_AQUA));
            }
            String proximo = switch (m.valor()) {
                case "auto" -> "on";
                case "on" -> "off";
                default -> "auto";
            };
            int corValor = switch (m.valor()) {
                case "on" -> VERDE;
                case "off" -> VERMELHO;
                default -> AZUL;
            };
            l.acoes.add(new Acao(Component.literal(m.valor().toUpperCase()),
                    () -> RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.MODULO, m.chave() + "=" + proximo)), dados.operador, null, corValor));
        }
    }

    private static Modulo moduloLocal(String chave) {
        for (Modulo m : Modulo.values()) {
            if (m.chave.equals(chave)) {
                return m;
            }
        }
        return null;
    }

    private void desempenho() {
        DadosPainel.Desempenho p = dados.desempenho;
        if (carregando(p)) {
            return;
        }
        titulo(t("mspt_5min"));
        Linha g = new Linha();
        g.tipo = Tipo.GRAFICO;
        g.altura = 74;
        linhas.add(g);
        texto(t("min_max", Math.round(p.minMs), Math.round(p.maxMs)), CINZA);
        Linha custo = texto(p.custoMs < 0 ? t("custo_medindo") : t("custo", String.format(PainelStatus.loc(), "%.3f", p.custoMs)), CINZA);
        custo.dica = List.of(Component.translatable("hlotimizacao.tela.custo_dica"));
        if (!p.ultimoSalvamentoHora.isEmpty()) {
            texto(t("ultimo_salvamento") + ": " + PainelStatus.duracao(p.ultimoSalvamentoMs) + " " + t("as") + " " + p.ultimoSalvamentoHora, CINZA);
        }
        texto(t("coleta_lixo", p.pausasGc) + (p.pausasGc > 0 ? ", " + t("maior") + " " + PainelStatus.duracao(p.maiorPausaGcMs) : "")
                + " · -Xms " + (p.xms > 0 ? gb(p.xms) + " GB" : "?") + " / -Xmx " + gb(dados.memMax) + " GB", p.pausasGc > 0 ? AMARELO : CINZA);
        texto(t("memoria_detalhe", gb(dados.memUsada), gb(p.memAlocada), gb(dados.memMax)), CINZA);
        titulo(t("dimensoes"));
        boolean listra = false;
        for (DadosPainel.Dimensao dim : p.dimensoes) {
            boolean pesada = dim.entidades() > 300 || dim.itens() > 200;
            Linha l = texto(Component.literal(nomeDimensao(dim.id())).withStyle(st -> st.withColor(pesada ? AMARELO : BRANCO)));
            l.texto2 = Component.literal(t("dimensao_linha", dim.jogadores(), PainelStatus.num(dim.chunks()), PainelStatus.num(dim.entidades()))
                    + (pesada ? "  ⚠" : "")).withStyle(st -> st.withColor(pesada ? AMARELO : CINZA));
            l.coluna2 = 130;
            l.listra = listra;
            listra = !listra;
            l.dica = List.of(Component.literal(dim.id()), Component.literal(t("itens_no_chao") + ": " + PainelStatus.num(dim.itens())));
        }
        espaco();
        Linha java = texto("Java " + p.java + (p.ajusteGc ? "" : " · GC: " + t("nenhum") + " ⚠"), p.ajusteGc ? CINZA : AMARELO);
        if (!p.ajusteGc) {
            java.dica = List.of(Component.literal(t("dica_gc")));
        }
    }

    private static String nomeDimensao(String id) {
        String nome = id.startsWith("minecraft:") ? id.substring(10) : id;
        return nome.length() > 24 ? nome.substring(0, 23) + "…" : nome;
    }

    private void lag() {
        DadosPainel.Lag l = dados.lag;
        if (carregando(l)) {
            return;
        }
        if (!dados.lagAtivo) {
            texto(t("lag_desligado"), CINZA);
            return;
        }
        if (l.total == 0) {
            texto("✔ " + t("sem_lag"), VERDE);
            return;
        }
        texto(t("lag_total", plural(l.total, "travamento"), PainelStatus.duracao(l.totalMs), PainelStatus.duracao(l.piorMs), l.piorHora), AMARELO);
        StringBuilder causas = new StringBuilder(t("mais_culpados") + ": ");
        for (int i = 0; i < Math.min(3, l.causas.size()); i++) {
            DadosPainel.Causa c = l.causas.get(i);
            causas.append(i > 0 ? " · " : "").append(c.rotulo()).append(' ').append(c.porcento()).append('%');
        }
        Linha lc = texto(causas.toString(), BRANCO);
        List<Component> dica = new ArrayList<>();
        for (DadosPainel.Causa c : l.causas) {
            dica.add(Component.literal(c.rotulo() + " " + c.porcento() + "%" + (c.detalhe().isEmpty() ? "" : ": " + c.detalhe())));
        }
        lc.dica = dica;
        separador();
        boolean listra = false;
        for (DadosPainel.Registro r : l.registros) {
            Linha lr = texto(Component.literal(r.hora() + "  ").withStyle(st -> st.withColor(CINZA))
                    .append(Component.literal(String.format("%6s", PainelStatus.duracao(r.duracaoMs()))).withStyle(st -> st.withColor(AMARELO))));
            lr.texto2 = Component.literal(r.causa() + (r.detalhe().isEmpty() ? "" : " (" + r.detalhe() + ")")).withStyle(st -> st.withColor(BRANCO));
            lr.coluna2 = 100;
            lr.listra = listra;
            listra = !listra;
            lr.dica = List.of(Component.literal(r.tipo()), Component.literal(r.causa() + (r.detalhe().isEmpty() ? "" : ": " + r.detalhe())));
            int indice = r.indice();
            lr.acoes.add(new Acao(Component.literal(t("ver")),
                    () -> RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.DETALHE_LAG, Integer.toString(indice))), true, null));
        }
    }

    private void limpeza() {
        DadosPainel.Limpeza l = dados.limpeza;
        if (carregando(l)) {
            return;
        }
        if (!dados.limpezaAtiva) {
            texto(t("limpeza_desligada"), CINZA);
            return;
        }
        String quando = l.limpando ? t("limpando") : l.segundos < 0 ? "-" : String.format("%02d:%02d", l.segundos / 60, l.segundos % 60);
        Linha topo = texto(t("proxima_limpeza_em") + "  " + quando, BRANCO);
        long total = l.dimensoes.stream().mapToLong(DadosPainel.DimensaoItens::itens).sum();
        topo.acoes.add(new Acao(Component.literal(t("limpar_agora")),
                () -> RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.LIMPAR, "")), dados.operador,
                Component.literal(t("confirmar_limpar", PainelStatus.num(total))), AMARELO));
        topo.acoes.add(new Acao(Component.literal(t("cancelar_proxima")),
                () -> RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.CANCELAR, "")), dados.operador, null));
        titulo(t("itens_por_dimensao"));
        long maior = l.dimensoes.stream().mapToLong(DadosPainel.DimensaoItens::itens).max().orElse(1);
        for (DadosPainel.DimensaoItens dim : l.dimensoes) {
            Linha ld = new Linha();
            ld.tipo = Tipo.BARRA;
            ld.texto = Component.literal(nomeDimensao(dim.id()));
            ld.texto2 = Component.literal(PainelStatus.num(dim.itens())).withStyle(st -> st.withColor(BRANCO));
            ld.fracao = (float) dim.itens() / Math.max(1, maior);
            ld.cor = dim.itens() > 200 ? AMARELO : VERDE;
            ld.altura = 15;
            ld.coluna2 = 110;
            String id = dim.id();
            ld.acoes.add(new Acao(Component.literal(t("ir_ate_la")),
                    () -> RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.IR_ITENS, id)), dados.operador, null));
            linhas.add(ld);
        }
        if (l.dimensoes.isEmpty()) {
            texto(t("nenhum_item"), CINZA);
        }
        espaco();
        texto(t("limpeza_sessao", PainelStatus.num(l.limpezas), PainelStatus.num(l.removidos), PainelStatus.num(l.protegidos)), CINZA);
        texto(t("intervalo", l.intervaloMin), CINZA_ESCURO);
    }

    private void avisos() {
        if (carregando(dados.listaAvisos)) {
            return;
        }
        int i = 0;
        for (DadosPainel.Aviso a : dados.listaAvisos) {
            int indice = i++;
            boolean aberto = avisosAbertos.contains(indice);
            Linha l = texto(Component.literal("⚠ ").withStyle(st -> st.withColor(AMARELO)).append(Component.literal(a.curto()).withStyle(st -> st.withColor(BRANCO))));
            l.listra = indice % 2 == 1;
            l.acoes.add(new Acao(Component.literal(t(aberto ? "esconder" : "como_resolver")), () -> {
                if (!avisosAbertos.remove(indice)) {
                    avisosAbertos.add(indice);
                }
                reconstruir();
            }, true, null));
            if (aberto) {
                for (FormattedCharSequence parte : font.split(Component.literal(a.detalhe()), cw - 24)) {
                    Linha d = new Linha();
                    d.textoFormatado = parte;
                    d.altura = 10;
                    linhas.add(d);
                }
                espaco();
            }
        }
        if (dados.mixinsOk) {
            texto("✔ " + t("mixins_ok"), VERDE);
        }
        if (dados.listaAvisos.isEmpty()) {
            texto("✔ " + t("nenhum_aviso"), VERDE);
        }
    }

    // ---------- aba Config ----------

    private void config() {
        if (carregando(dados.config)) {
            return;
        }
        if (!dados.operador) {
            texto("🔒 " + t("so_leitura"), AMARELO);
        } else {
            texto(t("config_ajuda"), CINZA);
        }
        String grupo = null;
        for (DadosPainel.ItemConfig item : dados.config) {
            String prefixo = item.chave().contains(".") ? item.chave().substring(0, item.chave().indexOf('.')) : item.chave();
            if (!prefixo.equals(grupo)) {
                grupo = prefixo;
                titulo(Component.translatable("hlotimizacao.tela.grupo." + prefixo).getString());
            }
            ControleConfig c = controles.get(item.chave());
            if (c == null) {
                c = new ControleConfig(item);
                controles.put(item.chave(), c);
                if (c.tipo == Config.TipoChave.TEXTO || c.tipo == Config.TipoChave.LISTA_NUMEROS || c.tipo == Config.TipoChave.LISTA_PARES) {
                    ControleConfig alvo = c;
                    EditBox caixa = new EditBox(font, 0, 0, 100, 13, Component.literal(item.chave()));
                    caixa.setMaxLength(400);
                    caixa.setValue(item.valor());
                    caixa.setEditable(dados.operador);
                    caixa.moveCursorToStart();
                    caixa.setResponder(v -> alvo.valor = v);
                    c.caixa = caixa;
                    addWidget(caixa);
                }
            } else {
                c.atualizar(item);
            }
            Linha l = new Linha();
            l.tipo = Tipo.CONTROLE;
            l.controle = c;
            l.altura = 30;
            List<Component> dica = new ArrayList<>();
            dica.add(Component.literal(item.descricao()).withStyle(ChatFormatting.WHITE));
            dica.add(Component.literal(t("chave", item.chave())).withStyle(ChatFormatting.DARK_GRAY));
            if (c.tipo == Config.TipoChave.NUMERO) {
                dica.add(Component.literal(t("faixa", PainelStatus.num(item.min()), PainelStatus.num(item.max()))).withStyle(ChatFormatting.GRAY));
                if (item.emUso() >= 0) {
                    dica.add(Component.literal(t("em_uso", PainelStatus.num(item.emUso()))).withStyle(ChatFormatting.GRAY));
                }
                dica.add(Component.literal(t("dica_controle")).withStyle(ChatFormatting.DARK_AQUA));
                if (item.aceitaAuto()) {
                    dica.add(Component.literal(t("dica_auto")).withStyle(ChatFormatting.DARK_AQUA));
                }
            }
            l.dica = dica;
            linhas.add(l);
        }
    }

    /** Descricao sem o que o controle ja mostra ("(true/false)", "(1 a 1440)"). */
    private static String rotuloControle(String descricao) {
        return descricao.replaceAll("\\s*\\(true/false\\)", "")
                .replaceAll("\\(true/false;\\s*", "(")
                .replaceAll("\\s*\\(\\d+ (a|to) \\d+\\)", "");
    }

    private int mudancas() {
        return (int) controles.values().stream().filter(ControleConfig::mudou).count();
    }

    private void salvarConfig() {
        List<ControleConfig> mudados = controles.values().stream().filter(ControleConfig::mudou).toList();
        if (mudados.isEmpty()) {
            mensagem(true, t("sem_mudancas"));
            return;
        }
        for (ControleConfig c : mudados) {
            String erro = c.erro();
            if (erro != null) {
                mensagem(false, t("corrija") + " " + erro);
                return;
            }
        }
        for (ControleConfig c : mudados) {
            String valor = c.valor.trim();
            RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.CONFIG, c.item.chave() + "=" + valor));
            c.original = valor;
            c.valor = valor;
        }
        RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.RECARREGAR, ""));
        mensagem(true, t("config_enviada", mudados.size()));
    }

    private void desfazerTudo() {
        for (ControleConfig c : controles.values()) {
            desfazer(c);
        }
    }

    private static void desfazer(ControleConfig c) {
        c.valor = c.original;
        if (c.caixa != null) {
            c.caixa.setValue(c.original);
        }
    }

    // ---------- desenho ----------

    @Override
    public void render(GuiGraphics g, int mx, int my, float parcial) {
        renderBackground(g);
        // moldura
        g.fill(x0, y0, x0 + w, y0 + h, FUNDO);
        g.fill(x0, y0, x0 + w, y0 + 17, FUNDO_BARRA);
        g.fill(x0, y0 + h - RODAPE, x0 + w, y0 + h, FUNDO_BARRA);
        g.renderOutline(x0, y0, w, h, BORDA_OURO);
        g.fill(x0 + 1, y0 + 17, x0 + w - 1, y0 + 18, BORDA);
        g.drawString(font, Component.literal("HLO").withStyle(ChatFormatting.BOLD), x0 + 7, y0 + 5, OURO, false);
        String sub = (dados == null ? "" : dados.versao) + (dados != null && dados.dedicado ? "  ·  " + t("servidor_dedicado") : "");
        g.drawString(font, sub, x0 + 7 + font.width(Component.literal("HLO").withStyle(ChatFormatting.BOLD)) + 6, y0 + 5, CINZA_ESCURO, false);
        desenharFechar(g, mx, my);
        desenharAbas(g, mx, my);
        super.render(g, mx, my, parcial);

        g.enableScissor(cx - 3, cy, cx + cw + 3, cy + ch);
        int y = cy + 2 - (int) rolagem;
        Linha sobMouse = null;
        for (Linha l : linhas) {
            l.yTela = y;
            l.retangulosAcoes.clear();
            l.zonas.clear();
            boolean visivel = y + l.altura >= cy && y <= cy + ch;
            if (visivel) {
                desenhar(g, l, y, mx, my);
                if (my >= Math.max(y, cy) && my < Math.min(y + l.altura, cy + ch) && mx >= cx && mx < cx + cw) {
                    sobMouse = l;
                }
            } else if (l.controle != null && l.controle.caixa != null) {
                l.controle.caixa.visible = false;
            }
            y += l.altura;
        }
        g.disableScissor();
        desenharRolagem(g, mx, my);
        desenharRodape(g, mx, my);
        if (tituloModal != null) {
            desenharModal(g, mx, my);
            return;
        }
        if (arrastando == null && sobMouse != null && sobMouse.dica != null && !sobreAcao(sobMouse, mx, my)) {
            List<Component> dica = sobMouse.dica;
            if (sobMouse.controle != null && sobMouse.controle.mudou() && sobMouse.controle.erro() != null) {
                dica = new ArrayList<>(dica);
                dica.add(Component.literal(sobMouse.controle.erro()).withStyle(ChatFormatting.RED));
            }
            List<FormattedCharSequence> quebrada = new ArrayList<>();
            for (Component linha : dica) {
                quebrada.addAll(font.split(linha, 240));
            }
            g.renderTooltip(font, quebrada, mx, my);
        }
    }

    private void desenharFechar(GuiGraphics g, int mx, int my) {
        int bx = x0 + w - 16;
        boolean sobre = mx >= bx && mx < bx + 12 && my >= y0 + 3 && my < y0 + 15;
        if (sobre) {
            g.fill(bx, y0 + 3, bx + 12, y0 + 15, 0x40FF6B6B);
        }
        g.drawString(font, "✕", bx + 3, y0 + 5, sobre ? VERMELHO : CINZA, false);
    }

    private void desenharAbas(GuiGraphics g, int mx, int my) {
        g.fill(x0 + 1, y0 + 33, x0 + w - 1, y0 + 34, BORDA);
        for (Object[] item : abas) {
            Aba a = (Aba) item[0];
            int[] r = (int[]) item[1];
            boolean atual = a == aba;
            boolean sobre = !atual && mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
            if (atual) {
                g.fill(r[0], r[1], r[2], r[3], 0xFF262C36);
                g.fill(r[0], r[3] - 2, r[2], r[3], 0xFF000000 | OURO);
            } else if (sobre) {
                g.fill(r[0], r[1], r[2], r[3], 0xFF1E232B);
            }
            Component rotulo = rotuloAba(a);
            int largura = Math.min(font.width(rotulo), r[2] - r[0] - 2);
            g.drawString(font, cortarLargura(rotulo, largura), r[0] + (r[2] - r[0] - largura) / 2, r[1] + 3,
                    atual ? BRANCO : sobre ? 0xD6DAE0 : CINZA, false);
        }
    }

    private void desenharRolagem(GuiGraphics g, int mx, int my) {
        if (alturaConteudo <= ch) {
            return;
        }
        int[] r = barraRolagem();
        int trilhoX = x0 + w - 7;
        g.fill(trilhoX, cy, trilhoX + 3, cy + ch, 0x30FFFFFF);
        boolean sobre = arrastandoBarra || (mx >= trilhoX - 2 && mx < trilhoX + 5 && my >= r[0] && my < r[1]);
        g.fill(trilhoX, r[0], trilhoX + 3, r[1], sobre ? 0xFFD0D4DA : 0xFF7C838E);
    }

    /** {y inicial, y final} do cursor da barra de rolagem. */
    private int[] barraRolagem() {
        int altura = Math.max(14, ch * ch / Math.max(1, alturaConteudo));
        int y = cy + (int) ((ch - altura) * (rolagem / Math.max(1, alturaConteudo - ch)));
        return new int[]{y, y + altura};
    }

    private void desenharRodape(GuiGraphics g, int mx, int my) {
        zonasRodape.clear();
        int y = y0 + h - RODAPE + 4;
        int direita = x0 + w - 6;
        if (aba == Aba.CONFIG && dados != null && dados.config != null && dados.operador) {
            int n = mudancas();
            direita = botao(g, "salvar", t("salvar_n", n), direita, y - 2, n > 0, n > 0 ? VERDE : CINZA_ESCURO, mx, my);
            direita = botao(g, "desfazer", t("desfazer"), direita - 4, y - 2, n > 0, CINZA, mx, my);
        }
        String texto;
        int cor;
        if (mensagem != null && System.currentTimeMillis() < mensagemAte) {
            texto = mensagem;
            cor = corMensagem;
        } else {
            texto = t("rodape");
            cor = CINZA_ESCURO;
        }
        g.drawString(font, font.plainSubstrByWidth(texto, direita - x0 - 14), x0 + 7, y, cor, false);
    }

    /** Botao com fundo e borda, alinhado pela direita; devolve o x onde ele comeca. */
    private int botao(GuiGraphics g, String id, String texto, int direita, int y, boolean ativo, int corTexto, int mx, int my) {
        int largura = font.width(texto) + 10;
        int x = direita - largura;
        boolean sobre = ativo && mx >= x && mx < direita && my >= y && my < y + 12;
        g.fill(x, y, direita, y + 12, sobre ? 0xFF323A47 : 0xFF232933);
        g.renderOutline(x, y, largura, 12, sobre ? 0xFF6C7686 : BORDA);
        g.drawString(font, texto, x + 5, y + 2, !ativo ? CINZA_ESCURO : sobre ? AMARELO : corTexto, false);
        zonasRodape.put(id, new int[]{x, y, direita, y + 12, ativo ? 1 : 0});
        return x;
    }

    private boolean sobreAcao(Linha l, int mx, int my) {
        for (int[] r : l.retangulosAcoes) {
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
                return true;
            }
        }
        for (int[] r : l.zonas.values()) {
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
                return true;
            }
        }
        return false;
    }

    /** Botoes "pilula" da linha, da direita para a esquerda; devolve o x livre a esquerda deles. */
    private int desenharAcoes(GuiGraphics g, Linha l, int y, int mx, int my) {
        int direita = cx + cw - 2;
        int topo = y + (l.altura - 11) / 2;
        for (int i = l.acoes.size() - 1; i >= 0; i--) {
            Acao a = l.acoes.get(i);
            int largura = font.width(a.rotulo()) + 8;
            int x = direita - largura;
            boolean sobre = a.ativa() && mx >= x && mx < direita && my >= topo && my < topo + 11;
            int corBase = a.cor() != 0 ? a.cor() : AZUL;
            int corTexto = !a.ativa() ? (a.cor() != 0 ? escurecer(escurecer(corBase)) : CINZA_ESCURO) : sobre ? 0xFFFFFF : corBase;
            g.fill(x, topo, direita, topo + 11, sobre ? 0xFF353E4C : 0xFF20262F);
            g.renderOutline(x, topo, largura, 11, sobre ? 0xFF7A8597 : !a.ativa() ? 0xFF2A2F37 : 0xFF3D4552);
            g.drawString(font, a.rotulo(), x + 4, topo + 2, corTexto, false);
            l.retangulosAcoes.add(0, new int[]{x, topo, direita, topo + 11});
            direita = x - 4;
        }
        return direita;
    }

    private void desenhar(GuiGraphics g, Linha l, int y, int mx, int my) {
        if (l.listra) {
            g.fill(cx - 2, y, cx + cw, y + l.altura, LISTRA);
        }
        switch (l.tipo) {
            case ESPACO -> {
            }
            case SEPARADOR -> g.fill(cx, y + 3, cx + cw - 2, y + 4, 0xFF2C323C);
            case TITULO -> {
                int ty = y + l.altura - 11;
                g.drawString(font, l.texto, cx, ty, OURO, false);
                int fim = cx + font.width(l.texto) + 6;
                g.fill(fim, ty + 4, cx + cw - 2, ty + 5, 0x40FFB547);
            }
            case GRAFICO -> desenharGrafico(g, y);
            case CONTROLE -> desenharControle(g, l, y, mx, my);
            case BARRA -> {
                int limite = desenharAcoes(g, l, y, mx, my);
                g.drawString(font, cortarLargura(l.texto, (l.coluna2 > 0 ? l.coluna2 : 44) - 6), cx + 2, y + 4, BRANCO, false);
                int bx = cx + (l.coluna2 > 0 ? l.coluna2 : 44);
                int bw = Math.max(40, Math.min(180, limite - bx - 90));
                desenharTrilho(g, bx, y + 3, bw, 8, l.fracao, l.cor);
                g.drawString(font, l.texto2, bx + bw + 8, y + 4, BRANCO, false);
            }
            case TEXTO -> {
                int limite = desenharAcoes(g, l, y, mx, my);
                boolean clicavel = l.clique != null && mx >= cx && mx < limite && my >= y && my < y + l.altura;
                if (clicavel) {
                    g.fill(cx - 2, y, limite, y + l.altura, REALCE);
                }
                if (l.textoFormatado != null) {
                    g.drawString(font, l.textoFormatado, cx + 14, y + 1, CINZA, false);
                    return;
                }
                int ty = y + (l.altura - 8) / 2;
                int fimTexto = l.texto2 != null ? cx + l.coluna2 - 6 : limite - 2;
                g.drawString(font, cortarLargura(l.texto, fimTexto - cx - 2), cx + 2, ty, BRANCO, false);
                if (l.texto2 != null) {
                    g.drawString(font, cortarLargura(l.texto2, limite - (cx + l.coluna2) - 4), cx + l.coluna2, ty, CINZA, false);
                }
                if (clicavel) {
                    g.drawString(font, "›", limite - 8, ty, CINZA, false);
                }
            }
        }
    }

    /** Barra com fundo escuro, borda e um brilho na parte de cima do preenchimento. */
    private static void desenharTrilho(GuiGraphics g, int x, int y, int largura, int altura, float fracao, int cor) {
        g.fill(x - 1, y - 1, x + largura + 1, y + altura + 1, 0xFF0A0C0F);
        g.fill(x, y, x + largura, y + altura, 0xFF262B33);
        int cheio = Math.round(largura * Math.max(0, Math.min(1, fracao)));
        if (cheio > 0) {
            g.fill(x, y, x + cheio, y + altura, 0xFF000000 | escurecer(cor));
            g.fill(x, y, x + cheio, y + Math.max(1, altura / 2), 0xFF000000 | cor);
        }
    }

    private static int escurecer(int cor) {
        int r = (cor >> 16 & 0xFF) * 3 / 4;
        int gr = (cor >> 8 & 0xFF) * 3 / 4;
        int b = (cor & 0xFF) * 3 / 4;
        return r << 16 | gr << 8 | b;
    }

    // ---------- controles da aba Config ----------

    private void desenharControle(GuiGraphics g, Linha l, int y, int mx, int my) {
        ControleConfig c = l.controle;
        boolean editavel = dados.operador;
        boolean invalido = c.mudou() && c.erro() != null;
        if (my >= y && my < y + l.altura && mx >= cx && mx < cx + cw) {
            g.fill(cx - 2, y, cx + cw, y + l.altura, LISTRA);
        }
        // linha 1: descricao, marcador de alterado e botao de desfazer este valor
        int direita = cx + cw - 2;
        if (c.mudou() && editavel) {
            String desfazer = t("desfazer");
            int largura = font.width(desfazer) + 8;
            int x = direita - largura;
            boolean sobre = mx >= x && mx < direita && my >= y + 1 && my < y + 12;
            g.fill(x, y + 1, direita, y + 12, sobre ? 0xFF353E4C : 0xFF20262F);
            g.renderOutline(x, y + 1, largura, 11, sobre ? 0xFF7A8597 : 0xFF3D4552);
            g.drawString(font, desfazer, x + 4, y + 3, sobre ? AMARELO : CINZA, false);
            l.zonas.put("desfazer", new int[]{x, y + 1, direita, y + 12});
            direita = x - 4;
        }
        Component rotulo = Component.literal((c.mudou() ? "● " : "") + rotuloControle(c.item.descricao()))
                .withStyle(st -> st.withColor(invalido ? VERMELHO : c.mudou() ? AMARELO : BRANCO));
        g.drawString(font, cortarLargura(rotulo, direita - cx - 2), cx + 2, y + 3, BRANCO, false);
        int linha2 = y + 15;
        switch (c.tipo) {
            case NUMERO -> desenharDeslizante(g, l, c, linha2, editavel, mx, my);
            case BOOLEANO -> desenharInterruptor(g, l, c, linha2, editavel, mx, my);
            default -> {
                if (c.caixa != null) {
                    c.caixa.setX(cx + 3);
                    c.caixa.setY(linha2);
                    c.caixa.setWidth(cw - 8);
                    c.caixa.setTextColor(invalido ? 0xFF8080 : editavel ? 0xE0E0E0 : 0x8A8F96);
                    c.caixa.visible = linha2 >= cy && linha2 + 13 <= cy + ch;
                    c.caixa.render(g, mx, my, 0);
                    if (invalido && c.caixa.visible) {
                        g.renderOutline(cx + 2, linha2 - 1, cw - 6, 15, 0xFFFF5555);
                    }
                }
            }
        }
    }

    private void desenharDeslizante(GuiGraphics g, Linha l, ControleConfig c, int y, boolean editavel, int mx, int my) {
        int x = cx + 4;
        int direita = cx + cw - 4;
        boolean auto = c.auto();
        if (c.item.aceitaAuto()) {
            String rotulo = "AUTO";
            int largura = font.width(rotulo) + 8;
            boolean sobre = editavel && mx >= x && mx < x + largura && my >= y && my < y + 11;
            g.fill(x, y, x + largura, y + 11, auto ? 0xFF1F3A55 : sobre ? 0xFF2C333D : 0xFF1A1E25);
            g.renderOutline(x, y, largura, 11, auto ? 0xFF4F8FD0 : 0xFF3D4552);
            g.drawString(font, rotulo, x + 4, y + 2, auto ? AZUL : sobre ? BRANCO : CINZA_ESCURO, false);
            if (editavel) {
                l.zonas.put("auto", new int[]{x, y, x + largura, y + 11});
            }
            x += largura + 6;
        }
        // valor a direita
        int caixaLargura = 74;
        int caixaX = direita - caixaLargura;
        int numero = c.numeroOuEmUso();
        String texto = auto ? t("auto_valor", PainelStatus.num(numero)) : PainelStatus.num(numero);
        g.fill(caixaX, y - 1, direita, y + 12, FUNDO_CAMPO);
        g.renderOutline(caixaX, y - 1, caixaLargura, 13, c.mudou() ? 0xFF8A6A2A : BORDA);
        g.drawString(font, texto, caixaX + (caixaLargura - font.width(texto)) / 2, y + 2, auto ? CINZA : BRANCO, false);
        // < trilho >
        int menosX = x;
        int maisX = caixaX - 14;
        int trilhoX1 = menosX + 14;
        int trilhoX2 = maisX - 4;
        desenharSeta(g, l, "menos", "‹", menosX, y, editavel, mx, my);
        desenharSeta(g, l, "mais", "›", maisX, y, editavel, mx, my);
        float f = c.fracao(numero);
        int cor = auto || !editavel ? 0x6A7280 : AZUL;
        int largura = Math.max(10, trilhoX2 - trilhoX1);
        g.fill(trilhoX1, y + 4, trilhoX1 + largura, y + 7, 0xFF262B33);
        g.fill(trilhoX1, y + 4, trilhoX1 + Math.round(largura * f), y + 7, 0xFF000000 | cor);
        int puxadorX = trilhoX1 + Math.round(largura * f) - 3;
        boolean sobrePuxador = editavel && (arrastando == c || mx >= trilhoX1 - 3 && mx < trilhoX2 + 3 && my >= y - 1 && my < y + 12);
        g.fill(puxadorX, y, puxadorX + 6, y + 11, sobrePuxador ? 0xFFFFFFFF : auto || !editavel ? 0xFF9099A6 : 0xFFDDE3EA);
        g.renderOutline(puxadorX, y, 6, 11, 0xFF0A0C0F);
        if (editavel) {
            l.zonas.put("trilho", new int[]{trilhoX1 - 3, y - 1, trilhoX2 + 3, y + 12});
        }
    }

    private void desenharSeta(GuiGraphics g, Linha l, String id, String seta, int x, int y, boolean editavel, int mx, int my) {
        boolean sobre = editavel && mx >= x && mx < x + 11 && my >= y && my < y + 11;
        g.fill(x, y, x + 11, y + 11, sobre ? 0xFF353E4C : 0xFF20262F);
        g.renderOutline(x, y, 11, 11, sobre ? 0xFF7A8597 : 0xFF3D4552);
        g.drawString(font, seta, x + 3, y + 2, !editavel ? CINZA_ESCURO : sobre ? AMARELO : BRANCO, false);
        if (editavel) {
            l.zonas.put(id, new int[]{x, y, x + 11, y + 11});
        }
    }

    private void desenharInterruptor(GuiGraphics g, Linha l, ControleConfig c, int y, boolean editavel, int mx, int my) {
        boolean ligado = c.valor.trim().equals("true");
        int x = cx + 4;
        int largura = 24;
        boolean sobre = editavel && mx >= x && mx < x + largura + 60 && my >= y && my < y + 11;
        g.fill(x, y, x + largura, y + 11, ligado ? 0xFF2E7D4F : 0xFF3A3F48);
        g.renderOutline(x, y, largura, 11, sobre ? 0xFFB0B8C4 : 0xFF0A0C0F);
        int bola = ligado ? x + largura - 10 : x + 1;
        g.fill(bola, y + 1, bola + 9, y + 10, editavel ? 0xFFF2F4F7 : 0xFF9099A6);
        g.drawString(font, t(ligado ? "interruptor_sim" : "interruptor_nao"), x + largura + 6, y + 2, ligado ? VERDE : CINZA, false);
        if (editavel) {
            l.zonas.put("interruptor", new int[]{x, y, x + largura + 60, y + 11});
        }
    }

    /** Corta o texto na largura, mantendo as cores de cada parte, e poe "…" no fim. */
    private FormattedCharSequence cortarLargura(Component c, int largura) {
        if (font.width(c) <= largura) {
            return c.getVisualOrderText();
        }
        net.minecraft.network.chat.FormattedText cortado = font.substrByWidth(c, Math.max(0, largura - font.width("…")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(
                net.minecraft.network.chat.FormattedText.composite(cortado, Component.literal("…").withStyle(ChatFormatting.GRAY)));
    }

    private void desenharGrafico(GuiGraphics g, int y) {
        DadosPainel.Desempenho p = dados.desempenho;
        int gx = cx + 24;
        int gw = Math.min(cw - 30, 320);
        int gh = 56;
        int topo = y + 3;
        int base = topo + gh;
        float escala = 100;
        for (float v : p.historico) {
            escala = Math.max(escala, v);
        }
        escala = (float) (Math.ceil(escala / 50) * 50);
        g.fill(gx - 1, topo - 1, gx + gw + 1, base + 1, 0xFF0A0C0F);
        g.fill(gx, topo, gx + gw, base, 0xFF171A20);
        // linhas de grade a cada 25% e a de 50 ms tracejada
        for (int i = 1; i < 4; i++) {
            int gy = base - gh * i / 4;
            g.fill(gx, gy, gx + gw, gy + 1, 0xFF1F232A);
        }
        int y50 = base - (int) (gh * 50 / escala);
        for (int x = gx; x < gx + gw; x += 4) {
            g.fill(x, y50, x + 2, y50 + 1, 0xFF8A6A2A);
        }
        g.drawString(font, Integer.toString(Math.round(escala)), cx, topo, CINZA_ESCURO, false);
        g.drawString(font, "50", cx, y50 - 4, CINZA, false);
        g.drawString(font, "0", cx, base - 8, CINZA_ESCURO, false);
        int n = p.historico.length;
        // Uma coluna por segundo, alinhado a direita (o mais recente no fim).
        float largura = (float) gw / Desempenho5min.SEGUNDOS;
        for (int i = 0; i < n; i++) {
            float v = p.historico[i];
            int x1 = gx + gw - (int) ((n - i) * largura);
            int x2 = Math.max(x1 + 1, gx + gw - (int) ((n - i - 1) * largura));
            int altura = Math.max(1, (int) (gh * Math.min(v, escala) / escala));
            int cor = p.salvou[i] ? 0x5599FF : corMspt(v);
            g.fill(x1, base - altura, x2, base, 0xFF000000 | escurecer(cor));
            g.fill(x1, base - altura, x2, base - altura + 1, 0xFF000000 | cor);
        }
        g.drawString(font, t("legenda_grafico"), gx, base + 4, CINZA_ESCURO, false);
    }

    /** Constantes do grafico (o servidor manda ate 300 s). */
    private static final class Desempenho5min {
        static final int SEGUNDOS = 300;
    }

    private void desenharModal(GuiGraphics g, int mx, int my) {
        int mxw = w - 20;
        int mxh = h - 20;
        int x = x0 + 10;
        int y = y0 + 10;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x0, y0, x0 + w, y0 + h, 0x90000000);
        g.fill(x, y, x + mxw, y + mxh, 0xF80E1014);
        g.fill(x, y, x + mxw, y + 16, FUNDO_BARRA);
        g.renderOutline(x, y, mxw, mxh, BORDA_OURO);
        g.drawString(font, tituloModal, x + 6, y + 4, OURO, false);
        int areaY = y + 20;
        int areaH = mxh - 38;
        g.enableScissor(x + 4, areaY, x + mxw - 4, areaY + areaH);
        int ly = areaY - (int) rolagemModal;
        for (FormattedCharSequence linha : linhasModal) {
            if (ly + 10 >= areaY && ly <= areaY + areaH) {
                g.drawString(font, linha, x + 8, ly, 0xDDDDDD, false);
            }
            ly += 10;
        }
        g.disableScissor();
        int[] zonaCopiar = zonaModal(true);
        int[] zonaFechar = zonaModal(false);
        botaoModal(g, t("copiar"), zonaCopiar, mx, my);
        botaoModal(g, t("fechar"), zonaFechar, mx, my);
        g.pose().popPose();
    }

    private void botaoModal(GuiGraphics g, String texto, int[] r, int mx, int my) {
        boolean sobre = mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
        g.fill(r[0], r[1], r[2], r[3], sobre ? 0xFF353E4C : 0xFF20262F);
        g.renderOutline(r[0], r[1], r[2] - r[0], r[3] - r[1], sobre ? 0xFF7A8597 : 0xFF3D4552);
        g.drawString(font, texto, r[0] + 5, r[1] + 2, sobre ? AMARELO : BRANCO, false);
    }

    /** Retangulo dos botoes Copiar (true) e Fechar (false) da janela de texto. */
    private int[] zonaModal(boolean copiar) {
        int x = x0 + 10;
        int y = y0 + 10;
        int mxw = w - 20;
        int mxh = h - 20;
        int by = y + mxh - 15;
        int larguraFechar = font.width(t("fechar")) + 10;
        int fx = x + mxw - 6 - larguraFechar;
        if (!copiar) {
            return new int[]{fx, by, fx + larguraFechar, by + 12};
        }
        int larguraCopiar = font.width(t("copiar")) + 10;
        int cxp = fx - 6 - larguraCopiar;
        return new int[]{cxp, by, cxp + larguraCopiar, by + 12};
    }

    private static boolean dentro(int[] r, int mx, int my) {
        return r != null && mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    // ---------- entrada ----------

    @Override
    public boolean mouseClicked(double mxd, double myd, int botao) {
        int mx = (int) mxd;
        int my = (int) myd;
        if (tituloModal != null) {
            if (dentro(zonaModal(true), mx, my)) {
                minecraft.keyboardHandler.setClipboard(textoModal);
                mensagem(true, t("copiado"));
            } else if (dentro(zonaModal(false), mx, my)) {
                tituloModal = null;
            }
            return true;
        }
        // fechar e abas
        if (mx >= x0 + w - 16 && mx < x0 + w - 4 && my >= y0 + 3 && my < y0 + 15) {
            onClose();
            return true;
        }
        for (Object[] item : abas) {
            if (dentro((int[]) item[1], mx, my)) {
                trocarAba((Aba) item[0]);
                return true;
            }
        }
        // rodape
        for (Map.Entry<String, int[]> e : zonasRodape.entrySet()) {
            int[] r = e.getValue();
            if (r[4] == 1 && dentro(r, mx, my)) {
                if (e.getKey().equals("salvar")) {
                    salvarConfig();
                } else {
                    desfazerTudo();
                }
                return true;
            }
        }
        // barra de rolagem
        if (alturaConteudo > ch && mx >= x0 + w - 9 && mx < x0 + w - 2 && my >= cy && my < cy + ch) {
            arrastandoBarra = true;
            int[] r = barraRolagem();
            if (my < r[0] || my >= r[1]) {
                rolarPara(my);
            }
            return true;
        }
        if (super.mouseClicked(mxd, myd, botao)) { // caixas de texto
            return true;
        }
        setFocused(null);
        if (mx < cx - 2 || mx >= cx + cw || my < cy || my >= cy + ch) {
            return false;
        }
        for (Linha l : linhas) {
            if (my < l.yTela || my >= l.yTela + l.altura) {
                continue;
            }
            if (l.controle != null && cliqueControle(l, mx, my)) {
                return true;
            }
            for (int i = 0; i < l.retangulosAcoes.size() && i < l.acoes.size(); i++) {
                Acao a = l.acoes.get(i);
                if (a.ativa() && dentro(l.retangulosAcoes.get(i), mx, my)) {
                    executar(a);
                    return true;
                }
            }
            if (l.clique != null) {
                l.clique.run();
                return true;
            }
        }
        return false;
    }

    private boolean cliqueControle(Linha l, int mx, int my) {
        ControleConfig c = l.controle;
        if (dentro(l.zonas.get("desfazer"), mx, my)) {
            desfazer(c);
            return true;
        }
        if (dentro(l.zonas.get("auto"), mx, my)) {
            c.alternarAuto();
            return true;
        }
        if (dentro(l.zonas.get("menos"), mx, my)) {
            c.passo(-1);
            return true;
        }
        if (dentro(l.zonas.get("mais"), mx, my)) {
            c.passo(1);
            return true;
        }
        if (dentro(l.zonas.get("interruptor"), mx, my)) {
            c.valor = c.valor.trim().equals("true") ? "false" : "true";
            return true;
        }
        int[] trilho = l.zonas.get("trilho");
        if (dentro(trilho, mx, my)) {
            arrastando = c;
            trilhoArrastado = new int[]{trilho[0] + 3, trilho[2] - 3};
            arrastar(mx);
            return true;
        }
        return false;
    }

    private void arrastar(int mx) {
        float f = (float) (mx - trilhoArrastado[0]) / Math.max(1, trilhoArrastado[1] - trilhoArrastado[0]);
        arrastando.definirNumero(arrastando.valorDe(f));
    }

    private void rolarPara(int my) {
        float f = (float) (my - cy) / ch;
        rolagem = Math.max(0, Math.min(alturaConteudo - ch, f * alturaConteudo - ch / 2.0));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int botao, double dx, double dy) {
        if (arrastando != null) {
            arrastar((int) mx);
            return true;
        }
        if (arrastandoBarra) {
            int[] r = barraRolagem();
            int altura = r[1] - r[0];
            double f = (my - cy - altura / 2.0) / Math.max(1, ch - altura);
            rolagem = Math.max(0, Math.min(alturaConteudo - ch, f * (alturaConteudo - ch)));
            return true;
        }
        return super.mouseDragged(mx, my, botao, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int botao) {
        arrastando = null;
        arrastandoBarra = false;
        return super.mouseReleased(mx, my, botao);
    }

    private void executar(Acao a) {
        if (a.confirmar() == null) {
            a.executar().run();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(sim -> {
            if (sim) {
                a.executar().run();
            }
            minecraft.setScreen(this);
        }, Component.translatable("hlotimizacao.tela.confirmar"), a.confirmar()));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tituloModal != null) {
            int max = Math.max(0, linhasModal.size() * 10 - (h - 58));
            rolagemModal = Math.max(0, Math.min(max, rolagemModal - delta * 20));
            return true;
        }
        rolagem = Math.max(0, Math.min(Math.max(0, alturaConteudo - ch), rolagem - delta * 24));
        return true;
    }

    @Override
    public boolean keyPressed(int tecla, int scan, int mods) {
        if (tituloModal != null && tecla == 256) {
            tituloModal = null;
            return true;
        }
        if (com.hlotimizacao.cliente.ClienteHL.ABRIR.matches(tecla, scan) && !(getFocused() instanceof EditBox)) {
            onClose();
            return true;
        }
        return super.keyPressed(tecla, scan, mods);
    }
}
