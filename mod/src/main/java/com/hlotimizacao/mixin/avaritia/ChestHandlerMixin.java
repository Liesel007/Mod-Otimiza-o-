package com.hlotimizacao.mixin.avaritia;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Duas correcoes no ChestHandler do Re-Avaritia:
 * - takeItem/saveTakeItem apagam o NBT guardado mesmo quando so parte da pilha sai; o que fica no bau
 *   passa a sair sem NBT. Agora o NBT so e apagado quando a entrada inteira sai.
 * - cada item novo reconstruia a lista de chaves inteira (updateItemKeys, um ItemStack por entrada).
 *   Agora a lista so e marcada como suja e e reconstruida na proxima leitura.
 */
@Pseudo
@Mixin(targets = "committee.nova.mods.avaritia.core.chest.ChestHandler", remap = false)
public abstract class ChestHandlerMixin {
    @Shadow
    @Final
    public HashMap<String, Long> storageItems;

    @Unique
    private volatile boolean hlotimizacao$chavesSujas;

    @Shadow
    public abstract void updateItemKeys();

    @Redirect(method = {"takeItem", "saveTakeItem"},
            at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"), require = 0)
    private Object hlotimizacao$manterNbtDoQueFica(Map<?, ?> cache, Object chave) {
        if (this.storageItems.containsKey(chave)) {
            return null; // ainda ha itens com essa chave no bau: o NBT deles continua guardado
        }
        return cache.remove(chave);
    }

    @Redirect(method = "onItemChanged",
            at = @At(value = "INVOKE", target = "Lcommittee/nova/mods/avaritia/core/chest/ChestHandler;updateItemKeys()V"), require = 0)
    private void hlotimizacao$adiarChaves(@Coerce Object self) {
        this.hlotimizacao$chavesSujas = true;
    }

    @Inject(method = "updateItemKeys", at = @At("HEAD"), require = 0)
    private void hlotimizacao$chavesLimpas(CallbackInfo ci) {
        this.hlotimizacao$chavesSujas = false;
    }

    @Inject(method = {"getStackInSlot", "extractItem"}, at = @At("HEAD"), require = 0)
    private void hlotimizacao$atualizarSeSujo(CallbackInfoReturnable<?> cir) {
        if (this.hlotimizacao$chavesSujas) {
            this.updateItemKeys();
        }
    }
}
