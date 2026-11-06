package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Correcao do Infinity Chest do Re-Avaritia (1.4.2).
 *
 * No Re-Avaritia, a chave de um item guardado e "id" ou "id#hash" (item com NBT). O
 * StorageUtils.getItem(chave) guarda a chave inteira no cache item -> id; dai em diante
 * getItemId(item) devolve "id#hash" e getNbtItemId acrescenta outro "#hash". As chaves crescem a cada
 * ciclo, o mesmo item vira varias entradas e o estado enviado ao abrir o bau cresce sem limite. Itens
 * sem NBT tambem acabam guardados com "#hash". Os dois caches sao HashMap comuns, usados ao mesmo tempo
 * pelas threads do cliente e do servidor no singleplayer.
 *
 * Aqui: caches proprios e thread-safe (mixin em StorageUtils) e, ao carregar o bau, cada chave e
 * reconstruida a partir do id puro + hash do NBT guardado, somando as entradas que ficam iguais.
 */
public final class ChavesInfinityChest {
    private static final Map<Item, String> ID_POR_ITEM = new ConcurrentHashMap<>();
    private static final Map<String, Item> ITEM_POR_ID = new ConcurrentHashMap<>();
    private static Method hashNbt;

    public static final AtomicLong CHAVES_CORRIGIDAS = new AtomicLong();
    public static final AtomicLong ENTRADAS_MESCLADAS = new AtomicLong();
    public static final AtomicLong BAUS_CORRIGIDOS = new AtomicLong();

    private ChavesInfinityChest() {
    }

    /** Id do item, sempre o do registro (nunca uma chave com "#hash"). */
    public static String idDoItem(Item item) {
        return ID_POR_ITEM.computeIfAbsent(item, i -> {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(i);
            return id == null ? "minecraft:air" : id.toString();
        });
    }

    /** Item de uma chave ("id" ou "id#hash..."), sem guardar a chave inteira em cache nenhum. */
    public static Item itemDaChave(String chave) {
        // Caminho rapido pela chave inteira. Com as chaves normalizadas, sao no maximo tantas quanto as
        // entradas dos baus; sem a correcao, chaves infladas poderiam encher o cache, por isso o limite.
        Item cache = ITEM_POR_ID.get(chave);
        if (cache != null) {
            return cache;
        }
        Item item = itemDoId(base(chave));
        if (ITEM_POR_ID.size() < 100_000) {
            ITEM_POR_ID.put(chave, item);
        }
        return item;
    }

    private static Item itemDoId(String base) {
        return ITEM_POR_ID.computeIfAbsent(base, b -> {
            ResourceLocation id = ResourceLocation.tryParse(b);
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            return item == null ? Items.AIR : item;
        });
    }

    public static String base(String chave) {
        int i = chave.indexOf('#');
        return i < 0 ? chave : chave.substring(0, i);
    }

    /** O mesmo hash que o Re-Avaritia usa (chamado por reflexao, para ficar identico). */
    static String hash(Tag tag) {
        try {
            if (hashNbt == null) {
                hashNbt = Class.forName("committee.nova.mods.avaritia.util.StorageUtils").getMethod("hashNbt", Tag.class);
            }
            return (String) hashNbt.invoke(null, tag);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("StorageUtils.hashNbt do Re-Avaritia nao encontrado", e);
        }
    }

    public record Resultado(int corrigidas, int mescladas, int entradasAntes, int entradasDepois) {
        public boolean mudou() {
            return corrigidas > 0 || mescladas > 0;
        }
    }

    /**
     * Normaliza os dados de um bau ("items": chave -> quantidade, "nbtData": chave -> NBT) no proprio tag.
     * Chave com NBT guardado: id + "#" + hash(NBT). Chave com "#" mas sem NBT guardado: era um item sem
     * NBT guardado com uma chave errada (o bau o devolveria sem NBT), entao vira so o id.
     */
    public static Resultado normalizar(CompoundTag dados) {
        if (!dados.contains("items", Tag.TAG_COMPOUND)) {
            return new Resultado(0, 0, 0, 0);
        }
        CompoundTag itens = dados.getCompound("items");
        CompoundTag nbt = dados.getCompound("nbtData");
        Map<String, Long> novosItens = new LinkedHashMap<>();
        Map<String, CompoundTag> novoNbt = new LinkedHashMap<>();
        int corrigidas = 0;
        int mescladas = 0;
        for (String chave : itens.getAllKeys()) {
            long quantidade = itens.getLong(chave);
            if (quantidade <= 0) {
                continue;
            }
            CompoundTag tag = nbt.get(chave) instanceof CompoundTag c ? c : null;
            String canonica = tag != null ? base(chave) + "#" + hash(tag) : base(chave);
            if (!canonica.equals(chave)) {
                corrigidas++;
            }
            Long anterior = novosItens.get(canonica);
            if (anterior != null) {
                mescladas++;
                long soma = anterior + quantidade;
                novosItens.put(canonica, soma < 0 ? Long.MAX_VALUE : soma);
            } else {
                novosItens.put(canonica, quantidade);
            }
            if (tag != null) {
                novoNbt.putIfAbsent(canonica, tag);
            }
        }
        Resultado r = new Resultado(corrigidas, mescladas, itens.size(), novosItens.size());
        if (!r.mudou()) {
            return r;
        }
        CompoundTag itensNovos = new CompoundTag();
        novosItens.forEach(itensNovos::putLong);
        CompoundTag nbtNovo = new CompoundTag();
        novoNbt.forEach(nbtNovo::put);
        dados.put("items", itensNovos);
        dados.put("nbtData", nbtNovo);
        return r;
    }

    /**
     * Antes de o Re-Avaritia carregar os baus: para cada arquivo que vai ser corrigido, faz uma copia
     * ".hlbak" (so a primeira vez, para guardar o original) e registra o que sera feito.
     */
    public static void prepararPasta(File pasta) {
        File[] donos = pasta.listFiles(File::isDirectory);
        if (donos == null) {
            return;
        }
        for (File dono : donos) {
            File[] arquivos = dono.listFiles(f -> f.isFile() && f.getName().endsWith(".dat"));
            if (arquivos == null) {
                continue;
            }
            for (File arquivo : arquivos) {
                try {
                    CompoundTag dados = NbtIo.readCompressed(arquivo);
                    Resultado r = normalizar(dados);
                    if (!r.mudou()) {
                        continue;
                    }
                    File copia = new File(arquivo.getPath() + ".hlbak");
                    if (!copia.exists()) {
                        Files.copy(arquivo.toPath(), copia.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    }
                    BAUS_CORRIGIDOS.incrementAndGet();
                    Config.LOGGER.warn("[correcao_infinity_chest] Infinity Chest {}/{}: {} chave(s) com '#hash' repetido ou errado, "
                                    + "{} entrada(s) duplicada(s) somada(s) ({} -> {} entradas). Copia do original: {}",
                            dono.getName(), arquivo.getName(), r.corrigidas(), r.mescladas(), r.entradasAntes(), r.entradasDepois(), copia.getName());
                } catch (IOException | RuntimeException e) {
                    Config.LOGGER.error("[correcao_infinity_chest] Nao foi possivel verificar {}", arquivo, e);
                }
            }
        }
    }

    /** Chamado quando o Re-Avaritia inicializa um bau a partir do tag salvo. */
    public static void aoInicializar(CompoundTag dados) {
        try {
            Resultado r = normalizar(dados);
            if (r.mudou()) {
                CHAVES_CORRIGIDAS.addAndGet(r.corrigidas());
                ENTRADAS_MESCLADAS.addAndGet(r.mescladas());
            }
        } catch (RuntimeException e) {
            Config.LOGGER.error("[correcao_infinity_chest] Erro ao normalizar um Infinity Chest; carregando como esta", e);
        }
    }
}
