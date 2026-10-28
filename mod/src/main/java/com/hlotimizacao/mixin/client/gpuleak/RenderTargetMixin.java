package com.hlotimizacao.mixin.client.gpuleak;

import com.hlotimizacao.recursos.LiberadorGpu;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {
    @Shadow
    public int frameBufferId;
    @Shadow
    protected int colorTextureId;
    @Shadow
    protected int depthBufferId;

    @Unique
    private LiberadorGpu.Ids hlotimizacao$ids;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void hlotimizacao$registrar(boolean useDepth, CallbackInfo ci) {
        this.hlotimizacao$ids = LiberadorGpu.registrar(this);
    }

    @Inject(method = "createBuffers", at = @At("RETURN"))
    private void hlotimizacao$aoCriar(int width, int height, boolean clearError, CallbackInfo ci) {
        this.hlotimizacao$ids.atualizar(this.frameBufferId, this.colorTextureId, this.depthBufferId);
    }

    @Inject(method = "destroyBuffers", at = @At("RETURN"))
    private void hlotimizacao$aoDestruir(CallbackInfo ci) {
        this.hlotimizacao$ids.atualizar(-1, -1, -1);
    }
}
