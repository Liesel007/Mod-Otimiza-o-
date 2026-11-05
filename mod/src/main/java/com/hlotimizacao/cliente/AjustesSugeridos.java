package com.hlotimizacao.cliente;

import com.hlotimizacao.recursos.MonitorGc;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * "O que pode ser ajustado" (aba FPS): sugestoes calculadas a partir da maquina e das opcoes do jogo.
 * Nunca muda nada sozinho: opcoes do jogo, argumentos da JVM e configs de outros mods ficam com o jogador.
 */
public final class AjustesSugeridos {
    private AjustesSugeridos() {
    }

    private static String t(String chave, Object... args) {
        return Component.translatable("hlotimizacao.tela." + chave, args).getString();
    }

    /** {texto curto, explicacao} */
    public static List<String[]> calcular() {
        List<String[]> lista = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return lista;
        }
        double escala = mc.options.entityDistanceScaling().get();
        int entidades = mc.level == null ? 0 : mc.level.getEntityCount();
        if (escala > 1.0 && entidades > 200) {
            lista.add(new String[]{t("ajuste_entidades", Math.round(escala * 100), entidades), t("ajuste_entidades_dica")});
        }
        int mistura = mc.options.biomeBlendRadius().get();
        if (mistura > 0 && MonitorGc.gpuIntegrada) {
            lista.add(new String[]{t("ajuste_biomas", mistura), t("ajuste_biomas_dica", MonitorGc.placaDeVideo)});
        }
        if (mc.options.getEffectiveRenderDistance() > 10 && MonitorGc.gpuIntegrada) {
            lista.add(new String[]{t("ajuste_distancia", mc.options.getEffectiveRenderDistance()), t("ajuste_distancia_dica")});
        }
        if (mc.hasSingleplayerServer() && ModList.get().isLoaded("chunky")) {
            lista.add(new String[]{t("ajuste_chunky"), t("ajuste_chunky_dica")});
        }
        long jeiMs = TempoJei.ms();
        if (jeiMs > 20_000) {
            lista.add(new String[]{t("ajuste_jei", Math.round(jeiMs / 1000.0)), t("ajuste_jei_dica")});
        }
        return lista;
    }
}
