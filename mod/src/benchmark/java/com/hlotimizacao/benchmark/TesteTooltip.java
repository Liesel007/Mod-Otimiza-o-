package com.hlotimizacao.benchmark;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reproduz o crash do pack: linha de tooltip com Component.literal(null).
 * - hlbench:item_ruim: o proprio item adiciona a linha nula (como o Silent Gear blueprint_package);
 * - graveto: outro "mod" adiciona pelo ItemTooltipEvent;
 * - maca: outro "mod" adiciona pelo RenderTooltipEvent.GatherComponents.
 */
final class TesteTooltip {
    private static boolean rodou = false;
    private static int ticks = 0;

    private TesteTooltip() {
    }

    static final class ItemRuim extends Item {
        ItemRuim() {
            super(new Item.Properties());
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> linhas, TooltipFlag flag) {
            linhas.add(Component.literal(null));
        }
    }

    static void registrarOuvintes() {
        MinecraftForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> {
            if (e.getItemStack().is(Items.STICK)) {
                e.getToolTip().add(Component.literal(null));
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderTooltipEvent.GatherComponents e) -> {
            if (e.getItemStack().is(Items.APPLE)) {
                e.getTooltipElements().add(Either.left(Component.literal(null)));
            }
        });
        MinecraftForge.EVENT_BUS.addListener(TesteTooltip::aoTick);
    }

    private static void aoTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || rodou || ++ticks < 200 || Minecraft.getInstance().font == null) {
            return;
        }
        rodou = true;
        testar("hlbench:item_ruim", new ItemStack(HLBench.ITEM_RUIM.get()));
        testar("minecraft:stick", new ItemStack(Items.STICK));
        testar("minecraft:apple", new ItemStack(Items.APPLE));
    }

    private static void testar(String nome, ItemStack stack) {
        int ok = 0;
        String erro = "-";
        for (int i = 0; i < 3; i++) {
            try {
                List<Component> linhas = stack.getTooltipLines(null, TooltipFlag.NORMAL);
                ForgeHooksClient.gatherTooltipComponents(stack, linhas, 10, 1280, 720, Minecraft.getInstance().font);
                ok++;
            } catch (Throwable t) {
                erro = t.toString();
            }
        }
        HLBench.LOGGER.info("HLBENCH tooltip item={} sem_erro={}/3 erro={}", nome, ok, erro);
    }

}
