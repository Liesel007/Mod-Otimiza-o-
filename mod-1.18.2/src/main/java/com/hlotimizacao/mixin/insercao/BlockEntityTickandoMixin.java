package com.hlotimizacao.mixin.insercao;

import com.hlotimizacao.recursos.Insercao;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guarda qual block entity esta tickando, para o diagnostico dizer quem despeja itens. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BlockEntityTickandoMixin {
    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void hlotimizacao$inicio(CallbackInfo ci) {
        if (this.blockEntity.getLevel() != null && !this.blockEntity.getLevel().isClientSide) {
            Insercao.tickando = this.blockEntity;
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void hlotimizacao$fim(CallbackInfo ci) {
        Insercao.tickando = null;
    }
}
