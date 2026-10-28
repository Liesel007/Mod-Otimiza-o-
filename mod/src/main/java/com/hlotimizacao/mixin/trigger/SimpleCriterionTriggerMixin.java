package com.hlotimizacao.mixin.trigger;

import com.hlotimizacao.duck.GatilhoComJogadores;
import com.hlotimizacao.recursos.CacheGatilhoInventario;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

@Mixin(SimpleCriterionTrigger.class)
public abstract class SimpleCriterionTriggerMixin implements GatilhoComJogadores {
    @Shadow
    @Final
    private Map<PlayerAdvancements, Set<CriterionTrigger.Listener<?>>> players;

    @Override
    public Set<CriterionTrigger.Listener<?>> hlotimizacao$listeners(PlayerAdvancements advancements) {
        return this.players.get(advancements);
    }

    @Inject(method = {"addPlayerListener", "removePlayerListener"}, at = @At("HEAD"))
    private void hlotimizacao$invalidar(PlayerAdvancements advancements, CriterionTrigger.Listener<?> listener, CallbackInfo ci) {
        if ((Object) this instanceof InventoryChangeTrigger) {
            CacheGatilhoInventario.invalidar(advancements);
        }
    }

    @Inject(method = "removePlayerListeners", at = @At("HEAD"))
    private void hlotimizacao$invalidarTodos(PlayerAdvancements advancements, CallbackInfo ci) {
        if ((Object) this instanceof InventoryChangeTrigger) {
            CacheGatilhoInventario.invalidar(advancements);
        }
    }
}
