package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Insercao de itens em inventarios grandes (ItemHandlerHelper.insertItemStacked do Forge).
 *
 * O Forge, para cada pilha, percorre todos os slots chamando getStackInSlot (ate 2 passadas). Com
 * milhares de slots, ou getStackInSlot caro (o Infinity Chest copia o item a cada chamada), uma maquina
 * que despeja todo tick trava o servidor.
 *
 * - diagnostico_insercao: durante um travamento, anota quem despeja (o block entity que esta tickando)
 *   e onde (classe e numero de slots do inventario), para o relatorio e o /hl.
 * - correcao_infinity_chest: o insertItem do Infinity Chest ignora o slot (guarda pela chave do item);
 *   inserir direto da exatamente o mesmo resultado da varredura, sem a varredura.
 * - insercao_rapida (opcional): cache de "onde coube da ultima vez", sempre validado pelo proprio
 *   insertItem do inventario; se o cache nao serve, faz a mesma varredura do Forge. Orcamento opcional
 *   de varreduras por inventario por tick.
 */
public final class Insercao {
    // ---------- diagnostico: quem esta tickando ----------

    /** Block entity tickando agora (so na thread do servidor; escrito pelo mixin do BoundTickingBlockEntity). */
    public static BlockEntity tickando;

    public record Despejo(String fonte, BlockPos posFonte, String destino, int slots, BlockPos posDestino) {
    }

    private static final Map<Despejo, long[]> DESPEJOS_NO_TRAVAMENTO = new LinkedHashMap<>();
    /** Para o aviso: o maior inventario visto recebendo despejo durante um travamento, por destino. */
    private static final Map<String, Despejo> PIORES = new LinkedHashMap<>();
    /** Maquinas (posicoes) vistas despejando em cada inventario grande, na sessao inteira. */
    private static final Map<String, java.util.Set<BlockPos>> FONTES_POR_DESTINO = new HashMap<>();
    private static final java.util.Locale PT = java.util.Locale.forLanguageTag("pt-BR");

    // ---------- estatisticas ----------
    public static final AtomicLong RAPIDAS = new AtomicLong();
    public static final AtomicLong VARREDURAS = new AtomicLong();
    public static final AtomicLong RECUSADAS = new AtomicLong();
    public static final AtomicLong ATALHO_AVARITIA = new AtomicLong();

    private static Class<?> classeChestHandler;
    private static boolean chestHandlerProcurado;

    private Insercao() {
    }

    /**
     * Chamado no comeco de ItemHandlerHelper.insertItemStacked. Devolve o resto (o que nao coube) quando
     * resolveu a insercao, ou null para o Forge seguir com o caminho original.
     */
    /** So para os testes do HLBench: true = o Forge segue o caminho original, sem nada deste mod. */
    public static boolean originalNoTeste;

    public static ItemStack aoInserir(IItemHandler destino, ItemStack pilha, boolean simular) {
        if (originalNoTeste || destino == null || pilha.isEmpty()) {
            return null;
        }
        if (Config.ativo(Modulo.DIAGNOSTICO_INSERCAO) && DiagnosticoLag.travando()) {
            anotar(destino);
        }
        if (Config.ativo(Modulo.CORRECAO_INFINITY_CHEST) && ehChestHandler(destino)) {
            ATALHO_AVARITIA.incrementAndGet();
            // O ChestHandler.insertItem ignora o slot e guarda pela chave do item: e o mesmo que a varredura
            // do Forge faria ao chegar no slot do item (ou no primeiro slot vazio), so que sem varrer.
            return destino.insertItem(0, pilha, simular);
        }
        if (Config.ativo(Modulo.INSERCAO_RAPIDA) && naThreadDoServidor()) {
            int slots = destino.getSlots();
            if (slots >= Config.insercaoLimiteSlots() && !ignorado(destino)) {
                return inserirRapido(destino, pilha, simular, slots);
            }
        }
        return null;
    }

    private static boolean naThreadDoServidor() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread();
    }

    static boolean ehChestHandler(IItemHandler h) {
        if (!chestHandlerProcurado) {
            chestHandlerProcurado = true;
            try {
                classeChestHandler = Class.forName("committee.nova.mods.avaritia.core.chest.ChestHandler", false, h.getClass().getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                classeChestHandler = null;
            }
        }
        return classeChestHandler != null && classeChestHandler.isInstance(h);
    }

    private static boolean ignorado(IItemHandler h) {
        if (Config.insercaoIgnorar().isEmpty()) {
            return false;
        }
        String classe = h.getClass().getName();
        for (String prefixo : Config.insercaoIgnorar()) {
            if (classe.startsWith(prefixo)) {
                return true;
            }
        }
        return false;
    }

    // ---------- diagnostico ----------

    private static BlockEntity ultimaFonte;
    private static IItemHandler ultimoDestino;
    private static long[] ultimoContador;

    private static synchronized void anotar(IItemHandler destino) {
        BlockEntity fonte = tickando;
        // Num travamento isto roda milhoes de vezes: o mesmo par maquina -> inventario so soma.
        if (fonte == ultimaFonte && destino == ultimoDestino && ultimoContador != null) {
            ultimoContador[0]++;
            return;
        }
        String nomeFonte = fonte == null ? "?" : idBlockEntity(fonte);
        BlockPos posFonte = fonte == null ? null : fonte.getBlockPos();
        int slots = destino.getSlots();
        Despejo d = new Despejo(nomeFonte, posFonte, destino.getClass().getSimpleName(), slots,
                fonte == null ? null : vizinhoComInventario(fonte.getLevel(), posFonte, destino));
        ultimoContador = DESPEJOS_NO_TRAVAMENTO.computeIfAbsent(d, k -> new long[1]);
        ultimoContador[0]++;
        ultimaFonte = fonte;
        ultimoDestino = destino;
    }

    private static String idBlockEntity(BlockEntity be) {
        ResourceLocation id = BlockEntityType.getKey(be.getType());
        return id == null ? be.getClass().getSimpleName() : id.toString();
    }

    /** Qual vizinho da fonte tem este inventario (mesma classe e mesmo numero de slots)? */
    private static BlockPos vizinhoComInventario(Level level, BlockPos pos, IItemHandler destino) {
        if (level == null || pos == null) {
            return null;
        }
        for (Direction d : Direction.values()) {
            BlockPos vizinho = pos.relative(d);
            BlockEntity be = level.getBlockEntity(vizinho);
            if (be == null) {
                continue;
            }
            IItemHandler h = be.getCapability(ForgeCapabilities.ITEM_HANDLER, d.getOpposite()).orElse(null);
            if (h == destino || (h != null && h.getClass() == destino.getClass() && h.getSlots() == destino.getSlots())) {
                return vizinho;
            }
        }
        return null;
    }

    /** Chamado pelo relatorio do diagnostico_lag: linhas com os despejos anotados no travamento, e limpa. */
    public static synchronized String relatorioDoTravamento() {
        if (DESPEJOS_NO_TRAVAMENTO.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("  Insercoes durante o travamento (quem despeja -> onde):\n");
        DESPEJOS_NO_TRAVAMENTO.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .forEach(e -> {
                    Despejo d = e.getKey();
                    sb.append(String.format(PT, "    %s%s -> %s (%,d slots)%s: %,d chamada(s)%n", d.fonte(),
                            d.posFonte() == null ? "" : " em " + d.posFonte().toShortString(), d.destino(), d.slots(),
                            d.posDestino() == null ? "" : " em " + d.posDestino().toShortString(), e.getValue()[0]));
                    if (d.slots() >= 54) {
                        PIORES.merge(d.destino(), d, (a, b) -> b.slots() >= a.slots() ? b : a);
                        if (d.posFonte() != null) {
                            FONTES_POR_DESTINO.computeIfAbsent(d.destino(), k -> new java.util.HashSet<>()).add(d.posFonte());
                        }
                        avisar(d);
                    }
                });
        DESPEJOS_NO_TRAVAMENTO.clear();
        ultimaFonte = null;
        ultimoDestino = null;
        ultimoContador = null;
        return sb.toString();
    }

    private static void avisar(Despejo d) {
        int fontes = FONTES_POR_DESTINO.getOrDefault(d.destino(), java.util.Set.of()).size();
        String fonte = d.fonte().contains(":") ? d.fonte().substring(d.fonte().indexOf(':') + 1) : d.fonte();
        Avisos.substituir("insercao:" + d.destino(),
                String.format(PT, "%s%d maquina(s) (%s) despejando num inventario de %,d slots", fontes > 1 ? "pelo menos " : "",
                        Math.max(1, fontes), fonte, d.slots()),
                "Contadas so as maquinas vistas durante os travamentos (pode haver mais). Durante um travamento, " + d.fonte() + (d.posFonte() == null ? "" : " em " + d.posFonte().toShortString())
                        + " estava inserindo itens direto em " + d.destino() + (d.posDestino() == null ? "" : " em " + d.posDestino().toShortString())
                        + ", que tem " + d.slots() + " slots. O Forge percorre todos os slots para cada pilha inserida. Coloque um bau comum ou uma rede "
                        + "(AE2/RS) entre a maquina e o inventario grande, ou ligue o modulo insercao_rapida.");
    }

    // ---------- insercao_rapida ----------

    /** Item + tags: a mesma comparacao do ItemStack.isSameItemSameTags. */
    private record ChaveItem(Item item, CompoundTag tag) {
        static ChaveItem de(ItemStack s) {
            return new ChaveItem(s.getItem(), s.getTag() == null ? null : s.getTag().copy());
        }
    }

    private static final class Cache {
        int slots;
        /** Ultimo slot que aceitou cada item (-1 = a ultima varredura nao achou pilha igual com espaco). */
        final Map<ChaveItem, Integer> slotPorItem = new HashMap<>();
        /** Primeiro slot vazio, se conhecido (todos os slots antes dele estavam ocupados na ultima varredura). */
        int primeiroVazio = -1;
        long tick = -1;
        int varredurasNoTick;
    }

    private static final Map<IItemHandler, Cache> CACHES = new WeakHashMap<>();

    private static Cache cache(IItemHandler h, int slots) {
        Cache c = CACHES.computeIfAbsent(h, k -> new Cache());
        if (c.slots != slots || c.slotPorItem.size() > 4096) {
            c.slots = slots;
            c.slotPorItem.clear();
            c.primeiroVazio = -1;
        }
        return c;
    }

    /** Para testes: esquece tudo. */
    public static void limparCaches() {
        CACHES.clear();
    }

    static ItemStack inserirRapido(IItemHandler h, ItemStack pilha, boolean simular, int slots) {
        if (!pilha.isStackable()) {
            return null; // o Forge so chama insertItem slot a slot; sem ganho seguro aqui
        }
        Cache c = cache(h, slots);
        ChaveItem chave = ChaveItem.de(pilha);
        Integer ultimo = c.slotPorItem.get(chave);

        // 1. Onde este item entrou da ultima vez.
        if (ultimo != null && ultimo >= 0 && ultimo < slots) {
            ItemStack noSlot = h.getStackInSlot(ultimo);
            if (ItemHandlerHelper.canItemStacksStackRelaxed(noSlot, pilha)) {
                ItemStack resto = h.insertItem(ultimo, pilha, simular);
                if (resto.isEmpty()) {
                    RAPIDAS.incrementAndGet();
                    return ItemStack.EMPTY;
                }
                // Encheu: segue a primeira passada do Forge a partir do proximo slot (os anteriores com
                // este item estavam cheios), depois os vazios.
                return varrer(h, c, chave, resto, simular, slots, ultimo + 1);
            }
            c.slotPorItem.remove(chave);
        }
        // 2. Nenhuma pilha igual com espaco (segundo a ultima varredura): direto no primeiro vazio conhecido.
        if (ultimo != null && ultimo < 0 && c.primeiroVazio >= 0 && c.primeiroVazio < slots && h.getStackInSlot(c.primeiroVazio).isEmpty()) {
            int vazio = c.primeiroVazio;
            ItemStack resto = h.insertItem(vazio, pilha, simular);
            if (resto.getCount() < pilha.getCount() && !simular) {
                c.slotPorItem.put(chave, vazio);
                c.primeiroVazio = proximoVazio(h, vazio + 1, slots);
            }
            if (resto.isEmpty()) {
                RAPIDAS.incrementAndGet();
                return ItemStack.EMPTY;
            }
            return varrer(h, c, chave, resto, simular, slots, slots); // sem pilha igual: so os vazios
        }
        return varrer(h, c, chave, pilha, simular, slots, 0);
    }

    /** Proximo slot vazio olhando no maximo 8 slots a frente; -1 se nao achar (a proxima varredura descobre). */
    private static int proximoVazio(IItemHandler h, int desde, int slots) {
        for (int i = desde; i < Math.min(slots, desde + 8); i++) {
            if (h.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A mesma varredura do ItemHandlerHelper.insertItemStacked (primeira passada nas pilhas iguais a partir
     * de "desde", depois os vazios desde o 0), anotando no cache onde o item entrou e o primeiro vazio.
     */
    private static ItemStack varrer(IItemHandler h, Cache c, ChaveItem chave, ItemStack pilha, boolean simular, int slots, int desde) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        long tick = server == null ? 0 : server.getTickCount();
        if (c.tick != tick) {
            c.tick = tick;
            c.varredurasNoTick = 0;
        }
        if (Config.insercaoOrcamento() && c.varredurasNoTick >= Config.insercaoVarredurasMaxPorTick()) {
            RECUSADAS.incrementAndGet();
            return pilha; // "inventario cheio" neste tick: a maquina tenta de novo no proximo
        }
        c.varredurasNoTick++;
        VARREDURAS.incrementAndGet();
        ItemStack resto = pilha;
        int aceitouEm = -1;
        for (int i = desde; i < slots; i++) {
            ItemStack noSlot = h.getStackInSlot(i);
            if (ItemHandlerHelper.canItemStacksStackRelaxed(noSlot, resto)) {
                int antes = resto.getCount();
                resto = h.insertItem(i, resto, simular);
                if (resto.getCount() < antes) {
                    aceitouEm = i;
                }
                if (resto.isEmpty()) {
                    break;
                }
            }
        }
        if (!resto.isEmpty()) {
            boolean vazioAnotado = false;
            // Os slots antes do primeiro vazio conhecido estao ocupados: a segunda passada do Forge os pularia.
            int inicioVazios = c.primeiroVazio > 0 && c.primeiroVazio < slots ? c.primeiroVazio : 0;
            for (int i = inicioVazios; i < slots; i++) {
                if (h.getStackInSlot(i).isEmpty()) {
                    if (!vazioAnotado) {
                        vazioAnotado = true;
                        c.primeiroVazio = i;
                    }
                    int antes = resto.getCount();
                    resto = h.insertItem(i, resto, simular);
                    if (resto.getCount() < antes) {
                        aceitouEm = i;
                        if (!simular) {
                            c.primeiroVazio = proximoVazio(h, i + 1, slots);
                        }
                    }
                    if (resto.isEmpty()) {
                        break;
                    }
                }
            }
            if (!vazioAnotado) {
                c.primeiroVazio = -1;
            }
        }
        if (!simular) {
            c.slotPorItem.put(chave, aceitouEm);
        }
        return resto;
    }
}
