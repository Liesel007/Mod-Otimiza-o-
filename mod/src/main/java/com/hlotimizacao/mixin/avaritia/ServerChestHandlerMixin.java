package com.hlotimizacao.mixin.avaritia;

import com.hlotimizacao.duck.BauInfinito;
import committee.nova.mods.avaritia.core.chest.ServerChestHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marca o bau como alterado; so baus alterados sao regravados no save. */
@Mixin(value = ServerChestHandler.class, remap = false)
public abstract class ServerChestHandlerMixin implements BauInfinito {
    @Unique
    private volatile boolean hlotimizacao$sujo;

    @Inject(method = "onItemChanged", at = @At("HEAD"))
    private void hlotimizacao$marcarSujo(String itemId, boolean listaMudou, CallbackInfo ci) {
        hlotimizacao$sujo = true;
    }

    @Override
    public boolean hlotimizacao$sujo() {
        return hlotimizacao$sujo;
    }

    @Override
    public void hlotimizacao$setSujo(boolean sujo) {
        hlotimizacao$sujo = sujo;
    }
}
