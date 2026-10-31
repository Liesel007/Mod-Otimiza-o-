package io.github.mosadie.eptest;

import com.brandon3055.draconicevolution.blocks.machines.EnergyPylon;
import com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyCore;
import com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyPylon;
import com.mojang.brigadier.Command;
import io.github.mosadie.exponentialpower.entities.BaseClasses.GeneratorBE;
import io.github.mosadie.exponentialpower.entities.BaseClasses.StorageBE;
import io.github.mosadie.exponentialpower.setup.Registration;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Testes de integracao do porte. /eptest monta os cenarios perto do spawn, deixa o servidor rodar
 * {@value #TICKS} ticks conferindo os valores a cada tick e escreve o resultado no log (linhas "EPTEST").
 */
@Mod(EPTest.MODID)
public class EPTest {
    public static final String MODID = "eptest";
    static final Logger LOG = LogManager.getLogger("EPTEST");
    static final int TICKS = 200;
    static final int[] CELULAS = {1, 2, 32, 64};

    static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    static final RegistryObject<Block> SONDA = BLOCKS.register("sonda", Sonda.Bloco::new);
    @SuppressWarnings("ConstantConditions")
    static final RegistryObject<BlockEntityType<Sonda.BE>> SONDA_BE =
            BES.register("sonda", () -> BlockEntityType.Builder.of(Sonda.BE::new, SONDA.get()).build(null));

    private final List<Cenario> cenarios = new ArrayList<>();
    private final List<String> falhas = new ArrayList<>();
    private int verificacoes;
    private int ticksRestantes = -1;
    private ServerLevel nivel;

    public EPTest() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        BES.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(this::comandos);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void comandos(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("eptest").requires(s -> s.hasPermission(2)).executes(ctx -> {
            iniciar(ctx.getSource().getServer().overworld());
            return Command.SINGLE_SUCCESS;
        }));
    }

    /** Um gerador e o que esta ligado nele. */
    static final class Cenario {
        final String nome;
        final GeneratorBE.GeneratorTier tier;
        final int celulas;
        BlockPos gerador, sonda, core, cabo, armazenamento;
        boolean sondaMentirosa;
        double maiorEnergiaGerador;
        long maiorCabo = -1;

        Cenario(String nome, GeneratorBE.GeneratorTier tier, int celulas) {
            this.nome = nome;
            this.tier = tier;
            this.celulas = celulas;
        }
    }

    private void iniciar(ServerLevel level) {
        nivel = level;
        cenarios.clear();
        falhas.clear();
        verificacoes = 0;
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos base = new BlockPos(spawn.getX(), 200, spawn.getZ());
        carregar(level, base.offset(-8, 0, -8), base.offset(24 * 4 + 8, 0, 30 * 9 + 8));
        try {
            testeSimulate(level, base.offset(-4, 0, -4));
            int linha = 0;
            for (GeneratorBE.GeneratorTier tier : GeneratorBE.GeneratorTier.values()) {
                for (boolean comCabo : new boolean[]{true, false}) {
                    for (int i = 0; i < CELULAS.length; i++) {
                        BlockPos p = base.offset(i * 24, 0, linha * 30);
                        cenarios.add(montarCore(level, p, tier, CELULAS[i], comCabo));
                    }
                    linha++;
                }
            }
            for (GeneratorBE.GeneratorTier tier : GeneratorBE.GeneratorTier.values()) {
                for (boolean mentirosa : new boolean[]{false, true}) {
                    for (int i = 0; i < CELULAS.length; i++) {
                        BlockPos p = base.offset(i * 6, 0, linha * 30);
                        cenarios.add(montarSonda(level, p, tier, CELULAS[i], mentirosa));
                    }
                    linha++;
                }
            }
            BlockPos p = base.offset(0, 0, linha * 30);
            cenarios.add(montarArmazenamento(level, p, GeneratorBE.GeneratorTier.ADVANCED, Registration.ENDER_STORAGE.get()));
            cenarios.add(montarArmazenamento(level, p.offset(8, 0, 0), GeneratorBE.GeneratorTier.REGULAR, Registration.ADV_ENDER_STORAGE.get()));
            cenarios.add(montarArmazenamento(level, p.offset(16, 0, 0), GeneratorBE.GeneratorTier.ADVANCED, Registration.ADV_ENDER_STORAGE.get()));
        } catch (Throwable t) {
            LOG.error("EPTEST erro ao montar os cenarios", t);
            falhas.add("montagem: " + t);
        }
        LOG.info("EPTEST montados {} cenarios; rodando {} ticks", cenarios.size(), TICKS);
        ticksRestantes = TICKS;
    }

    private static void carregar(ServerLevel level, BlockPos a, BlockPos b) {
        for (int cx = Math.min(a.getX(), b.getX()) >> 4; cx <= Math.max(a.getX(), b.getX()) >> 4; cx++) {
            for (int cz = Math.min(a.getZ(), b.getZ()) >> 4; cz <= Math.max(a.getZ(), b.getZ()) >> 4; cz++) {
                level.getChunk(cx, cz);
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    private static Block bloco(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (b == null || b == Blocks.AIR) throw new IllegalStateException("bloco nao encontrado: " + id);
        return b;
    }

    private static GeneratorBE gerador(ServerLevel level, BlockPos p, GeneratorBE.GeneratorTier tier, int celulas) {
        Block b = tier == GeneratorBE.GeneratorTier.ADVANCED ? Registration.ADV_ENDER_GENERATOR.get() : Registration.ENDER_GENERATOR.get();
        level.setBlockAndUpdate(p, b.defaultBlockState());
        GeneratorBE g = (GeneratorBE) level.getBlockEntity(p);
        g.setItem(0, new ItemStack(Registration.ENDER_CELL.get(), celulas));
        g.setChanged();
        return g;
    }

    private static String tierNome(GeneratorBE.GeneratorTier tier) {
        return tier == GeneratorBE.GeneratorTier.ADVANCED ? "Advanced Ender Generator" : "Ender Generator";
    }

    /** Gerador -> (3 cabos universais Ultimate do Mekanism) -> Energy Pylon -> Energy Core (tier 1). */
    private Cenario montarCore(ServerLevel level, BlockPos p, GeneratorBE.GeneratorTier tier, int celulas, boolean comCabo) {
        Cenario c = new Cenario(String.format(Locale.ROOT, "%s, %d celula(s) -> %s Energy Core", tierNome(tier), celulas,
                comCabo ? "cabo Mekanism ->" : "direto no pylon do"), tier, celulas);
        c.gerador = p;
        BlockPos pylonPos = p.east(comCabo ? 4 : 1);
        BlockPos corePos = pylonPos.south(8);
        c.core = corePos;

        level.setBlockAndUpdate(corePos, bloco("draconicevolution:energy_core").defaultBlockState());
        Block estabilizador = bloco("draconicevolution:energy_core_stabilizer");
        for (BlockPos s : new BlockPos[]{corePos.east(3), corePos.west(3), corePos.north(3), corePos.south(3)}) {
            level.setBlockAndUpdate(s, estabilizador.defaultBlockState());
        }
        TileEnergyCore core = (TileEnergyCore) level.getBlockEntity(corePos);
        core.validateStructure();
        core.toggleActivation();
        if (!core.active.get()) {
            throw new IllegalStateException("o Energy Core nao ativou em " + corePos);
        }

        level.setBlockAndUpdate(pylonPos.above(), Blocks.GLASS.defaultBlockState());
        level.setBlockAndUpdate(pylonPos, bloco("draconicevolution:energy_pylon").defaultBlockState());
        TileEnergyPylon pylon = (TileEnergyPylon) level.getBlockEntity(pylonPos);
        if (!pylon.validateStructure()) {
            throw new IllegalStateException("o Energy Pylon nao encontrou o core em " + pylonPos);
        }
        pylon.ioMode.set(EnergyPylon.Mode.INPUT);

        gerador(level, p, tier, celulas);
        if (comCabo) {
            Block cabo = bloco("mekanism:ultimate_universal_cable");
            for (int i = 1; i <= 3; i++) {
                level.setBlockAndUpdate(p.east(i), cabo.defaultBlockState());
            }
            c.cabo = p.east(2);
        }
        return c;
    }

    /** Gerador -> sonda (registra cada valor oferecido). */
    private Cenario montarSonda(ServerLevel level, BlockPos p, GeneratorBE.GeneratorTier tier, int celulas, boolean mentirosa) {
        Cenario c = new Cenario(String.format(Locale.ROOT, "%s, %d celula(s) -> sonda%s", tierNome(tier), celulas,
                mentirosa ? " que devolve valores invalidos" : ""), tier, celulas);
        c.gerador = p;
        c.sonda = p.east();
        c.sondaMentirosa = mentirosa;
        level.setBlockAndUpdate(c.sonda, SONDA.get().defaultBlockState());
        ((Sonda.BE) level.getBlockEntity(c.sonda)).mentirosa = mentirosa;
        gerador(level, p, tier, celulas);
        return c;
    }

    /** Gerador (64 celulas) -> Ender Storage -> sonda. Na 1.19 o armazenamento nunca enviava (sem ticker). */
    private Cenario montarArmazenamento(ServerLevel level, BlockPos p, GeneratorBE.GeneratorTier tier, Block armazenamento) {
        Cenario c = new Cenario(String.format(Locale.ROOT, "%s, 64 celulas -> %s -> sonda", tierNome(tier),
                ForgeRegistries.BLOCKS.getKey(armazenamento).getPath()), tier, 64);
        c.gerador = p;
        c.armazenamento = p.east();
        c.sonda = p.east(2);
        level.setBlockAndUpdate(c.sonda, SONDA.get().defaultBlockState());
        level.setBlockAndUpdate(c.armazenamento, armazenamento.defaultBlockState());
        gerador(level, p, tier, 64);
        return c;
    }

    /** O receiveEnergy do armazenamento respeita simulate e ignora pedidos negativos. */
    private void testeSimulate(ServerLevel level, BlockPos p) {
        level.setBlockAndUpdate(p, Registration.ADV_ENDER_STORAGE.get().defaultBlockState());
        StorageBE s = (StorageBE) level.getBlockEntity(p);
        IEnergyStorage cap = s.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        int r1 = cap.receiveEnergy(1000, true);
        verificar("simulate: receiveEnergy(1000, simulate) devolve 1000 e nao altera a energia (" + r1 + ", " + s.energy + ")",
                r1 == 1000 && s.energy == 0);
        int r2 = cap.receiveEnergy(1000, false);
        verificar("receiveEnergy(1000) guarda 1000 (" + r2 + ", " + s.energy + ")", r2 == 1000 && s.energy == 1000);
        int r3 = cap.extractEnergy(400, true);
        verificar("simulate: extractEnergy(400, simulate) nao altera a energia (" + r3 + ", " + s.energy + ")", r3 == 400 && s.energy == 1000);
        int r4 = cap.receiveEnergy(-5, false);
        int r5 = cap.extractEnergy(-5, false);
        verificar("pedidos negativos devolvem 0 (" + r4 + ", " + r5 + ", " + s.energy + ")", r4 == 0 && r5 == 0 && s.energy == 1000);
        s.energy = Double.MAX_VALUE;
        verificar("leitura de energia acima de int fica em Integer.MAX_VALUE (" + cap.getEnergyStored() + ")",
                cap.getEnergyStored() == Integer.MAX_VALUE);
        level.removeBlock(p, false);
    }

    private void verificar(String descricao, boolean ok) {
        verificacoes++;
        if (!ok) {
            falhas.add(descricao);
            LOG.error("EPTEST FALHA {}", descricao);
        } else {
            LOG.info("EPTEST ok {}", descricao);
        }
    }

    private static boolean valido(double v) {
        return v >= 0 && !Double.isNaN(v);
    }

    private void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ticksRestantes < 0) return;
        int tickAtual = TICKS - ticksRestantes;
        for (Cenario c : cenarios) {
            try {
                conferirTick(c, tickAtual);
            } catch (Throwable t) {
                falhas.add(c.nome + ": excecao no tick " + tickAtual + ": " + t);
                LOG.error("EPTEST excecao em {}", c.nome, t);
            }
        }
        if (ticksRestantes-- == 0) {
            relatorio();
            ticksRestantes = -1;
        }
    }

    /** Confere, a cada tick, que nenhum valor ficou negativo ou NaN. */
    private void conferirTick(Cenario c, int tick) {
        GeneratorBE g = (GeneratorBE) nivel.getBlockEntity(c.gerador);
        if (!valido(g.energy) || !valido(g.currentOutput)) {
            falhas.add(c.nome + ": energia do gerador invalida no tick " + tick + ": " + g.energy + " / " + g.currentOutput);
        }
        c.maiorEnergiaGerador = Math.max(c.maiorEnergiaGerador, g.currentOutput);
        if (c.core != null) {
            TileEnergyCore core = (TileEnergyCore) nivel.getBlockEntity(c.core);
            if (core.energy.getOPStored() < 0) {
                falhas.add(c.nome + ": Energy Core negativo no tick " + tick + ": " + core.energy.getOPStored());
            }
        }
        if (c.cabo != null) {
            int lido = lerEnergia(nivel.getBlockEntity(c.cabo));
            if (lido < 0) falhas.add(c.nome + ": cabo do Mekanism com energia negativa no tick " + tick + ": " + lido);
            c.maiorCabo = Math.max(c.maiorCabo, lido);
        }
        if (c.armazenamento != null) {
            StorageBE s = (StorageBE) nivel.getBlockEntity(c.armazenamento);
            if (!valido(s.energy)) falhas.add(c.nome + ": armazenamento invalido no tick " + tick + ": " + s.energy);
        }
    }

    private static int lerEnergia(BlockEntity be) {
        if (be == null) return Integer.MIN_VALUE;
        for (Direction d : Direction.values()) {
            IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, d).orElse(null);
            if (cap != null) return cap.getEnergyStored();
        }
        return Integer.MIN_VALUE;
    }

    private void relatorio() {
        for (Cenario c : cenarios) {
            StringBuilder linha = new StringBuilder(String.format(Locale.ROOT, "%s: geracao %.6g FE/t", c.nome, c.maiorEnergiaGerador));
            boolean ok = true;
            if (c.core != null) {
                TileEnergyCore core = (TileEnergyCore) nivel.getBlockEntity(c.core);
                long op = core.energy.getOPStored();
                linha.append(String.format(Locale.ROOT, "; core ativo=%s, guardado=%d de %d OP", core.active.get(), op, core.energy.getMaxOPStored()));
                ok &= core.active.get() && op > 0;
                if (c.cabo != null) {
                    linha.append("; maior leitura do cabo=").append(c.maiorCabo).append(" FE");
                    ok &= c.maiorCabo >= 0;
                }
            }
            if (c.sonda != null) {
                Sonda.BE s = (Sonda.BE) nivel.getBlockEntity(c.sonda);
                linha.append(String.format(Locale.ROOT, "; sonda: %d chamadas, oferta min %d / max %d, max %d chamada(s) por tick, negativas %d",
                        s.chamadas, s.menorOferta, s.maiorOferta, s.maxChamadasPorTick, s.chamadasNegativas));
                ok &= s.chamadas > 0 && s.menorOferta > 0 && s.chamadasNegativas == 0 && s.maxChamadasPorTick == 1;
                // A partir de Integer.MAX_VALUE FE/t, cada chamada deve oferecer exatamente o limite.
                if (c.maiorEnergiaGerador >= Integer.MAX_VALUE && c.armazenamento == null) {
                    ok &= s.maiorOferta == Integer.MAX_VALUE;
                }
            }
            if (c.armazenamento != null) {
                StorageBE st = (StorageBE) nivel.getBlockEntity(c.armazenamento);
                linha.append(String.format(Locale.ROOT, "; armazenamento %.6g FE", st.energy));
            }
            verificar(linha.toString(), ok);
        }
        LOG.info("EPTEST FIM: {} verificacoes, {} falhas", verificacoes, falhas.size());
        for (String f : falhas) LOG.error("EPTEST falha: {}", f);
    }
}
