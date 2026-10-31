package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.LimpezaItens;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/** Testes do modulo limpeza_itens: itens espalhados com cada tipo de protecao. */
final class TesteLimpeza {
    private TesteLimpeza() {
    }

    private static final String TIPO = "hlbench_tipo";

    /** n itens em lado x lado chunks em volta do spawn; 1 em cada 50 de cada tipo protegido. */
    static int espalhar(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        int n = IntegerArgumentType.getInteger(ctx, "n");
        int lado = IntegerArgumentType.getInteger(ctx, "chunks_lado");
        BlockPos spawn = level.getSharedSpawnPos();
        Random r = new Random(7);
        int[] contagem = new int[7];
        for (int i = 0; i < n; i++) {
            int x = spawn.getX() - lado * 8 + r.nextInt(lado * 16);
            int z = spawn.getZ() - lado * 8 + r.nextInt(lado * 16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int tipo = i % 50 < 5 ? i % 50 : 6;
            ItemStack stack;
            switch (tipo) {
                case 0 -> {
                    stack = new ItemStack(Items.IRON_SWORD);
                    stack.enchant(Enchantments.SHARPNESS, 1);
                }
                case 1 -> {
                    stack = new ItemStack(Items.DIRT);
                    stack.setHoverName(Component.literal("terra com nome"));
                }
                case 2 -> stack = new ItemStack(Items.NETHER_STAR);
                case 3, 4, 5 -> stack = new ItemStack(Items.COBBLESTONE, 3);
                default -> stack = new ItemStack(Items.COBBLESTONE, 1 + r.nextInt(16));
            }
            if (tipo == 4) {
                level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.HOPPER.defaultBlockState());
                y++;
            }
            ItemEntity item = new ItemEntity(level, x + 0.5, y + 0.2, z + 0.5, stack);
            item.setDeltaMovement(0, 0, 0);
            item.setNoGravity(true);
            // Sem jogadores o servidor nao processa entidades e a idade nao sobe: grava uma idade de 50 s.
            net.minecraft.nbt.CompoundTag dados = item.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            dados.putShort("Age", (short) 1000);
            item.load(dados);
            if (tipo == 3) {
                item.getPersistentData().putBoolean(LimpezaItens.MARCA_MORTE, true);
            }
            item.getPersistentData().putInt(TIPO, tipo);
            level.addFreshEntity(item);
            contagem[tipo]++;
        }
        HLBench.LOGGER.info("HLBENCH itens espalhar n={} chunks={} encantados={} com_nome={} nether_star={} morte={} sobre_funil={} comuns={}",
                n, lado * lado, contagem[0], contagem[1], contagem[2], contagem[3], contagem[4], contagem[6]);
        return 1;
    }

    /** Itens largados agora (devem ser protegidos pela idade minima). */
    static int novos(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        int n = IntegerArgumentType.getInteger(ctx, "n");
        BlockPos spawn = level.getSharedSpawnPos();
        for (int i = 0; i < n; i++) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX() + i, spawn.getZ());
            ItemEntity item = new ItemEntity(level, spawn.getX() + i + 0.5, y + 0.2, spawn.getZ() + 0.5, new ItemStack(Items.COBBLESTONE));
            item.setNoGravity(true);
            item.setDeltaMovement(0, 0, 0);
            item.getPersistentData().putInt(TIPO, 5);
            level.addFreshEntity(item);
        }
        HLBench.LOGGER.info("HLBENCH itens novos n={}", n);
        return 1;
    }

    /** Conta os itens que sobraram, por tipo. */
    static int contar(CommandContext<CommandSourceStack> ctx) {
        int[] c = new int[7];
        int total = 0;
        for (Entity e : ctx.getSource().getServer().overworld().getAllEntities()) {
            if (e instanceof ItemEntity item && item.getPersistentData().contains(TIPO)) {
                c[item.getPersistentData().getInt(TIPO)]++;
                total++;
            }
        }
        HLBench.LOGGER.info("HLBENCH itens contar total={} encantados={} com_nome={} nether_star={} morte={} sobre_funil={} novos={} comuns={}",
                total, c[0], c[1], c[2], c[3], c[4], c[5], c[6]);
        return 1;
    }
}
