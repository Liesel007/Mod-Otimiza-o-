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
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limite de entidades por tipo numa regiao (ideia dos "mobcaps" do ServerCore, mas por tipo): se ja ha
 * limite_enxame.lista[tipo] entidades daquele tipo no chunk e nos vizinhos (raio_chunks), o spawn
 * natural, de geracao de chunk e de spawner e cancelado. Nunca vale para ovo, comando, balde, cria,
 * evento de mod etc., e nunca remove entidades que ja existem.
 */
public final class LimiteEnxame {
    private static final Set<MobSpawnType> LIMITADOS = Set.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.SPAWNER);
    public static final AtomicLong BLOQUEADOS = new AtomicLong();
    public static final Map<String, AtomicLong> BLOQUEADOS_POR_TIPO = new ConcurrentHashMap<>();

    private LimiteEnxame() {
    }

    public static void aoSpawnar(MobSpawnEvent.FinalizeSpawn event) {
        if (!LIMITADOS.contains(event.getSpawnType())) {
            return;
        }
        Mob mob = event.getEntity();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (id == null) {
            return;
        }
        Integer limite = Config.enxameLimites().get(id.toString());
        if (limite == null || !(event.getLevel() instanceof Level level)) {
            return;
        }
        if (contar(level, mob.getType(), new ChunkPos(mob.blockPosition()), Config.enxameRaioChunks()) >= limite) {
            event.setSpawnCancelled(true);
            BLOQUEADOS.incrementAndGet();
            BLOQUEADOS_POR_TIPO.computeIfAbsent(id.toString(), k -> new AtomicLong()).incrementAndGet();
        }
    }

    /** Entidades vivas do tipo no chunk e nos vizinhos ate o raio, na altura toda. */
    public static int contar(Level level, EntityType<?> tipo, ChunkPos centro, int raio) {
        AABB area = new AABB((centro.x - raio) * 16.0, level.getMinBuildHeight(), (centro.z - raio) * 16.0,
                (centro.x + raio + 1) * 16.0, level.getMaxBuildHeight(), (centro.z + raio + 1) * 16.0);
        return level.getEntities(tipo, area, Entity::isAlive).size();
    }

    /** "ant: 120, butterfly: 3" para o resumo. */
    public static String porTipo() {
        StringBuilder sb = new StringBuilder();
        BLOQUEADOS_POR_TIPO.forEach((tipo, n) -> sb.append(sb.length() > 0 ? ", " : "").append(tipo.substring(tipo.indexOf(':') + 1)).append(": ").append(n.get()));
        return sb.toString();
    }
}
