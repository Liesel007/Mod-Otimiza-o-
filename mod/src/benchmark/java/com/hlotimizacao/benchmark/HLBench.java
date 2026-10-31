package com.hlotimizacao.benchmark;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Random;

/** Comandos /hlbench para medir o antes e depois dos modulos do HL Otimizacao. */
@Mod("hlbench")
public class HLBench {
    static final Logger LOGGER = LoggerFactory.getLogger("HLBENCH");

    static final net.minecraftforge.registries.DeferredRegister<net.minecraft.world.item.Item> ITENS =
            net.minecraftforge.registries.DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, "hlbench");
    static final net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> ITEM_RUIM =
            ITENS.register("item_ruim", TesteTooltip.ItemRuim::new);

    public HLBench() {
        ITENS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.addListener(this::registrarComandos);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(FpsCliente::aoTick);
            TesteTooltip.registrarOuvintes();
        }
    }

    private void registrarComandos(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hlbench")
                .then(Commands.literal("salvar")
                        .then(Commands.argument("kb", IntegerArgumentType.integer(1))
                                .then(Commands.argument("repeticoes", IntegerArgumentType.integer(1))
                                        .executes(c -> seguro(c, HLBench::salvar)))))
                .then(Commands.literal("gatilho")
                        .then(Commands.argument("chamadas", IntegerArgumentType.integer(1))
                                .executes(c -> seguro(c, HLBench::gatilho))))
                .then(Commands.literal("mobs")
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                .executes(c -> seguro(c, HLBench::criarMobs))))
                .then(Commands.literal("contar").executes(c -> seguro(c, HLBench::contarMobs)))
                .then(Commands.literal("esvaziar").executes(c -> seguro(c, HLBench::esvaziar)))
                .then(Commands.literal("travar").then(Commands.argument("ms", IntegerArgumentType.integer(1))
                        .executes(c -> seguro(c, TesteLag::travar))))
                .then(Commands.literal("gerar").then(Commands.argument("raio", IntegerArgumentType.integer(0, 20))
                        .executes(c -> seguro(c, TesteLag::gerar))))
                .then(Commands.literal("nomes").executes(c -> seguro(c, TesteLag::nomes)))
                .then(Commands.literal("pausa").then(Commands.argument("parado_ms", IntegerArgumentType.integer(1))
                        .then(Commands.argument("ocupado_ms", IntegerArgumentType.integer(1)).executes(c -> seguro(c, TesteLag013::pausa)))))
                .then(Commands.literal("ocupado").then(Commands.argument("ms", IntegerArgumentType.integer(1))
                        .executes(c -> seguro(c, TesteLag013::ocupado))))
                .then(Commands.literal("salvarlento").then(Commands.argument("ms", IntegerArgumentType.integer(1))
                        .executes(c -> seguro(c, TesteLag013::salvarLento))))
                .then(Commands.literal("permitido").executes(c -> seguro(c, TesteLag013::conferirPermitido)))
                .then(Commands.literal("itens")
                        .then(Commands.literal("espalhar").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .then(Commands.argument("chunks_lado", IntegerArgumentType.integer(1, 30))
                                        .executes(c -> seguro(c, TesteLimpeza::espalhar)))))
                        .then(Commands.literal("novos").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(c -> seguro(c, TesteLimpeza::novos))))
                        .then(Commands.literal("contar").executes(c -> seguro(c, TesteLimpeza::contar))))
                .then(Commands.literal("bau")
                        .then(Commands.literal("ciclos").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(c -> seguro(c, TesteBau::ciclos))))
                        .then(Commands.literal("parcial").executes(c -> seguro(c, TesteBau::parcial)))
                        .then(Commands.literal("gerar").then(Commands.argument("entradas", IntegerArgumentType.integer(1))
                                .then(Commands.argument("com_nbt", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("inflacao", IntegerArgumentType.integer(1))
                                                .executes(c -> seguro(c, TesteBau::gerar))))))
                        .then(Commands.literal("medir").executes(c -> seguro(c, TesteBau::medir))))
                .then(Commands.literal("pendentes").then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                        .then(Commands.argument("mb", IntegerArgumentType.integer(1))
                                .executes(c -> seguro(c, TesteLag::pendentes)))))
                .then(Commands.literal("salvamento")
                        .then(Commands.literal("preparar").executes(c -> seguro(c, TesteSalvamento::preparar)))
                        .then(Commands.literal("verificar").executes(c -> seguro(c, TesteSalvamento::verificar)))));
    }

    /** Tempo que a thread principal fica parada salvando N KB de dados (SavedData), como no autosave. */
    private static int salvar(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int kb = IntegerArgumentType.getInteger(ctx, "kb");
        int repeticoes = IntegerArgumentType.getInteger(ctx, "repeticoes");
        Random random = new Random(42);
        long[] tempos = new long[repeticoes];
        for (int r = 0; r < repeticoes; r++) {
            // Dados aleatorios (pouco compressiveis), divididos em 16 entradas.
            CompoundTag tag = new CompoundTag();
            int longsPorEntrada = Math.max(1, kb * 1024 / 8 / 16);
            for (int i = 0; i < 16; i++) {
                long[] dados = new long[longsPorEntrada];
                for (int j = 0; j < dados.length; j++) {
                    dados[j] = random.nextLong();
                }
                tag.put("bloco" + i, new LongArrayTag(dados));
            }
            server.getCommandStorage().set(new ResourceLocation("hlbench", "dados"), tag);
            long inicio = System.nanoTime();
            server.overworld().getDataStorage().save();
            tempos[r] = System.nanoTime() - inicio;
        }
        Arrays.sort(tempos);
        double mediana = tempos[repeticoes / 2] / 1e6;
        double media = Arrays.stream(tempos).average().orElse(0) / 1e6;
        LOGGER.info("HLBENCH salvar kb={} repeticoes={} mediana_ms={} media_ms={} max_ms={}",
                kb, repeticoes, String.format("%.2f", mediana), String.format("%.2f", media), String.format("%.2f", tempos[repeticoes - 1] / 1e6));
        return 1;
    }

    /** Custo de N chamadas do gatilho de inventario com pilha esvaziada (o que acontece ao usar/mover itens). */
    private static int gatilho(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int chamadas = IntegerArgumentType.getInteger(ctx, "chamadas");
        // Jogador comum (FakePlayer nao recebe conquistas no Forge), com a conquista de teste
        // trinta_vazios ja obtida: sobram so as conquistas do vanilla, como num mundo normal.
        net.minecraft.server.level.ServerPlayer jogador = novoJogador(level, "hlbench_gatilho");
        net.minecraft.advancements.Advancement conquistaTeste = level.getServer().getAdvancements().getAdvancement(new ResourceLocation("hlbench", "trinta_vazios"));
        if (conquistaTeste != null) {
            jogador.getAdvancements().award(conquistaTeste, "vazios");
        }
        Inventory inventario = jogador.getInventory();
        for (int i = 0; i < inventario.getContainerSize(); i++) {
            inventario.setItem(i, new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.DIRT, 1 + i));
        }
        for (int i = 0; i < chamadas / 10; i++) {
            CriteriaTriggers.INVENTORY_CHANGED.trigger(jogador, inventario, ItemStack.EMPTY);
        }
        long inicio = System.nanoTime();
        for (int i = 0; i < chamadas; i++) {
            CriteriaTriggers.INVENTORY_CHANGED.trigger(jogador, inventario, ItemStack.EMPTY);
        }
        long total = System.nanoTime() - inicio;
        LOGGER.info("HLBENCH gatilho chamadas={} total_ms={} ns_por_chamada={}",
                chamadas, String.format("%.1f", total / 1e6), String.format("%.1f", (double) total / chamadas));
        return 1;
    }

    /** Cria N zumbis numa plataforma, cada um com um item no chao para pegar. */
    private static int criarMobs(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int quantidade = IntegerArgumentType.getInteger(ctx, "quantidade");
        int lado = (int) Math.ceil(Math.sqrt(quantidade));
        for (int x = 0; x < lado * 3; x++) {
            for (int z = 0; z < lado * 3; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.GLASS.defaultBlockState());
            }
        }
        for (int i = 0; i < quantidade; i++) {
            BlockPos pos = new BlockPos((i % lado) * 3 + 1, 200, (i / lado) * 3 + 1);
            Zombie zumbi = EntityType.ZOMBIE.create(level);
            if (zumbi == null) {
                continue;
            }
            zumbi.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            zumbi.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
            zumbi.setCanPickUpLoot(true);
            zumbi.setInvulnerable(true);
            zumbi.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            level.addFreshEntity(zumbi);
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, new ItemStack(Items.IRON_SWORD)));
        }
        LOGGER.info("HLBENCH mobs criados={}", quantidade);
        return 1;
    }

    /** Conta quantos zumbis pegaram item e quantos ficaram presos (persistentes) no mundo. */
    private static int contarMobs(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int total = 0;
        int comItem = 0;
        int persistentes = 0;
        for (Zombie zumbi : level.getEntitiesOfClass(Zombie.class, new AABB(-1000, -64, -1000, 1000, 320, 1000))) {
            total++;
            if (!zumbi.getMainHandItem().isEmpty()) {
                comItem++;
            }
            if (((Mob) zumbi).isPersistenceRequired()) {
                persistentes++;
            }
        }
        LOGGER.info("HLBENCH contar zumbis={} com_item={} persistentes={}", total, comItem, persistentes);
        return 1;
    }

    /**
     * Correcao: a conquista de teste hlbench:trinta_vazios ("pelo menos 30 slots vazios") so pode
     * ser obtida esvaziando pilhas. Ela precisa continuar funcionando com o modulo ligado.
     */
    private static int esvaziar(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.server.level.ServerPlayer jogador = novoJogador(level, "hlbench_esvaziar");
        Inventory inventario = jogador.getInventory();
        net.minecraft.advancements.Advancement conquista = level.getServer().getAdvancements().getAdvancement(new ResourceLocation("hlbench", "trinta_vazios"));
        int tamanho = inventario.getContainerSize();
        // Enche todos os slots sem disparar o gatilho, depois dispara uma vez (0 slots vazios).
        for (int i = 0; i < tamanho; i++) {
            inventario.setItem(i, new ItemStack(Items.STONE));
        }
        CriteriaTriggers.INVENTORY_CHANGED.trigger(jogador, inventario, inventario.getItem(0));
        boolean antesDeEsvaziar = conquista != null && jogador.getAdvancements().getOrStartProgress(conquista).isDone();
        int vazios = 0;
        for (int i = 0; i < tamanho; i++) {
            inventario.setItem(i, ItemStack.EMPTY);
            vazios++;
            CriteriaTriggers.INVENTORY_CHANGED.trigger(jogador, inventario, ItemStack.EMPTY);
            if (conquista != null && jogador.getAdvancements().getOrStartProgress(conquista).isDone()) {
                break;
            }
        }
        LOGGER.info("HLBENCH esvaziar obtida_antes_de_esvaziar={}", antesDeEsvaziar);
        boolean obtida = conquista != null && jogador.getAdvancements().getOrStartProgress(conquista).isDone();
        LOGGER.info("HLBENCH esvaziar conquista_existe={} obtida={} slots_vazios_ao_obter={}", conquista != null, obtida, vazios);
        return 1;
    }

    private static int seguro(CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.Command<CommandSourceStack> comando) {
        try {
            return comando.run(ctx);
        } catch (Throwable t) {
            LOGGER.error("HLBENCH erro no comando", t);
            return 0;
        }
    }

    private static net.minecraft.server.level.ServerPlayer novoJogador(ServerLevel level, String nome) {
        net.minecraft.server.level.ServerPlayer jogador = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(nome.getBytes()), nome));
        new ConexaoMuda(jogador); // o construtor faz jogador.connection = this
        return jogador;
    }

    static final class FpsCliente {
        private static long ultimo = 0;

        static void aoTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            long agora = System.currentTimeMillis();
            if (agora - ultimo >= 5000) {
                ultimo = agora;
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                LOGGER.info("HLBENCH fps={} janela_ativa={}", mc.getFps(), mc.isWindowActive());
            }
        }
    }
}
