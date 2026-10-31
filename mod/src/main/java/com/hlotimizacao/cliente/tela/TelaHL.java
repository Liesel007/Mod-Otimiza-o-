package com.hlotimizacao.cliente.tela;

import com.hlotimizacao.PainelStatus;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.rede.DadosPainel;
import com.hlotimizacao.rede.DadosPainel.Aba;
import com.hlotimizacao.rede.RedeHL;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tela do /hl (e da tecla F8): abas com tudo o que o mod mostra. Os dados vem do servidor
 * (DadosPainel) a cada 1 s, so da aba aberta. Nao pausa o jogo. Os botoes de acao so aparecem
 * para operadores, mas a permissao e conferida de novo no servidor.
 */
public class TelaHL extends Screen {
    private static final int LARGURA_MAX = 420;
    private static final int ALTURA_MAX = 260;
    private static final int LINHA = 11;
    private static final int VERDE = 0x55FF55;
    private static final int AMARELO = 0xFFFF55;
    private static final int VERMELHO = 0xFF5555;
    private static final int CINZA = 0xAAAAAA;
    private static final int CINZA_ESCURO = 0x555555;
    private static final int BRANCO = 0xFFFFFF;
    private static final int OURO = 0xFFAA00;

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
    private final Map<String, EditBox> campos = new LinkedHashMap<>();
    private final Map<String, String> camposOriginais = new LinkedHashMap<>();
    private String mensagem;
    private int corMensagem = BRANCO;
    private long mensagemAte;
    private String tituloModal;
    private List<FormattedCharSequence> linhasModal;
    private String textoModal;
    private double rolagemModal;

    /** Um botao de texto dentro de uma linha ("[Limpar agora]"). */
    private record Acao(Component rotulo, Runnable executar, boolean ativa, Component confirmar) {
    }

    private enum Tipo {
        TEXTO, BARRA, GRAFICO, SEPARADOR, ESPACO, CAMPO
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
        String campo;
        FormattedCharSequence textoFormatado;
        int altura = LINHA;
        // calculado ao desenhar
        int yTela;
        final List<int[]> retangulosAcoes = new ArrayList<>();
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
        cx = x0 + 6;
        cy = y0 + 36;
        cw = w - 12;
        ch = h - 42;
        int x = x0 + 4;
        for (Aba a : Aba.values()) {
            Component rotulo = Component.translatable("hlotimizacao.tela.aba." + a.name().toLowerCase());
            int largura = font.width(rotulo) + 8;
            Aba destino = a;
            Button botao = Button.builder(rotulo, b -> trocarAba(destino)).bounds(x, y0 + 17, largura, 14).build();
            botao.active = a != aba;
            addRenderableWidget(botao);
            x += largura + 2;
        }
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose()).bounds(x0 + w - 15, y0 + 2, 13, 13).build());
        campos.values().forEach(this::addWidget);
        RedeHL.CANAL.sendToServer(new RedeHL.C2SInscrever(aba));
        reconstruir();
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
        campos.values().forEach(EditBox::tick);
    }

    private void trocarAba(Aba nova) {
        aba = nova;
        rolagem = 0;
        if (nova != Aba.CONFIG) {
            campos.values().forEach(this::removeWidget);
            campos.clear();
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
        l.altura = 6;
        linhas.add(l);
    }

    private void barra(String rotulo, float fracao, int cor, String valor) {
        Linha l = new Linha();
        l.tipo = Tipo.BARRA;
        l.texto = Component.literal(rotulo);
        l.texto2 = Component.literal(valor).withStyle(st -> st.withColor(cor));
        l.fracao = Math.max(0, Math.min(1, fracao));
        l.cor = cor;
        l.altura = 13;
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
        alturaConteudo = linhas.stream().mapToInt(l -> l.altura).sum();
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
            texto(t("reiniciar"), AMARELO);
            espaco();
        }
        for (DadosPainel.Modulo m : dados.modulos) {
            boolean ligado = m.estado() == DadosPainel.Modulo.LIGADO;
            String simbolo = ligado ? "✔" : m.estado() == DadosPainel.Modulo.NAO_NECESSARIO ? "–" : "✘";
            int cor = ligado ? VERDE : m.estado() == DadosPainel.Modulo.NAO_NECESSARIO ? CINZA_ESCURO : CINZA;
            String resumo = m.resumo();
            Modulo local = moduloLocal(m.chave());
            if (ligado && m.soCliente() && local != null) {
                resumo = PainelStatus.resumoCurto(local); // contadores deste cliente (GPU, tooltip)
            }
            Linha l = texto(Component.literal(simbolo + " ").withStyle(st -> st.withColor(cor))
                    .append(Component.literal(m.nome()).withStyle(st -> st.withColor(ligado ? BRANCO : CINZA))));
            l.texto2 = Component.literal(resumo).withStyle(st -> st.withColor(ligado ? CINZA : cor));
            l.coluna2 = 172;
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
            l.acoes.add(new Acao(Component.literal("[" + m.valor().toUpperCase() + "]"),
                    () -> RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.MODULO, m.chave() + "=" + proximo)), dados.operador, null));
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
        texto(t("mspt_5min"), BRANCO);
        Linha g = new Linha();
        g.tipo = Tipo.GRAFICO;
        g.altura = 72;
        linhas.add(g);
        texto(t("min_max", Math.round(p.minMs), Math.round(p.maxMs)), CINZA);
        if (!p.ultimoSalvamentoHora.isEmpty()) {
            texto(t("ultimo_salvamento") + ": " + PainelStatus.duracao(p.ultimoSalvamentoMs) + " " + t("as") + " " + p.ultimoSalvamentoHora, CINZA);
        }
        texto(t("coleta_lixo", p.pausasGc) + (p.pausasGc > 0 ? ", " + t("maior") + " " + PainelStatus.duracao(p.maiorPausaGcMs) : "")
                + " · -Xms " + (p.xms > 0 ? gb(p.xms) + " GB" : "?") + " / -Xmx " + gb(dados.memMax) + " GB", p.pausasGc > 0 ? AMARELO : CINZA);
        texto(t("memoria_detalhe", gb(dados.memUsada), gb(p.memAlocada), gb(dados.memMax)), CINZA);
        espaco();
        texto(t("dimensoes"), BRANCO);
        for (DadosPainel.Dimensao dim : p.dimensoes) {
            boolean pesada = dim.entidades() > 300 || dim.itens() > 200;
            Linha l = texto(Component.literal("  " + nomeDimensao(dim.id())).withStyle(st -> st.withColor(pesada ? AMARELO : BRANCO)));
            l.texto2 = Component.literal(t("dimensao_linha", dim.jogadores(), PainelStatus.num(dim.chunks()), PainelStatus.num(dim.entidades()))
                    + (pesada ? "  ⚠" : "")).withStyle(st -> st.withColor(pesada ? AMARELO : CINZA));
            l.coluna2 = 120;
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
        for (DadosPainel.Registro r : l.registros) {
            Linha lr = texto(Component.literal(r.hora() + "  ").withStyle(st -> st.withColor(CINZA))
                    .append(Component.literal(String.format("%6s", PainelStatus.duracao(r.duracaoMs()))).withStyle(st -> st.withColor(AMARELO))));
            lr.texto2 = Component.literal(r.causa() + (r.detalhe().isEmpty() ? "" : " (" + r.detalhe() + ")")).withStyle(st -> st.withColor(BRANCO));
            lr.coluna2 = 100;
            lr.dica = List.of(Component.literal(r.tipo()), Component.literal(r.causa() + (r.detalhe().isEmpty() ? "" : ": " + r.detalhe())));
            int indice = r.indice();
            lr.acoes.add(new Acao(Component.literal("[" + t("ver") + "]"),
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
        topo.acoes.add(new Acao(Component.literal("[" + t("limpar_agora") + "]"),
                () -> RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.LIMPAR, "")), dados.operador,
                Component.literal(t("confirmar_limpar", PainelStatus.num(total)))));
        topo.acoes.add(new Acao(Component.literal("[" + t("cancelar_proxima") + "]"),
                () -> RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.CANCELAR, "")), dados.operador, null));
        espaco();
        texto(t("itens_por_dimensao"), BRANCO);
        long maior = l.dimensoes.stream().mapToLong(DadosPainel.DimensaoItens::itens).max().orElse(1);
        for (DadosPainel.DimensaoItens dim : l.dimensoes) {
            Linha ld = new Linha();
            ld.tipo = Tipo.BARRA;
            ld.texto = Component.literal("  " + nomeDimensao(dim.id()));
            ld.texto2 = Component.literal(PainelStatus.num(dim.itens())).withStyle(st -> st.withColor(BRANCO));
            ld.fracao = (float) dim.itens() / Math.max(1, maior);
            ld.cor = dim.itens() > 200 ? AMARELO : VERDE;
            ld.altura = 13;
            ld.coluna2 = 120;
            String id = dim.id();
            ld.acoes.add(new Acao(Component.literal("[" + t("ir_ate_la") + "]"),
                    () -> RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.IR_ITENS, id)), dados.operador, null));
            linhas.add(ld);
        }
        if (l.dimensoes.isEmpty()) {
            texto("  " + t("nenhum_item"), CINZA);
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
            l.acoes.add(new Acao(Component.literal("[" + t(aberto ? "esconder" : "como_resolver") + "]"), () -> {
                if (!avisosAbertos.remove(indice)) {
                    avisosAbertos.add(indice);
                }
                reconstruir();
            }, true, null));
            if (aberto) {
                for (FormattedCharSequence parte : font.split(Component.literal(a.detalhe()), cw - 20)) {
                    Linha d = new Linha();
                    d.textoFormatado = parte;
                    linhas.add(d);
                }
            }
        }
        if (dados.mixinsOk) {
            texto("✔ " + t("mixins_ok"), VERDE);
        }
        if (dados.listaAvisos.isEmpty()) {
            texto("✔ " + t("nenhum_aviso"), VERDE);
        }
    }

    private void config() {
        if (carregando(dados.config)) {
            return;
        }
        if (!dados.operador) {
            texto(t("so_leitura"), AMARELO);
        }
        for (DadosPainel.ItemConfig item : dados.config) {
            Linha rotulo = texto(item.chave(), BRANCO);
            rotulo.dica = List.of(Component.literal(item.descricao()));
            Linha campo = new Linha();
            campo.tipo = Tipo.CAMPO;
            campo.campo = item.chave();
            campo.altura = 18;
            linhas.add(campo);
            if (!campos.containsKey(item.chave())) {
                EditBox caixa = new EditBox(font, 0, 0, cw - 8, 14, Component.literal(item.chave()));
                caixa.setMaxLength(400);
                caixa.setValue(item.valor());
                caixa.setEditable(dados.operador);
                campos.put(item.chave(), caixa);
                camposOriginais.put(item.chave(), item.valor());
                addWidget(caixa);
            }
        }
        espaco();
        Linha salvar = texto(t("config_ajuda"), CINZA);
        salvar.acoes.add(new Acao(Component.literal("[" + t("salvar_recarregar") + "]"), this::salvarConfig, dados.operador, null));
    }

    private void salvarConfig() {
        int mudados = 0;
        for (Map.Entry<String, EditBox> e : campos.entrySet()) {
            String valor = e.getValue().getValue();
            if (!valor.equals(camposOriginais.get(e.getKey()))) {
                RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.CONFIG, e.getKey() + "=" + valor));
                camposOriginais.put(e.getKey(), valor);
                mudados++;
            }
        }
        RedeHL.CANAL.sendToServer(new RedeHL.C2SAcao(RedeHL.Acao.RECARREGAR, ""));
        mensagem(true, t("config_enviada", mudados));
    }

    // ---------- desenho ----------

    @Override
    public void render(GuiGraphics g, int mx, int my, float parcial) {
        renderBackground(g);
        g.fill(x0, y0, x0 + w, y0 + h, 0xE0101010);
        g.renderOutline(x0, y0, w, h, 0xFF806020);
        g.drawString(font, Component.literal(t("titulo_versao", dados == null ? "?" : dados.versao)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                x0 + 6, y0 + 5, OURO);
        g.fill(x0 + 2, y0 + 33, x0 + w - 2, y0 + 34, 0xFF806020);
        super.render(g, mx, my, parcial);

        g.enableScissor(cx, cy, cx + cw, cy + ch);
        int y = cy - (int) rolagem;
        Linha sobMouse = null;
        for (Linha l : linhas) {
            l.yTela = y;
            l.retangulosAcoes.clear();
            if (y + l.altura >= cy && y <= cy + ch) {
                desenhar(g, l, y, mx, my);
                if (my >= Math.max(y, cy) && my < Math.min(y + l.altura, cy + ch) && mx >= cx && mx < cx + cw) {
                    sobMouse = l;
                }
            } else if (l.tipo == Tipo.CAMPO && campos.containsKey(l.campo)) {
                campos.get(l.campo).visible = false;
            }
            y += l.altura;
        }
        g.disableScissor();
        if (alturaConteudo > ch) {
            int barraAltura = Math.max(10, ch * ch / alturaConteudo);
            int barraY = cy + (int) ((ch - barraAltura) * (rolagem / (alturaConteudo - ch)));
            g.fill(cx + cw - 2, barraY, cx + cw, barraY + barraAltura, 0xFF888888);
        }
        if (mensagem != null && System.currentTimeMillis() < mensagemAte) {
            g.drawString(font, font.plainSubstrByWidth(mensagem, w - 12), x0 + 6, y0 + h - 10, corMensagem);
        }
        if (tituloModal != null) {
            desenharModal(g, mx, my);
            return;
        }
        if (sobMouse != null && sobMouse.dica != null && !sobreAcao(sobMouse, mx, my)) {
            g.renderComponentTooltip(font, sobMouse.dica, mx, my);
        }
    }

    private boolean sobreAcao(Linha l, int mx, int my) {
        for (int[] r : l.retangulosAcoes) {
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
                return true;
            }
        }
        return false;
    }

    private int desenharAcoes(GuiGraphics g, Linha l, int y, int mx, int my) {
        int direita = cx + cw - 4;
        for (int i = l.acoes.size() - 1; i >= 0; i--) {
            Acao a = l.acoes.get(i);
            int largura = font.width(a.rotulo());
            int x = direita - largura;
            boolean sobre = a.ativa() && mx >= x && mx < direita && my >= y && my < y + LINHA;
            g.drawString(font, a.rotulo(), x, y + 1, !a.ativa() ? CINZA_ESCURO : sobre ? AMARELO : 0x55FFFF, false);
            l.retangulosAcoes.add(0, new int[]{x, y, direita, y + LINHA});
            direita = x - 6;
        }
        return direita;
    }

    private void desenhar(GuiGraphics g, Linha l, int y, int mx, int my) {
        switch (l.tipo) {
            case ESPACO -> {
            }
            case SEPARADOR -> g.fill(cx, y + 2, cx + cw - 4, y + 3, 0xFF444444);
            case GRAFICO -> desenharGrafico(g, y);
            case CAMPO -> {
                EditBox caixa = campos.get(l.campo);
                if (caixa != null) {
                    caixa.setX(cx + 2);
                    caixa.setY(y + 1);
                    caixa.visible = y + 1 >= cy && y + 15 <= cy + ch;
                    caixa.render(g, mx, my, 0);
                }
            }
            case BARRA -> {
                int limite = desenharAcoes(g, l, y, mx, my);
                g.drawString(font, l.texto, cx + 2, y + 2, BRANCO, false);
                int bx = cx + (l.coluna2 > 0 ? l.coluna2 : 44);
                int bw = Math.min(170, limite - bx - 70);
                g.fill(bx, y + 2, bx + bw, y + 10, 0xFF333333);
                g.fill(bx, y + 2, bx + (int) (bw * l.fracao), y + 10, 0xFF000000 | l.cor);
                g.drawString(font, l.texto2, bx + bw + 6, y + 2, BRANCO, false);
            }
            case TEXTO -> {
                int limite = desenharAcoes(g, l, y, mx, my);
                boolean clicavel = l.clique != null && mx >= cx && mx < limite && my >= y && my < y + LINHA;
                if (clicavel) {
                    g.fill(cx, y, limite, y + LINHA, 0x30FFFFFF);
                }
                if (l.textoFormatado != null) {
                    g.drawString(font, l.textoFormatado, cx + 14, y + 1, CINZA, false);
                    return;
                }
                int fimTexto = l.texto2 != null ? cx + l.coluna2 - 4 : limite - 2;
                g.drawString(font, cortarLargura(l.texto, fimTexto - cx - 2), cx + 2, y + 1, BRANCO, false);
                if (l.texto2 != null) {
                    g.drawString(font, cortarLargura(l.texto2, limite - (cx + l.coluna2) - 4), cx + l.coluna2, y + 1, CINZA, false);
                }
            }
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
        int gx = cx + 26;
        int gw = Math.min(cw - 34, 300);
        int gh = 56;
        int base = y + gh + 2;
        float escala = 100;
        for (float v : p.historico) {
            escala = Math.max(escala, v);
        }
        escala = (float) (Math.ceil(escala / 50) * 50);
        g.fill(gx, y + 2, gx + gw, base, 0xFF1A1A1A);
        int y50 = base - (int) (gh * 50 / escala);
        for (int x = gx; x < gx + gw; x += 4) {
            g.fill(x, y50, x + 2, y50 + 1, 0xFF886622);
        }
        g.drawString(font, Integer.toString(Math.round(escala)), cx, y + 2, CINZA, false);
        g.drawString(font, "50", cx, y50 - 4, CINZA, false);
        g.drawString(font, "0", cx, base - 8, CINZA, false);
        int n = p.historico.length;
        // Uma coluna por segundo, alinhado a direita (o mais recente no fim).
        float largura = (float) gw / Desempenho5min.SEGUNDOS;
        for (int i = 0; i < n; i++) {
            float v = p.historico[i];
            int x1 = gx + gw - (int) ((n - i) * largura);
            int x2 = Math.max(x1 + 1, gx + gw - (int) ((n - i - 1) * largura));
            int altura = Math.max(1, (int) (gh * Math.min(v, escala) / escala));
            int cor = p.salvou[i] ? 0xFF5599FF : 0xFF000000 | corMspt(v);
            g.fill(x1, base - altura, x2, base, cor);
        }
        g.drawString(font, t("legenda_grafico"), gx, base + 1, CINZA_ESCURO, false);
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
        g.fill(x, y, x + mxw, y + mxh, 0xF0000000);
        g.renderOutline(x, y, mxw, mxh, 0xFFFFAA00);
        g.drawString(font, tituloModal, x + 6, y + 5, OURO);
        int areaY = y + 18;
        int areaH = mxh - 34;
        g.enableScissor(x + 4, areaY, x + mxw - 4, areaY + areaH);
        int ly = areaY - (int) rolagemModal;
        for (FormattedCharSequence linha : linhasModal) {
            if (ly + 10 >= areaY && ly <= areaY + areaH) {
                g.drawString(font, linha, x + 8, ly, 0xDDDDDD, false);
            }
            ly += 10;
        }
        g.disableScissor();
        String copiar = "[" + t("copiar") + "]";
        String fechar = "[" + t("fechar") + "]";
        int by = y + mxh - 13;
        int fx = x + mxw - 6 - font.width(fechar);
        int cxp = fx - 10 - font.width(copiar);
        g.drawString(font, copiar, cxp, by, sobre(mx, my, cxp, by, font.width(copiar)) ? AMARELO : 0x55FFFF, false);
        g.drawString(font, fechar, fx, by, sobre(mx, my, fx, by, font.width(fechar)) ? AMARELO : 0x55FFFF, false);
        g.pose().popPose();
    }

    private static boolean sobre(int mx, int my, int x, int y, int largura) {
        return mx >= x && mx < x + largura && my >= y - 1 && my < y + 10;
    }

    // ---------- entrada ----------

    @Override
    public boolean mouseClicked(double mxd, double myd, int botao) {
        int mx = (int) mxd;
        int my = (int) myd;
        if (tituloModal != null) {
            int mxw = w - 20;
            int mxh = h - 20;
            int x = x0 + 10;
            int y = y0 + 10;
            String copiar = "[" + t("copiar") + "]";
            String fechar = "[" + t("fechar") + "]";
            int by = y + mxh - 13;
            int fx = x + mxw - 6 - font.width(fechar);
            int cxp = fx - 10 - font.width(copiar);
            if (sobre(mx, my, cxp, by, font.width(copiar))) {
                minecraft.keyboardHandler.setClipboard(textoModal);
                mensagem(true, t("copiado"));
            } else if (sobre(mx, my, fx, by, font.width(fechar))) {
                tituloModal = null;
            }
            return true;
        }
        if (super.mouseClicked(mxd, myd, botao)) {
            return true;
        }
        if (mx < cx || mx >= cx + cw || my < cy || my >= cy + ch) {
            return false;
        }
        for (Linha l : linhas) {
            if (my < l.yTela || my >= l.yTela + l.altura) {
                continue;
            }
            for (int i = 0; i < l.retangulosAcoes.size() && i < l.acoes.size(); i++) {
                int[] r = l.retangulosAcoes.get(i);
                Acao a = l.acoes.get(i);
                if (a.ativa() && mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
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
            int max = Math.max(0, linhasModal.size() * 10 - (h - 54));
            rolagemModal = Math.max(0, Math.min(max, rolagemModal - delta * 20));
            return true;
        }
        rolagem = Math.max(0, Math.min(Math.max(0, alturaConteudo - ch), rolagem - delta * 22));
        return true;
    }

    @Override
    public boolean keyPressed(int tecla, int scan, int mods) {
        if (tituloModal != null && tecla == 256) {
            tituloModal = null;
            return true;
        }
        if (ClienteTecla.ehAbrir(tecla, scan) && getFocused() == null) {
            onClose();
            return true;
        }
        return super.keyPressed(tecla, scan, mods);
    }

    /** F8 de novo fecha a tela (quando nenhum campo de texto esta em foco). */
    private static final class ClienteTecla {
        static boolean ehAbrir(int tecla, int scan) {
            return com.hlotimizacao.cliente.ClienteHL.ABRIR.matches(tecla, scan);
        }
    }
}
