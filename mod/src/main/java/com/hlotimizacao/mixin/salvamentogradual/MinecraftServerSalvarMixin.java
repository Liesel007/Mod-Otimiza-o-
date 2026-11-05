package com.hlotimizacao.mixin.salvamentogradual;

import com.hlotimizacao.recursos.SalvamentoGradual;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marca quando o saveEverything em andamento e o autosave (sem log, sem flush, nao forcado). */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSalvarMixin {
    @Inject(method = "saveEverything", at = @At("HEAD"))
    private void hlotimizacao$inicio(boolean semLog, boolean flush, boolean forcado, CallbackInfoReturnable<Boolean> cir) {
        SalvamentoGradual.inicioSaveEverything((MinecraftServer) (Object) this, semLog, flush, forcado);
    }

    @Inject(method = "saveEverything", at = @At("RETURN"))
    private void hlotimizacao$fim(boolean semLog, boolean flush, boolean forcado, CallbackInfoReturnable<Boolean> cir) {
        SalvamentoGradual.fimSaveEverything();
    }
}
