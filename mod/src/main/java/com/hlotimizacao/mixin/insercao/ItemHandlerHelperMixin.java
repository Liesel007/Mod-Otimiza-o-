package com.hlotimizacao.mixin.insercao;

import com.hlotimizacao.recursos.Insercao;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ponto unico da insercao em inventarios: diagnostico, atalho do Infinity Chest e insercao_rapida (ver Insercao). */
@Mixin(value = ItemHandlerHelper.class, remap = false)
public abstract class ItemHandlerHelperMixin {
    @Inject(method = "insertItemStacked", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hlotimizacao$inserir(IItemHandler destino, ItemStack pilha, boolean simular, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack resto = Insercao.aoInserir(destino, pilha, simular);
        if (resto != null) {
            cir.setReturnValue(resto);
        }
    }
}
