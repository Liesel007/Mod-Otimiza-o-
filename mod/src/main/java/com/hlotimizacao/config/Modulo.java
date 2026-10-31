package com.hlotimizacao.config;

import java.util.List;

/**
 * Cada modulo do mod. Um modulo em modo "auto" fica desligado quando algum dos mods listados em
 * {@link #modsConflitantes} esta instalado, para nao duplicar o que o mod original ja faz.
 */
public enum Modulo {
    DESPAWN_ITENS("Despawn com itens", false, "despawn_itens", "mixin.despawn",
            "Mobs que pegam itens do chao continuam podendo desaparecer (e devolvem so os itens que pegaram).",
            List.of("letmedespawn")),
    GATILHO_INVENTARIO("Gatilho de inventário", false, "gatilho_inventario", "mixin.trigger",
            "Ignora o gatilho de conquistas de inventario quando uma pilha fica vazia.",
            List.of("icterine")),
    SALVAMENTO_ASSINCRONO("Salvamento assíncrono", false, "salvamento_assincrono", "mixin.save",
            "Grava os dados do mundo (SavedData) em outra thread, esperando a gravacao terminar ao fechar o mundo.",
            List.of("fastasyncworldsave")),
    VAZAMENTO_GPU("Vazamento de GPU", true, "vazamento_gpu", "mixin.client.gpuleak",
            "Libera framebuffers que mods esqueceram de destruir, usando Cleaner em vez de finalize().",
            List.of("gpumemleakfix")),
    FPS_SEM_FOCO("FPS sem foco", true, "fps_sem_foco", "mixin.client.fps",
            "Limita o FPS quando a janela esta sem foco ou minimizada.",
            List.of("dynamic_fps")),
    TOASTS_VAZIOS("Toasts vazios", true, "toasts_vazios", "mixin.client.toast",
            "Nao processa a camada de notificacoes (toasts) quando nao ha nenhuma.",
            List.of("badoptimizations")),
    TOOLTIP_SEGURO("Tooltip seguro", true, "tooltip_seguro", null,
            "Troca linhas nulas de tooltip (que crasham o jogo ao passar o mouse no item) por linhas vazias e avisa no log.",
            List.of()),
    DIAGNOSTICO_LAG("Diagnóstico de lag", false, "diagnostico_lag", "mixin.lag",
            "Quando um tick do servidor demora demais, registra no log onde o servidor travou e o estado de cada dimensao.",
            List.of()),
    CORRECAO_FASTASYNCWORLDSAVE("Correção do FAWS", false, "correcao_fastasyncworldsave", null,
            "Correcao para o Fast Async World Save: espera as gravacoes pendentes ao fechar o mundo.",
            List.of()),
    CORRECAO_INFINITY_CHEST("Correção do Infinity Chest", false, "correcao_infinity_chest", "mixin.avaritia",
            "Correcao para o Infinity Chest do Re-Avaritia: chaves de itens com NBT que crescem sem parar (travamentos ao abrir o bau), "
                    + "NBT perdido ao tirar parte de uma pilha e lista de itens reconstruida a cada item novo.",
            List.of()),
    LIMPEZA_ITENS("Limpeza de itens", false, "limpeza_itens", null,
            "Apaga de tempos em tempos os itens soltos no chao, com avisos no chat antes e protecao para itens valiosos, recentes e de morte.",
            List.of("clearlag", "clearlagg", "itemclearer", "clear_lag")),
    RELATORIO_CONFLITOS("Relatório de conflitos", false, "relatorio_conflitos", null,
            "Mostra no log conflitos conhecidos entre mods de otimizacao instalados.",
            List.of());

    /** Nome para mostrar no /hl status. */
    public final String nomeAmigavel;
    /** So faz algo no cliente (no servidor dedicado nao ha contadores). */
    public final boolean soCliente;
    public final String chave;
    /** Subpacote de mixins deste modulo (relativo a com.hlotimizacao), ou null se nao usa mixins. */
    public final String pacoteMixin;
    public final String descricao;
    public final List<String> modsConflitantes;

    Modulo(String nomeAmigavel, boolean soCliente, String chave, String pacoteMixin, String descricao, List<String> modsConflitantes) {
        this.nomeAmigavel = nomeAmigavel;
        this.soCliente = soCliente;
        this.chave = chave;
        this.pacoteMixin = pacoteMixin;
        this.descricao = descricao;
        this.modsConflitantes = modsConflitantes;
    }
}
