package com.hlotimizacao.mixin.salvamentogradual;

import com.hlotimizacao.recursos.SalvamentoGradual;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

/** salvamento_gradual: a fila de chunks a salvar de cada dimensao (ver SalvamentoGradual). */
@Mixin(ChunkMap.class)
public abstract class ChunkMapGradualMixin implements SalvamentoGradual.MapaGradual {
    @Unique
    private final ArrayDeque<ChunkHolder> hlotimizacao$fila = new ArrayDeque<>();

    @Shadow
    protected abstract Iterable<ChunkHolder> getChunks();

    @Shadow
    protected abstract boolean saveChunkIfNeeded(ChunkHolder holder);

    @Inject(method = "saveAllChunks", at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$gradual(boolean flush, CallbackInfo ci) {
        if (SalvamentoGradual.interceptar(this, flush)) {
            ci.cancel();
        }
    }

    @Override
    public void hlotimizacao$enfileirar() {
        hlotimizacao$fila.clear(); // um autosave novo substitui o que sobrou do anterior (getChunks traz todos)
        for (ChunkHolder holder : getChunks()) {
            if (holder.wasAccessibleSinceLastSave()) {
                hlotimizacao$fila.add(holder);
            }
        }
    }

    @Override
    public void hlotimizacao$limparFila() {
        hlotimizacao$fila.clear();
    }

    @Override
    public int hlotimizacao$passo(long ateNs) {
        int salvos = 0;
        while (!hlotimizacao$fila.isEmpty() && System.nanoTime() < ateNs) {
            if (saveChunkIfNeeded(hlotimizacao$fila.poll())) {
                salvos++;
            }
        }
        return salvos;
    }

    @Override
    public int hlotimizacao$pendentes() {
        return hlotimizacao$fila.size();
    }
}
