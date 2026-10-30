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
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Gravacoes pedidas que ainda nao terminaram. */
    private static final AtomicInteger PENDENTES = new AtomicInteger();
    private static final int ESPERA_AO_DESLIGAR_S = 10;

    static {
        // Se o processo fechar sem ServerStoppedEvent (crash, System.exit), a thread daemon morreria
        // com gravacoes na fila. O shutdown hook espera um pouco por elas.
        // (Um "matar processo" forcado pelo sistema nao roda shutdown hooks; nada a fazer nesse caso.)
        Runtime.getRuntime().addShutdownHook(new Thread(GravadorAssincrono::aoDesligar, "HL Otimizacao - gravacoes ao desligar"));
    }

    private GravadorAssincrono() {
    }

    /**
     * Espera ate {@value #ESPERA_AO_DESLIGAR_S} s pelas gravacoes pendentes. Retorna quantas ainda
     * ficaram pendentes (0 = tudo gravado).
     */
    public static int aoDesligar() {
        int antes = PENDENTES.get();
        if (antes == 0) {
            return 0;
        }
        avisarDesligando("[salvamento_assincrono] Desligando com " + antes + " gravacao(oes) pendente(s); esperando ate "
                + ESPERA_AO_DESLIGAR_S + " s");
        try {
            EXECUTOR.submit(() -> {
            }).get(ESPERA_AO_DESLIGAR_S, TimeUnit.SECONDS);
        } catch (Exception e) {
            // segue para informar quantas ficaram
        }
        int depois = PENDENTES.get();
        avisarDesligando(depois == 0
                ? "[salvamento_assincrono] Todas as gravacoes pendentes foram concluidas"
                : "[salvamento_assincrono] " + depois + " gravacao(oes) NAO terminaram antes de o processo fechar");
        return depois;
    }

    /** Durante o desligamento o sistema de log pode ja ter parado; escreve tambem no stderr. */
    private static void avisarDesligando(String mensagem) {
        try {
            Config.LOGGER.warn(mensagem);
        } catch (Throwable ignorado) {
            // log indisponivel
        }
        System.err.println(mensagem);
    }

    public static int pendentes() {
        return PENDENTES.get();
    }

    /**
     * @param destino caminho ja validado na thread do servidor
     * @param nome    nome do SavedData, para o log
     * @param aoFalhar chamado (na thread de gravacao) se a gravacao falhar, para o dado voltar a ser
     *                 marcado como sujo e ser regravado no proximo save
     */
    public static void gravar(CompoundTag tag, Path destino, String nome, Runnable aoFalhar) {
        PENDENTES.incrementAndGet();
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
            } finally {
                PENDENTES.decrementAndGet();
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
        esperar(executor, nome, 60);
    }

    public static void esperar(ExecutorService executor, String nome, int segundos) {
        try {
            Future<?> marcador = executor.submit(() -> {
            });
            marcador.get(segundos, TimeUnit.SECONDS);
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
