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

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.zip.Adler32;
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
 * alguns dados de mod ficam marcados como alterados sempre (o ConduitSavedData do Ender IO sobrescreve isDirty() para
 * devolver true e e criado em toda dimensao), entao sao gravados em toda dimensao, a cada salvamento.
 * <ol>
 * <li><b>Sem mudanca, sem gravacao:</b> antes de gravar um dado marcado como alterado, compara o hash do NBT com o da
 *     ultima gravacao; igual = nao grava (a serializacao continua; disco e compressao nao). Sem gravacao anterior
 *     nesta sessao, compara com o arquivo no disco (uma leitura por arquivo na sessao).</li>
 * <li><b>Dimensao parada:</b> sem jogador e sem chunks carregados desde o ultimo salvamento, e nenhum dado com o
 *     conteudo diferente do que esta gravado: o ServerLevel.save dela e pulado. O Overworld nunca e pulado.</li>
 * </ol>
 * A 0.5.0 so aceitava pular quando a classe do dado ja tinha sido vista igual numa gravacao anterior desta sessao; no
 * primeiro salvamento ainda nao havia nenhuma, e as 78 dimensoes ficavam "ativas" pelo proprio dado que se queria
 * pular. Agora cada dado e comparado de verdade (com o disco no primeiro salvamento), e cada dimensao ativa tem o
 * motivo no log (uma vez por motivo na sessao).
 * <p>
 * So no autosave e no salvamento ao pausar (saveEverything nao forcado). Fechar o mundo, /save-all e /save-all flush
 * gravam tudo, de todas as dimensoes, como no vanilla.
 */
public final class SalvamentoSeletivo {
    private static volatile boolean parcial;
    /** arquivo -> hash do NBT (sem o envelope "data"/DataVersion) da ultima gravacao, ou do disco. */
    private static final Map<String, Long> HASH_POR_ARQUIVO = new ConcurrentHashMap<>();
    /** Arquivos ja comparados com o disco nesta sessao (le cada um no maximo uma vez). */
    private static final Set<String> LIDOS_DO_DISCO = ConcurrentHashMap.newKeySet();
    /** classe -> {gravacoes, puladas sem mudanca} */
    private static final Map<String, long[]> POR_CLASSE = new ConcurrentHashMap<>();
    /** Dimensoes com jogador ou chunk carregado desde o ultimo salvamento delas. */
    private static final Set<ResourceKey<Level>> ATIVAS = ConcurrentHashMap.newKeySet();
    /** "dimensao|tipo de motivo" ja escritos no log nesta sessao. */
    private static final Set<String> MOTIVOS_LOGADOS = ConcurrentHashMap.newKeySet();
    private static final List<String> MOTIVOS_NOVOS = new ArrayList<>();
    /** Motivo -> quantas dimensoes no ultimo salvamento parcial. */
    private static final Map<String, Integer> MOTIVOS_ULTIMO = new ConcurrentHashMap<>();
    private static Field campoModId;
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
            MOTIVOS_ULTIMO.clear();
        }
    }

    public static void fimSaveEverything() {
        parcial = false;
        List<String> novos;
        synchronized (MOTIVOS_NOVOS) {
            if (MOTIVOS_NOVOS.isEmpty()) {
                return;
            }
            novos = new ArrayList<>(MOTIVOS_NOVOS);
            MOTIVOS_NOVOS.clear();
        }
        Config.LOGGER.info("[salvamento_dimensoes_vazias] {}\n  {}", Msg.f("Dimensoes ativas neste salvamento ({} de {}; cada motivo aparece uma vez por sessao):",
                dimensoesUltimo - puladasUltimo, dimensoesUltimo), String.join("\n  ", novos));
    }

    /**
     * Antes de gravar um SavedData alterado: true = o conteudo e igual ao gravado e, num salvamento parcial, nao
     * precisa gravar. Sempre guarda o hash do que vai ser gravado (fechar o mundo tambem registra).
     */
    public static boolean semMudanca(File arquivo, String classe, CompoundTag dados) {
        if (!Config.ativo(Modulo.SALVAMENTO_DIMENSOES_VAZIAS)) {
            return false;
        }
        boolean igual = parcial && ligado() && igualAoGravado(arquivo, dados);
        HASH_POR_ARQUIVO.put(arquivo.getPath(), hash(paraComparar(arquivo, dados)));
        long[] v = POR_CLASSE.computeIfAbsent(classe, k -> new long[2]);
        if (igual) {
            v[1]++;
            return true;
        }
        v[0]++;
        return false;
    }

    /** A gravacao deste arquivo falhou: o proximo salvamento grava de novo. */
    public static void esquecer(File arquivo) {
        HASH_POR_ARQUIVO.remove(arquivo.getPath());
    }

    /** O NBT e igual ao da ultima gravacao desta sessao ou, sem nenhuma, ao do arquivo no disco. */
    static boolean igualAoGravado(File arquivo, CompoundTag dados) {
        String chave = arquivo.getPath();
        dados = paraComparar(arquivo, dados);
        Long anterior = HASH_POR_ARQUIVO.get(chave);
        if (anterior != null) {
            return anterior == hash(dados);
        }
        if (!LIDOS_DO_DISCO.add(chave) || !arquivo.isFile()) {
            return false;
        }
        try {
            // Formato vanilla (e o do ConduitSavedData, que grava com super.save num .tmp): {data: ..., DataVersion}.
            // equals do CompoundTag nao depende da ordem das chaves.
            CompoundTag disco = NbtIo.readCompressed(arquivo);
            if (disco.contains("data", Tag.TAG_COMPOUND) && paraComparar(arquivo, disco.getCompound("data")).equals(dados)) {
                HASH_POR_ARQUIVO.put(chave, hash(dados));
                return true;
            }
        } catch (Exception e) {
            // formato proprio do mod ou arquivo corrompido: trata como alterado (grava)
        }
        return false;
    }

    /**
     * O que conta como "mudou". O raids.dat (raids, raids_nether, raids_end: um por dimensao, no vanilla) e marcado
     * como alterado a cada 200 ticks so porque o contador "Tick" anda, mesmo sem raid nenhuma; isso deixava toda
     * dimensao "alterada" a cada 10 s. Sem raid ativa, o contador nao e comparado (fechar o mundo grava tudo).
     */
    static CompoundTag paraComparar(File arquivo, CompoundTag dados) {
        if (arquivo.getName().startsWith("raids") && dados.contains("Tick") && dados.getList("Raids", Tag.TAG_COMPOUND).isEmpty()) {
            CompoundTag copia = dados.copy();
            copia.remove("Tick");
            return copia;
        }
        return dados;
    }

    /** CRC32 e Adler32 do NBT juntos (64 bits): igualdade por acaso fica fora de questao na pratica. */
    static long hash(CompoundTag tag) {
        CRC32 crc = new CRC32();
        Adler32 adler = new Adler32();
        try (DataOutputStream saida = new DataOutputStream(new CheckedOutputStream(new CheckedOutputStream(OutputStream.nullOutputStream(), crc), adler))) {
            NbtIo.write(tag, saida);
        } catch (IOException e) {
            return -1; // nunca acontece com o stream nulo
        }
        return crc.getValue() << 32 | adler.getValue();
    }

    /** ServerLevel.save: true = pular esta dimensao neste salvamento. */
    public static boolean pularDimensao(ServerLevel level, Map<String, SavedData> dados, Function<String, File> arquivoDe) {
        if (!parcial || !ligado()) {
            return false;
        }
        dimensoesUltimo++;
        String motivo = null;
        String tipo = null;
        int chunks = level.getChunkSource().getLoadedChunksCount();
        if (!level.players().isEmpty()) {
            tipo = Msg.t("jogador presente");
            motivo = tipo;
        } else if (level.dimension() == Level.OVERWORLD) {
            tipo = Msg.t("overworld (nunca pulado)");
            motivo = tipo;
        } else if (chunks > 0) {
            tipo = Msg.t("chunks carregados");
            motivo = Msg.f("{} chunk(s) carregado(s){}", chunks, forcados(level, dados));
        } else if (ATIVAS.contains(level.dimension())) {
            tipo = Msg.t("teve chunks carregados desde o ultimo salvamento");
            motivo = tipo;
        } else {
            List<String> alterados = new ArrayList<>();
            for (Map.Entry<String, SavedData> e : dados.entrySet()) {
                SavedData d = e.getValue();
                if (d == null || !d.isDirty()) {
                    continue;
                }
                boolean igual;
                try {
                    igual = igualAoGravado(arquivoDe.apply(e.getKey()), d.save(new CompoundTag()));
                } catch (Exception erro) {
                    igual = false;
                }
                if (!igual) {
                    alterados.add(e.getKey());
                }
            }
            if (!alterados.isEmpty()) {
                tipo = Msg.t("dado alterado");
                motivo = Msg.f("dado alterado {}", alterados.size() > 6 ? alterados.subList(0, 6) + "..." : alterados);
            }
        }
        if (motivo != null) {
            MOTIVOS_ULTIMO.merge(tipo, 1, Integer::sum);
            if (MOTIVOS_LOGADOS.add(level.dimension().location() + "|" + tipo + "|" + (tipo.equals(Msg.t("dado alterado")) ? motivo : ""))) {
                synchronized (MOTIVOS_NOVOS) {
                    MOTIVOS_NOVOS.add(Msg.f("{} ativa: {}", level.dimension().location(), motivo));
                }
            }
            return false;
        }
        puladasUltimo++;
        dimensoesPuladas++;
        return true;
    }

    /** ", 4 forcado(s) [lantea]": /forceload do vanilla e tickets do Forge por mod. */
    private static String forcados(ServerLevel level, Map<String, SavedData> dados) {
        try {
            int vanilla = level.getForcedChunks().size();
            Set<String> mods = new TreeSet<>();
            if (dados.get("chunks") instanceof ForcedChunksSavedData f) {
                modsDosTickets(f.getBlockForcedChunks(), mods);
                modsDosTickets(f.getEntityForcedChunks(), mods);
            }
            if (vanilla == 0 && mods.isEmpty()) {
                return "";
            }
            return Msg.f(", forcados: {} pelo /forceload{}", vanilla, mods.isEmpty() ? "" : Msg.f(", tickets de {}", mods));
        } catch (Throwable t) {
            return "";
        }
    }

    private static void modsDosTickets(ForgeChunkManager.TicketTracker<?> tickets, Set<String> mods) throws ReflectiveOperationException {
        for (Map.Entry<? extends ForgeChunkManager.TicketOwner<?>, LongSet> e : tickets.getChunks().entrySet()) {
            mods.add(modId(e.getKey()));
        }
        for (Map.Entry<? extends ForgeChunkManager.TicketOwner<?>, LongSet> e : tickets.getTickingChunks().entrySet()) {
            mods.add(modId(e.getKey()));
        }
    }

    private static String modId(Object dono) throws ReflectiveOperationException {
        if (campoModId == null) {
            Field f = ForgeChunkManager.TicketOwner.class.getDeclaredField("modId");
            f.setAccessible(true);
            campoModId = f;
        }
        return String.valueOf(campoModId.get(dono));
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

    /** "ConduitSavedData: 150 de 156 gravacoes puladas (sem mudanca), ..." */
    public static String porClasse() {
        Map<String, long[]> ordenado = new TreeMap<>(POR_CLASSE);
        StringBuilder sb = new StringBuilder();
        ordenado.entrySet().stream().filter(e -> e.getValue()[1] > 0)
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1])).limit(6)
                .forEach(e -> sb.append(sb.length() > 0 ? ", " : "").append(Msg.f("{}: {} de {} gravacoes puladas (sem mudanca)",
                        e.getKey().substring(e.getKey().lastIndexOf('.') + 1), e.getValue()[1], e.getValue()[0] + e.getValue()[1])));
        return sb.toString();
    }

    /** "78 dimensoes, 2 ativas, 76 puladas no ultimo salvamento (jogador presente 1, overworld 1)" */
    public static String dimensoes() {
        if (dimensoesUltimo == 0) {
            return Msg.t("nenhum autosave ou pausa ainda");
        }
        String texto = Msg.f("{} dimensoes, {} ativas, {} puladas no ultimo salvamento", dimensoesUltimo, dimensoesUltimo - puladasUltimo, puladasUltimo);
        if (!MOTIVOS_ULTIMO.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            new TreeMap<>(MOTIVOS_ULTIMO).forEach((k, v) -> sb.append(sb.length() > 0 ? ", " : "").append(k).append(' ').append(v));
            texto += " (" + sb + ")";
        }
        return texto;
    }
}
