package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.config.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

/**
 * salvamento_dimensoes_vazias: o autosave e o salvamento ao pausar passam por todas as dimensoes (78 no pack HL), e
 * alguns dados de mod (ConduitSavedData do Ender IO) ficam marcados como alterados sempre, entao sao gravados em toda
 * dimensao, a cada salvamento, na thread principal.
 * <ol>
 * <li><b>Sem mudanca, sem gravacao:</b> antes de gravar um dado marcado como alterado, compara o CRC32 do NBT com o da
 *     ultima gravacao; igual = nao grava (a serializacao continua; disco e compressao nao).</li>
 * <li><b>Dimensao parada:</b> sem jogador e sem chunks carregados desde o ultimo salvamento, e nenhum dado alterado de
 *     verdade (so classes que ja se mostraram "sempre alteradas sem mudar"): o ServerLevel.save dela e pulado.
 *     O Overworld nunca e pulado: dados globais de muitos mods ficam no armazenamento dele.</li>
 * </ol>
 * So no autosave e no salvamento ao pausar (saveEverything nao forcado). Fechar o mundo, /save-all e /save-all flush
 * gravam tudo, de todas as dimensoes, como no vanilla.
 */
public final class SalvamentoSeletivo {
    private static volatile boolean parcial;
    private static final Map<String, Long> CRC_POR_ARQUIVO = new ConcurrentHashMap<>();
    /** classe -> {gravacoes, puladas sem mudanca} */
    private static final Map<String, long[]> POR_CLASSE = new ConcurrentHashMap<>();
    /** Classes que ja chegaram marcadas como alteradas com o conteudo igual: nao impedem pular a dimensao. */
    private static final Set<String> SEMPRE_SUJAS = ConcurrentHashMap.newKeySet();
    /** Dimensoes com jogador ou chunk carregado desde o ultimo salvamento delas. */
    private static final Set<ResourceKey<Level>> ATIVAS = ConcurrentHashMap.newKeySet();
    public static volatile int dimensoesUltimo;
    public static volatile int puladasUltimo;
    public static volatile long dimensoesPuladas;
    private static int contadorTicks;

    private SalvamentoSeletivo() {
    }

    static boolean ligado() {
        return Config.ativo(Modulo.SALVAMENTO_DIMENSOES_VAZIAS) && !ModoSeguro.desligado(Modulo.SALVAMENTO_DIMENSOES_VAZIAS);
    }

    /** saveEverything: autosave (true, false, false) e pausa (false, false, false) sao parciais; /save-all e forcado. */
    public static void inicioSaveEverything(boolean flush, boolean forcado) {
        parcial = !flush && !forcado;
        if (parcial) {
            dimensoesUltimo = 0;
            puladasUltimo = 0;
        }
    }

    public static void fimSaveEverything() {
        parcial = false;
    }

    /**
     * Antes de gravar um SavedData alterado: true = o conteudo e igual ao da ultima gravacao e, num salvamento parcial,
     * nao precisa gravar. Sempre atualiza o CRC (fechar o mundo tambem registra o que gravou).
     */
    public static boolean semMudanca(String arquivo, String classe, CompoundTag tag) {
        if (!Config.ativo(Modulo.SALVAMENTO_DIMENSOES_VAZIAS)) {
            return false;
        }
        long crc = crc(tag);
        Long anterior = CRC_POR_ARQUIVO.put(arquivo, crc);
        boolean igual = anterior != null && anterior == crc;
        long[] v = POR_CLASSE.computeIfAbsent(classe, k -> new long[2]);
        if (igual) {
            SEMPRE_SUJAS.add(classe);
        }
        if (igual && parcial && ligado()) {
            v[1]++;
            return true;
        }
        v[0]++;
        return false;
    }

    static long crc(CompoundTag tag) {
        CRC32 crc = new CRC32();
        try (DataOutputStream saida = new DataOutputStream(new CheckedOutputStream(OutputStream.nullOutputStream(), crc))) {
            NbtIo.write(tag, saida);
        } catch (IOException e) {
            return -1; // nunca acontece com o stream nulo; -1 nunca bate com um CRC real de 32 bits
        }
        return crc.getValue();
    }

    /** ServerLevel.save: true = pular esta dimensao neste salvamento. */
    public static boolean pularDimensao(ServerLevel level, Map<String, SavedData> dados) {
        if (!parcial || !ligado()) {
            return false;
        }
        dimensoesUltimo++;
        if (level.dimension() == Level.OVERWORLD || !level.players().isEmpty() || level.getChunkSource().getLoadedChunksCount() > 0
                || ATIVAS.contains(level.dimension())) {
            return false;
        }
        for (SavedData d : dados.values()) {
            if (d != null && d.isDirty() && !SEMPRE_SUJAS.contains(d.getClass().getName())) {
                return false; // alterado de verdade (ou ainda nao sabemos): salva
            }
        }
        puladasUltimo++;
        dimensoesPuladas++;
        return true;
    }

    /** Depois de salvar uma dimensao: ela so volta a ser "ativa" se tiver chunk ou jogador de novo. */
    public static void salvou(ServerLevel level) {
        ATIVAS.remove(level.dimension());
    }

    public static void aoTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++contadorTicks % 20 != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.players().isEmpty() || level.getChunkSource().getLoadedChunksCount() > 0) {
                ATIVAS.add(level.dimension());
            }
        }
    }

    /** "ConduitSavedData: 300 de 312 gravacoes puladas (sem mudanca), ..." */
    public static String porClasse() {
        Map<String, long[]> ordenado = new TreeMap<>(POR_CLASSE);
        StringBuilder sb = new StringBuilder();
        ordenado.entrySet().stream().filter(e -> e.getValue()[1] > 0)
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1])).limit(6)
                .forEach(e -> sb.append(sb.length() > 0 ? ", " : "").append(Msg.f("{}: {} de {} gravacoes puladas (sem mudanca)",
                        e.getKey().substring(e.getKey().lastIndexOf('.') + 1), e.getValue()[1], e.getValue()[0] + e.getValue()[1])));
        return sb.toString();
    }

    /** "78 dimensoes, 9 ativas, 69 puladas no ultimo salvamento" */
    public static String dimensoes() {
        if (dimensoesUltimo == 0) {
            return Msg.t("nenhum autosave ou pausa ainda");
        }
        return Msg.f("{} dimensoes, {} ativas, {} puladas no ultimo salvamento", dimensoesUltimo, dimensoesUltimo - puladasUltimo, puladasUltimo);
    }
}
