package com.hlotimizacao.cliente;

import com.hlotimizacao.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * render_enxame (so cliente): com muitos mobs iguais a vista (formigas, peixes, morcegos), desenha so os N mais
 * proximos de cada tipo e nenhum alem de D blocos; entre D/2 e D, sem sombra. Nao mexe no mundo: as entidades
 * continuam existindo, so nao sao desenhadas.
 * <p>
 * A lista de escondidas e recalculada 10 vezes por segundo (nao a cada entidade); o mixin no
 * EntityRenderDispatcher.shouldRender so consulta um conjunto.
 * <p>
 * Automatico (lista vazia) so para mobs nao hostis; hostis so quando listados em render_enxame.lista.
 * Nunca esconde: jogadores, chefes, entidades com nome, domesticadas, montarias e passageiros, a entidade na mira,
 * entidades brilhando, machucadas nos ultimos 5 s, e as de alcance_ativacao.excecoes_entidades.
 */
public final class RenderEnxame {
    private static final long RECALCULO_NS = 100_000_000L;
    private static final long COMBATE_MS = 5_000;

    private static volatile Set<Entity> escondidas = Collections.emptySet();
    private static volatile Set<Entity> semSombra = Collections.emptySet();
    private static final Map<Entity, Long> MACHUCADAS = new WeakHashMap<>();
    private static long ultimoRecalculoNs;

    private RenderEnxame() {
    }

    /** Chamado pelo mixin para cada entidade, a cada frame. */
    public static boolean esconder(Entity e) {
        long agora = System.nanoTime();
        if (agora - ultimoRecalculoNs > RECALCULO_NS) {
            ultimoRecalculoNs = agora;
            recalcular();
        }
        return escondidas.contains(e);
    }

    public static boolean semSombra(Entity e) {
        return semSombra.contains(e);
    }

    private static void recalcular() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            escondidas = Collections.emptySet();
            semSombra = Collections.emptySet();
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        int maximo = Config.renderEnxameMaximo();
        double distancia = Config.renderEnxameDistancia();
        double d2 = distancia * distancia;
        double meio2 = d2 / 4;
        Set<String> lista = Config.renderEnxameLista();
        Set<String> nunca = Config.ativacaoExcecoesEntidades();
        Map<EntityType<?>, List<Entity>> grupos = new HashMap<>();
        long agoraMs = System.currentTimeMillis();
        int total = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity vivo) || e instanceof Player) {
                continue;
            }
            total++;
            if (vivo.hurtTime > 0) {
                MACHUCADAS.put(e, agoraMs);
            }
            grupos.computeIfAbsent(e.getType(), k -> new ArrayList<>()).add(e);
        }
        Set<Entity> novas = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Entity> novasSemSombra = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, int[]> tipos = new HashMap<>();
        for (Map.Entry<EntityType<?>, List<Entity>> g : grupos.entrySet()) {
            List<Entity> lista2 = g.getValue();
            ResourceLocation id = ForgeRegistries.ENTITIES.getKey(g.getKey());
            String nome = id == null ? "?" : id.toString();
            // Automatico so para mobs nao hostis: um esqueleto invisivel atirando de longe e perigoso. Hostis so
            // entram quando listados de proposito em render_enxame.lista.
            boolean hostil = lista2.get(0) instanceof net.minecraft.world.entity.monster.Enemy;
            boolean enxame = lista.contains(nome) || (lista.isEmpty() && !hostil && lista2.size() > maximo);
            if (!enxame || nunca.contains(nome)) {
                continue;
            }
            lista2.sort((a, b) -> Double.compare(a.distanceToSqr(camera), b.distanceToSqr(camera)));
            int mostradas = 0;
            int escondidasTipo = 0;
            for (Entity e : lista2) {
                double dist = e.distanceToSqr(camera);
                if (protegida(mc, e, agoraMs)) {
                    continue;
                }
                if (mostradas >= maximo || dist > d2) {
                    novas.add(e);
                    escondidasTipo++;
                    continue;
                }
                mostradas++;
                if (dist > meio2) {
                    novasSemSombra.add(e);
                }
            }
            tipos.put(nome, new int[]{lista2.size(), escondidasTipo});
        }
        MACHUCADAS.values().removeIf(t -> agoraMs - t > COMBATE_MS);
        escondidas = novas;
        semSombra = novasSemSombra;
        com.hlotimizacao.recursos.ContadoresCliente.enxameExistentes = total;
        com.hlotimizacao.recursos.ContadoresCliente.enxameDesenhadas = total - novas.size();
        com.hlotimizacao.recursos.ContadoresCliente.enxamePorTipo = tipos;
    }

    private static boolean protegida(Minecraft mc, Entity e, long agoraMs) {
        if (e.hasCustomName() || e.isVehicle() || e.isPassenger() || e.isCurrentlyGlowing() || e == mc.crosshairPickEntity
                || !e.canChangeDimensions()) { // chefes (dragao, wither) nao mudam de dimensao
            return true;
        }
        if (e instanceof OwnableEntity dono && dono.getOwnerUUID() != null) {
            return true;
        }
        Long machucada = MACHUCADAS.get(e);
        return machucada != null && agoraMs - machucada <= COMBATE_MS;
    }
}
