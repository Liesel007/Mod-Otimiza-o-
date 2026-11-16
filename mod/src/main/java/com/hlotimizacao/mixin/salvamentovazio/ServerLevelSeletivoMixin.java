package com.hlotimizacao.mixin.salvamentovazio;

import com.hlotimizacao.recursos.SalvamentoSeletivo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pula o salvamento de dimensoes paradas no autosave e na pausa (ver SalvamentoSeletivo). */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSeletivoMixin {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$pularParada(ProgressListener progresso, boolean flush, boolean pular, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (SalvamentoSeletivo.pularDimensao(level, ((DimensionDataStorageAcessor) level.getDataStorage()).hlotimizacao$dadosSeletivo())) {
            ci.cancel();
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void hlotimizacao$salvou(ProgressListener progresso, boolean flush, boolean pular, CallbackInfo ci) {
        SalvamentoSeletivo.salvou((ServerLevel) (Object) this);
    }
}
