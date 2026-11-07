package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Msg;
import com.hlotimizacao.config.Config;
import net.minecraft.server.MinecraftServer;

/** Problemas encontrados no estudo do codigo dos mods de otimizacao do pack (docs/aprendizados.md). */
public final class RelatorioConflitos {
    /** Quantos avisos havia ao terminar o relatorio da inicializacao (para o /hl status). */
    public static volatile int avisosInicializacao;

    private RelatorioConflitos() {
    }

    public static void registrar(MinecraftServer server) {
        int avisos = 0;

        if (Config.modInstalado("fastsuite") && Config.modInstalado("recipeessentials")) {
            String ativo = server.getRecipeManager().getClass().getName();
            if (ativo.startsWith("com.recipeessentials")) {
                Config.LOGGER.warn("[Conflito] FastSuite e Recipe Essentials substituem o RecipeManager. O ativo e o do Recipe Essentials ({}), "
                        + "entao a busca paralela de receitas do FastSuite nao esta sendo usada. Remova um dos dois.", ativo);
            } else {
                Config.LOGGER.warn("[Conflito] FastSuite e Recipe Essentials substituem o RecipeManager. Ativo: {}. Remova um dos dois.", ativo);
            }
            Avisos.adicionar(Msg.t("FastSuite + Recipe Essentials: so um funciona"),
                    Msg.f("Os dois substituem o gerenciador de receitas e so o do Recipe Essentials fica ativo (ativo agora: {}). Remova um dos dois.", ativo));
            avisos++;
        }
        if (Config.modInstalado("neruina")) {
            // Sem aviso: os dois se completam. O Neruina segura entidades/blocos que crasham no tick;
            // o modo seguro do HLO so protege o codigo do proprio HLO.
            Config.LOGGER.info("[relatorio_conflitos] Neruina instalado: ele trata entidades e blocos que crasham no tick; o modo seguro do HLO cuida so dos modulos do HLO");
        }
        // Porte 1.18.2: no 1.18 o Starlight ainda vale a pena (o motor de luz vanilla so mudou no 1.20); sem aviso.
        if (false && Config.modInstalado("starlight")) {
            Config.LOGGER.warn("[Aviso] Starlight substitui o motor de luz inteiro, mas na 1.20+ o motor vanilla ja usa o mesmo algoritmo "
                    + "(segundo o proprio autor). Se houver bugs de luz ou conflitos, teste sem ele.");
            Avisos.adicionar(Msg.t("Starlight: redundante na 1.20"),
                    Msg.t("O Starlight substitui o motor de luz, mas na 1.20+ o motor vanilla ja usa o mesmo algoritmo (segundo o proprio autor). Remova se notar bugs de luz."));
            avisos++;
        }
        if (Config.modInstalado("ksyxis")) {
            Config.LOGGER.info("[Aviso] Ksyxis esta instalado: os spawn chunks nao ficam carregados, entao fazendas no spawn param sem jogador por perto.");
            Avisos.adicionar(Msg.t("Ksyxis: spawn chunks descarregados"),
                    Msg.t("Com o Ksyxis os spawn chunks nao ficam carregados: fazendas no spawn param sem jogador por perto."));
            avisos++;
        }
        if (Config.modInstalado("connectivity")) {
            Config.LOGGER.info("[Aviso] Connectivity remove os limites de tamanho de pacote e de NBT. Em servidor publico, "
                    + "considere disablePacketLimits=false em config/connectivity.json.");
            Avisos.adicionar(Msg.t("Connectivity: limites de pacote desligados"),
                    Msg.t("O Connectivity remove os limites de tamanho de pacote e de NBT. Em servidor publico, use disablePacketLimits=false em config/connectivity.json."));
            avisos++;
        }
        if (Config.modInstalado("letmedespawn")) {
            Config.LOGGER.info("[Aviso] Let Me Despawn tira a persistencia de qualquer mob que pega item, mesmo mobs que ja eram persistentes "
                    + "por outro motivo, e devolve todo o equipamento ao desaparecer. O modulo despawn_itens deste mod corrige isso "
                    + "(remova o Let Me Despawn para ele ligar sozinho).");
            Avisos.adicionar(Msg.t("Let Me Despawn: use o despawn_itens"),
                    Msg.t("O Let Me Despawn tira a persistencia de mobs que ja eram persistentes e devolve todo o equipamento. O modulo despawn_itens corrige isso; remova o Let Me Despawn para ele ligar."));
            avisos++;
        }
        if (Config.modInstalado("fastasyncworldsave")) {
            Config.LOGGER.info("[Aviso] Fast Async World Save ignora SavedData que sobrescrevem save(File). O modulo salvamento_assincrono "
                    + "deste mod trata esse caso (remova o Fast Async World Save para ele ligar sozinho).");
            Avisos.adicionar(Msg.t("Fast Async World Save: ignora save(File)"),
                    Msg.t("O Fast Async World Save ignora SavedData que sobrescrevem save(File). O modulo salvamento_assincrono trata esse caso; remova o FAWS para ele ligar."));
            avisos++;
        }
        avisos += DiagnosticoAmbiente.verificar();
        avisosInicializacao = Avisos.quantidade();
        Config.LOGGER.info("Relatorio de conflitos: {} aviso(s). Mais informacoes: {}", avisos, Config.URL_PROJETO);
    }
}
