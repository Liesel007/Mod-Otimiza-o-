package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limite de entidades por tipo numa regiao (ideia dos "mobcaps" do ServerCore, mas por tipo): se ja ha
 * limite_enxame.lista[tipo] entidades daquele tipo no chunk e nos vizinhos (raio_chunks), o spawn
 * natural, de geracao de chunk e de spawner e cancelado. Nunca vale para ovo, comando, balde ou cria, e
 * nunca remove entidades que ja existem (so o comando /hl enxame reduzir, pedido pelo operador).
 *
 * Mods que criam mobs sem o MobSpawnEvent (ex.: o formigueiro do Chaos Persists, que faz EntityType.create +
 * addFreshEntity no randomTick do bloco) sao pegos pelo EntityJoinLevelEvent. Para nao contar duas vezes, quem
 * ja passou pelo FinalizeSpawn (natural, spawner, ovo, comando, balde) fica marcado e e ignorado na entrada.
 * Na entrada nunca se cancela cria (reproducao), mob com nome, persistente, montaria ou passageiro: reproducao
 * e conversoes (aldeao zumbi curado etc.) tambem usam addFreshEntity e nao podem sumir.
 */
public final class LimiteEnxame {
    private static final Set<MobSpawnType> LIMITADOS = Set.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.SPAWNER);
    public static final AtomicLong BLOQUEADOS = new AtomicLong();
    public static final Map<String, AtomicLong> BLOQUEADOS_POR_TIPO = new ConcurrentHashMap<>();

    /** Mobs que passaram pelo FinalizeSpawn e ainda nao entraram no mundo (fraco: o que nao entrar some sozinho). */
    private static final Set<Entity> PASSARAM = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    /** Bloqueios pelo EntityJoinLevelEvent (mods que criam mobs sem o evento de spawn). */
    public static final AtomicLong BLOQUEADOS_ENTRADA = new AtomicLong();

    private LimiteEnxame() {
    }

    public static void aoSpawnar(LivingSpawnEvent.CheckSpawn event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (event.getWorld() instanceof Level) {
            PASSARAM.add(mob);
        }
        if (!LIMITADOS.contains(event.getSpawnReason())) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ENTITIES.getKey(mob.getType());
        if (id == null) {
            return;
        }
        Integer limite = Config.enxameLimites().get(id.toString());
        if (limite == null || !(event.getWorld() instanceof Level level)) {
            return;
        }
        if (contar(level, mob.getType(), new ChunkPos(mob.blockPosition()), Config.enxameRaioChunks()) >= limite) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            BLOQUEADOS.incrementAndGet();
            BLOQUEADOS_POR_TIPO.computeIfAbsent(id.toString(), k -> new AtomicLong()).incrementAndGet();
        }
    }

    /** Entrada de entidade nova que nao passou pelo FinalizeSpawn (ver o comentario da classe). */
    public static void aoEntrar(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob) || !(event.getWorld() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        if (PASSARAM.remove(mob) || mob.isBaby() || mob.hasCustomName() || mob.isPersistenceRequired() || mob.isPassenger() || mob.isVehicle()) {
            return;
        }
        Map<String, Integer> limites = Config.enxameLimites();
        if (limites.isEmpty()) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ENTITIES.getKey(mob.getType());
        Integer limite = id == null ? null : limites.get(id.toString());
        if (limite == null) {
            return;
        }
        if (contar(level, mob.getType(), new ChunkPos(mob.blockPosition()), Config.enxameRaioChunks()) >= limite) {
            event.setCanceled(true);
            BLOQUEADOS.incrementAndGet();
            BLOQUEADOS_ENTRADA.incrementAndGet();
            BLOQUEADOS_POR_TIPO.computeIfAbsent(id.toString(), k -> new AtomicLong()).incrementAndGet();
        }
    }

    /**
     * /hl enxame reduzir: remove (sem drops) o excesso de um tipo que ja existe, deixando no maximo o limite em cada
     * regiao de raio_chunks. Pula mobs com nome, persistentes, montarias e passageiros. Com executar=false so conta.
     */
    public static int reduzir(net.minecraft.server.MinecraftServer server, String tipo, boolean executar) {
        Integer limite = Config.enxameLimites().get(tipo);
        EntityType<?> et = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(tipo));
        if (limite == null || et == null) {
            return -1;
        }
        int raio = Config.enxameRaioChunks();
        int removidos = 0;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            Map<Long, Integer> mantidosPorChunk = new java.util.HashMap<>();
            java.util.List<Entity> todos = new java.util.ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e.getType() == et && e.isAlive()) {
                    todos.add(e);
                }
            }
            todos.sort(java.util.Comparator.comparingInt(Entity::getId)); // os mais antigos ficam
            for (Entity e : todos) {
                ChunkPos c = e.chunkPosition();
                boolean protegido = e.hasCustomName() || (e instanceof Mob bebe && bebe.isBaby()) || e.isPassenger() || e.isVehicle() || (e instanceof Mob m && m.isPersistenceRequired());
                int naRegiao = 0;
                for (int dx = -raio; dx <= raio; dx++) {
                    for (int dz = -raio; dz <= raio; dz++) {
                        naRegiao += mantidosPorChunk.getOrDefault(ChunkPos.asLong(c.x + dx, c.z + dz), 0);
                    }
                }
                if (!protegido && naRegiao >= limite) {
                    removidos++;
                    if (executar) {
                        e.discard();
                    }
                } else {
                    mantidosPorChunk.merge(c.toLong(), 1, Integer::sum);
                }
            }
        }
        return removidos;
    }

    /** Entidades vivas do tipo no chunk e nos vizinhos ate o raio, na altura toda. */
    public static int contar(Level level, EntityType<?> tipo, ChunkPos centro, int raio) {
        AABB area = new AABB((centro.x - raio) * 16.0, level.getMinBuildHeight(), (centro.z - raio) * 16.0,
                (centro.x + raio + 1) * 16.0, level.getMaxBuildHeight(), (centro.z + raio + 1) * 16.0);
        return level.getEntities(tipo, area, Entity::isAlive).size();
    }

    /** "ant: 120, rainbow_ant: 0" para o resumo e o painel: todos os tipos da lista, inclusive os com 0. */
    public static String porTipo() {
        java.util.TreeSet<String> tipos = new java.util.TreeSet<>(Config.enxameLimites().keySet());
        tipos.addAll(BLOQUEADOS_POR_TIPO.keySet());
        StringBuilder sb = new StringBuilder();
        for (String tipo : tipos) {
            AtomicLong n = BLOQUEADOS_POR_TIPO.get(tipo);
            sb.append(sb.length() > 0 ? ", " : "").append(tipo.substring(tipo.indexOf(':') + 1)).append(": ").append(n == null ? 0 : n.get());
        }
        return sb.toString();
    }
}
