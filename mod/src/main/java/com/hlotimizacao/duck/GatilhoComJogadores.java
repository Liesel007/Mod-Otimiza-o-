package com.hlotimizacao.duck;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.server.PlayerAdvancements;

import java.util.Set;

public interface GatilhoComJogadores {
    Set<CriterionTrigger.Listener<?>> hlotimizacao$listeners(PlayerAdvancements advancements);
}
