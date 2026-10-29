package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Grava arquivos NBT comprimidos numa thread separada. Uma unica thread garante que gravacoes do
 * mesmo arquivo acontecem na ordem em que foram pedidas.
 */
public final class GravadorAssincrono {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "HL Otimizacao - gravacao de dados");
        thread.setDaemon(true);
        return thread;
    });

    private GravadorAssincrono() {
    }

    /**
     * @param destino caminho ja validado na thread do servidor
     * @param nome    nome do SavedData, para o log
     * @param aoFalhar chamado (na thread de gravacao) se a gravacao falhar, para o dado voltar a ser
     *                 marcado como sujo e ser regravado no proximo save
     */
    public static void gravar(CompoundTag tag, Path destino, String nome, Runnable aoFalhar) {
        EXECUTOR.execute(() -> {
            try {
                Path temporario = destino.resolveSibling(destino.getFileName() + ".hltmp");
                Files.createDirectories(destino.getParent());
                try (FileOutputStream arquivo = new FileOutputStream(temporario.toFile())) {
                    NbtIo.writeCompressed(tag, new NaoFecha(arquivo));
                    // Garante que os bytes estao no disco antes de substituir o arquivo antigo.
                    arquivo.getFD().sync();
                }
                try {
                    Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable t) {
                Config.LOGGER.error("Nao foi possivel salvar os dados '{}' em {}; serao regravados no proximo save", nome, destino, t);
                try {
                    aoFalhar.run();
                } catch (Throwable t2) {
                    Config.LOGGER.error("Nao foi possivel remarcar '{}' para regravar", nome, t2);
                }
            }
        });
    }

    /**
     * Espera todas as gravacoes pedidas ate agora terminarem. Chamado quando o servidor para, para
     * nao perder o ultimo save (a thread e daemon e morreria junto com o processo).
     */
    public static void esperarPendentes() {
        esperar(EXECUTOR, "HL Otimizacao");
    }

    public static void esperar(ExecutorService executor, String nome) {
        try {
            Future<?> marcador = executor.submit(() -> {
            });
            marcador.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            Config.LOGGER.warn("Gravacoes pendentes de {} nao terminaram a tempo", nome, e);
        }
    }

    /** NbtIo.writeCompressed fecha o stream; este invólucro so faz flush, para dar tempo do fsync. */
    private static final class NaoFecha extends FilterOutputStream {
        NaoFecha(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
