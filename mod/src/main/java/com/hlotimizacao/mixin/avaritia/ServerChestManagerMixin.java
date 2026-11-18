package com.hlotimizacao.mixin.avaritia;

import com.hlotimizacao.recursos.ChavesInfinityChest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/** Antes de carregar os baus: copia de seguranca (.hlbak) dos arquivos que vao ser corrigidos, e o tamanho deles. */
@Pseudo
@Mixin(targets = "committee.nova.mods.avaritia.core.chest.ServerChestManager", remap = false)
public abstract class ServerChestManagerMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "load", at = @At("HEAD"), require = 0)
    private void hlotimizacao$copiaDeSeguranca(CallbackInfo ci) {
        ChavesInfinityChest.prepararPasta(new File(this.server.getWorldPath(LevelResource.ROOT).toFile(), "data/avaritia/infinity_chest"));
        ChavesInfinityChest.registrarTamanho();
    }

    /** Tempo de cada gravacao dos baus (o Re-Avaritia grava todos a cada LevelEvent.Save, uma vez por dimensao). */
    @Inject(method = "save", at = @At("HEAD"), require = 0)
    private void hlotimizacao$inicioGravacao(MinecraftServer servidor, CallbackInfo ci) {
        ChavesInfinityChest.inicioGravacao();
    }

    @Inject(method = "save", at = @At("RETURN"), require = 0)
    private void hlotimizacao$fimGravacao(MinecraftServer servidor, CallbackInfo ci) {
        ChavesInfinityChest.fimGravacao();
    }
}
