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

/** Antes de carregar os baus: copia de seguranca (.hlbak) dos arquivos que vao ser corrigidos. */
@Pseudo
@Mixin(targets = "committee.nova.mods.avaritia.core.chest.ServerChestManager", remap = false)
public abstract class ServerChestManagerMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "load", at = @At("HEAD"), require = 0)
    private void hlotimizacao$copiaDeSeguranca(CallbackInfo ci) {
        ChavesInfinityChest.prepararPasta(new File(this.server.getWorldPath(LevelResource.ROOT).toFile(), "data/avaritia/infinity_chest"));
    }
}
