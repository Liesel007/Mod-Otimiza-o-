package com.hlotimizacao.mixin.trigger;

import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.MinMaxBounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InventoryChangeTrigger.TriggerInstance.class)
public interface TriggerInstanceAccessor {
    @Accessor("slotsOccupied")
    MinMaxBounds.Ints hlotimizacao$slotsOccupied();

    @Accessor("slotsFull")
    MinMaxBounds.Ints hlotimizacao$slotsFull();

    @Accessor("slotsEmpty")
    MinMaxBounds.Ints hlotimizacao$slotsEmpty();
}
