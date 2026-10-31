package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limpeza periodica dos itens soltos no chao (como os plugins ClearLag), com avisos no chat antes e
 * protecoes para o que o jogador quer guardar. A remocao e espalhada em varios ticks.
 */
public final class LimpezaItens {
    /** Marca nos itens que sairam da morte de um jogador: nunca sao apagados. */
    public static final String MARCA_MORTE = "hlotimizacao_drop_morte";

    public static final AtomicLong LIMPEZAS = new AtomicLong();
    public static final AtomicLong ITENS_REMOVIDOS = new AtomicLong();

    private static long proximaLimpezaTick = -1;
    private static final Deque<ItemEntity> FILA = new ArrayDeque<>();
    private static boolean limpando;
    private static int removidosNestaLimpeza;
    private static long itensNestaLimpeza;
    private static int protegidosNestaLimpeza;
    private static long nsNestaLimpeza;
    private static long maiorTickNs;
    private static int ticksNestaLimpeza;
    private static final java.util.Map<String, Integer> MOTIVOS = new java.util.TreeMap<>();

    private LimpezaItens() {
    }

    private static int ticksPorMinuto() {
        return 20 * 60;
    }

    public static void aoIniciarServidor(MinecraftServer server) {
        FILA.clear();
        limpando = false;
        agendar(server, Config.limpezaIntervaloMin() * (long) ticksPorMinuto());
    }

    private static void agendar(MinecraftServer server, long daquiTicks) {
        proximaLimpezaTick = server.getTickCount() + Math.max(1, daquiTicks);
    }

    /** Marca os itens que um jogador deixou cair ao morrer. */
    public static void aoMorrer(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player) {
            for (ItemEntity item : event.getDrops()) {
                item.getPersistentData().putBoolean(MARCA_MORTE, true);
            }
        }
    }

    public static void aoTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || proximaLimpezaTick < 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (limpando) {
            continuar(server);
            return;
        }
        long faltamTicks = proximaLimpezaTick - server.getTickCount();
        if (faltamTicks <= 0) {
            comecar(server);
            return;
        }
        if (faltamTicks % 20 == 0) {
            int faltamS = (int) (faltamTicks / 20);
            if (Config.limpezaAvisosS().contains(faltamS)) {
                avisar(server, Component.literal("[HL] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Os itens no chao serao apagados em " + faltamS + " segundo" + (faltamS == 1 ? "" : "s") + "!")
                                .withStyle(ChatFormatting.YELLOW)));
            }
        }
    }

    private static void avisar(MinecraftServer server, Component mensagem) {
        server.getPlayerList().broadcastSystemMessage(mensagem, false);
    }

    private static void comecar(MinecraftServer server) {
        FILA.clear();
        List<String> dimensoes = Config.limpezaDimensoes();
        for (ServerLevel level : server.getAllLevels()) {
            if (!dimensoes.isEmpty() && !dimensoes.contains(level.dimension().location().toString())) {
                continue;
            }
            for (Entity entidade : level.getAllEntities()) {
                if (entidade instanceof ItemEntity item) {
                    FILA.add(item);
                }
            }
        }
        limpando = true;
        MOTIVOS.clear();
        nsNestaLimpeza = 0;
        maiorTickNs = 0;
        ticksNestaLimpeza = 0;
        removidosNestaLimpeza = 0;
        itensNestaLimpeza = 0;
        protegidosNestaLimpeza = 0;
        continuar(server);
    }

    private static void continuar(MinecraftServer server) {
        long inicio = System.nanoTime();
        int limite = Config.limpezaMaxPorTick();
        for (int i = 0; i < limite && !FILA.isEmpty(); i++) {
            ItemEntity item = FILA.poll();
            if (item.isRemoved()) {
                continue;
            }
            String motivo = motivoProtecao(item);
            if (motivo != null) {
                protegidosNestaLimpeza++;
                MOTIVOS.merge(motivo.startsWith("recente") ? "recente" : motivo, 1, Integer::sum);
                if (Config.LOGGER.isDebugEnabled() && protegidosNestaLimpeza <= 5) {
                    Config.LOGGER.debug("[limpeza_itens] protegido: {} em {} ({})", item.getItem(), item.blockPosition(), motivo);
                }
                continue;
            }
            itensNestaLimpeza += item.getItem().getCount();
            item.discard(); // discard(): sem som nem particulas
            removidosNestaLimpeza++;
        }
        long gasto = System.nanoTime() - inicio;
        nsNestaLimpeza += gasto;
        maiorTickNs = Math.max(maiorTickNs, gasto);
        ticksNestaLimpeza++;
        if (FILA.isEmpty()) {
            limpando = false;
            LIMPEZAS.incrementAndGet();
            ITENS_REMOVIDOS.addAndGet(itensNestaLimpeza);
            avisar(server, Component.literal("[HL] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(String.format("%d pilha(s) de itens (%d itens) removida(s) do chao.", removidosNestaLimpeza, itensNestaLimpeza))
                            .withStyle(ChatFormatting.YELLOW)));
            Config.LOGGER.info("[limpeza_itens] {} pilha(s) ({} itens) removida(s), {} protegida(s) {}; {} ms em {} tick(s), no maximo {} ms num tick",
                    removidosNestaLimpeza, itensNestaLimpeza, protegidosNestaLimpeza, MOTIVOS, String.format("%.1f", nsNestaLimpeza / 1e6),
                    ticksNestaLimpeza, String.format("%.1f", maiorTickNs / 1e6));
            agendar(server, Config.limpezaIntervaloMin() * (long) ticksPorMinuto());
        }
    }

    static boolean protegido(ItemEntity entidade) {
        return motivoProtecao(entidade) != null;
    }

    /** Por que o item nao e apagado, ou null se pode ser apagado. */
    static String motivoProtecao(ItemEntity entidade) {
        ItemStack stack = entidade.getItem();
        if (entidade.getAge() < Config.limpezaIdadeMinimaS() * 20) {
            return "recente (idade " + entidade.getAge() + " ticks)"; // inclui idade negativa (itens marcados para nao sumir)
        }
        if (entidade.getPersistentData().getBoolean(MARCA_MORTE)) {
            return "drop de morte";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String idTexto = id == null ? "" : id.toString();
        if (Config.limpezaItensProtegidos().contains(idTexto) || stack.getItem() instanceof SpawnEggItem || idTexto.endsWith("shulker_box")) {
            return "item protegido";
        }
        if (Config.limpezaProtegerNbt() && (stack.isEnchanted() || stack.hasCustomHoverName()
                || (stack.getTag() != null && stack.getTag().contains("BlockEntityTag")))) {
            return "encantado, com nome ou com conteudo";
        }
        if (Config.limpezaProtegerColetores()) {
            BlockPos abaixo = entidade.blockPosition().below();
            BlockEntity be = entidade.level().isLoaded(abaixo) ? entidade.level().getBlockEntity(abaixo) : null;
            if (be instanceof Hopper || (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent())) {
                return "sobre funil ou coletor"; // fazendas que usam itens no chao
            }
        }
        return null;
    }

    /** /hl limpar: limpa daqui a 10 s, com os avisos de 10 s para baixo. */
    public static void limparAgora(MinecraftServer server) {
        if (!limpando) {
            agendar(server, 10 * 20);
        }
    }

    /** /hl limpar cancelar: pula a proxima limpeza (a seguinte fica para daqui a um intervalo). */
    public static boolean cancelar(MinecraftServer server) {
        if (limpando) {
            return false;
        }
        agendar(server, Config.limpezaIntervaloMin() * (long) ticksPorMinuto());
        avisar(server, Component.literal("[HL] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("A limpeza de itens foi cancelada.").withStyle(ChatFormatting.YELLOW)));
        return true;
    }

    public static boolean limpandoAgora() {
        return limpando;
    }

    /** Segundos ate a proxima limpeza (-1 se nao agendada). */
    public static long segundosParaProxima(MinecraftServer server) {
        return proximaLimpezaTick < 0 ? -1 : Math.max(0, proximaLimpezaTick - server.getTickCount()) / 20;
    }

    /** {pilhas, itens} soltos no chao agora. */
    public static long[] contarNoChao(MinecraftServer server) {
        long pilhas = 0;
        long itens = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ItemEntity item) {
                    pilhas++;
                    itens += item.getItem().getCount();
                }
            }
        }
        return new long[]{pilhas, itens};
    }

    public static List<String> status(MinecraftServer server) {
        List<String> linhas = new ArrayList<>();
        long faltam = Math.max(0, proximaLimpezaTick - server.getTickCount()) / 20;
        int itens = 0;
        int pilhas = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ItemEntity item) {
                    pilhas++;
                    itens += item.getItem().getCount();
                }
            }
        }
        linhas.add(limpando ? "Limpando agora." : String.format("Proxima limpeza em %d min %02d s.", faltam / 60, faltam % 60));
        linhas.add(String.format("Itens no chao agora: %d pilha(s), %d itens.", pilhas, itens));
        return linhas;
    }
}
