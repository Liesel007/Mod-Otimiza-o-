package com.hlotimizacao.benchmark.mixin;

import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mixin que falha de proposito (metodo inexistente), para testar o detector do relatorio_conflitos. */
@Mixin(value = Block.class, remap = false)
public abstract class MixinQueFalha {
    @Inject(method = "metodoQueNaoExiste", at = @At("HEAD"), require = 1)
    private void hlbench$nunca(CallbackInfo ci) {
    }
}
