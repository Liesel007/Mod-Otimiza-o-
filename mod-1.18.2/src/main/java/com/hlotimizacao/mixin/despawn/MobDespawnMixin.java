package com.hlotimizacao.mixin.despawn;

import com.hlotimizacao.recursos.Estatisticas;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No vanilla, um mob que pega um item do chao vira persistente para sempre e se acumula.
 *
 * Diferencas em relacao ao Let Me Despawn:
 * - so tira a persistencia que o proprio item causou (um mob que ja era persistente por outro
 *   motivo, como um spawner de estrutura ou outro mod, continua persistente);
 * - ao desaparecer, devolve so os itens que o mob pegou (chance de drop > 1, marcada pelo vanilla
 *   e salva no NBT), em vez de todo o equipamento com que ele nasceu;
 * - funciona depois de salvar e recarregar o mundo, porque nao depende de um campo nao salvo.
 */
@Mixin(Mob.class)
public abstract class MobDespawnMixin extends LivingEntity {
    @Shadow
    private boolean persistenceRequired;

    @Unique
    private boolean hlotimizacao$persistenteAntes;

    protected MobDespawnMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Shadow
    protected abstract float getEquipmentDropChance(EquipmentSlot slot);

    @Inject(method = "setItemSlotAndDropWhenKilled", at = @At("HEAD"))
    private void hlotimizacao$lembrarPersistencia(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        this.hlotimizacao$persistenteAntes = this.persistenceRequired;
    }

    @Inject(method = "setItemSlotAndDropWhenKilled", at = @At("TAIL"))
    private void hlotimizacao$desfazerPersistencia(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (!this.hlotimizacao$persistenteAntes && !this.hasCustomName()) {
            if (this.persistenceRequired) {
                Estatisticas.DESPAWN_MOBS_LIBERADOS.incrementAndGet();
            }
            this.persistenceRequired = false;
        }
    }

    @Inject(method = "checkDespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;discard()V"))
    private void hlotimizacao$devolverItensPegos(CallbackInfo ci) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = this.getItemBySlot(slot);
            if (!stack.isEmpty() && this.getEquipmentDropChance(slot) > 1.0F && !EnchantmentHelper.hasVanishingCurse(stack)) {
                Estatisticas.DESPAWN_ITENS_DEVOLVIDOS.incrementAndGet();
                this.spawnAtLocation(stack);
                this.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}
