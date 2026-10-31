package com.hlotimizacao.mixin.avaritia;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.duck.BauInfinito;
import com.hlotimizacao.recursos.CorrecaoInfinityChest;
import com.hlotimizacao.recursos.Estatisticas;
import com.hlotimizacao.recursos.GravadorAssincrono;
import committee.nova.mods.avaritia.core.chest.ServerChestHandler;
import committee.nova.mods.avaritia.core.chest.ServerChestManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

/**
 * Salvamento dos Infinity Chests do Re-Avaritia 1.4.2. O original:
 * - salva em LevelEvent.Save, que o Forge dispara uma vez POR DIMENSAO: num pack com 30 dimensoes,
 *   todos os baus sao serializados, comprimidos e gravados 30 vezes a cada autosave;
 * - grava todos os baus, mesmo os que nao mudaram;
 * - faz tudo isso na thread do servidor (com antivirus, so abrir cada arquivo pode levar segundos).
 *
 * Aqui: salva so no evento do overworld, so os baus alterados (ou sem arquivo), serializa na thread
 * do servidor e grava em outra thread (a mesma fila do salvamento_assincrono, esperada ao fechar).
 * Ao carregar, conserta as chaves que cresceram com o bug do StorageUtils (ver CorrecaoInfinityChest).
 */
@Mixin(value = ServerChestManager.class, remap = false)
public abstract class ServerChestManagerMixin {
    @Shadow
    private File saveDataPath;
    @Shadow
    private boolean loadSuccess;
    @Shadow
    @Final
    private MinecraftServer server;
    @Shadow
    @Final
    private HashMap<UUID, HashMap<UUID, ServerChestHandler>> chestList;

    @Inject(method = "onLevelSave", at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$soNoOverworld(LevelEvent.Save event, CallbackInfo ci) {
        if (event.getLevel() instanceof ServerLevel nivel && nivel.dimension() != Level.OVERWORLD) {
            Estatisticas.AVARITIA_SAVES_EVITADOS.incrementAndGet();
            ci.cancel();
        }
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$salvar(MinecraftServer servidor, CallbackInfo ci) {
        ci.cancel();
        if (!loadSuccess) {
            return;
        }
        chestList.forEach((dono, baus) -> {
            File pasta = new File(saveDataPath, dono.toString());
            baus.forEach((id, bau) -> {
                File arquivo = new File(pasta, id + ".dat");
                BauInfinito estado = (BauInfinito) (Object) bau;
                if (!estado.hlotimizacao$sujo() && arquivo.exists()) {
                    Estatisticas.AVARITIA_BAUS_PULADOS.incrementAndGet();
                    return;
                }
                CompoundTag dados;
                try {
                    // copy(): o NBT dos itens guardados e compartilhado com o bau, que continua mudando.
                    dados = bau.buildData().copy();
                } catch (Exception e) {
                    Config.LOGGER.error("[correcao_infinity_chest] Falha ao serializar o Infinity Chest {}/{}", dono, id, e);
                    return;
                }
                estado.hlotimizacao$setSujo(false);
                Estatisticas.AVARITIA_BAUS_GRAVADOS.incrementAndGet();
                GravadorAssincrono.gravar(dados, arquivo.toPath(), "avaritia/infinity_chest/" + dono + "/" + id,
                        hlotimizacao$remarcar(estado));
            });
        });
    }

    @Unique
    private Runnable hlotimizacao$remarcar(BauInfinito estado) {
        MinecraftServer servidor = this.server;
        return () -> {
            if (servidor != null && servidor.isRunning()) {
                servidor.execute(() -> estado.hlotimizacao$setSujo(true));
            }
        };
    }

    /** Apaga na mesma fila das gravacoes, para uma gravacao pendente nao recriar o arquivo depois. */
    @Redirect(method = "tryRemoveChest", at = @At(value = "INVOKE", target = "Ljava/io/File;delete()Z"))
    private boolean hlotimizacao$apagarNaFila(File arquivo) {
        GravadorAssincrono.apagar(arquivo.toPath());
        return true;
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void hlotimizacao$repararAoCarregar(CallbackInfo ci) {
        chestList.forEach((dono, baus) -> baus.forEach((id, bau) -> {
            File arquivo = new File(new File(saveDataPath, dono.toString()), id + ".dat");
            if (CorrecaoInfinityChest.reparar(bau, arquivo, dono + "/" + id)) {
                ((BauInfinito) (Object) bau).hlotimizacao$setSujo(true);
            }
        }));
    }
}
