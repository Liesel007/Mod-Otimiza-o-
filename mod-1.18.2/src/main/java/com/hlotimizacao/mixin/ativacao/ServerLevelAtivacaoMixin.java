package com.hlotimizacao.mixin.ativacao;

import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.recursos.AlcanceAtivacao;
import com.hlotimizacao.recursos.Custo;
import com.hlotimizacao.recursos.ModoSeguro;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Alcance de ativacao: entidades longe dos jogadores so passam pelo tick 1 vez a cada N ticks
 * (ver AlcanceAtivacao). Nos ticks pulados faz so o que o vanilla faz antes do tick (guardar a
 * posicao anterior e avancar o tickCount).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelAtivacaoMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void hlotimizacao$alcanceAtivacao(Entity entidade, CallbackInfo ci) {
        if (ModoSeguro.desligado(Modulo.ALCANCE_ATIVACAO)) {
            return;
        }
        long inicio = Custo.inicio();
        boolean processar;
        try {
            ModoSeguro.testar(Modulo.ALCANCE_ATIVACAO);
            processar = AlcanceAtivacao.deveProcessar((ServerLevel) (Object) this, entidade);
        } catch (RuntimeException e) {
            ModoSeguro.falhou(Modulo.ALCANCE_ATIVACAO, e);
            processar = true;
        }
        if (!processar) {
            entidade.setOldPosAndRot();
            entidade.tickCount++;
            ci.cancel();
        }
        Custo.fim(inicio);
    }
}
