package com.hlotimizacao.mixin.trigger;

import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.MinMaxBounds;
import com.hlotimizacao.duck.CriterioDeSlots;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Guarda em cada criterio, calculado uma vez, se ele pode passar a ser verdadeiro quando uma pilha
 * do inventario fica vazia.
 */
@Mixin(InventoryChangeTrigger.TriggerInstance.class)
public abstract class TriggerInstanceMixin implements CriterioDeSlots {
    @Shadow
    private MinMaxBounds.Ints slotsOccupied;
    @Shadow
    private MinMaxBounds.Ints slotsFull;
    @Shadow
    private MinMaxBounds.Ints slotsEmpty;

    @Unique
    private byte hlotimizacao$cache = -1;

    @Override
    public boolean hlotimizacao$podeMudarAoEsvaziar() {
        if (this.hlotimizacao$cache < 0) {
            // Esvaziar uma pilha diminui "ocupados" e "cheios" e aumenta "vazios". So limites
            // nessas direcoes podem passar a ser satisfeitos.
            boolean pode = this.slotsOccupied.getMax() != null
                    || this.slotsFull.getMax() != null
                    || this.slotsEmpty.getMin() != null;
            this.hlotimizacao$cache = (byte) (pode ? 1 : 0);
        }
        return this.hlotimizacao$cache == 1;
    }
}
