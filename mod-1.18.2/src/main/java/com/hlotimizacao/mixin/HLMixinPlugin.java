package com.hlotimizacao.mixin;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.config.Msg;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Aplica os mixins de cada modulo so se o modulo estiver ativo. */
public class HLMixinPlugin implements IMixinConfigPlugin {
    private static final String RAIZ = "com.hlotimizacao.";

    @Override
    public void onLoad(String mixinPackage) {
        Config.carregar();
        if (Config.ativo(Modulo.RELATORIO_CONFLITOS)) {
            try {
                org.spongepowered.asm.mixin.Mixins.registerErrorHandlerClass(ErrosMixin.class.getName());
            } catch (Throwable t) {
                Config.LOGGER.debug("Nao foi possivel registrar o leitor de erros de mixin", t);
            }
        }
        Config.LOGGER.info("{} mods detectados -> preset \"{}\"{} (mude com preset= em config/hlotimizacao.properties)",
                Config.modsCarregados(), Config.preset().nome(), Config.presetAutomatico() ? "" : Msg.t(" (escolhido na config)"));
        for (Modulo modulo : Modulo.values()) {
            Config.LOGGER.info("Modulo {}: {}", modulo.chave, Config.motivo(modulo));
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(RAIZ + "mixin.insercao.")) {
            // Um ponto so (a insercao do Forge) serve ao diagnostico, ao atalho do Infinity Chest e a insercao_rapida.
            return Config.ativo(Modulo.DIAGNOSTICO_INSERCAO) || Config.ativo(Modulo.INSERCAO_RAPIDA) || Config.ativo(Modulo.CORRECAO_INFINITY_CHEST);
        }
        for (Modulo modulo : Modulo.values()) {
            if (modulo.pacoteMixin != null && mixinClassName.startsWith(RAIZ + modulo.pacoteMixin + ".")) {
                return Config.ativo(modulo);
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
