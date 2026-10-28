package com.hlotimizacao.mixin.trigger;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;

@Mixin(SimpleCriterionTrigger.class)
public interface SimpleCriterionTriggerAccessor {
    @Accessor("players")
    Map<PlayerAdvancements, Set<CriterionTrigger.Listener<?>>> hlotimizacao$getPlayers();
}
