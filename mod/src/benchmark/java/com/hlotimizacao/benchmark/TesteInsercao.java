package com.hlotimizacao.benchmark;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.recursos.Insercao;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import committee.nova.mods.avaritia.core.chest.ServerChestHandler;
import committee.nova.mods.avaritia.core.chest.ServerChestManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Testes de insercao em inventarios grandes: equivalencia, velocidade, orcamento e produtores no mundo. */
final class TesteInsercao {
    private TesteInsercao() {
    }

    // ---------- inventario que copia no getStackInSlot (como o Infinity Chest) ----------

    static final class Copiando extends ItemStackHandler {
        Copiando(int slots) {
            super(slots);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return super.getStackInSlot(slot).copy();
        }
    }

    // ---------- blocos de teste ----------

    static final DeferredRegister<Block> BLOCOS = DeferredRegister.create(ForgeRegistries.BLOCKS, "hlbench");
    static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "hlbench");
    static final RegistryObject<Block> PRODUTOR = BLOCOS.register("produtor", () -> new BlocoProdutor());
    static final RegistryObject<Block> BAU_GRANDE = BLOCOS.register("bau_grande", () -> new BlocoBauGrande());
    @SuppressWarnings("ConstantConditions")
    static final RegistryObject<BlockEntityType<Produtor>> PRODUTOR_BE = BES.register("produtor",
            () -> BlockEntityType.Builder.of(Produtor::new, PRODUTOR.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    static final RegistryObject<BlockEntityType<BauGrande>> BAU_GRANDE_BE = BES.register("bau_grande",
            () -> BlockEntityType.Builder.of(BauGrande::new, BAU_GRANDE.get()).build(null));

    /** Pilhas que cada produtor despeja por tick (0 = parado). */
    static volatile int pilhasPorTick;

    static final class BlocoProdutor extends Block implements EntityBlock {
        BlocoProdutor() {
            super(BlockBehaviour.Properties.of().strength(1f));
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Produtor(pos, state);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> tipo) {
            return level.isClientSide || tipo != PRODUTOR_BE.get() ? null : (l, p, s, be) -> ((Produtor) be).tickServidor();
        }
    }

    /** Como uma Mob Farm: todo tick despeja pilhas no inventario vizinho pelo insertItemStacked do Forge. */
    static final class Produtor extends BlockEntity {
        private final Random r = new Random();

        Produtor(BlockPos pos, BlockState state) {
            super(PRODUTOR_BE.get(), pos, state);
        }

        void tickServidor() {
            if (pilhasPorTick <= 0 || level == null) {
                return;
            }
            IItemHandler h = null;
            for (Direction d : Direction.values()) {
                BlockEntity alvo = level.getBlockEntity(worldPosition.relative(d));
                if (alvo != null && !(alvo instanceof Produtor)) {
                    h = alvo.getCapability(ForgeCapabilities.ITEM_HANDLER, d.getOpposite()).orElse(null);
                    if (h != null) {
                        break;
                    }
                }
            }
            if (h == null) {
                return;
            }
            for (int i = 0; i < pilhasPorTick; i++) {
                ItemHandlerHelper.insertItemStacked(h, saidaDeFarm(r), false);
            }
        }
    }

    static final class BlocoBauGrande extends Block implements EntityBlock {
        BlocoBauGrande() {
            super(BlockBehaviour.Properties.of().strength(1f));
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new BauGrande(pos, state);
        }
    }

    /** Inventario de 5.000 slots que copia no getStackInSlot. */
    static final class BauGrande extends BlockEntity {
        final Copiando inventario = new Copiando(5000);
        private final LazyOptional<IItemHandler> cap = LazyOptional.of(() -> inventario);

        BauGrande(BlockPos pos, BlockState state) {
            super(BAU_GRANDE_BE.get(), pos, state);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> c, @Nullable Direction lado) {
            return c == ForgeCapabilities.ITEM_HANDLER ? cap.cast() : super.getCapability(c, lado);
        }
    }

    // ---------- itens ----------

    private static final Item[] FARM = {Items.ROTTEN_FLESH, Items.BONE, Items.ARROW, Items.STRING, Items.SPIDER_EYE, Items.GUNPOWDER,
            Items.ENDER_PEARL, Items.BLAZE_ROD, Items.SLIME_BALL, Items.IRON_INGOT};

    /** Saida tipica de uma farm de mobs: 10 tipos de item, de 1 a 4 por pilha. */
    static ItemStack saidaDeFarm(Random r) {
        return new ItemStack(FARM[r.nextInt(FARM.length)], 1 + r.nextInt(4));
    }

    static ItemStack papelComNome(int i, int quantidade) {
        ItemStack s = new ItemStack(Items.PAPER, quantidade);
        s.setHoverName(Component.literal("papel " + i));
        return s;
    }

    /** Mistura: itens de farm, papeis com nome (NBT), espadas (nao empilham). */
    static ItemStack aleatoria(Random r) {
        int t = r.nextInt(10);
        if (t < 6) {
            return new ItemStack(FARM[r.nextInt(FARM.length)], 1 + r.nextInt(64));
        }
        if (t < 9) {
            return papelComNome(r.nextInt(40), 1 + r.nextInt(64));
        }
        return new ItemStack(Items.IRON_SWORD);
    }

    private static boolean igual(ItemStack a, ItemStack b) {
        return ItemStack.matches(a, b);
    }

    // ---------- 1. equivalencia ----------

    static int equivalencia(CommandContext<CommandSourceStack> ctx) {
        int falhas = 0;
        for (int tamanho : new int[]{27, 108, 1000, 5000}) {
            for (boolean comRetiradas : new boolean[]{false, true}) {
                Random r = new Random(tamanho * 7L + (comRetiradas ? 1 : 0));
                IItemHandler original = new Copiando(tamanho);
                IItemHandler rapido = new Copiando(tamanho);
                Insercao.limparCaches();
                int restosDiferentes = 0;
                // Conservacao: o que entrou - o que saiu = o que esta no inventario (para cada lado).
                long entrouA = 0;
                long entrouB = 0;
                long saiuA = 0;
                long saiuB = 0;
                int recusasIndevidas = 0;
                for (int i = 0; i < 10_000; i++) {
                    if (comRetiradas && r.nextInt(10) == 0) {
                        int slot = r.nextInt(tamanho);
                        int qtd = 1 + r.nextInt(64);
                        saiuA += original.extractItem(slot, qtd, false).getCount();
                        saiuB += rapido.extractItem(slot, qtd, false).getCount();
                        continue;
                    }
                    ItemStack pilha = aleatoria(r);
                    boolean simular = r.nextInt(20) == 0;
                    Insercao.originalNoTeste = true;
                    ItemStack restoA = ItemHandlerHelper.insertItemStacked(original, pilha.copy(), simular);
                    Insercao.originalNoTeste = false;
                    ItemStack restoB = ItemHandlerHelper.insertItemStacked(rapido, pilha.copy(), simular);
                    if (!igual(restoA, restoB)) {
                        restosDiferentes++;
                    }
                    if (!simular && !restoB.isEmpty()) {
                        // O original aceitaria mais no mesmo inventario? (seria uma recusa indevida da insercao rapida)
                        Insercao.originalNoTeste = true;
                        ItemStack deNovo = ItemHandlerHelper.insertItemStacked(rapido, restoB.copy(), true);
                        Insercao.originalNoTeste = false;
                        if (deNovo.getCount() < restoB.getCount()) {
                            recusasIndevidas++;
                        }
                    }
                    if (!simular) {
                        entrouA += pilha.getCount() - restoA.getCount();
                        entrouB += pilha.getCount() - restoB.getCount();
                    }
                }
                int slotsDiferentes = 0;
                Map<String, Integer> totalA = new HashMap<>();
                Map<String, Integer> totalB = new HashMap<>();
                for (int s = 0; s < tamanho; s++) {
                    ItemStack a = original.getStackInSlot(s);
                    ItemStack b = rapido.getStackInSlot(s);
                    if (!igual(a, b)) {
                        slotsDiferentes++;
                    }
                    if (!a.isEmpty()) {
                        totalA.merge(a.getItem() + String.valueOf(a.getTag()), a.getCount(), Integer::sum);
                    }
                    if (!b.isEmpty()) {
                        totalB.merge(b.getItem() + String.valueOf(b.getTag()), b.getCount(), Integer::sum);
                    }
                }
                boolean totaisIguais = totalA.equals(totalB);
                long noA = totalA.values().stream().mapToLong(Integer::longValue).sum();
                long noB = totalB.values().stream().mapToLong(Integer::longValue).sum();
                boolean conservaA = entrouA - saiuA == noA;
                boolean conservaB = entrouB - saiuB == noB;
                // Sem retiradas: tem que ser identico slot a slot. Com retiradas feitas por fora, o cache pode por o
                // item num slot diferente; ai o que vale e nao criar nem perder item.
                boolean ok = recusasIndevidas == 0 && (comRetiradas ? conservaA && conservaB
                        : restosDiferentes == 0 && totaisIguais && slotsDiferentes == 0);
                if (!ok) {
                    falhas++;
                }
                HLBench.LOGGER.info("HLBENCH insercao equivalencia {} slots={} retiradas={} restos_diferentes={} slots_diferentes={} totais_iguais={} "
                                + "conservacao_original={} conservacao_rapida={} recusas_indevidas={}",
                        ok ? "ok" : "FALHA", tamanho, comRetiradas, restosDiferentes, slotsDiferentes, totaisIguais, conservaA, conservaB, recusasIndevidas);
            }
        }
        Insercao.originalNoTeste = false;
        HLBench.LOGGER.info("HLBENCH insercao equivalencia falhas={}", falhas);
        return 1;
    }

    // ---------- 2. velocidade ----------

    private static double medir(IItemHandler h, List<ItemStack> pilhas, boolean original) {
        Insercao.originalNoTeste = original;
        long inicio = System.nanoTime();
        for (ItemStack p : pilhas) {
            ItemHandlerHelper.insertItemStacked(h, p.copy(), false);
        }
        Insercao.originalNoTeste = false;
        return (System.nanoTime() - inicio) / 1e6;
    }

    /** 5.000 slots que copiam, quase cheios de papeis com nome (cada um diferente), e mais os 10 itens de farm. */
    private static Copiando preparado() {
        Copiando h = new Copiando(5000);
        for (int i = 0; i < 4900; i++) {
            h.setStackInSlot(i, papelComNome(i, 1));
        }
        for (int i = 0; i < FARM.length; i++) {
            h.setStackInSlot(4900 + i, new ItemStack(FARM[i], 1));
        }
        return h;
    }

    static int velocidade(CommandContext<CommandSourceStack> ctx) {
        Random r = new Random(5);
        List<ItemStack> farm = new ArrayList<>();
        List<ItemStack> variados = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            farm.add(saidaDeFarm(r));
            variados.add(papelComNome(r.nextInt(4900), 1));
        }
        medir(preparado(), farm, true); // aquece o JIT
        Insercao.limparCaches();
        medir(preparado(), farm, false);
        Insercao.limparCaches();
        double farmOriginal = medir(preparado(), farm, true);
        double farmRapido = medir(preparado(), farm, false);
        Insercao.limparCaches();
        double variadosOriginal = medir(preparado(), variados, true);
        double variadosRapido = medir(preparado(), variados, false);
        HLBench.LOGGER.info(String.format(java.util.Locale.ROOT,
                "HLBENCH insercao velocidade 1000 pilhas em 5.000 slots que copiam | 10 tipos de farm: original %.1f ms, rapida %.1f ms (%.0fx) | "
                        + "1000 tipos aleatorios entre 4.900: original %.1f ms, rapida %.1f ms (%.1fx) | rapidas=%d varreduras=%d",
                farmOriginal, farmRapido, farmOriginal / farmRapido, variadosOriginal, variadosRapido, variadosOriginal / variadosRapido,
                Insercao.RAPIDAS.get(), Insercao.VARREDURAS.get()));
        // Atalho do Infinity Chest: 5.000 entradas, mesmas pilhas, comparando o conteudo final.
        UUID dono = UUID.fromString("3f1d2c4b-5a6e-4f70-8a9b-0c1d2e3f4a5b");
        ServerChestHandler a = ServerChestManager.getInstance().getChest(dono, UUID.randomUUID());
        ServerChestHandler b = ServerChestManager.getInstance().getChest(dono, UUID.randomUUID());
        for (int i = 0; i < 5000; i++) {
            a.insertItem(0, papelComNome(i, 1), false);
            b.insertItem(0, papelComNome(i, 1), false);
        }
        a.updateItemKeys();
        b.updateItemKeys();
        List<ItemStack> paraBau = new ArrayList<>(farm.subList(0, 200));
        for (int i = 0; i < 200; i++) {
            paraBau.add(papelComNome(r.nextInt(5000), 1 + r.nextInt(8)));
        }
        double bauOriginal = medir(a, paraBau, true);
        double bauAtalho = medir(b, paraBau, false);
        boolean mesmoConteudo = new HashMap<>(a.storageItems).equals(new HashMap<>(b.storageItems));
        HLBench.LOGGER.info(String.format(java.util.Locale.ROOT,
                "HLBENCH insercao infinity_chest 400 pilhas num bau de 5.000 entradas: original %.1f ms, atalho %.2f ms (%.0fx) mesmo_conteudo=%s",
                bauOriginal, bauAtalho, bauOriginal / Math.max(0.001, bauAtalho), mesmoConteudo));
        return 1;
    }

    // ---------- 3. orcamento ----------

    static int orcamento(CommandContext<CommandSourceStack> ctx) {
        Copiando cheio = new Copiando(5000);
        for (int i = 0; i < 5000; i++) {
            cheio.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        List<ItemStack> pilhas = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            pilhas.add(new ItemStack(Items.BONE, 16));
        }
        String antes = Config.valorTexto("insercao_rapida.orcamento");
        for (boolean comOrcamento : new boolean[]{false, true}) {
            Config.gravarValor("insercao_rapida.orcamento", Boolean.toString(comOrcamento));
            Insercao.limparCaches();
            int perdidos = 0;
            long inicio = System.nanoTime();
            for (ItemStack p : pilhas) {
                ItemStack resto = ItemHandlerHelper.insertItemStacked(cheio, p.copy(), false);
                perdidos += p.getCount() - resto.getCount();
            }
            double ms = (System.nanoTime() - inicio) / 1e6;
            HLBench.LOGGER.info(String.format(java.util.Locale.ROOT,
                    "HLBENCH insercao orcamento=%s 100 pilhas num inventario cheio de 5.000 slots: %.2f ms, itens_que_sumiram=%d, recusadas_total=%d",
                    comOrcamento, ms, perdidos, Insercao.RECUSADAS.get()));
        }
        Config.gravarValor("insercao_rapida.orcamento", antes.isEmpty() ? "false" : antes);
        return 1;
    }

    // ---------- 4. no mundo ----------

    /**
     * Monta, longe do spawn: um Infinity Chest com 5.000 entradas e um bau grande de 5.000 slots (quase
     * cheio), cada um com 4 produtores em volta (como 4 Mob Farms).
     */
    static int montar(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        BlockPos base = level.getSharedSpawnPos().above(60);
        UUID dono = UUID.fromString("3f1d2c4b-5a6e-4f70-8a9b-0c1d2e3f4a5b");
        BlockPos[] alvos = {base, base.south(10)};
        for (int a = 0; a < alvos.length; a++) {
            BlockPos alvo = alvos[a];
            if (a == 0) {
                level.setBlockAndUpdate(alvo, ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("avaritia:infinity_chest")).defaultBlockState());
                if (level.getBlockEntity(alvo) instanceof committee.nova.mods.avaritia.common.tile.InfinityChestTile bau) {
                    bau.setOwner(dono);
                    bau.setChannelId(UUID.fromString("7a8b9c0d-1e2f-4a3b-9c4d-5e6f7a8b9c11"));
                    ServerChestHandler canal = bau.getChannel();
                    for (int i = 0; i < 5000; i++) {
                        canal.insertItem(0, papelComNome(i, 1), false);
                    }
                    canal.updateItemKeys();
                }
            } else {
                level.setBlockAndUpdate(alvo, BAU_GRANDE.get().defaultBlockState());
                if (level.getBlockEntity(alvo) instanceof BauGrande bau) {
                    for (int i = 0; i < 4900; i++) {
                        bau.inventario.setStackInSlot(i, papelComNome(i, 1));
                    }
                }
            }
            // 4 produtores em volta do alvo (como 4 Mob Farms), cada um despeja no vizinho com inventario.
            for (BlockPos pos : new BlockPos[]{alvo.west(), alvo.east(), alvo.north(), alvo.above()}) {
                level.setBlockAndUpdate(pos, PRODUTOR.get().defaultBlockState());
            }
        }
        HLBench.LOGGER.info("HLBENCH insercao montado em {}", base.toShortString());
        return 1;
    }

    static int produzir(CommandContext<CommandSourceStack> ctx) {
        pilhasPorTick = IntegerArgumentType.getInteger(ctx, "pilhas_por_tick");
        HLBench.LOGGER.info("HLBENCH insercao produtores pilhas_por_tick={}", pilhasPorTick);
        return 1;
    }
}
