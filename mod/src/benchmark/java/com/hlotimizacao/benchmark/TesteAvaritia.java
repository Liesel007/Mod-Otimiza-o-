package com.hlotimizacao.benchmark;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import committee.nova.mods.avaritia.core.chest.ServerChestHandler;
import committee.nova.mods.avaritia.core.chest.ServerChestManager;
import committee.nova.mods.avaritia.util.StorageUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Testes do Infinity Chest do Re-Avaritia 1.4.2 (modulo correcao_infinity_chest). So funciona com o
 * Re-Avaritia no ambiente (./gradlew runServer -PcomAvaritia).
 */
final class TesteAvaritia {
    static final UUID DONO = UUID.nameUUIDFromBytes("hlbench-dono".getBytes());
    static final UUID BAU = UUID.nameUUIDFromBytes("hlbench-bau".getBytes());
    static final int VARIANTES = 10;

    private TesteAvaritia() {
    }

    static ServerChestHandler bau() {
        return ServerChestManager.getInstance().getChest(DONO, BAU);
    }

    /** Livro encantado: VARIANTES NBTs diferentes, que se repetem. */
    static ItemStack livro(int i) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.SHARPNESS, 1 + (i % VARIANTES)));
    }

    /**
     * Como um cano/funil: para cada item, simula e depois insere. Por rodada entram VARIANTES livros
     * diferentes (repetidos) e 1 diamante. Sem o bug, o bau fica com VARIANTES + 1 entradas.
     */
    static int encher(CommandContext<CommandSourceStack> ctx) {
        int itens = IntegerArgumentType.getInteger(ctx, "itens");
        ServerChestHandler bau = bau();
        long inicio = System.nanoTime();
        for (int i = 0; i < itens; i++) {
            ItemStack pilha = i % (VARIANTES + 1) == VARIANTES ? new ItemStack(Items.DIAMOND) : livro(i);
            bau.insertItem(0, pilha.copy(), true);
            bau.insertItem(0, pilha, false);
        }
        double ms = (System.nanoTime() - inicio) / 1e6;
        HLBench.LOGGER.info("HLBENCH avaritia encher itens={} ms={} {}", itens, String.format("%.1f", ms), estadoTexto(bau));
        return 1;
    }

    static int estado(CommandContext<CommandSourceStack> ctx) {
        HLBench.LOGGER.info("HLBENCH avaritia estado {}", estadoTexto(bau()));
        return 1;
    }

    static String estadoTexto(ServerChestHandler bau) {
        int maior = 0;
        int comVariosHash = 0;
        for (String chave : bau.storageItems.keySet()) {
            maior = Math.max(maior, chave.length());
            if (chave.indexOf('#') != chave.lastIndexOf('#')) {
                comVariosHash++;
            }
        }
        long diamantes = bau.storageItems.getOrDefault("minecraft:diamond", 0L);
        return String.format("entradas=%d chaves_com_varios_hash=%d maior_chave=%d diamantes_na_chave_certa=%d id_diamante=%s id_livro=%s",
                bau.storageItems.size(), comVariosHash, maior, diamantes,
                StorageUtils.getItemId(Items.DIAMOND), StorageUtils.getItemId(Items.ENCHANTED_BOOK));
    }

    /**
     * NBT ao tirar: 5 livros iguais, tira 2 com takeItem (os 3 que ficam devem manter o NBT) e depois os
     * 3 com extractItem (devem sair com NBT).
     */
    static int tirar(CommandContext<CommandSourceStack> ctx) {
        ServerChestHandler bau = new ServerChestHandler();
        ItemStack livro = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.MENDING, 1));
        livro.setCount(5);
        bau.insertItem(0, livro.copy(), false);
        String chave = StorageUtils.getNbtItemId(livro);
        ItemStack tirados = bau.takeItem(chave, 2);
        // o jogador mexe no item que saiu: nao pode mudar os que ficaram
        tirados.getOrCreateTag().putString("hlbench", "mexido");
        int slot = -1;
        for (int s = 27; s < bau.getSlots(); s++) {
            if (!bau.getStackInSlot(s).isEmpty()) {
                slot = s;
            }
        }
        ItemStack noBau = slot < 0 ? ItemStack.EMPTY : bau.getStackInSlot(slot);
        ItemStack extraidos = slot < 0 ? ItemStack.EMPTY : bau.extractItem(slot, 64, false);
        HLBench.LOGGER.info("HLBENCH avaritia tirar take={} take_tem_nbt={} no_bau={} no_bau_tem_nbt={} no_bau_mexido={} extract={} extract_tem_nbt={} sobrou={}",
                tirados.getCount(), tirados.hasTag(), noBau.getCount(), noBau.hasTag(),
                noBau.hasTag() && noBau.getTag().contains("hlbench"), extraidos.getCount(), extraidos.hasTag(), bau.storageItems.size());
        return 1;
    }

    /** O que o Forge faz num autosave: um LevelEvent.Save por dimensao. */
    static int salvar(CommandContext<CommandSourceStack> ctx) {
        int repeticoes = IntegerArgumentType.getInteger(ctx, "repeticoes");
        int dimensoes = 0;
        long inicio = System.nanoTime();
        for (int r = 0; r < repeticoes; r++) {
            dimensoes = 0;
            for (ServerLevel nivel : ctx.getSource().getServer().getAllLevels()) {
                MinecraftForge.EVENT_BUS.post(new LevelEvent.Save(nivel));
                dimensoes++;
            }
        }
        double ms = (System.nanoTime() - inicio) / 1e6 / repeticoes;
        HLBench.LOGGER.info("HLBENCH avaritia salvar dimensoes={} repeticoes={} ms_por_autosave={} {}", dimensoes, repeticoes,
                String.format("%.1f", ms), estadoTexto(bau()));
        return 1;
    }

    static int mexer(CommandContext<CommandSourceStack> ctx) {
        bau().insertItem(0, new ItemStack(Items.DIAMOND), false);
        return 1;
    }

    static Map<String, Long> itens() {
        return bau().storageItems;
    }
}
