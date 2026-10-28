package com.hlotimizacao.mixin.save;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.recursos.GravadorAssincrono;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O vanilla serializa e grava (GZIP + disco) todos os SavedData sujos na thread principal.
 * Aqui a serializacao continua na thread principal (le estado do jogo), e so a gravacao vai para
 * outra thread.
 *
 * Correcoes em relacao ao Fast Async World Save:
 * - SavedData que sobrescrevem save(File) (mods com formato proprio) sao salvos do jeito deles,
 *   na thread principal, em vez de terem essa logica ignorada;
 * - ao parar o servidor, as gravacoes pendentes sao esperadas (ver HLOtimizacao).
 */
@Mixin(DimensionDataStorage.class)
public abstract class DimensionDataStorageMixin {
    @Unique
    private static final Map<Class<?>, Boolean> hlotimizacao$sobrescreveSave = new ConcurrentHashMap<>();
    @Unique
    private static Method hlotimizacao$saveArquivo;

    @Shadow
    @Final
    private Map<String, SavedData> cache;

    @Shadow
    protected abstract File getDataFile(String name);

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$salvarAssincrono(CallbackInfo ci) {
        for (Map.Entry<String, SavedData> entrada : this.cache.entrySet()) {
            SavedData dados = entrada.getValue();
            if (dados == null) {
                continue;
            }
            File arquivo = this.getDataFile(entrada.getKey());
            if (hlotimizacao$temSaveProprio(dados.getClass())) {
                dados.save(arquivo);
                continue;
            }
            if (!dados.isDirty()) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            try {
                // copy(): alguns mods devolvem um CompoundTag interno que continuam alterando depois;
                // sem a copia, a thread de gravacao poderia ler o tag no meio de uma alteracao.
                tag.put("data", dados.save(new CompoundTag()).copy());
            } catch (Exception e) {
                Config.LOGGER.error("Falha ao serializar {}; o mod responsavel por esses dados tem um bug", entrada.getKey(), e);
                continue;
            }
            NbtUtils.addCurrentDataVersion(tag);
            dados.setDirty(false);
            GravadorAssincrono.gravar(tag, arquivo);
        }
        ci.cancel();
    }

    @Unique
    private static boolean hlotimizacao$temSaveProprio(Class<?> classe) {
        return hlotimizacao$sobrescreveSave.computeIfAbsent(classe, c -> {
            try {
                if (hlotimizacao$saveArquivo == null) {
                    hlotimizacao$saveArquivo = ObfuscationReflectionHelper.findMethod(SavedData.class, "m_77757_", File.class);
                }
                return c.getMethod(hlotimizacao$saveArquivo.getName(), File.class).getDeclaringClass() != SavedData.class;
            } catch (Throwable t) {
                return true; // na duvida, usa o caminho original
            }
        });
    }
}
