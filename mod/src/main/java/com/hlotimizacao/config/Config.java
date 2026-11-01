package com.hlotimizacao.config;

import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;

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
    public static final Log LOGGER = new Log("HLO");
    private static final String ARQUIVO = "hlotimizacao.properties";
    public static final String URL_PROJETO = "https://github.com/Liesel007/Mod-Otimiza-o-";

    private static final Map<Modulo, Boolean> ATIVOS = new EnumMap<>(Modulo.class);
    private static final Map<Modulo, String> MOTIVOS = new EnumMap<>(Modulo.class);
    private static final Map<Modulo, String> MOTIVOS_CURTOS = new EnumMap<>(Modulo.class);
    private static final Map<Modulo, String> VALORES = new EnumMap<>(Modulo.class);
    private static final java.util.Set<Modulo> NAO_NECESSARIO = java.util.EnumSet.noneOf(Modulo.class);
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
        EDITAVEIS.put("alcance_ativacao.horizontal", "Distancia horizontal (blocos) de um jogador ate a qual a entidade e processada todo tick");
        EDITAVEIS.put("alcance_ativacao.vertical", "Distancia vertical (blocos) de um jogador ate a qual a entidade e processada todo tick");
        EDITAVEIS.put("alcance_ativacao.intervalo", "Entidades longe sao processadas 1 vez a cada N ticks (2 a 200)");
        EDITAVEIS.put("alcance_ativacao.proteger_itens", "Itens e orbes de XP sempre processados (fazendas com coletores) (true/false)");
        EDITAVEIS.put("alcance_ativacao.proteger_aldeoes", "Aldeoes sempre processados (fazendas de ferro) (true/false)");
        EDITAVEIS.put("alcance_ativacao.proteger_areas_sem_jogador", "Nao mexer em areas carregadas por chunk loader, sem jogador na distancia de simulacao (true/false)");
        EDITAVEIS.put("alcance_ativacao.excecoes_entidades", "Entidades sempre processadas (ids separados por virgula, ex.: chefes de mods)");
        EDITAVEIS.put("alcance_ativacao.excecoes_mods", "Mods cujas entidades sao sempre processadas (ids de mod)");
        EDITAVEIS.put("alcance_ativacao.dimensoes_ignoradas", "Dimensoes onde o alcance de ativacao nao age");
        EDITAVEIS.put("limite_enxame.lista", "Limite por tipo na regiao (tipo=numero, separados por virgula)");
        EDITAVEIS.put("limite_enxame.raio_chunks", "Raio da regiao em chunks (1 = o chunk e os 8 vizinhos)");
        EDITAVEIS.put("insercao_rapida.limite_slots", "Inventarios com pelo menos este numero de slots usam a insercao rapida");
        EDITAVEIS.put("insercao_rapida.varreduras_max_por_tick", "Varreduras completas por inventario por tick antes do orcamento recusar (se ligado)");
        EDITAVEIS.put("insercao_rapida.orcamento", "Recusar insercoes alem do limite de varreduras no tick (true/false; teste as maquinas antes)");
        EDITAVEIS.put("insercao_rapida.ignorar", "Classes ou pacotes de inventarios que a insercao rapida nao toca (prefixos, separados por virgula)");
    }
    private static int fpsSemFoco = 15;
    private static int fpsMinimizado = 1;
    private static int lagLimiteMs = 2000;
    private static int lagIntervaloS = 30;
    private static int lagToleranciaS = 30;
    private static Set<String> lagIgnorarMixins = Set.of("chrono:scalePlayerGameplayTick");
    private static int ativacaoHorizontal = 48;
    private static int ativacaoVertical = 48;
    private static int ativacaoIntervalo = 20;
    private static boolean ativacaoProtegerItens = true;
    private static boolean ativacaoProtegerAldeoes = true;
    private static boolean ativacaoProtegerSemJogador = true;
    private static Set<String> ativacaoExcecoesEntidades = Set.of();
    private static Set<String> ativacaoExcecoesMods = Set.of();
    private static Set<String> ativacaoDimensoesIgnoradas = Set.of();
    private static Map<String, Integer> enxameLimites = Map.of();
    private static int enxameRaioChunks = 1;
    private static int insercaoLimiteSlots = 54;
    private static int insercaoVarredurasMaxPorTick = 4;
    private static boolean insercaoOrcamento = false;
    private static Set<String> insercaoIgnorar = Set.of();
    /** Muda a cada leitura dos valores, para quem guarda cache (ex.: excecoes por tipo de entidade). */
    private static volatile int versao;
    /** Vazio para o publico; modpacks escrevem a propria lista (ex.: o HL usa chaospersists:ant=8,...). */
    private static final String ENXAME_PADRAO = "";
    private static Preset preset = Preset.PADRAO;
    private static boolean presetAutomatico = true;
    private static int modsCarregados;
    private static final String EXCECOES_PADRAO = "minecraft:ender_dragon,minecraft:wither,minecraft:elder_guardian,minecraft:warden";
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
        Msg.definirIdioma(props.getProperty("log.idioma", "auto"));

        boolean faltando = false;
        LoadingModList lista = LoadingModList.get();
        modsCarregados = lista == null ? 0 : lista.getMods().size();
        String textoPreset = props.getProperty("preset");
        if (textoPreset == null) {
            faltando = true;
            textoPreset = "auto";
            props.setProperty("preset", textoPreset);
        }
        Preset escolhido = Preset.deTexto(textoPreset);
        presetAutomatico = escolhido == null;
        preset = escolhido != null ? escolhido : Preset.porQuantidade(modsCarregados);
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
        ativacaoHorizontal = lerInt(props, "alcance_ativacao.horizontal", preset.valor(48, 40), 8, 512);
        ativacaoVertical = lerInt(props, "alcance_ativacao.vertical", 48, 8, 512);
        ativacaoIntervalo = lerInt(props, "alcance_ativacao.intervalo", preset.valor(20, 30), 2, 200);
        ativacaoProtegerItens = Boolean.parseBoolean(props.getProperty("alcance_ativacao.proteger_itens", "true").trim());
        ativacaoProtegerAldeoes = Boolean.parseBoolean(props.getProperty("alcance_ativacao.proteger_aldeoes", "true").trim());
        ativacaoProtegerSemJogador = Boolean.parseBoolean(props.getProperty("alcance_ativacao.proteger_areas_sem_jogador", "true").trim());
        ativacaoExcecoesEntidades = lista(props, "alcance_ativacao.excecoes_entidades", EXCECOES_PADRAO);
        ativacaoExcecoesMods = lista(props, "alcance_ativacao.excecoes_mods", "");
        ativacaoDimensoesIgnoradas = lista(props, "alcance_ativacao.dimensoes_ignoradas", "");
        Map<String, Integer> limites = new java.util.HashMap<>();
        for (String par : lista(props, "limite_enxame.lista", ENXAME_PADRAO)) {
            int igual = par.lastIndexOf('=');
            if (igual > 0) {
                try {
                    limites.put(par.substring(0, igual).trim(), Math.max(0, Integer.parseInt(par.substring(igual + 1).trim())));
                } catch (NumberFormatException ignorado) {
                    // par invalido: ignora
                }
            }
        }
        enxameLimites = Map.copyOf(limites);
        enxameRaioChunks = lerInt(props, "limite_enxame.raio_chunks", 1, 0, 4);
        insercaoLimiteSlots = lerInt(props, "insercao_rapida.limite_slots", 54, 1, 1_000_000);
        insercaoVarredurasMaxPorTick = lerInt(props, "insercao_rapida.varreduras_max_por_tick", 4, 1, 10_000);
        insercaoOrcamento = Boolean.parseBoolean(props.getProperty("insercao_rapida.orcamento", "false").trim());
        insercaoIgnorar = lista(props, "insercao_rapida.ignorar", "");
        versao++;
        limpezaIntervaloMin = lerInt(props, "limpeza_itens.intervalo_min", preset.valor(10, 5), 1, 1440);
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
                {"alcance_ativacao.horizontal", "auto"},
                {"alcance_ativacao.vertical", "48"},
                {"alcance_ativacao.intervalo", "auto"},
                {"alcance_ativacao.proteger_itens", "true"},
                {"alcance_ativacao.proteger_aldeoes", "true"},
                {"alcance_ativacao.proteger_areas_sem_jogador", "true"},
                {"alcance_ativacao.excecoes_entidades", EXCECOES_PADRAO},
                {"alcance_ativacao.excecoes_mods", ""},
                {"alcance_ativacao.dimensoes_ignoradas", ""},
                {"limite_enxame.lista", ENXAME_PADRAO},
                {"limite_enxame.raio_chunks", "1"},
                {"insercao_rapida.limite_slots", "54"},
                {"insercao_rapida.varreduras_max_por_tick", "4"},
                {"insercao_rapida.orcamento", "false"},
                {"insercao_rapida.ignorar", ""},
                {"limpeza_itens.intervalo_min", "auto"},
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
                motivo = Msg.t("desligado na config");
                curto = motivo;
            }
            case "on", "true", "ligado" -> {
                ativo = true;
                motivo = conflito.map(id -> Msg.f("forcado na config (atencao: {} tambem esta instalado)", id)).orElse(Msg.t("ligado na config"));
                curto = conflito.map(id -> Msg.f("forçado ({} também instalado)", nomeDoMod(id))).orElse(Msg.t("ligado na config"));
            }
            default -> {
                if (preset == Preset.LEVE && Preset.MUDAM_COMPORTAMENTO.contains(modulo)) {
                    ativo = false;
                    motivo = Msg.t("desligado pelo preset leve (muda o comportamento do jogo; use on para ligar)");
                    curto = Msg.t("preset leve: desligado (muda o jogo)");
                    break;
                }
                if (modulo == Modulo.INSERCAO_RAPIDA && preset != Preset.AGRESSIVO) {
                    ativo = false;
                    motivo = Msg.t("desligado por padrao (opcional; ligado so no preset agressivo; use on para ligar)");
                    curto = Msg.t("opcional: desligado (use on)");
                    break;
                }
                ativo = conflito.isEmpty();
                motivo = conflito.map(id -> Msg.f("desligado automaticamente: {} ja faz isso", id)).orElse(Msg.t("ligado (auto)"));
                curto = conflito.map(id -> Msg.f("desligado: {} já faz isso", nomeDoMod(id))).orElse(Msg.t("ligado (auto)"));
            }
        }
        if (modulo == Modulo.CORRECAO_FASTASYNCWORLDSAVE && ativo && !modInstalado("fastasyncworldsave")) {
            ativo = false;
            motivo = Msg.t("nao necessario: fastasyncworldsave nao esta instalado");
            curto = Msg.t("não necessário (sem o Fast Async World Save)");
        }
        if (modulo == Modulo.CORRECAO_INFINITY_CHEST && ativo && !modInstalado("avaritia")) {
            ativo = false;
            motivo = Msg.t("nao necessario: avaritia (Re-Avaritia) nao esta instalado");
            curto = Msg.t("não necessário (sem o Re-Avaritia)");
        }
        ATIVOS.put(modulo, ativo);
        MOTIVOS.put(modulo, motivo);
        MOTIVOS_CURTOS.put(modulo, curto);
        if (!ativo && (modulo == Modulo.CORRECAO_FASTASYNCWORLDSAVE || modulo == Modulo.CORRECAO_INFINITY_CHEST)
                && motivo.equals(Msg.t(modulo == Modulo.CORRECAO_FASTASYNCWORLDSAVE ? "nao necessario: fastasyncworldsave nao esta instalado"
                : "nao necessario: avaritia (Re-Avaritia) nao esta instalado"))) {
            NAO_NECESSARIO.add(modulo);
        }
    }

    /** Modo seguro: desliga um modulo durante o jogo (ate reiniciar), sem mexer no arquivo. */
    public static synchronized void desligarEmExecucao(Modulo modulo, String motivo, String curto) {
        ATIVOS.put(modulo, false);
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
                return Msg.f("valor invalido para {}", chave);
            }
        } else if (!EDITAVEIS.containsKey(chave)) {
            return Msg.f("chave nao editavel: {}", chave);
        } else if (!v.equals("auto") && (chave.endsWith("_min") || chave.endsWith("_s") && !chave.endsWith("avisos_s") || chave.endsWith("_ms")
                || chave.endsWith("max_por_tick") || chave.endsWith("horizontal") || chave.endsWith("vertical") || chave.endsWith("intervalo")
                || chave.endsWith("limite_slots") || chave.endsWith("raio_chunks"))) {
            try {
                Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return Msg.f("{} precisa ser um numero", chave);
            }
        } else if (chave.startsWith("limpeza_itens.proteger") && !(v.equals("true") || v.equals("false"))) {
            return Msg.f("{} precisa ser true ou false", chave);
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
        return NAO_NECESSARIO.contains(modulo);
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

    public static Preset preset() {
        carregar();
        return preset;
    }

    public static boolean presetAutomatico() {
        carregar();
        return presetAutomatico;
    }

    public static int modsCarregados() {
        carregar();
        return modsCarregados;
    }

    public static int versao() {
        return versao;
    }

    public static int ativacaoHorizontal() {
        return ativacaoHorizontal;
    }

    public static int ativacaoVertical() {
        return ativacaoVertical;
    }

    public static int ativacaoIntervalo() {
        return ativacaoIntervalo;
    }

    public static boolean ativacaoProtegerItens() {
        return ativacaoProtegerItens;
    }

    public static boolean ativacaoProtegerAldeoes() {
        return ativacaoProtegerAldeoes;
    }

    public static boolean ativacaoProtegerSemJogador() {
        return ativacaoProtegerSemJogador;
    }

    public static Set<String> ativacaoExcecoesEntidades() {
        return ativacaoExcecoesEntidades;
    }

    public static Set<String> ativacaoExcecoesMods() {
        return ativacaoExcecoesMods;
    }

    public static Set<String> ativacaoDimensoesIgnoradas() {
        return ativacaoDimensoesIgnoradas;
    }

    public static Map<String, Integer> enxameLimites() {
        return enxameLimites;
    }

    public static int insercaoLimiteSlots() {
        return insercaoLimiteSlots;
    }

    public static int insercaoVarredurasMaxPorTick() {
        return insercaoVarredurasMaxPorTick;
    }

    public static boolean insercaoOrcamento() {
        return insercaoOrcamento;
    }

    public static Set<String> insercaoIgnorar() {
        return insercaoIgnorar;
    }

    public static int enxameRaioChunks() {
        return enxameRaioChunks;
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
        sb.append(Msg.t("# HLO: HL Otimizacao\n"));
        sb.append(Msg.t("# Valores dos modulos: auto (padrao), on, off.\n"));
        sb.append(Msg.t("# auto = ligado, a menos que o mod original que faz o mesmo esteja instalado.\n"));
        sb.append(Msg.t("# Mudancas so valem depois de reiniciar o jogo.\n\n"));
        sb.append(Msg.t("# Preset: auto (pela quantidade de mods: ate 120 = leve, ate 250 = padrao, mais = agressivo), leve, padrao, agressivo ou personalizado.\n"));
        sb.append(Msg.t("# O preset so define valores padrao; o que estiver escrito aqui tem prioridade. Valores 'auto' abaixo seguem o preset.\n"));
        sb.append("preset=").append(props.getProperty("preset", "auto")).append("\n\n");
        sb.append(Msg.t("# Idioma do log e dos textos do mod: auto (o do Java), en_us ou pt_br.\n"));
        sb.append("log.idioma=").append(props.getProperty("log.idioma", "auto")).append("\n\n");
        for (Modulo modulo : Modulo.values()) {
            sb.append("# ").append(modulo.texto()).append('\n');
            if (!modulo.modsConflitantes.isEmpty()) {
                sb.append(Msg.t("# Desliga sozinho (em auto) se estiver instalado: ")).append(String.join(", ", modulo.modsConflitantes)).append('\n');
            }
            sb.append("modulo.").append(modulo.chave).append('=').append(props.getProperty("modulo." + modulo.chave)).append("\n\n");
        }
        sb.append(Msg.t("# FPS maximo com a janela sem foco e minimizada (modulo fps_sem_foco).\n"));
        sb.append("fps_sem_foco.limite=").append(props.getProperty("fps_sem_foco.limite")).append('\n');
        sb.append("fps_sem_foco.limite_minimizado=").append(props.getProperty("fps_sem_foco.limite_minimizado")).append("\n\n");
        sb.append(Msg.t("# Tempo de tick (ms) a partir do qual o diagnostico_lag registra o travamento,\n"));
        sb.append(Msg.t("# e intervalo minimo (s) entre dois registros.\n"));
        sb.append("diagnostico_lag.limite_ms=").append(props.getProperty("diagnostico_lag.limite_ms")).append('\n');
        sb.append("diagnostico_lag.intervalo_s=").append(props.getProperty("diagnostico_lag.intervalo_s")).append('\n');
        sb.append(Msg.t("# Segundos sem registrar depois de abrir o mundo (e depois do primeiro jogador entrar),\n"));
        sb.append(Msg.t("# enquanto classes e chunks ainda estao carregando.\n"));
        sb.append("diagnostico_lag.tolerancia_inicio_s=").append(props.getProperty("diagnostico_lag.tolerancia_inicio_s")).append('\n');
        sb.append(Msg.t("# Mixins que so repassam a chamada e nunca sao a causa de um travamento (mod:metodo, separados por virgula).\n"));
        sb.append("diagnostico_lag.ignorar_mixins=").append(props.getProperty("diagnostico_lag.ignorar_mixins")).append("\n\n");
        sb.append(Msg.t("# salvamento_assincrono: SavedData que sobrescrevem save(File) sao gravados no thread principal, do jeito do mod.\n"));
        sb.append(Msg.t("# O log mostra quais classes sao essas. Classes listadas aqui (nomes completos, separados por virgula) passam\n"));
        sb.append(Msg.t("# a ser serializadas com save(CompoundTag) e gravadas em outra thread, IGNORANDO o save(File) do mod.\n"));
        sb.append(Msg.t("# So use se souber que o save(File) da classe apenas chama o do Minecraft (risco de perder dados se fizer mais).\n"));
        sb.append("salvamento_assincrono.serializar_classes=").append(props.getProperty("salvamento_assincrono.serializar_classes")).append("\n\n");
        sb.append(Msg.t("# alcance_ativacao: entidades a mais de 'horizontal' blocos (ou 'vertical') de todos os jogadores sao processadas\n"));
        sb.append(Msg.t("# so 1 vez a cada 'intervalo' ticks. Nunca: jogadores, projeteis, veiculos/passageiros, chefes, entidades com nome,\n"));
        sb.append(Msg.t("# domesticadas, em raid, machucadas ou com alvo, e (opcoes) itens, aldeoes e areas so carregadas por chunk loader.\n"));
        for (String chave : new String[]{"horizontal", "vertical", "intervalo", "proteger_itens", "proteger_aldeoes", "proteger_areas_sem_jogador",
                "excecoes_entidades", "excecoes_mods", "dimensoes_ignoradas"}) {
            sb.append("alcance_ativacao.").append(chave).append('=').append(props.getProperty("alcance_ativacao." + chave)).append('\n');
        }
        sb.append('\n').append(Msg.t("# limite_enxame: spawn natural, de geracao de chunk e de spawner e cancelado quando ja ha 'numero' entidades daquele\n"));
        sb.append(Msg.t("# tipo no chunk e nos vizinhos ate raio_chunks. Nunca vale para ovo, comando ou balde, e nunca remove entidades.\n"));
        sb.append("limite_enxame.lista=").append(props.getProperty("limite_enxame.lista")).append('\n');
        sb.append("limite_enxame.raio_chunks=").append(props.getProperty("limite_enxame.raio_chunks")).append("\n\n");
        sb.append(Msg.t("# insercao_rapida (modulo desligado por padrao): inventarios com limite_slots ou mais usam um cache de onde o item\n"));
        sb.append(Msg.t("# coube; orcamento=true recusa a insercao (a maquina tenta no proximo tick) depois de varreduras_max_por_tick varreduras.\n"));
        for (String chave : new String[]{"limite_slots", "varreduras_max_por_tick", "orcamento", "ignorar"}) {
            sb.append("insercao_rapida.").append(chave).append('=').append(props.getProperty("insercao_rapida." + chave)).append('\n');
        }
        sb.append('\n');
        sb.append(Msg.t("# limpeza_itens: a cada intervalo_min minutos apaga os itens soltos no chao, com avisos no chat antes (avisos_s).\n"));
        sb.append(Msg.t("# Nunca apaga: itens com menos de idade_minima_s segundos no chao, drops da morte de jogadores, itens_protegidos,\n"));
        sb.append(Msg.t("# ovos de spawn, shulker boxes, itens encantados/com nome/com conteudo (proteger_itens_com_nbt) e itens sobre\n"));
        sb.append(Msg.t("# funis ou blocos que recebem itens (proteger_sobre_coletores). dimensoes vazio = todas. /hl recarregar rele estes valores.\n"));
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
