package com.hlotimizacao.config;

import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuracao lida antes dos mixins serem aplicados, por isso usa um arquivo .properties simples
 * em vez do sistema de config do Forge (que so carrega depois).
 */
public final class Config {
    public static final Logger LOGGER = LoggerFactory.getLogger("HL Otimizacao");
    private static final String ARQUIVO = "hlotimizacao.properties";
    public static final String URL_PROJETO = "https://github.com/Liesel007/Mod-Otimiza-o-";

    private static final Map<Modulo, Boolean> ATIVOS = new EnumMap<>(Modulo.class);
    private static final Map<Modulo, String> MOTIVOS = new EnumMap<>(Modulo.class);
    private static final Map<Modulo, String> MOTIVOS_CURTOS = new EnumMap<>(Modulo.class);
    private static final Map<Modulo, String> VALORES = new EnumMap<>(Modulo.class);
    /** Valores de modulo mudados pela tela nesta sessao (valem depois de reiniciar). */
    private static final Map<Modulo, String> PENDENTES = new EnumMap<>(Modulo.class);

    /** Chaves que a tela pode editar (relidas sem reiniciar) e a descricao de cada uma. */
    public static final Map<String, String> EDITAVEIS = new java.util.LinkedHashMap<>();

    static {
        EDITAVEIS.put("limpeza_itens.intervalo_min", "Minutos entre as limpezas de itens (1 a 1440)");
        EDITAVEIS.put("limpeza_itens.avisos_s", "Segundos antes da limpeza em que o chat avisa (ex.: 60,30,10,5)");
        EDITAVEIS.put("limpeza_itens.idade_minima_s", "Itens no chao ha menos que isso nunca sao apagados (segundos)");
        EDITAVEIS.put("limpeza_itens.proteger_itens_com_nbt", "Nao apagar itens encantados, com nome ou com conteudo (true/false)");
        EDITAVEIS.put("limpeza_itens.proteger_sobre_coletores", "Nao apagar itens sobre funis ou coletores (true/false)");
        EDITAVEIS.put("limpeza_itens.itens_protegidos", "Itens que nunca sao apagados (ids separados por virgula)");
        EDITAVEIS.put("limpeza_itens.dimensoes", "Dimensoes limpas (vazio = todas)");
        EDITAVEIS.put("limpeza_itens.max_por_tick", "Itens removidos por tick durante a limpeza (10 a 100000)");
        EDITAVEIS.put("diagnostico_lag.limite_ms", "Tick a partir do qual o diagnostico registra um travamento (ms)");
        EDITAVEIS.put("diagnostico_lag.intervalo_s", "Intervalo minimo entre dois registros de lag (s)");
        EDITAVEIS.put("diagnostico_lag.tolerancia_inicio_s", "Segundos sem registrar depois de abrir o mundo");
        EDITAVEIS.put("diagnostico_lag.ignorar_mixins", "Mixins que so repassam a chamada (mod:metodo, separados por virgula)");
        EDITAVEIS.put("salvamento_assincrono.serializar_classes", "Classes com save(File) proprio gravadas em outra thread (cuidado)");
    }
    private static int fpsSemFoco = 15;
    private static int fpsMinimizado = 1;
    private static int lagLimiteMs = 2000;
    private static int lagIntervaloS = 30;
    private static int lagToleranciaS = 30;
    private static Set<String> lagIgnorarMixins = Set.of("chrono:scalePlayerGameplayTick");
    private static Set<String> salvamentoSerializarClasses = Set.of();
    private static int limpezaIntervaloMin = 10;
    private static Set<Integer> limpezaAvisosS = Set.of(60, 30, 10, 5);
    private static int limpezaIdadeMinimaS = 30;
    private static boolean limpezaProtegerNbt = true;
    private static boolean limpezaProtegerColetores = true;
    private static Set<String> limpezaItensProtegidos = Set.of();
    private static List<String> limpezaDimensoes = List.of();
    private static int limpezaMaxPorTick = 500;
    private static final String PROTEGIDOS_PADRAO = "minecraft:nether_star,minecraft:dragon_egg,minecraft:elytra,minecraft:totem_of_undying,"
            + "minecraft:beacon,minecraft:heart_of_the_sea,minecraft:trident,minecraft:netherite_ingot,minecraft:netherite_block";
    private static boolean carregado = false;

    private Config() {
    }

    public static synchronized void carregar() {
        if (carregado) {
            return;
        }
        carregado = true;
        Path caminho = pastaConfig().resolve(ARQUIVO);
        Properties props = lerArquivo(caminho);

        boolean faltando = false;
        for (Modulo modulo : Modulo.values()) {
            String chave = "modulo." + modulo.chave;
            String valor = props.getProperty(chave);
            if (valor == null) {
                faltando = true;
                valor = "auto";
                props.setProperty(chave, valor);
            }
            decidir(modulo, valor.trim().toLowerCase());
        }
        faltando |= lerValores(props);
        if (faltando) {
            salvar(caminho, props);
        }
    }

    private static Properties lerArquivo(Path caminho) {
        Properties props = new Properties();
        if (Files.exists(caminho)) {
            try (Reader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                LOGGER.error("Nao foi possivel ler {}, usando valores padrao", caminho, e);
            }
        }
        return props;
    }

    /**
     * /hl recarregar: rele os valores que nao precisam reiniciar (limites, listas, intervalos). Ligar e
     * desligar modulos continua exigindo reiniciar (os mixins sao aplicados na inicializacao).
     */
    public static synchronized void recarregarValores() {
        carregar();
        lerValores(lerArquivo(pastaConfig().resolve(ARQUIVO)));
    }

    private static Set<String> lista(Properties props, String chave, String padrao) {
        return Stream.of(props.getProperty(chave, padrao).split(","))
                .map(String::trim).filter(c -> !c.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** Le os valores; devolve true se alguma chave faltava (e a coloca em props com o valor padrao). */
    private static boolean lerValores(Properties props) {
        boolean faltando = false;
        fpsSemFoco = lerInt(props, "fps_sem_foco.limite", 15, 1, 260);
        fpsMinimizado = lerInt(props, "fps_sem_foco.limite_minimizado", 1, 1, 260);
        lagLimiteMs = lerInt(props, "diagnostico_lag.limite_ms", 2000, 100, 600000);
        lagIntervaloS = lerInt(props, "diagnostico_lag.intervalo_s", 30, 1, 3600);
        lagToleranciaS = lerInt(props, "diagnostico_lag.tolerancia_inicio_s", 30, 0, 3600);
        salvamentoSerializarClasses = lista(props, "salvamento_assincrono.serializar_classes", "");
        lagIgnorarMixins = lista(props, "diagnostico_lag.ignorar_mixins", "chrono:scalePlayerGameplayTick");
        limpezaIntervaloMin = lerInt(props, "limpeza_itens.intervalo_min", 10, 1, 1440);
        Set<Integer> avisos = new java.util.TreeSet<>();
        for (String aviso : lista(props, "limpeza_itens.avisos_s", "60,30,10,5")) {
            try {
                avisos.add(Integer.parseInt(aviso));
            } catch (NumberFormatException ignorado) {
                // valor invalido na lista: ignora
            }
        }
        limpezaAvisosS = Set.copyOf(avisos);
        limpezaIdadeMinimaS = lerInt(props, "limpeza_itens.idade_minima_s", 30, 0, 3600);
        limpezaProtegerNbt = Boolean.parseBoolean(props.getProperty("limpeza_itens.proteger_itens_com_nbt", "true").trim());
        limpezaProtegerColetores = Boolean.parseBoolean(props.getProperty("limpeza_itens.proteger_sobre_coletores", "true").trim());
        limpezaItensProtegidos = lista(props, "limpeza_itens.itens_protegidos", PROTEGIDOS_PADRAO);
        limpezaDimensoes = List.copyOf(lista(props, "limpeza_itens.dimensoes", ""));
        limpezaMaxPorTick = lerInt(props, "limpeza_itens.max_por_tick", 500, 10, 100000);
        for (String[] chave : new String[][]{
                {"fps_sem_foco.limite", Integer.toString(fpsSemFoco)},
                {"fps_sem_foco.limite_minimizado", Integer.toString(fpsMinimizado)},
                {"diagnostico_lag.limite_ms", Integer.toString(lagLimiteMs)},
                {"diagnostico_lag.intervalo_s", Integer.toString(lagIntervaloS)},
                {"diagnostico_lag.tolerancia_inicio_s", Integer.toString(lagToleranciaS)},
                {"salvamento_assincrono.serializar_classes", String.join(",", salvamentoSerializarClasses)},
                {"diagnostico_lag.ignorar_mixins", "chrono:scalePlayerGameplayTick"},
                {"limpeza_itens.intervalo_min", Integer.toString(limpezaIntervaloMin)},
                {"limpeza_itens.avisos_s", "60,30,10,5"},
                {"limpeza_itens.idade_minima_s", Integer.toString(limpezaIdadeMinimaS)},
                {"limpeza_itens.proteger_itens_com_nbt", "true"},
                {"limpeza_itens.proteger_sobre_coletores", "true"},
                {"limpeza_itens.itens_protegidos", PROTEGIDOS_PADRAO},
                {"limpeza_itens.dimensoes", ""},
                {"limpeza_itens.max_por_tick", Integer.toString(limpezaMaxPorTick)}}) {
            if (!props.containsKey(chave[0])) {
                faltando = true;
                props.setProperty(chave[0], chave[1]);
            }
        }
        return faltando;
    }

    private static void decidir(Modulo modulo, String valor) {
        Optional<String> conflito = modulo.modsConflitantes.stream().filter(Config::modInstalado).findFirst();
        VALORES.put(modulo, valor);
        boolean ativo;
        String motivo;
        String curto;
        switch (valor) {
            case "off", "false", "desligado" -> {
                ativo = false;
                motivo = "desligado na config";
                curto = "desligado na config";
            }
            case "on", "true", "ligado" -> {
                ativo = true;
                motivo = conflito.map(id -> "forcado na config (atencao: " + id + " tambem esta instalado)").orElse("ligado na config");
                curto = conflito.map(id -> "forçado (" + nomeDoMod(id) + " também instalado)").orElse("ligado na config");
            }
            default -> {
                ativo = conflito.isEmpty();
                motivo = conflito.map(id -> "desligado automaticamente: " + id + " ja faz isso").orElse("ligado (auto)");
                curto = conflito.map(id -> "desligado: " + nomeDoMod(id) + " já faz isso").orElse("ligado (auto)");
            }
        }
        if (modulo == Modulo.CORRECAO_FASTASYNCWORLDSAVE && ativo && !modInstalado("fastasyncworldsave")) {
            ativo = false;
            motivo = "nao necessario: fastasyncworldsave nao esta instalado";
            curto = "não necessário (sem o Fast Async World Save)";
        }
        if (modulo == Modulo.CORRECAO_INFINITY_CHEST && ativo && !modInstalado("avaritia")) {
            ativo = false;
            motivo = "nao necessario: avaritia (Re-Avaritia) nao esta instalado";
            curto = "não necessário (sem o Re-Avaritia)";
        }
        ATIVOS.put(modulo, ativo);
        MOTIVOS.put(modulo, motivo);
        MOTIVOS_CURTOS.put(modulo, curto);
    }

    public static boolean ativo(Modulo modulo) {
        carregar();
        return ATIVOS.getOrDefault(modulo, false);
    }

    public static String motivo(Modulo modulo) {
        carregar();
        return MOTIVOS.getOrDefault(modulo, "");
    }

    /** Motivo curto para o /hl status ("desligado: Icterine já faz isso"). */
    public static String motivoCurto(Modulo modulo) {
        carregar();
        return MOTIVOS_CURTOS.getOrDefault(modulo, "");
    }

    /** Valor configurado (inclui mudancas feitas pela tela nesta sessao, que valem depois de reiniciar). */
    public static String valorConfigurado(Modulo modulo) {
        carregar();
        return PENDENTES.getOrDefault(modulo, VALORES.getOrDefault(modulo, "auto"));
    }

    public static boolean temMudancaPendente() {
        return PENDENTES.entrySet().stream().anyMatch(e -> !e.getValue().equals(VALORES.getOrDefault(e.getKey(), "auto")));
    }

    /** Valor atual de uma chave editavel, como esta no arquivo. */
    public static synchronized String valorTexto(String chave) {
        return lerArquivo(pastaConfig().resolve(ARQUIVO)).getProperty(chave, "");
    }

    /**
     * Grava uma chave no arquivo (pela tela). Modulos: so valem depois de reiniciar. Chaves editaveis:
     * relidas na hora. Devolve null se deu certo ou a mensagem de erro.
     */
    public static synchronized String gravarValor(String chave, String valor) {
        String v = valor == null ? "" : valor.trim();
        Modulo modulo = null;
        if (chave.startsWith("modulo.")) {
            for (Modulo m : Modulo.values()) {
                if (chave.equals("modulo." + m.chave)) {
                    modulo = m;
                }
            }
            if (modulo == null || !(v.equals("auto") || v.equals("on") || v.equals("off"))) {
                return "valor invalido para " + chave;
            }
        } else if (!EDITAVEIS.containsKey(chave)) {
            return "chave nao editavel: " + chave;
        } else if (chave.endsWith("_min") || chave.endsWith("_s") && !chave.endsWith("avisos_s") || chave.endsWith("_ms") || chave.endsWith("max_por_tick")) {
            try {
                Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return chave + " precisa ser um numero";
            }
        } else if (chave.startsWith("limpeza_itens.proteger") && !(v.equals("true") || v.equals("false"))) {
            return chave + " precisa ser true ou false";
        }
        Path caminho = pastaConfig().resolve(ARQUIVO);
        Properties props = lerArquivo(caminho);
        props.setProperty(chave, v);
        lerValores(props); // completa as chaves que faltarem
        salvar(caminho, props);
        if (modulo != null) {
            PENDENTES.put(modulo, v);
        } else {
            lerValores(props);
        }
        LOGGER.info("Configuracao alterada pela tela: {}={}", chave, v);
        return null;
    }

    /** Valor escrito na config (auto, on, off). */
    public static String valor(Modulo modulo) {
        carregar();
        return VALORES.getOrDefault(modulo, "auto");
    }

    /** "nao necessario": desligado porque o mod que ele corrige nao esta instalado. */
    public static boolean naoNecessario(Modulo modulo) {
        return MOTIVOS.getOrDefault(modulo, "").startsWith("nao necessario");
    }

    /** Nome de exibicao de um mod pela lista de mods (cai no id se nao achar). */
    public static String nomeDoMod(String modId) {
        LoadingModList lista = LoadingModList.get();
        if (lista != null) {
            for (net.minecraftforge.fml.loading.moddiscovery.ModInfo mod : lista.getMods()) {
                if (mod.getModId().equals(modId)) {
                    return mod.getDisplayName();
                }
            }
        }
        return modId;
    }

    public static int fpsSemFoco() {
        carregar();
        return fpsSemFoco;
    }

    public static int fpsMinimizado() {
        carregar();
        return fpsMinimizado;
    }

    public static int lagLimiteMs() {
        carregar();
        return lagLimiteMs;
    }

    public static int lagIntervaloS() {
        carregar();
        return lagIntervaloS;
    }

    public static Set<String> lagIgnorarMixins() {
        return lagIgnorarMixins;
    }

    public static int lagToleranciaS() {
        carregar();
        return lagToleranciaS;
    }

    /** Classes com save(File) proprio que o usuario permitiu serializar e gravar em outra thread. */
    public static Set<String> salvamentoSerializarClasses() {
        carregar();
        return salvamentoSerializarClasses;
    }

    public static int limpezaIntervaloMin() {
        return limpezaIntervaloMin;
    }

    public static Set<Integer> limpezaAvisosS() {
        return limpezaAvisosS;
    }

    public static int limpezaIdadeMinimaS() {
        return limpezaIdadeMinimaS;
    }

    public static boolean limpezaProtegerNbt() {
        return limpezaProtegerNbt;
    }

    public static boolean limpezaProtegerColetores() {
        return limpezaProtegerColetores;
    }

    public static Set<String> limpezaItensProtegidos() {
        return limpezaItensProtegidos;
    }

    public static List<String> limpezaDimensoes() {
        return limpezaDimensoes;
    }

    public static int limpezaMaxPorTick() {
        return limpezaMaxPorTick;
    }

    public static boolean modInstalado(String modId) {
        LoadingModList lista = LoadingModList.get();
        return lista != null && lista.getModFileById(modId) != null;
    }

    private static int lerInt(Properties props, String chave, int padrao, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(props.getProperty(chave, Integer.toString(padrao)).trim())));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private static Path pastaConfig() {
        try {
            return FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            return Path.of("config");
        }
    }

    private static void salvar(Path caminho, Properties props) {
        StringBuilder sb = new StringBuilder();
        sb.append("# HL Otimizacao\n");
        sb.append("# Valores dos modulos: auto (padrao), on, off.\n");
        sb.append("# auto = ligado, a menos que o mod original que faz o mesmo esteja instalado.\n");
        sb.append("# Mudancas so valem depois de reiniciar o jogo.\n\n");
        for (Modulo modulo : Modulo.values()) {
            sb.append("# ").append(modulo.descricao).append('\n');
            if (!modulo.modsConflitantes.isEmpty()) {
                sb.append("# Desliga sozinho (em auto) se estiver instalado: ").append(String.join(", ", modulo.modsConflitantes)).append('\n');
            }
            sb.append("modulo.").append(modulo.chave).append('=').append(props.getProperty("modulo." + modulo.chave)).append("\n\n");
        }
        sb.append("# FPS maximo com a janela sem foco e minimizada (modulo fps_sem_foco).\n");
        sb.append("fps_sem_foco.limite=").append(props.getProperty("fps_sem_foco.limite")).append('\n');
        sb.append("fps_sem_foco.limite_minimizado=").append(props.getProperty("fps_sem_foco.limite_minimizado")).append("\n\n");
        sb.append("# Tempo de tick (ms) a partir do qual o diagnostico_lag registra o travamento,\n");
        sb.append("# e intervalo minimo (s) entre dois registros.\n");
        sb.append("diagnostico_lag.limite_ms=").append(props.getProperty("diagnostico_lag.limite_ms")).append('\n');
        sb.append("diagnostico_lag.intervalo_s=").append(props.getProperty("diagnostico_lag.intervalo_s")).append('\n');
        sb.append("# Segundos sem registrar depois de abrir o mundo (e depois do primeiro jogador entrar),\n");
        sb.append("# enquanto classes e chunks ainda estao carregando.\n");
        sb.append("diagnostico_lag.tolerancia_inicio_s=").append(props.getProperty("diagnostico_lag.tolerancia_inicio_s")).append('\n');
        sb.append("# Mixins que so repassam a chamada e nunca sao a causa de um travamento (mod:metodo, separados por virgula).\n");
        sb.append("diagnostico_lag.ignorar_mixins=").append(props.getProperty("diagnostico_lag.ignorar_mixins")).append("\n\n");
        sb.append("# salvamento_assincrono: SavedData que sobrescrevem save(File) sao gravados no thread principal, do jeito do mod.\n");
        sb.append("# O log mostra quais classes sao essas. Classes listadas aqui (nomes completos, separados por virgula) passam\n");
        sb.append("# a ser serializadas com save(CompoundTag) e gravadas em outra thread, IGNORANDO o save(File) do mod.\n");
        sb.append("# So use se souber que o save(File) da classe apenas chama o do Minecraft (risco de perder dados se fizer mais).\n");
        sb.append("salvamento_assincrono.serializar_classes=").append(props.getProperty("salvamento_assincrono.serializar_classes")).append("\n\n");
        sb.append("# limpeza_itens: a cada intervalo_min minutos apaga os itens soltos no chao, com avisos no chat antes (avisos_s).\n");
        sb.append("# Nunca apaga: itens com menos de idade_minima_s segundos no chao, drops da morte de jogadores, itens_protegidos,\n");
        sb.append("# ovos de spawn, shulker boxes, itens encantados/com nome/com conteudo (proteger_itens_com_nbt) e itens sobre\n");
        sb.append("# funis ou blocos que recebem itens (proteger_sobre_coletores). dimensoes vazio = todas. /hl recarregar rele estes valores.\n");
        for (String chave : new String[]{"intervalo_min", "avisos_s", "idade_minima_s", "proteger_itens_com_nbt", "proteger_sobre_coletores",
                "itens_protegidos", "dimensoes", "max_por_tick"}) {
            sb.append("limpeza_itens.").append(chave).append('=').append(props.getProperty("limpeza_itens." + chave)).append('\n');
        }
        try {
            Files.createDirectories(caminho.getParent());
            try (Writer writer = Files.newBufferedWriter(caminho, StandardCharsets.UTF_8)) {
                writer.write(sb.toString());
            }
        } catch (IOException e) {
            LOGGER.error("Nao foi possivel salvar {}", caminho, e);
        }
    }
}
