package com.hlotimizacao.rede;

import com.hlotimizacao.PainelStatus;
import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.config.Msg;
import com.hlotimizacao.mixin.ErrosMixin;
import com.hlotimizacao.recursos.Avisos;
import com.hlotimizacao.recursos.Desempenho;
import com.hlotimizacao.recursos.DiagnosticoAmbiente;
import com.hlotimizacao.recursos.DiagnosticoLag;
import com.hlotimizacao.recursos.Estatisticas;
import com.hlotimizacao.recursos.LimpezaItens;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Monta o DadosPainel no servidor, com os mesmos contadores e textos do /hl status no chat
 * (PainelStatus), para a tela e o chat nunca mostrarem numeros diferentes.
 */
public final class ColetorStatus {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_REGISTROS = 20;

    private ColetorStatus() {
    }

    public static DadosPainel coletar(MinecraftServer server, DadosPainel.Aba aba, boolean operador) {
        DadosPainel d = new DadosPainel();
        d.aba = aba;
        d.versao = ModList.get().getModContainerById("hlotimizacao").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        d.operador = operador;
        d.dedicado = FMLEnvironment.dist == Dist.DEDICATED_SERVER;
        Desempenho.Tempos tempos = Desempenho.tempos();
        d.msptMedio = (float) (tempos.ticks() > 0 ? tempos.mediaMs() : server.getAverageTickTime());
        d.tps = (float) (tempos.ticks() > 0 ? tempos.tps() : Math.min(20, 1000 / Math.max(50, d.msptMedio)));
        d.msptPico = (float) tempos.maxMs();
        Runtime rt = Runtime.getRuntime();
        d.memUsada = rt.totalMemory() - rt.freeMemory();
        d.memMax = rt.maxMemory();
        d.sessaoMs = Desempenho.sessaoMs();
        for (Modulo m : Modulo.values()) {
            if (Config.ativo(m)) {
                d.ligados++;
            } else {
                d.desligados++;
            }
        }
        d.limpezaAtiva = Config.ativo(Modulo.LIMPEZA_ITENS);
        long[] chao = null;
        if (d.limpezaAtiva) {
            chao = LimpezaItens.contarNoChao(server);
            d.limpezaSegundos = LimpezaItens.segundosParaProxima(server);
            d.limpando = LimpezaItens.limpandoAgora();
            d.itensNoChao = chao[1];
        }
        d.lagAtivo = Config.ativo(Modulo.DIAGNOSTICO_LAG);
        d.lagRegistros = Estatisticas.LAG_REGISTROS.get();
        DiagnosticoLag.Registro pior = DiagnosticoLag.pior();
        d.lagPiorMs = pior == null ? 0 : pior.duracaoMs();
        String culpado = PainelStatus.maisCulpado();
        d.maisCulpado = culpado == null ? "" : culpado;
        List<Avisos.Aviso> avisos = PainelStatus.todosOsAvisos();
        d.avisos = avisos.size();
        d.preset = Config.preset().nome();
        d.mods = Config.modsCarregados();

        switch (aba) {
            case MODULOS -> d.modulos = modulos(d);
            case DESEMPENHO -> d.desempenho = desempenho(server, tempos);
            case LAG -> d.lag = lag();
            case LIMPEZA -> d.limpeza = limpeza(server);
            case AVISOS -> {
                d.listaAvisos = new ArrayList<>();
                for (Avisos.Aviso a : avisos) {
                    d.listaAvisos.add(new DadosPainel.Aviso(a.curto(), a.detalhe()));
                }
                d.mixinsOk = ErrosMixin.FALHAS.isEmpty();
            }
            case CONFIG -> {
                d.config = new ArrayList<>();
                for (Map.Entry<String, String> e : Config.EDITAVEIS.entrySet()) {
                    d.config.add(new DadosPainel.ItemConfig(e.getKey(), Config.valorTexto(e.getKey()), com.hlotimizacao.config.Msg.t(e.getValue())));
                }
            }
            default -> {
            }
        }
        return d;
    }

    static List<DadosPainel.Modulo> modulos(DadosPainel d) {
        List<DadosPainel.Modulo> lista = new ArrayList<>();
        List<Modulo> ordem = new ArrayList<>(List.of(Modulo.values()));
        ordem.sort(Comparator.comparingInt(m -> Config.ativo(m) ? 0 : Config.naoNecessario(m) ? 2 : 1));
        for (Modulo m : ordem) {
            boolean ativo = Config.ativo(m);
            byte estado = ativo ? DadosPainel.Modulo.LIGADO : Config.naoNecessario(m) ? DadosPainel.Modulo.NAO_NECESSARIO : DadosPainel.Modulo.DESLIGADO;
            String resumo = !ativo ? (Config.naoNecessario(m) ? com.hlotimizacao.config.Msg.t("não necessário") : Config.motivoCurto(m)) : PainelStatus.resumoCurto(m);
            String detalhe = ativo ? PainelStatus.detalhe(m) : Config.motivo(m);
            lista.add(new DadosPainel.Modulo(m.chave, m.nome(), estado, Config.valorConfigurado(m), resumo, m.texto(), detalhe, m.soCliente));
        }
        d.mudancaPendente = Config.temMudancaPendente();
        return lista;
    }

    static DadosPainel.Desempenho desempenho(MinecraftServer server, Desempenho.Tempos tempos) {
        DadosPainel.Desempenho p = new DadosPainel.Desempenho();
        boolean[] salvou = new boolean[Desempenho.SEGUNDOS_HISTORICO];
        p.historico = Desempenho.historicoMspt(salvou);
        p.salvou = java.util.Arrays.copyOf(salvou, p.historico.length);
        p.minMs = (float) tempos.minMs();
        p.maxMs = (float) tempos.maxMs();
        p.ultimoSalvamentoMs = Desempenho.ultimoSalvamentoMs();
        p.ultimoSalvamentoHora = Desempenho.ultimoSalvamentoHora() == null ? "" : Desempenho.ultimoSalvamentoHora().format(HORA);
        p.memAlocada = Runtime.getRuntime().totalMemory();
        p.pausasGc = Desempenho.pausasGcLongas();
        p.maiorPausaGcMs = Desempenho.maiorPausaGcMs();
        p.xms = DiagnosticoAmbiente.xms();
        Runtime.Version v = Runtime.version();
        p.java = v.feature() + "." + v.interim() + "." + v.update() + " (" + System.getProperty("java.vendor", "?") + ")";
        p.ajusteGc = DiagnosticoAmbiente.temAjusteGc();
        p.custoMs = (float) com.hlotimizacao.recursos.Custo.msPorTick();
        for (ServerLevel level : server.getAllLevels()) {
            int entidades = 0;
            int itens = 0;
            for (Entity e : level.getAllEntities()) {
                entidades++;
                if (e instanceof ItemEntity) {
                    itens++;
                }
            }
            int chunks = level.getChunkSource().getLoadedChunksCount();
            int jogadores = level.players().size();
            if (jogadores > 0 || chunks > 0 || entidades > 0) {
                p.dimensoes.add(new DadosPainel.Dimensao(level.dimension().location().toString(), jogadores, chunks, entidades, itens));
            }
        }
        return p;
    }

    static DadosPainel.Lag lag() {
        DadosPainel.Lag l = new DadosPainel.Lag();
        l.total = Estatisticas.LAG_REGISTROS.get();
        l.totalMs = DiagnosticoLag.msTotal();
        DiagnosticoLag.Registro pior = DiagnosticoLag.pior();
        if (pior != null) {
            l.piorMs = pior.duracaoMs();
            l.piorHora = pior.hora().format(HORA);
        }
        long total = Math.max(1, l.totalMs);
        List<Map.Entry<String, Long>> ranking = new ArrayList<>(DiagnosticoLag.msPorMod().entrySet());
        ranking.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        for (int i = 0; i < Math.min(5, ranking.size()); i++) {
            Map.Entry<String, Long> e = ranking.get(i);
            String chave = e.getKey();
            String detalhe = DiagnosticoLag.ehPadrao(chave) ? DiagnosticoLag.explicacao(chave)
                    : chave.equals(DiagnosticoLag.SEM_MOD) ? "" : PainelStatus.frameCurto(DiagnosticoLag.framePorMod(chave));
            l.causas.add(new DadosPainel.Causa(DiagnosticoLag.rotulo(chave), (int) Math.round(100.0 * e.getValue() / total), detalhe));
        }
        List<DiagnosticoLag.Registro> historico = DiagnosticoLag.historico();
        for (int i = 0; i < Math.min(MAX_REGISTROS, historico.size()); i++) {
            DiagnosticoLag.Registro r = historico.get(i);
            String detalhe = DiagnosticoLag.ehPadrao(r.mod()) ? r.frame() : PainelStatus.frameCurto(r.frame());
            l.registros.add(new DadosPainel.Registro(i, r.hora().format(HORA), r.duracaoMs(), Msg.t(r.tipo()), DiagnosticoLag.rotulo(r.mod()), detalhe));
        }
        return l;
    }

    static DadosPainel.Limpeza limpeza(MinecraftServer server) {
        DadosPainel.Limpeza l = new DadosPainel.Limpeza();
        l.segundos = LimpezaItens.segundosParaProxima(server);
        l.limpando = LimpezaItens.limpandoAgora();
        l.limpezas = LimpezaItens.LIMPEZAS.get();
        l.removidos = LimpezaItens.ITENS_REMOVIDOS.get();
        l.protegidos = LimpezaItens.PROTEGIDOS.get();
        l.intervaloMin = Config.limpezaIntervaloMin();
        for (ServerLevel level : server.getAllLevels()) {
            long itens = 0;
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ItemEntity item) {
                    itens += item.getItem().getCount();
                }
            }
            if (itens > 0) {
                l.dimensoes.add(new DadosPainel.DimensaoItens(level.dimension().location().toString(), itens));
            }
        }
        l.dimensoes.sort(Comparator.comparingLong(DadosPainel.DimensaoItens::itens).reversed());
        return l;
    }

    /** Texto completo de um travamento (pilha resumida e dimensoes), para a janela [ver] e o botao Copiar. */
    public static String detalheLag(int indice) {
        List<DiagnosticoLag.Registro> historico = DiagnosticoLag.historico();
        if (indice < 0 || indice >= historico.size()) {
            return null;
        }
        DiagnosticoLag.Registro r = historico.get(indice);
        return Msg.f("Travamento as {}: {} ({})", r.hora().format(HORA), PainelStatus.duracao(r.duracaoMs()), Msg.t(r.tipo())) + "\n"
                + Msg.f("Causa: {}", DiagnosticoLag.rotulo(r.mod())) + (r.frame().isEmpty() ? "" : " - " + r.frame()) + "\n"
                + (DiagnosticoLag.ehPadrao(r.mod()) ? DiagnosticoLag.explicacao(r.mod()) + "\n" : "")
                + "\n" + Msg.t("Pilha (primeira amostra; * = frame de mod):") + "\n" + r.pilha()
                + "\n" + Msg.t("Dimensoes naquela hora:") + "\n" + r.dimensoes();
    }
}
