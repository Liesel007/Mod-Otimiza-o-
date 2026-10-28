package com.hlotimizacao.mixin.trigger;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * O gatilho "inventario mudou" percorre o inventario inteiro e testa cada conquista a cada mudanca
 * de slot. Quando uma pilha fica vazia, nenhuma conquista baseada em itens pode ser obtida.
 *
 * Diferenca em relacao ao Icterine: conquistas que contam slots ("pelo menos N slots vazios")
 * podem ser obtidas justamente ao esvaziar uma pilha. Elas continuam sendo verificadas.
 */
@Mixin(InventoryChangeTrigger.class)
public abstract class InventoryChangeTriggerMixin {
    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$ignorarPilhaVazia(ServerPlayer player, Inventory inventory, ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty() && !hlotimizacao$temCriterioDeSlots(player)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean hlotimizacao$temCriterioDeSlots(ServerPlayer player) {
        Set<CriterionTrigger.Listener<?>> listeners = ((SimpleCriterionTriggerAccessor) this).hlotimizacao$getPlayers().get(player.getAdvancements());
        if (listeners == null) {
            return false;
        }
        for (CriterionTrigger.Listener<?> listener : listeners) {
            if (listener.getTriggerInstance() instanceof TriggerInstanceAccessor instance
                    && (!instance.hlotimizacao$slotsEmpty().isAny()
                    || !instance.hlotimizacao$slotsFull().isAny()
                    || !instance.hlotimizacao$slotsOccupied().isAny())) {
                return true;
            }
        }
        return false;
    }
}
