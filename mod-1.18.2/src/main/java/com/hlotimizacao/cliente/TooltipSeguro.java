package com.hlotimizacao.cliente;

import com.hlotimizacao.compat.C;
import com.hlotimizacao.config.Msg;
import com.hlotimizacao.config.Config;
import com.hlotimizacao.recursos.Estatisticas;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uma linha de tooltip criada com C.lit(null) crasha o jogo quando o mouse passa no item
 * (NullPointerException em StringDecomposer.iterateFormatted, chamado por
 * ForgeHooksClient.gatherTooltipComponents ao medir a largura das linhas).
 *
 * Tres pontos de protecao, todos por evento do Forge:
 * 1. ItemTooltipEvent, prioridade mais alta: as linhas ja estao la so com o que o proprio item e o
 *    vanilla adicionaram; uma linha nula aqui veio do mod do item.
 * 2. ItemTooltipEvent, prioridade mais baixa: linhas nulas novas vieram de outros mods que ouvem esse
 *    evento. Limpar aqui tambem protege telas que usam getTooltipLines() (JEI, etc.).
 * 3. RenderTooltipEvent.GatherComponents, prioridade mais baixa: roda logo antes do calculo de
 *    largura que crashava, e pega linhas adicionadas na hora de desenhar.
 */
public final class TooltipSeguro {
    private static final Set<String> AVISADOS = ConcurrentHashMap.newKeySet();

    private TooltipSeguro() {
    }

    public static void registrar() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, TooltipSeguro::linhasDoItem);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, TooltipSeguro::linhasDeOutrosMods);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, TooltipSeguro::componentesNaHoraDeDesenhar);
    }

    private static void linhasDoItem(ItemTooltipEvent event) {
        if (limpar(event.getToolTip())) {
            avisar(event.getItemStack(), Msg.f("o proprio item (mod {})", modDoItem(event.getItemStack())));
        }
        vigiarLinhas(event);
    }

    private static void linhasDeOutrosMods(ItemTooltipEvent event) {
        List<Component> linhas = event.getToolTip();
        String culpado = linhas instanceof ListaVigiada vigiada ? vigiada.culpado : null;
        if (limpar(linhas)) {
            avisar(event.getItemStack(), culpado != null ? Msg.f("o mod {}, por um ouvinte do ItemTooltipEvent", culpado)
                    : Msg.t("outro mod, por um ouvinte do ItemTooltipEvent"));
        }
    }

    // ---------- quem adicionou a linha nula ----------

    private static java.lang.reflect.Field campoLinhas;
    private static boolean campoProcurado;

    /**
     * O Forge nao diz qual ouvinte adicionou cada linha. Aqui a lista do evento e trocada por uma que repassa tudo para
     * a original (o ItemStack devolve a original, entao nada se perde) e, so quando alguem adiciona uma linha
     * invalida, olha na pilha qual mod chamou. Linhas validas custam so a chamada repassada.
     */
    private static void vigiarLinhas(ItemTooltipEvent event) {
        try {
            if (!campoProcurado) {
                campoProcurado = true;
                campoLinhas = ItemTooltipEvent.class.getDeclaredField("toolTip");
                campoLinhas.setAccessible(true);
            }
            if (campoLinhas != null && !(event.getToolTip() instanceof ListaVigiada)) {
                campoLinhas.set(event, new ListaVigiada(event.getToolTip()));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            campoLinhas = null; // sem a troca, a mensagem so diz "outro mod"
        }
    }

    private static final StackWalker PILHA = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private static final class ListaVigiada extends java.util.AbstractList<Component> implements java.util.RandomAccess {
        private final List<Component> original;
        volatile String culpado;

        ListaVigiada(List<Component> original) {
            this.original = original;
        }

        private void conferir(Component c) {
            if (culpado == null && invalido(c)) {
                culpado = PILHA.walk(frames -> frames
                        .map(f -> com.hlotimizacao.recursos.DiagnosticoLag.modDoModulo(f.getDeclaringClass().getModule().getName()))
                        .filter(java.util.Objects::nonNull)
                        .filter(m -> !m.equals("hlotimizacao"))
                        .findFirst().orElse(null));
            }
        }

        @Override
        public Component get(int i) {
            return original.get(i);
        }

        @Override
        public int size() {
            return original.size();
        }

        @Override
        public Component set(int i, Component c) {
            conferir(c);
            return original.set(i, c);
        }

        @Override
        public void add(int i, Component c) {
            conferir(c);
            original.add(i, c);
        }

        @Override
        public Component remove(int i) {
            return original.remove(i);
        }
    }

    private static void componentesNaHoraDeDesenhar(RenderTooltipEvent.GatherComponents event) {
        List<Either<FormattedText, TooltipComponent>> elementos = event.getTooltipElements();
        boolean achou = false;
        for (int i = 0; i < elementos.size(); i++) {
            Either<FormattedText, TooltipComponent> elemento = elementos.get(i);
            if (elemento == null || elemento.left().map(TooltipSeguro::invalido).orElse(false)) {
                elementos.set(i, Either.left(C.empty()));
                achou = true;
            }
        }
        if (achou) {
            avisar(event.getItemStack(), Msg.t("outro mod, pelo RenderTooltipEvent.GatherComponents"));
        }
    }

    /** Troca linhas invalidas por linhas vazias. Retorna true se trocou alguma. */
    private static boolean limpar(List<Component> linhas) {
        boolean achou = false;
        for (int i = 0; i < linhas.size(); i++) {
            if (invalido(linhas.get(i))) {
                linhas.set(i, C.empty());
                achou = true;
            }
        }
        return achou;
    }

    /** Uma linha e invalida se for nula ou se algum trecho de texto dentro dela for nulo. */
    static boolean invalido(FormattedText texto) {
        if (texto == null) {
            return true;
        }
        try {
            return texto.visit(trecho -> trecho == null ? Optional.of(Boolean.TRUE) : Optional.empty()).isPresent();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void avisar(ItemStack stack, String origem) {
        String id = idDoItem(stack);
        Estatisticas.TOOLTIP_ITENS.add(id);
        if (AVISADOS.add(id + "|" + origem)) {
            Config.LOGGER.warn("[tooltip_seguro] Tooltip do item {} tinha linha nula (crasharia o jogo). Origem: {}. "
                    + "A linha foi trocada por uma linha vazia. Avise o autor do mod.", id, origem);
        }
    }

    private static String idDoItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Msg.t("(sem item)");
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? stack.getItem().toString() : id.toString();
    }

    private static String modDoItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "?";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "?" : id.getNamespace();
    }
}
