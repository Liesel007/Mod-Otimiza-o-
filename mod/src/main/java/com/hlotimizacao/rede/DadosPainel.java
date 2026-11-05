package com.hlotimizacao.rede;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Tudo o que a tela do /hl mostra, so com numeros e textos curtos (nada de objeto do jogo).
 * A visao geral vai sempre; as outras secoes so quando a aba correspondente esta aberta, para o
 * pacote ficar pequeno.
 */
public final class DadosPainel {
    /**
     * Formato dos pacotes. Vai antes de tudo: quem le um protocolo diferente nao tenta decodificar o resto
     * (servidor e cliente com versoes diferentes do HLO), e a tela avisa em vez de mostrar lixo.
     * Aumente sempre que mudar escrever/ler.
     */
    public static final int PROTOCOLO = 4; // 4: pausas da JVM na aba Desempenho (0.4.0)
    public static final int TEXTO_CURTO = 120;
    public static final int TEXTO_LONGO = 600;

    public enum Aba {
        GERAL, MODULOS, DESEMPENHO, LAG, LIMPEZA, AVISOS, CONFIG
    }

    // ---- visao geral (sempre) ----
    public Aba aba = Aba.GERAL;
    public String versao = "?";
    /** Protocolo de quem mandou; diferente de PROTOCOLO = o resto nao foi lido. */
    public int protocolo = PROTOCOLO;
    public boolean operador;
    public boolean dedicado;
    public float tps;
    public float msptMedio;
    public float msptPico;
    public long memUsada;
    public long memMax;
    public long sessaoMs = -1;
    public int ligados;
    public int desligados;
    public boolean limpezaAtiva;
    public long limpezaSegundos = -1;
    public boolean limpando;
    public long itensNoChao;
    public boolean lagAtivo;
    public long lagRegistros;
    public long lagPiorMs;
    public String maisCulpado = "";
    /** Preset ativo (nome no idioma do servidor) e quantidade de mods. */
    public String preset = "";
    public int mods;
    public int avisos;

    // ---- secoes opcionais ----
    public List<Modulo> modulos;
    public Desempenho desempenho;
    public Lag lag;
    public Limpeza limpeza;
    public List<Aviso> listaAvisos;
    public boolean mixinsOk;
    public List<ItemConfig> config;
    public boolean mudancaPendente;

    public record Modulo(String chave, String nome, byte estado, String valor, String resumo, String descricao, String detalhe, boolean soCliente) {
        public static final byte LIGADO = 0;
        public static final byte DESLIGADO = 1;
        public static final byte NAO_NECESSARIO = 2;
    }

    public record Dimensao(String id, int jogadores, int chunks, int entidades, int itens) {
    }

    public static final class Desempenho {
        public float[] historico = new float[0];
        public boolean[] salvou = new boolean[0];
        public float minMs;
        public float maxMs;
        public long ultimoSalvamentoMs = -1;
        public String ultimoSalvamentoHora = "";
        public long memAlocada;
        public int pausasGc;
        public long maiorPausaGcMs;
        public long xms = -1;
        public String java = "";
        public boolean ajusteGc = true;
        /** Custo do proprio HLO (ms/tick, -1 = ainda medindo). */
        public float custoMs = -1;
        /** Ultimas pausas da JVM (monitor_gc): "hora · descricao". */
        public List<String> pausasJvm = new ArrayList<>();
        public List<Dimensao> dimensoes = new ArrayList<>();
    }

    public record Causa(String rotulo, int porcento, String detalhe) {
    }

    public record Registro(int indice, String hora, long duracaoMs, String tipo, String causa, String detalhe) {
    }

    public static final class Lag {
        public long total;
        public long totalMs;
        public long piorMs;
        public String piorHora = "";
        public List<Causa> causas = new ArrayList<>();
        public List<Registro> registros = new ArrayList<>();
    }

    public record DimensaoItens(String id, long itens) {
    }

    public static final class Limpeza {
        public long segundos = -1;
        public boolean limpando;
        public long limpezas;
        public long removidos;
        public long protegidos;
        public int intervaloMin;
        public List<DimensaoItens> dimensoes = new ArrayList<>();
    }

    public record Aviso(String curto, String detalhe) {
    }

    /**
     * Uma chave da aba Config. tipo = ordinal de Config.TipoChave; min/max: faixa aceita pelo servidor;
     * emUso: valor que o servidor esta usando (o do preset quando o valor e "auto").
     */
    public record ItemConfig(String chave, String valor, String descricao, int tipo, int min, int max, boolean aceitaAuto, int emUso) {
    }

    // ---------- serializacao ----------

    static String cortar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }

    private static void texto(FriendlyByteBuf buf, String s, int max) {
        buf.writeUtf(cortar(s, max));
    }

    public void escrever(FriendlyByteBuf buf) {
        buf.writeVarInt(PROTOCOLO);
        texto(buf, versao, 32);
        buf.writeEnum(aba);
        buf.writeBoolean(operador);
        buf.writeBoolean(dedicado);
        buf.writeFloat(tps);
        buf.writeFloat(msptMedio);
        buf.writeFloat(msptPico);
        buf.writeVarLong(memUsada);
        buf.writeVarLong(memMax);
        buf.writeLong(sessaoMs);
        buf.writeVarInt(ligados);
        buf.writeVarInt(desligados);
        buf.writeBoolean(limpezaAtiva);
        buf.writeLong(limpezaSegundos);
        buf.writeBoolean(limpando);
        buf.writeVarLong(itensNoChao);
        buf.writeBoolean(lagAtivo);
        buf.writeVarLong(lagRegistros);
        buf.writeVarLong(lagPiorMs);
        texto(buf, maisCulpado, 40);
        buf.writeVarInt(avisos);
        texto(buf, preset, 20);
        buf.writeVarInt(mods);

        buf.writeBoolean(modulos != null);
        if (modulos != null) {
            buf.writeVarInt(modulos.size());
            for (Modulo m : modulos) {
                texto(buf, m.chave(), 40);
                texto(buf, m.nome(), 40);
                buf.writeByte(m.estado());
                texto(buf, m.valor(), 8);
                texto(buf, m.resumo(), 80);
                texto(buf, m.descricao(), 300);
                texto(buf, m.detalhe(), 400);
                buf.writeBoolean(m.soCliente());
            }
            buf.writeBoolean(mudancaPendente);
        }
        buf.writeBoolean(desempenho != null);
        if (desempenho != null) {
            Desempenho d = desempenho;
            buf.writeVarInt(d.historico.length);
            for (int i = 0; i < d.historico.length; i++) {
                buf.writeFloat(d.historico[i]);
                buf.writeBoolean(i < d.salvou.length && d.salvou[i]);
            }
            buf.writeFloat(d.minMs);
            buf.writeFloat(d.maxMs);
            buf.writeLong(d.ultimoSalvamentoMs);
            texto(buf, d.ultimoSalvamentoHora, 12);
            buf.writeVarLong(d.memAlocada);
            buf.writeVarInt(d.pausasGc);
            buf.writeVarLong(d.maiorPausaGcMs);
            buf.writeLong(d.xms);
            texto(buf, d.java, 60);
            buf.writeBoolean(d.ajusteGc);
            buf.writeFloat(d.custoMs);
            buf.writeVarInt(d.pausasJvm.size());
            for (String p : d.pausasJvm) {
                texto(buf, p, 200);
            }
            buf.writeVarInt(d.dimensoes.size());
            for (Dimensao dim : d.dimensoes) {
                texto(buf, dim.id(), 60);
                buf.writeVarInt(dim.jogadores());
                buf.writeVarInt(dim.chunks());
                buf.writeVarInt(dim.entidades());
                buf.writeVarInt(dim.itens());
            }
        }
        buf.writeBoolean(lag != null);
        if (lag != null) {
            buf.writeVarLong(lag.total);
            buf.writeVarLong(lag.totalMs);
            buf.writeVarLong(lag.piorMs);
            texto(buf, lag.piorHora, 12);
            buf.writeVarInt(lag.causas.size());
            for (Causa c : lag.causas) {
                texto(buf, c.rotulo(), 40);
                buf.writeVarInt(c.porcento());
                texto(buf, c.detalhe(), 200);
            }
            buf.writeVarInt(lag.registros.size());
            for (Registro r : lag.registros) {
                buf.writeVarInt(r.indice());
                texto(buf, r.hora(), 12);
                buf.writeVarLong(r.duracaoMs());
                texto(buf, r.tipo(), 20);
                texto(buf, r.causa(), 40);
                texto(buf, r.detalhe(), 120);
            }
        }
        buf.writeBoolean(limpeza != null);
        if (limpeza != null) {
            buf.writeLong(limpeza.segundos);
            buf.writeBoolean(limpeza.limpando);
            buf.writeVarLong(limpeza.limpezas);
            buf.writeVarLong(limpeza.removidos);
            buf.writeVarLong(limpeza.protegidos);
            buf.writeVarInt(limpeza.intervaloMin);
            buf.writeVarInt(limpeza.dimensoes.size());
            for (DimensaoItens d : limpeza.dimensoes) {
                texto(buf, d.id(), 60);
                buf.writeVarLong(d.itens());
            }
        }
        buf.writeBoolean(listaAvisos != null);
        if (listaAvisos != null) {
            buf.writeVarInt(listaAvisos.size());
            for (Aviso a : listaAvisos) {
                texto(buf, a.curto(), 80);
                texto(buf, a.detalhe(), 400);
            }
            buf.writeBoolean(mixinsOk);
        }
        buf.writeBoolean(config != null);
        if (config != null) {
            buf.writeVarInt(config.size());
            for (ItemConfig c : config) {
                texto(buf, c.chave(), 60);
                texto(buf, c.valor(), 400);
                texto(buf, c.descricao(), 160);
                buf.writeVarInt(c.tipo());
                buf.writeVarInt(c.min());
                buf.writeVarInt(c.max());
                buf.writeBoolean(c.aceitaAuto());
                buf.writeVarInt(c.emUso() + 1);
            }
        }
    }

    public static DadosPainel ler(FriendlyByteBuf buf) {
        DadosPainel d = new DadosPainel();
        d.protocolo = buf.readVarInt();
        d.versao = buf.readUtf(64);
        if (d.protocolo != PROTOCOLO) {
            buf.skipBytes(buf.readableBytes());
            return d;
        }
        d.aba = buf.readEnum(Aba.class);
        d.operador = buf.readBoolean();
        d.dedicado = buf.readBoolean();
        d.tps = buf.readFloat();
        d.msptMedio = buf.readFloat();
        d.msptPico = buf.readFloat();
        d.memUsada = buf.readVarLong();
        d.memMax = buf.readVarLong();
        d.sessaoMs = buf.readLong();
        d.ligados = buf.readVarInt();
        d.desligados = buf.readVarInt();
        d.limpezaAtiva = buf.readBoolean();
        d.limpezaSegundos = buf.readLong();
        d.limpando = buf.readBoolean();
        d.itensNoChao = buf.readVarLong();
        d.lagAtivo = buf.readBoolean();
        d.lagRegistros = buf.readVarLong();
        d.lagPiorMs = buf.readVarLong();
        d.maisCulpado = buf.readUtf();
        d.avisos = buf.readVarInt();
        d.preset = buf.readUtf();
        d.mods = buf.readVarInt();

        if (buf.readBoolean()) {
            int n = buf.readVarInt();
            d.modulos = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                d.modulos.add(new Modulo(buf.readUtf(), buf.readUtf(), buf.readByte(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readBoolean()));
            }
            d.mudancaPendente = buf.readBoolean();
        }
        if (buf.readBoolean()) {
            Desempenho p = new Desempenho();
            int n = Math.min(buf.readVarInt(), 4096);
            p.historico = new float[n];
            p.salvou = new boolean[n];
            for (int i = 0; i < n; i++) {
                p.historico[i] = buf.readFloat();
                p.salvou[i] = buf.readBoolean();
            }
            p.minMs = buf.readFloat();
            p.maxMs = buf.readFloat();
            p.ultimoSalvamentoMs = buf.readLong();
            p.ultimoSalvamentoHora = buf.readUtf();
            p.memAlocada = buf.readVarLong();
            p.pausasGc = buf.readVarInt();
            p.maiorPausaGcMs = buf.readVarLong();
            p.xms = buf.readLong();
            p.java = buf.readUtf();
            p.ajusteGc = buf.readBoolean();
            p.custoMs = buf.readFloat();
            int pausas = buf.readVarInt();
            for (int i = 0; i < pausas; i++) {
                p.pausasJvm.add(buf.readUtf());
            }
            int dims = buf.readVarInt();
            for (int i = 0; i < dims; i++) {
                p.dimensoes.add(new Dimensao(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            d.desempenho = p;
        }
        if (buf.readBoolean()) {
            Lag l = new Lag();
            l.total = buf.readVarLong();
            l.totalMs = buf.readVarLong();
            l.piorMs = buf.readVarLong();
            l.piorHora = buf.readUtf();
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                l.causas.add(new Causa(buf.readUtf(), buf.readVarInt(), buf.readUtf()));
            }
            n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                l.registros.add(new Registro(buf.readVarInt(), buf.readUtf(), buf.readVarLong(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
            }
            d.lag = l;
        }
        if (buf.readBoolean()) {
            Limpeza l = new Limpeza();
            l.segundos = buf.readLong();
            l.limpando = buf.readBoolean();
            l.limpezas = buf.readVarLong();
            l.removidos = buf.readVarLong();
            l.protegidos = buf.readVarLong();
            l.intervaloMin = buf.readVarInt();
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                l.dimensoes.add(new DimensaoItens(buf.readUtf(), buf.readVarLong()));
            }
            d.limpeza = l;
        }
        if (buf.readBoolean()) {
            int n = buf.readVarInt();
            d.listaAvisos = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                d.listaAvisos.add(new Aviso(buf.readUtf(), buf.readUtf()));
            }
            d.mixinsOk = buf.readBoolean();
        }
        if (buf.readBoolean()) {
            int n = buf.readVarInt();
            d.config = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                d.config.add(new ItemConfig(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean(), buf.readVarInt() - 1));
            }
        }
        return d;
    }
}
