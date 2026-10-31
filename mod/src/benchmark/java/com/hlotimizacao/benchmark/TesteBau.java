package com.hlotimizacao.benchmark;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import committee.nova.mods.avaritia.core.chest.ServerChestHandler;
import committee.nova.mods.avaritia.core.chest.ServerChestManager;
import committee.nova.mods.avaritia.util.StorageUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Testes do modulo correcao_infinity_chest com o Re-Avaritia 1.4.2. */
final class TesteBau {
    // O Re-Avaritia so carrega pastas de dono com UUID valido (versao 1-5).
    private static final UUID DONO = UUID.fromString("3f1d2c4b-5a6e-4f70-8a9b-0c1d2e3f4a5b");
    private static final UUID BAU_SINTETICO = UUID.fromString("7a8b9c0d-1e2f-4a3b-9c4d-5e6f7a8b9c0d");

    private TesteBau() {
    }

    private static ServerChestHandler bauNovo() {
        return ServerChestManager.getInstance().getChest(DONO, UUID.randomUUID());
    }

    private static int maiorChave(ServerChestHandler bau) {
        return bau.storageItems.keySet().stream().mapToInt(String::length).max().orElse(0);
    }

    /** Coloca o mesmo item com NBT n vezes, lendo o bau entre uma e outra (como a tela aberta faz). */
    static int ciclos(CommandContext<CommandSourceStack> ctx) {
        int n = IntegerArgumentType.getInteger(ctx, "n");
        ServerChestHandler bau = bauNovo();
        // Papel (nao tem dano: espadas ganham {Damage:0} ao serem criadas e nunca ficam sem NBT).
        ItemStack papel = new ItemStack(Items.PAPER);
        papel.setHoverName(Component.literal("papel de teste"));
        for (int i = 0; i < n; i++) {
            bau.insertItem(0, papel.copy(), false);
            bau.getStackInSlot(27);
        }
        // Um item sem NBT do mesmo tipo: deveria ficar com a chave "minecraft:paper".
        bau.insertItem(0, new ItemStack(Items.PAPER), false);
        bau.getStackInSlot(27);
        boolean semNbtCerto = bau.storageItems.containsKey("minecraft:paper");
        HLBench.LOGGER.info("HLBENCH bau ciclos n={} entradas={} maior_chave={} sem_nbt_com_chave_certa={} chaves={}",
                n, bau.storageItems.size(), maiorChave(bau), semNbtCerto,
                bau.storageItems.keySet().stream().map(k -> k.length() > 80 ? k.substring(0, 80) + "...(" + k.length() + ")" : k).toList());
        return 1;
    }

    /** Tira parte de uma pilha de itens com NBT e depois o resto: o resto ainda tem o NBT? */
    static int parcial(CommandContext<CommandSourceStack> ctx) {
        ServerChestHandler bau = bauNovo();
        ItemStack papel = new ItemStack(Items.PAPER, 10);
        papel.setHoverName(Component.literal("papel com nome"));
        String chave = StorageUtils.getNbtItemId(papel);
        bau.insertItem(0, papel.copy(), false);
        ItemStack primeiro = bau.saveTakeItem(chave, 3);
        ItemStack resto = bau.takeItem(chave, 7);
        HLBench.LOGGER.info("HLBENCH bau parcial primeiro={}x com_nome={} resto={}x com_nome={}",
                primeiro.getCount(), primeiro.hasCustomHoverName(), resto.getCount(), resto.hasCustomHoverName());
        return 1;
    }

    /**
     * Grava um bau sintetico parecido com o do save real: "entradas" itens, dos quais "comNbt" com NBT e
     * chaves infladas (id#h, id#h#h, ... ate "inflacao" vezes, todas com o mesmo NBT), e 2 itens sem NBT
     * guardados com uma chave "#hash". Carregado no proximo inicio do servidor.
     */
    static int gerar(CommandContext<CommandSourceStack> ctx) {
        try {
            return gerarIo(ctx);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static int gerarIo(CommandContext<CommandSourceStack> ctx) throws IOException {
        int entradas = IntegerArgumentType.getInteger(ctx, "entradas");
        int comNbt = IntegerArgumentType.getInteger(ctx, "com_nbt");
        int inflacao = IntegerArgumentType.getInteger(ctx, "inflacao");
        List<Item> itens = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item != Items.AIR && ForgeRegistries.ITEMS.getKey(item).getNamespace().equals("minecraft")) {
                itens.add(item);
            }
            if (itens.size() == entradas) {
                break;
            }
        }
        CompoundTag itensTag = new CompoundTag();
        CompoundTag nbtTag = new CompoundTag();
        int total = 0;
        for (int i = 0; i < itens.size(); i++) {
            String id = ForgeRegistries.ITEMS.getKey(itens.get(i)).toString();
            if (i < comNbt) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("hl", i);
                tag.putString("lore", "x".repeat(200));
                String h = StorageUtils.hashNbt(tag);
                StringBuilder chave = new StringBuilder(id);
                for (int k = 0; k < inflacao; k++) {
                    chave.append('#').append(h);
                    itensTag.putLong(chave.toString(), 5);
                    nbtTag.put(chave.toString(), tag.copy());
                    total++;
                }
            } else if (i < comNbt + 2) {
                itensTag.putLong(id + "#0123456789abcdef", 7); // item sem NBT com chave errada
                total++;
            } else {
                itensTag.putLong(id, 64);
                total++;
            }
        }
        CompoundTag dados = new CompoundTag();
        dados.put("items", itensTag);
        dados.put("nbtData", nbtTag);
        File pasta = ctx.getSource().getServer().getWorldPath(LevelResource.ROOT).resolve("data/avaritia/infinity_chest/" + DONO).toFile();
        pasta.mkdirs();
        File arquivo = new File(pasta, BAU_SINTETICO + ".dat");
        NbtIo.writeCompressed(dados, arquivo);
        HLBench.LOGGER.info("HLBENCH bau gerar entradas_no_arquivo={} arquivo_kb={}", total, arquivo.length() / 1024);
        return 1;
    }

    /** Mede o bau sintetico carregado: entradas, tempo do updateItemKeys e tamanho do pacote de estado. */
    static int medir(CommandContext<CommandSourceStack> ctx) {
        try {
            return medirIo(ctx);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static int medirIo(CommandContext<CommandSourceStack> ctx) throws IOException {
        ServerChestHandler bau = ServerChestManager.getInstance().getChest(DONO, BAU_SINTETICO);
        int repeticoes = 50;
        long inicio = System.nanoTime();
        for (int i = 0; i < repeticoes; i++) {
            bau.updateItemKeys();
        }
        double msChaves = (System.nanoTime() - inicio) / 1e6 / repeticoes;
        inicio = System.nanoTime();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(bau.buildData(), new DataOutputStream(bytes));
        double msPacote = (System.nanoTime() - inicio) / 1e6;
        long comNbt = bau.storageItems.keySet().stream().filter(k -> k.contains("#")).count();
        int comTag = 0;
        for (int slot = 27; slot < 27 + bau.storageItems.size(); slot++) {
            if (bau.getStackInSlot(slot).hasTag()) {
                comTag++;
            }
        }
        HLBench.LOGGER.info("HLBENCH bau medir entradas={} com_hash={} slots_com_nbt={} maior_chave={} updateItemKeys_ms={} pacote_kb={} pacote_ms={}",
                bau.storageItems.size(), comNbt, comTag, maiorChave(bau), String.format("%.3f", msChaves),
                bytes.size() / 1024, String.format("%.2f", msPacote));
        return 1;
    }
}
