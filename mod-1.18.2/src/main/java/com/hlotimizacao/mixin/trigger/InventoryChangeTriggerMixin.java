package com.hlotimizacao.mixin.trigger;

import com.hlotimizacao.duck.CriterioDeSlots;
import com.hlotimizacao.duck.GatilhoComJogadores;
import com.hlotimizacao.recursos.CacheGatilhoInventario;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.server.PlayerAdvancements;
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
 * podem ser obtidas justamente ao esvaziar uma pilha. Se o jogador tiver alguma pendente desse
 * tipo, a verificacao completa acontece normalmente. A resposta fica em cache por jogador e e
 * recalculada quando as conquistas pendentes dele mudam.
 */
@Mixin(InventoryChangeTrigger.class)
public abstract class InventoryChangeTriggerMixin {
    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$ignorarPilhaVazia(ServerPlayer player, Inventory inventory, ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty() && !hlotimizacao$podeMudarAoEsvaziar(player.getAdvancements())) {
            ci.cancel();
        }
    }

    @Unique
    private boolean hlotimizacao$podeMudarAoEsvaziar(PlayerAdvancements advancements) {
        Boolean emCache = CacheGatilhoInventario.get(advancements);
        if (emCache != null) {
            return emCache;
        }
        boolean resultado = false;
        Set<CriterionTrigger.Listener<?>> listeners = ((GatilhoComJogadores) this).hlotimizacao$listeners(advancements);
        if (listeners != null) {
            for (CriterionTrigger.Listener<?> listener : listeners) {
                if (listener.getTriggerInstance() instanceof CriterioDeSlots criterio && criterio.hlotimizacao$podeMudarAoEsvaziar()) {
                    resultado = true;
                    break;
                }
            }
        }
        CacheGatilhoInventario.put(advancements, resultado);
        return resultado;
    }
}
