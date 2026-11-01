package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Msg;
import com.hlotimizacao.config.Config;
import com.hlotimizacao.mixin.ErrosMixin;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Detectores do relatorio_conflitos que olham o ambiente: mixins que falharam, JVM, Java, antivirus. */
public final class DiagnosticoAmbiente {
    /** Atualizacao do Java 17 a partir da qual nao avisa (17.0.12, julho de 2024). */
    private static final int JAVA17_MINIMO = 12;
    private static final long ABRIR_ARQUIVO_LENTO_MS = 500;
    private static final AtomicInteger ARQUIVOS_LENTOS = new AtomicInteger();
    public static final String FLAGS_G1 = "O Java 17 ja usa o G1, mas para modpacks grandes costuma ajudar: -XX:+UseG1GC -XX:MaxGCPauseMillis=130 "
            + "-XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:G1HeapRegionSize=32M";
    public static final String DICA_ANTIVIRUS = "Isso costuma ser o antivirus examinando cada arquivo do save. Adicione a pasta da instancia "
            + "as exclusoes do Windows Defender (ou do antivirus que voce usa).";
    private static volatile boolean ajusteGc = true;
    private static volatile long xmsLido = -1;
    private static volatile boolean avisouAntivirus;

    /** Correcoes conhecidas: trecho do nome da config do mixin, trecho do motivo, explicacao. */
    private record Correcao(String config, String motivo, String texto) {
    }

    private static final List<Correcao> CORRECOES = List.of(
            new Correcao("betterchunkloading", "canary",
                    "Better Chunk Loading nao funciona por conflito com o Canary. Correcao: mixin.world.chunk_access=false em config/canary.properties"),
            new Correcao("betterchunkloading", "lithium",
                    "Better Chunk Loading nao funciona por conflito com um porte do Lithium. Desligue a opcao de chunk_access dele"));

    private DiagnosticoAmbiente() {
    }

    public static int verificar() {
        int avisos = 0;
        avisos += mixinsQueFalharam();
        avisos += argumentosJvm();
        avisos += versaoJava();
        return avisos;
    }

    static int mixinsQueFalharam() {
        int avisos = 0;
        for (ErrosMixin.Falha falha : ErrosMixin.FALHAS) {
            String explicacao = CORRECOES.stream()
                    .filter(c -> falha.config().toLowerCase(Locale.ROOT).contains(c.config()) && falha.motivo().toLowerCase(Locale.ROOT).contains(c.motivo()))
                    .map(Correcao::texto).findFirst()
                    .map(Msg::t)
                    .orElse(Msg.t("A parte do mod que depende desse mixin nao esta funcionando. Normalmente outro mod alterou o mesmo codigo antes."));
            Config.LOGGER.warn("[Mixin] {} ({}) falhou ao aplicar em {}. {}\n    Motivo: {}",
                    falha.mixin(), falha.config(), falha.alvo(), explicacao, falha.motivo());
            String config = falha.config().replace(".mixins.json", "").replace(".json", "");
            Avisos.adicionar(Msg.f("Mixin de {} falhou", config.length() > 30 ? config.substring(0, 29) + "…" : config),
                    Msg.f("{} falhou ao aplicar em {}. {}", falha.mixin(), falha.alvo(), explicacao) + "\n"
                            + Msg.f("Motivo: {}", falha.motivo().length() > 300 ? falha.motivo().substring(0, 300) + "…" : falha.motivo()));
            avisos++;
        }
        return avisos;
    }

    static int argumentosJvm() {
        List<String> args;
        try {
            args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        } catch (Throwable t) {
            return 0;
        }
        long xmx = Runtime.getRuntime().maxMemory();
        long xms = -1;
        boolean ajusteGc = false;
        for (String arg : args) {
            if (arg.startsWith("-Xms")) {
                xms = tamanho(arg.substring(4));
            }
            if (arg.startsWith("-XX:+Use") && arg.endsWith("GC") || arg.startsWith("-XX:MaxGCPauseMillis") || arg.startsWith("-XX:G1")) {
                ajusteGc = true;
            }
        }
        int avisos = 0;
        if (xms > 0 && xms < xmx / 4) {
            Config.LOGGER.info("[JVM] -Xms ({} MB) e bem menor que -Xmx ({} MB): a JVM vai aumentando a memoria aos poucos durante o jogo, "
                    + "com pausas de coleta de lixo maiores no comeco. Sugestao: -Xms igual a metade (ou ao total) do -Xmx.", xms >> 20, xmx >> 20);
            Avisos.adicionar(Msg.t("JVM: -Xms muito menor que -Xmx"),
                    Msg.f("-Xms {} MB e -Xmx {} MB: a JVM aumenta a memoria aos poucos, com pausas maiores no comeco. Sugestao: -Xms igual a metade (ou ao total) do -Xmx.",
                            xms >> 20, xmx >> 20));
            avisos++;
        }
        DiagnosticoAmbiente.ajusteGc = ajusteGc;
        xmsLido = xms;
        if (!ajusteGc) {
            Config.LOGGER.info("[JVM] Nenhuma opcao de coleta de lixo (GC) nos argumentos. O Java 17 ja usa o G1, mas para modpacks grandes "
                    + "costuma ajudar: -XX:+UseG1GC -XX:MaxGCPauseMillis=130 -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 "
                    + "-XX:G1ReservePercent=20 -XX:G1HeapRegionSize=32M");
            Avisos.adicionar(Msg.t("JVM: sem opcoes de coleta de lixo"), Msg.t(FLAGS_G1));
            avisos++;
        }
        return avisos;
    }

    static long tamanho(String valor) {
        try {
            String v = valor.trim().toLowerCase(Locale.ROOT);
            long mult = 1;
            char fim = v.charAt(v.length() - 1);
            if (Character.isLetter(fim)) {
                mult = switch (fim) {
                    case 'k' -> 1L << 10;
                    case 'm' -> 1L << 20;
                    case 'g' -> 1L << 30;
                    case 't' -> 1L << 40;
                    default -> 1;
                };
                v = v.substring(0, v.length() - 1);
            }
            return Long.parseLong(v) * mult;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static int versaoJava() {
        Runtime.Version versao = Runtime.version();
        if (versao.feature() == 17 && versao.update() < JAVA17_MINIMO) {
            Config.LOGGER.warn("[Java] Java {} e antigo (de antes de 2024). Atualize para o Java 17 mais recente (por exemplo, Eclipse Temurin 17 ou "
                    + "Microsoft OpenJDK 17): ha correcoes de desempenho e de travamentos do proprio Java.", versao);
            Avisos.adicionar(Msg.f("Java {} antigo", versao.feature() + "." + versao.interim() + "." + versao.update()),
                    Msg.t("Atualize para o Java 17 mais recente (Eclipse Temurin 17 ou Microsoft OpenJDK 17): ha correcoes de desempenho e de travamentos do proprio Java."));
            return 1;
        }
        return 0;
    }

    /** Chamado por quem mede o tempo de abrir um arquivo para gravar (ou pelo diagnostico_lag). */
    public static void arquivoAberto(long ms, String arquivo) {
        if (ms < ABRIR_ARQUIVO_LENTO_MS) {
            return;
        }
        int n = ARQUIVOS_LENTOS.incrementAndGet();
        Config.LOGGER.debug("[relatorio_conflitos] Abrir {} levou {} ms", arquivo, ms);
        if (n >= 3 && !avisouAntivirus) {
            avisouAntivirus = true;
            Config.LOGGER.warn("[Antivirus?] Abrir arquivos para gravar levou mais de {} ms {} vezes (ultimo: {} ms). Isso costuma ser o antivirus "
                    + "examinando cada arquivo do save. Adicione a pasta da instancia as exclusoes do Windows Defender.", ABRIR_ARQUIVO_LENTO_MS, n, ms);
        }
    }

    /** -Xms dos argumentos da JVM, ou -1 se nao foi passado. */
    public static long xms() {
        return xmsLido;
    }

    public static boolean temAjusteGc() {
        return ajusteGc;
    }

    public static int arquivosLentos() {
        return ARQUIVOS_LENTOS.get();
    }
}
