package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.AlcanceAtivacao;
import com.hlotimizacao.recursos.LimiteEnxame;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Testes de carga e de excecoes do alcance_ativacao, e do limite_enxame com um spawner. */
final class TesteEntidades {
    private static final String MARCA = "hlbench_carga";
    private static ServerPlayer jogador;
    private static boolean ouvinte;
    private static long medindoAteMs = -1;
    private static long inicioTickNs;
    private static final List<Long> TICKS = Collections.synchronizedList(new ArrayList<>());
    private static long puladosAntes;
    private static long medicaoInicioMs;
    private static String rotulo = "";
    private static boolean pularProximo;

    private TesteEntidades() {
    }

    private static void registrarOuvinte() {
        if (ouvinte) {
            return;
        }
        ouvinte = true;
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START) {
                inicioTickNs = System.nanoTime(); // sempre, para o primeiro tick medido nao usar um inicio velho
                return;
            }
            if (medindoAteMs < 0) {
                return;
            }
            if (pularProximo) {
                pularProximo = false; // o tick em que o comando /hlbench entidades medir rodou
                return;
            }
            TICKS.add(System.nanoTime() - inicioTickNs);
            if (System.currentTimeMillis() >= medindoAteMs) {
                medindoAteMs = -1;
                relatorio(e.getServer());
            }
        });
    }

    /** Um jogador sem cliente, colocado no mundo: carrega os chunks em volta e mantem as entidades processadas. */
    static int jogador(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        posicionar(level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
        return 1;
    }

    private static void posicionar(ServerLevel level, double x, double y, double z) {
        if (jogador == null) {
            jogador = HLBench.jogadorDeTeste(level, "JogadorCarga");
            jogador.moveTo(x, y, z, 0, 0);
            level.addNewPlayer(jogador);
        } else {
            // teleportTo espera o cliente confirmar a posicao; o jogador de teste nao tem cliente.
            jogador.moveTo(x, y, z, 0, 0);
            level.getChunkSource().move(jogador);
        }
        HLBench.LOGGER.info("HLBENCH entidades jogador em {} {} {} (jogadores no mundo: {})", (int) x, (int) y, (int) z, level.players().size());
    }

    /** n entidades do tipo num anel entre min e max blocos do jogador. */
    static int criar(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        int n = IntegerArgumentType.getInteger(ctx, "n");
        int min = IntegerArgumentType.getInteger(ctx, "min");
        int max = IntegerArgumentType.getInteger(ctx, "max");
        Random r = new Random(n * 31L + min);
        long inicio = System.nanoTime();
        int criadas = 0;
        for (int i = 0; i < n; i++) {
            double angulo = r.nextDouble() * Math.PI * 2;
            double raio = Math.sqrt(min * (double) min + r.nextDouble() * (max * (double) max - min * (double) min));
            int x = (int) (jogador.getX() + Math.cos(angulo) * raio);
            int z = (int) (jogador.getZ() + Math.sin(angulo) * raio);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            Cow vaca = EntityType.COW.create(level);
            if (vaca == null) {
                continue;
            }
            vaca.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
            vaca.getPersistentData().putBoolean(MARCA, true);
            if (level.addFreshEntity(vaca)) {
                criadas++;
            }
        }
        HLBench.LOGGER.info("HLBENCH entidades criar n={} anel={}-{} criadas={} em_ms={}", n, min, max, criadas, (System.nanoTime() - inicio) / 1_000_000);
        return 1;
    }

    static int medir(CommandContext<CommandSourceStack> ctx) {
        registrarOuvinte();
        TICKS.clear();
        puladosAntes = AlcanceAtivacao.TICKS_PULADOS.get();
        medicaoInicioMs = System.currentTimeMillis();
        rotulo = ctx.getInput();
        pularProximo = true;
        medindoAteMs = medicaoInicioMs + IntegerArgumentType.getInteger(ctx, "segundos") * 1000L;
        return 1;
    }

    private static void relatorio(MinecraftServer server) {
        List<Long> tempos;
        synchronized (TICKS) {
            tempos = new ArrayList<>(TICKS);
        }
        Collections.sort(tempos);
        double segundos = (System.currentTimeMillis() - medicaoInicioMs) / 1000.0;
        long soma = 0;
        for (long t : tempos) {
            soma += t;
        }
        int entidades = 0;
        int vacas = 0;
        for (Entity e : server.overworld().getAllEntities()) {
            entidades++;
            if (e.getPersistentData().getBoolean(MARCA)) {
                vacas++;
            }
        }
        double media = tempos.isEmpty() ? 0 : soma / 1e6 / tempos.size();
        double p95 = tempos.isEmpty() ? 0 : tempos.get((int) Math.min(tempos.size() - 1, Math.floor(tempos.size() * 0.95))) / 1e6;
        double max = tempos.isEmpty() ? 0 : tempos.get(tempos.size() - 1) / 1e6;
        long pulados = AlcanceAtivacao.TICKS_PULADOS.get() - puladosAntes;
        HLBench.LOGGER.info(String.format(java.util.Locale.ROOT,
                "HLBENCH entidades medir entidades=%d vacas_de_teste=%d ticks=%d tps=%.1f mspt_medio=%.2f mspt_p95=%.2f mspt_max=%.2f pulados_por_tick=%.0f",
                entidades, vacas, tempos.size(), tempos.size() / segundos, media, p95, max, tempos.isEmpty() ? 0 : (double) pulados / tempos.size()));
    }

    /** As excecoes do alcance de ativacao, com entidades criadas mas nao colocadas no mundo. */
    static int excecoes(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        double px = jogador.getX();
        double py = jogador.getY();
        double pz = jogador.getZ();
        int intervalo = com.hlotimizacao.config.Config.ativacaoIntervalo();
        long t = level.getGameTime();
        List<Object[]> casos = new ArrayList<>();
        casos.add(new Object[]{"vaca perto (10 blocos)", criarEm(EntityType.COW.create(level), px + 10, py, pz, t, 1), true});
        casos.add(new Object[]{"vaca longe (100), tick pulado", criarEm(EntityType.COW.create(level), px + 100, py, pz, t, 1), false});
        casos.add(new Object[]{"vaca longe (100), tick da vez", criarEm(EntityType.COW.create(level), px + 100, py, pz, t, 0), true});
        Cow nomeada = EntityType.COW.create(level);
        nomeada.setCustomName(Component.literal("Mimosa"));
        casos.add(new Object[]{"vaca longe com nome", criarEm(nomeada, px + 100, py, pz, t, 1), true});
        Wolf lobo = EntityType.WOLF.create(level);
        lobo.setOwnerUUID(UUID.randomUUID());
        lobo.setTame(true);
        casos.add(new Object[]{"lobo domesticado longe", criarEm(lobo, px + 100, py, pz, t, 1), true});
        casos.add(new Object[]{"flecha longe", criarEm(new Arrow(EntityType.ARROW, level), px + 100, py, pz, t, 1), true});
        casos.add(new Object[]{"item longe", criarEm(new ItemEntity(level, 0, 0, 0, new ItemStack(Items.DIAMOND)), px + 100, py, pz, t, 1), true});
        casos.add(new Object[]{"aldeao longe", criarEm(EntityType.VILLAGER.create(level), px + 100, py, pz, t, 1), true});
        casos.add(new Object[]{"wither longe", criarEm(new WitherBoss(EntityType.WITHER, level), px + 100, py, pz, t, 1), true});
        casos.add(new Object[]{"dragao longe", criarEm(new EnderDragon(EntityType.ENDER_DRAGON, level), px + 100, py, pz, t, 1), true});
        Zombie machucado = EntityType.ZOMBIE.create(level);
        machucado.hurtTime = 10;
        casos.add(new Object[]{"zumbi machucado longe", criarEm(machucado, px + 100, py, pz, t, 1), true});
        casos.add(new Object[]{"zumbi longe", criarEm(EntityType.ZOMBIE.create(level), px + 100, py, pz, t, 1), false});
        casos.add(new Object[]{"vaca longe na vertical (60 acima)", criarEm(EntityType.COW.create(level), px + 5, py + 60, pz, t, 1), false});
        casos.add(new Object[]{"vaca alem da simulacao (400), area de chunk loader",
                criarEm(EntityType.COW.create(level), px + 400, py, pz, t, 1), true});
        int falhas = 0;
        for (Object[] caso : casos) {
            Entity e = (Entity) caso[1];
            boolean processa = AlcanceAtivacao.deveProcessar(level, e);
            boolean ok = processa == (boolean) caso[2];
            if (!ok) {
                falhas++;
            }
            HLBench.LOGGER.info("HLBENCH ativacao {} [{}] processa={}", ok ? "ok" : "FALHA", caso[0], processa);
        }
        HLBench.LOGGER.info("HLBENCH ativacao casos={} falhas={} (intervalo={})", casos.size(), falhas, intervalo);
        return 1;
    }

    /** Posiciona e escolhe o id: resto 0 = o tick da vez (processa), resto 1 = tick pulado. */
    private static Entity criarEm(Entity e, double x, double y, double z, long gameTime, int resto) {
        e.moveTo(x, y, z, 0, 0);
        int intervalo = com.hlotimizacao.config.Config.ativacaoIntervalo();
        int id = (int) (((resto - gameTime) % intervalo + intervalo) % intervalo) + intervalo * 100_000;
        e.setId(id);
        return e;
    }

    /** Plataforma de grama no ar com um spawner de vacas e o jogador em cima (dentro do alcance do spawner). */
    static int spawner(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centro = new BlockPos(spawn.getX() + 300, 200, spawn.getZ());
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlockAndUpdate(centro.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(centro, Blocks.SPAWNER.defaultBlockState());
        if (level.getBlockEntity(centro) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(EntityType.COW, level.getRandom());
        }
        posicionar(level, centro.getX() + 4.5, centro.getY(), centro.getZ() + 4.5);
        HLBench.LOGGER.info("HLBENCH enxame spawner em {}", centro.toShortString());
        return 1;
    }

    static int contarSpawner(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centro = new BlockPos(spawn.getX() + 300, 200, spawn.getZ());
        int vacas = LimiteEnxame.contar(level, EntityType.COW, new ChunkPos(centro), 1);
        HLBench.LOGGER.info("HLBENCH enxame vacas_na_regiao={} bloqueados={} ({})", vacas, LimiteEnxame.BLOQUEADOS.get(), LimiteEnxame.porTipo());
        return 1;
    }
}
