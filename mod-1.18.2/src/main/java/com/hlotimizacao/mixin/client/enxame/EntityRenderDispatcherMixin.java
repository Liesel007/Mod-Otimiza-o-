package com.hlotimizacao.mixin.client.enxame;

import com.hlotimizacao.cliente.RenderEnxame;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.recursos.ModoSeguro;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** render_enxame: o desenho de cada entidade pergunta se ela esta entre as escondidas (ver RenderEnxame). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void hlotimizacao$enxame(E entidade, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (ModoSeguro.desligado(Modulo.RENDER_ENXAME)) {
            return;
        }
        try {
            if (RenderEnxame.esconder(entidade)) {
                cir.setReturnValue(false);
            }
        } catch (RuntimeException e) {
            ModoSeguro.falhou(Modulo.RENDER_ENXAME, e);
        }
    }

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hlotimizacao$semSombra(PoseStack pose, MultiBufferSource buffers, Entity entidade, float opacidade, float parcial,
                                               LevelReader nivel, float raio, CallbackInfo ci) {
        if (!ModoSeguro.desligado(Modulo.RENDER_ENXAME) && RenderEnxame.semSombra(entidade)) {
            ci.cancel();
        }
    }
}
