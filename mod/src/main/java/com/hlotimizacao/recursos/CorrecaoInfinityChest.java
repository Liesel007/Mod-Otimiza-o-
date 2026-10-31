package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.mixin.avaritia.ChestHandlerAccessor;
import committee.nova.mods.avaritia.core.chest.ChestHandler;
import committee.nova.mods.avaritia.util.StorageUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conserta as chaves de um Infinity Chest do Re-Avaritia 1.4.2 que cresceram com o bug do
 * StorageUtils.getItem ("mod:item#h1#h2#h3...", ver StorageUtilsMixin).
 *
 * Cada chave vira a que o mod corrigido geraria para o mesmo item:
 * - chave com mais de um "#" e NBT guardado: "mod:item#" + hash do NBT guardado;
 * - chave com "#" sem NBT guardado (item sem NBT que caiu numa chave com hash, ou NBT perdido pelo
 *   takeItem): "mod:item";
 * - as outras ficam como estao.
 * Quantidades de chaves que viram a mesma sao somadas (no maximo Long.MAX_VALUE). NBT guardado sem
 * item correspondente e descartado. O arquivo original e copiado para "<id>.dat.antes_hl" antes.
 */
public final class CorrecaoInfinityChest {
    public static final String SUFIXO_COPIA = ".antes_hl";

    private CorrecaoInfinityChest() {
    }

    /** @return true se o bau mudou (e precisa ser regravado) */
    public static boolean reparar(ChestHandler bau, File arquivo, String nome) {
        Map<String, Long> itens = bau.storageItems;
        Map<String, Tag> nbt = ((ChestHandlerAccessor) (Object) bau).hlotimizacao$nbt();
        Resultado r = calcular(itens, nbt);
        if (r == null) {
            return false;
        }
        Path copia = arquivo.toPath().resolveSibling(arquivo.getName() + SUFIXO_COPIA);
        try {
            if (arquivo.exists() && !Files.exists(copia)) {
                Files.copy(arquivo.toPath(), copia);
            }
        } catch (IOException e) {
            Config.LOGGER.error("[correcao_infinity_chest] Nao foi possivel copiar {} antes de consertar; o bau fica como esta", arquivo, e);
            return false;
        }
        itens.clear();
        itens.putAll(r.itens);
        nbt.clear();
        nbt.putAll(r.nbt);
        bau.updateItemKeys();
        Estatisticas.AVARITIA_CHAVES_CONSERTADAS.addAndGet(r.chavesAlteradas);
        Config.LOGGER.warn("[correcao_infinity_chest] Infinity Chest {}: {} entradas -> {} ({} chave(s) com hash repetido ou sem NBT "
                        + "consertada(s), maior chave tinha {} caracteres; {} NBT(s) sem item descartado(s)). Copia do original: {}",
                nome, r.entradasAntes, r.itens.size(), r.chavesAlteradas, r.maiorChave, r.nbtDescartados, copia.getFileName());
        return true;
    }

    record Resultado(Map<String, Long> itens, Map<String, Tag> nbt, int entradasAntes, int chavesAlteradas, int nbtDescartados, int maiorChave) {
    }

    /** null se nada precisa mudar. */
    static Resultado calcular(Map<String, Long> itens, Map<String, Tag> nbt) {
        Map<String, Long> novosItens = new LinkedHashMap<>();
        Map<String, Tag> novosNbt = new HashMap<>();
        int alteradas = 0;
        int maior = 0;
        for (Map.Entry<String, Long> entrada : itens.entrySet()) {
            String chave = entrada.getKey();
            maior = Math.max(maior, chave.length());
            Tag tag = nbt.get(chave);
            String correta = chaveCorreta(chave, tag);
            if (!correta.equals(chave)) {
                alteradas++;
            }
            novosItens.merge(correta, entrada.getValue(), CorrecaoInfinityChest::somar);
            if (tag instanceof CompoundTag && correta.indexOf('#') >= 0) {
                novosNbt.putIfAbsent(correta, tag);
            }
        }
        int descartados = 0;
        for (String chave : nbt.keySet()) {
            if (!itens.containsKey(chave)) {
                descartados++;
            }
        }
        if (alteradas == 0 && descartados == 0) {
            return null;
        }
        return new Resultado(novosItens, novosNbt, itens.size(), alteradas, descartados, maior);
    }

    static String chaveCorreta(String chave, Tag tag) {
        int primeiro = chave.indexOf('#');
        if (primeiro < 0) {
            return chave;
        }
        String base = chave.substring(0, primeiro);
        if (!(tag instanceof CompoundTag composto)) {
            return base;
        }
        if (chave.indexOf('#', primeiro + 1) < 0) {
            return chave; // chave normal de item com NBT
        }
        return base + "#" + StorageUtils.hashNbt(composto);
    }

    private static long somar(long a, long b) {
        long soma = a + b;
        return soma < 0 ? Long.MAX_VALUE : soma;
    }
}
