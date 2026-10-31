package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raider;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Alcance de ativacao (ideia do ServerCore, do NoTick e do "entity activation range" do Paper):
 * entidades longe de todos os jogadores sao processadas so 1 vez a cada N ticks, em vez de todo tick.
 *
 * O despawn continua valendo: o vanilla chama checkDespawn() todo tick, antes e fora do tick da
 * entidade. As entidades puladas nao andam, nao pensam e nao envelhecem nos ticks pulados (o tickCount
 * avanca, como no vanilla, para timers baseados nele continuarem certos).
 *
 * Nunca sao puladas: jogadores, projeteis, veiculos e passageiros, chefes (dragao, wither, a lista de
 * excecoes e qualquer ser vivo com vida maxima de 200 ou mais), entidades com nome, domesticadas, em
 * raid, machucadas agora ou com alvo, itens e orbes de XP (fazendas com coletores), aldeoes (fazendas de
 * ferro) e, por padrao, as que estao em areas carregadas sem jogador por perto (chunk loaders).
 */
public final class AlcanceAtivacao {
    public static final AtomicLong TICKS_PULADOS = new AtomicLong();
    public static final AtomicLong TICKS_PROCESSADOS_LONGE = new AtomicLong();

    /** Excecao por tipo, calculada uma vez por tipo (lista de entidades e de mods da config). */
    private static final Map<EntityType<?>, Boolean> EXCECAO_POR_TIPO = new ConcurrentHashMap<>();
    private static volatile int versaoConfig = -1;

    private AlcanceAtivacao() {
    }

    /** A config mudou (/hl recarregar ou a tela): recalcula as excecoes por tipo. */
    public static void limparCache() {
        EXCECAO_POR_TIPO.clear();
    }

    private static boolean tipoExcecao(EntityType<?> tipo) {
        if (versaoConfig != Config.versao()) {
            versaoConfig = Config.versao();
            EXCECAO_POR_TIPO.clear();
        }
        return EXCECAO_POR_TIPO.computeIfAbsent(tipo, t -> {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(t);
            if (id == null) {
                return true;
            }
            return Config.ativacaoExcecoesEntidades().contains(id.toString()) || Config.ativacaoExcecoesMods().contains(id.getNamespace());
        });
    }

    /** A entidade e de um tipo que nunca deve ser pulada, independente da distancia? */
    static boolean sempreAtiva(Entity e) {
        if (e instanceof Player || e instanceof Projectile || e.isVehicle() || e.isPassenger()) {
            return true;
        }
        if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
            return Config.ativacaoProtegerItens();
        }
        if (e instanceof EnderDragon || e instanceof WitherBoss || e.hasCustomName()) {
            return true;
        }
        if (e instanceof AbstractVillager && Config.ativacaoProtegerAldeoes()) {
            return true;
        }
        if (e instanceof OwnableEntity dono && dono.getOwnerUUID() != null) {
            return true; // domesticado
        }
        if (e instanceof Raider raider && raider.getCurrentRaid() != null) {
            return true;
        }
        if (e instanceof LivingEntity vivo) {
            if (vivo.hurtTime > 0 || vivo.getMaxHealth() >= 200) {
                return true; // machucado agora, ou chefe (de mod) pela vida
            }
            if (vivo instanceof Mob mob && mob.getTarget() != null) {
                return true;
            }
        }
        return tipoExcecao(e.getType());
    }

    /**
     * Processar esta entidade neste tick? Chamado pelo mixin no ServerLevel.tickNonPassenger, para
     * cada entidade, todo tick: precisa ser barato.
     */
    public static boolean deveProcessar(ServerLevel level, Entity e) {
        if (sempreAtiva(e)) {
            return true;
        }
        if (!Config.ativacaoDimensoesIgnoradas().isEmpty()
                && Config.ativacaoDimensoesIgnoradas().contains(level.dimension().location().toString())) {
            return true;
        }
        List<? extends Player> jogadores = level.players();
        if (jogadores.isEmpty()) {
            return Config.ativacaoProtegerSemJogador();
        }
        double h = Config.ativacaoHorizontal();
        double h2 = h * h;
        double v = Config.ativacaoVertical();
        double menorH2 = Double.MAX_VALUE;
        double x = e.getX();
        double y = e.getY();
        double z = e.getZ();
        for (Player p : jogadores) {
            if (p.isSpectator()) {
                continue;
            }
            double dx = p.getX() - x;
            double dz = p.getZ() - z;
            double d2 = dx * dx + dz * dz;
            if (d2 <= h2 && Math.abs(p.getY() - y) <= v) {
                return true; // perto de um jogador
            }
            menorH2 = Math.min(menorH2, d2);
        }
        if (Config.ativacaoProtegerSemJogador()) {
            // Longe de qualquer jogador alem da distancia de simulacao: o chunk so esta carregado por um
            // chunk loader (fazenda) ou e spawn chunk. Nao mexe para nao deixar fazendas mais lentas.
            double simulacao = level.getServer().getPlayerList().getSimulationDistance() * 16.0;
            if (menorH2 > simulacao * simulacao) {
                return true;
            }
        }
        // Longe: 1 tick a cada N, espalhado pelo id para nao processar todas no mesmo tick.
        boolean agora = (level.getGameTime() + e.getId()) % Config.ativacaoIntervalo() == 0;
        if (agora) {
            TICKS_PROCESSADOS_LONGE.incrementAndGet();
        } else {
            TICKS_PULADOS.incrementAndGet();
        }
        return agora;
    }
}
