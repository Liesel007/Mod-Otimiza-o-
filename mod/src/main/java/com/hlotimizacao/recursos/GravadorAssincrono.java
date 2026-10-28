package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
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

    public static void gravar(CompoundTag tag, File destino) {
        EXECUTOR.execute(() -> {
            Path alvo = destino.toPath();
            Path temporario = alvo.resolveSibling(alvo.getFileName() + ".hltmp");
            try {
                Files.createDirectories(alvo.getParent());
                NbtIo.writeCompressed(tag, temporario.toFile());
                try {
                    Files.move(temporario, alvo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporario, alvo, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                Config.LOGGER.error("Nao foi possivel salvar {}", destino, e);
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
}
