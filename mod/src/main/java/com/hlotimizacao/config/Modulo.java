package com.hlotimizacao.config;

import java.util.List;

/**
 * Cada modulo do mod. Um modulo em modo "auto" fica desligado quando algum dos mods listados em
 * {@link #modsConflitantes} esta instalado, para nao duplicar o que o mod original ja faz.
 */
public enum Modulo {
    DESPAWN_ITENS("despawn_itens", "mixin.despawn",
            "Mobs que pegam itens do chao continuam podendo desaparecer (e devolvem so os itens que pegaram).",
            List.of("letmedespawn")),
    GATILHO_INVENTARIO("gatilho_inventario", "mixin.trigger",
            "Ignora o gatilho de conquistas de inventario quando uma pilha fica vazia.",
            List.of("icterine")),
    SALVAMENTO_ASSINCRONO("salvamento_assincrono", "mixin.save",
            "Grava os dados do mundo (SavedData) em outra thread, esperando a gravacao terminar ao fechar o mundo.",
            List.of("fastasyncworldsave")),
    VAZAMENTO_GPU("vazamento_gpu", "mixin.client.gpuleak",
            "Libera framebuffers que mods esqueceram de destruir, usando Cleaner em vez de finalize().",
            List.of("gpumemleakfix")),
    FPS_SEM_FOCO("fps_sem_foco", "mixin.client.fps",
            "Limita o FPS quando a janela esta sem foco ou minimizada.",
            List.of("dynamic_fps")),
    TOASTS_VAZIOS("toasts_vazios", "mixin.client.toast",
            "Nao processa a camada de notificacoes (toasts) quando nao ha nenhuma.",
            List.of("badoptimizations")),
    TOOLTIP_SEGURO("tooltip_seguro", null,
            "Troca linhas nulas de tooltip (que crasham o jogo ao passar o mouse no item) por linhas vazias e avisa no log.",
            List.of()),
    DIAGNOSTICO_LAG("diagnostico_lag", "mixin.lag",
            "Quando um tick do servidor demora demais, registra no log onde o servidor travou e o estado de cada dimensao.",
            List.of()),
    CORRECAO_FASTASYNCWORLDSAVE("correcao_fastasyncworldsave", null,
            "Correcao para o Fast Async World Save: espera as gravacoes pendentes ao fechar o mundo.",
            List.of()),
    CORRECAO_INFINITY_CHEST("correcao_infinity_chest", "mixin.avaritia",
            "Correcao para o Infinity Chest do Re-Avaritia: chaves de itens com NBT que crescem sem parar (travamentos ao abrir o bau), "
                    + "NBT perdido ao tirar parte de uma pilha e lista de itens reconstruida a cada item novo.",
            List.of()),
    LIMPEZA_ITENS("limpeza_itens", null,
            "Apaga de tempos em tempos os itens soltos no chao, com avisos no chat antes e protecao para itens valiosos, recentes e de morte.",
            List.of("clearlag", "clearlagg", "itemclearer", "clear_lag")),
    RELATORIO_CONFLITOS("relatorio_conflitos", null,
            "Mostra no log conflitos conhecidos entre mods de otimizacao instalados.",
            List.of());

    public final String chave;
    /** Subpacote de mixins deste modulo (relativo a com.hlotimizacao), ou null se nao usa mixins. */
    public final String pacoteMixin;
    public final String descricao;
    public final List<String> modsConflitantes;

    Modulo(String chave, String pacoteMixin, String descricao, List<String> modsConflitantes) {
        this.chave = chave;
        this.pacoteMixin = pacoteMixin;
        this.descricao = descricao;
        this.modsConflitantes = modsConflitantes;
    }
}
