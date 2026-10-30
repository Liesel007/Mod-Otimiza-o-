package com.hlotimizacao.cliente;

import com.hlotimizacao.config.Config;
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
 * Uma linha de tooltip criada com Component.literal(null) crasha o jogo quando o mouse passa no item
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
            avisar(event.getItemStack(), "o proprio item (mod " + modDoItem(event.getItemStack()) + ")");
        }
    }

    private static void linhasDeOutrosMods(ItemTooltipEvent event) {
        if (limpar(event.getToolTip())) {
            avisar(event.getItemStack(), "outro mod, por um ouvinte do ItemTooltipEvent");
        }
    }

    private static void componentesNaHoraDeDesenhar(RenderTooltipEvent.GatherComponents event) {
        List<Either<FormattedText, TooltipComponent>> elementos = event.getTooltipElements();
        boolean achou = false;
        for (int i = 0; i < elementos.size(); i++) {
            Either<FormattedText, TooltipComponent> elemento = elementos.get(i);
            if (elemento == null || elemento.left().map(TooltipSeguro::invalido).orElse(false)) {
                elementos.set(i, Either.left(Component.empty()));
                achou = true;
            }
        }
        if (achou) {
            avisar(event.getItemStack(), "outro mod, pelo RenderTooltipEvent.GatherComponents");
        }
    }

    /** Troca linhas invalidas por linhas vazias. Retorna true se trocou alguma. */
    private static boolean limpar(List<Component> linhas) {
        boolean achou = false;
        for (int i = 0; i < linhas.size(); i++) {
            if (invalido(linhas.get(i))) {
                linhas.set(i, Component.empty());
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
        if (AVISADOS.add(id + "|" + origem)) {
            Config.LOGGER.warn("[tooltip_seguro] Tooltip do item {} tinha linha nula (crasharia o jogo). Origem: {}. "
                    + "A linha foi trocada por uma linha vazia. Avise o autor do mod.", id, origem);
        }
    }

    private static String idDoItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "(sem item)";
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
