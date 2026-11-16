package com.hlotimizacao.mixin.salvamentovazio;

import com.hlotimizacao.recursos.SalvamentoSeletivo;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marca o salvamento parcial (autosave e pausa); /save-all (forcado) e fechar o mundo gravam tudo. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSeletivoMixin {
    @Inject(method = "saveEverything", at = @At("HEAD"))
    private void hlotimizacao$inicioSeletivo(boolean semLog, boolean flush, boolean forcado, CallbackInfoReturnable<Boolean> cir) {
        SalvamentoSeletivo.inicioSaveEverything(flush, forcado);
    }

    @Inject(method = "saveEverything", at = @At("RETURN"))
    private void hlotimizacao$fimSeletivo(boolean semLog, boolean flush, boolean forcado, CallbackInfoReturnable<Boolean> cir) {
        SalvamentoSeletivo.fimSaveEverything();
    }
}
