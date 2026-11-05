package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.LimiteEnxame;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * 0.4.0: mod que cria mobs sem o MobSpawnEvent (o formigueiro do Chaos Persists faz EntityType.create +
 * addFreshEntity no randomTick). Com limite_enxame.lista=minecraft:cow=8, 40 tentativas assim devem parar em 8;
 * cria, ovo e /summon continuam passando.
 */
final class TesteFormigueiro {
    private TesteFormigueiro() {
    }

    private static BlockPos centro(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        return new BlockPos(spawn.getX() + 40, 200, spawn.getZ() + 40); // spawn chunks: sempre carregados com entidades
    }

    static int rodar(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        BlockPos c = centro(level);
        level.getChunk(c); // carrega
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                level.setBlockAndUpdate(c.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        long antes = LimiteEnxame.BLOQUEADOS_ENTRADA.get();
        int aceitas = 0;
        for (int i = 0; i < 40; i++) { // como o formigueiro: create + addFreshEntity, sem finalizeSpawn
            Cow vaca = EntityType.COW.create(level);
            vaca.moveTo(c.getX() + (i % 6), c.getY(), c.getZ() + (i / 6 % 6), 0, 0);
            if (level.addFreshEntity(vaca)) {
                aceitas++;
            }
        }
        int adultas = LimiteEnxame.contar(level, EntityType.COW, new ChunkPos(c), 1);
        // casos que nunca podem ser bloqueados
        Cow cria = EntityType.COW.create(level);
        cria.setBaby(true);
        cria.moveTo(c.getX(), c.getY(), c.getZ(), 0, 0);
        boolean criaOk = level.addFreshEntity(cria);
        boolean ovoOk = EntityType.COW.spawn(level, c.offset(1, 0, 1), MobSpawnType.SPAWN_EGG) != null;
        Cow nomeada = EntityType.COW.create(level);
        nomeada.setCustomName(net.minecraft.network.chat.Component.literal("Mimosa"));
        nomeada.moveTo(c.getX() + 2, c.getY(), c.getZ() + 2, 0, 0);
        boolean nomeadaOk = level.addFreshEntity(nomeada);
        int depois = LimiteEnxame.contar(level, EntityType.COW, new ChunkPos(c), 1);
        HLBench.LOGGER.info("HLBENCH formigueiro tentativas=40 aceitas={} na_regiao={} bloqueadas_entrada={} cria={} ovo={} nomeada={} total_depois={} ({})",
                aceitas, adultas, LimiteEnxame.BLOQUEADOS_ENTRADA.get() - antes, criaOk, ovoOk, nomeadaOk, depois, LimiteEnxame.porTipo());
        return 1;
    }

    static int reduzir(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        BlockPos c = centro(level);
        // 30 vacas acima do limite que ja existiam antes do limite: criadas com a lista vazia
        String lista = String.join(",", com.hlotimizacao.config.Config.enxameLimites().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).toList());
        com.hlotimizacao.config.Config.gravarValor("limite_enxame.lista", "");
        for (int i = 0; i < 30; i++) {
            Cow vaca = EntityType.COW.create(level);
            vaca.moveTo(c.getX() + (i % 6), c.getY(), c.getZ() + 3 + (i / 6 % 6), 0, 0);
            level.addFreshEntity(vaca);
        }
        com.hlotimizacao.config.Config.gravarValor("limite_enxame.lista", lista);
        int antes = LimiteEnxame.contar(level, EntityType.COW, new ChunkPos(c), 1);
        int previa = LimiteEnxame.reduzir(ctx.getSource().getServer(), "minecraft:cow", false);
        int removidas = LimiteEnxame.reduzir(ctx.getSource().getServer(), "minecraft:cow", true);
        int depois = LimiteEnxame.contar(level, EntityType.COW, new ChunkPos(c), 1);
        HLBench.LOGGER.info("HLBENCH reduzir antes={} previa={} removidas={} depois={}", antes, previa, removidas, depois);
        return 1;
    }
}
