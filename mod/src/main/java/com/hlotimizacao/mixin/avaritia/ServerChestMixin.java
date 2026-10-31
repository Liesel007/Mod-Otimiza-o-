package com.hlotimizacao.mixin.avaritia;

import com.hlotimizacao.recursos.ChavesInfinityChest;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Normaliza as chaves de cada bau quando ele e carregado (ver ChavesInfinityChest). */
@Pseudo
@Mixin(targets = "committee.nova.mods.avaritia.core.chest.ServerChestHandler", remap = false)
public abstract class ServerChestMixin {
    @Inject(method = "initialize", at = @At("HEAD"), require = 0)
    private void hlotimizacao$normalizar(CompoundTag dados, CallbackInfo ci) {
        ChavesInfinityChest.aoInicializar(dados);
    }
}
