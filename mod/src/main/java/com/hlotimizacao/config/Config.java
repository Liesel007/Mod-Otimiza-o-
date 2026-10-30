package com.hlotimizacao.config;

import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Configuracao lida antes dos mixins serem aplicados, por isso usa um arquivo .properties simples
 * em vez do sistema de config do Forge (que so carrega depois).
 */
public final class Config {
    public static final Logger LOGGER = LoggerFactory.getLogger("HL Otimizacao");
    private static final String ARQUIVO = "hlotimizacao.properties";

    private static final Map<Modulo, Boolean> ATIVOS = new EnumMap<>(Modulo.class);
    private static final Map<Modulo, String> MOTIVOS = new EnumMap<>(Modulo.class);
    private static int fpsSemFoco = 15;
    private static int fpsMinimizado = 1;
    private static int lagLimiteMs = 2000;
    private static int lagIntervaloS = 30;
    private static boolean carregado = false;

    private Config() {
    }

    public static synchronized void carregar() {
        if (carregado) {
            return;
        }
        carregado = true;
        Path caminho = pastaConfig().resolve(ARQUIVO);
        Properties props = new Properties();
        if (Files.exists(caminho)) {
            try (Reader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                LOGGER.error("Nao foi possivel ler {}, usando valores padrao", caminho, e);
            }
        }

        boolean faltando = false;
        for (Modulo modulo : Modulo.values()) {
            String chave = "modulo." + modulo.chave;
            String valor = props.getProperty(chave);
            if (valor == null) {
                faltando = true;
                valor = "auto";
                props.setProperty(chave, valor);
            }
            decidir(modulo, valor.trim().toLowerCase());
        }
        fpsSemFoco = lerInt(props, "fps_sem_foco.limite", 15, 1, 260);
        fpsMinimizado = lerInt(props, "fps_sem_foco.limite_minimizado", 1, 1, 260);
        lagLimiteMs = lerInt(props, "diagnostico_lag.limite_ms", 2000, 100, 600000);
        lagIntervaloS = lerInt(props, "diagnostico_lag.intervalo_s", 30, 1, 3600);
        for (String[] chave : new String[][]{
                {"fps_sem_foco.limite", Integer.toString(fpsSemFoco)},
                {"fps_sem_foco.limite_minimizado", Integer.toString(fpsMinimizado)},
                {"diagnostico_lag.limite_ms", Integer.toString(lagLimiteMs)},
                {"diagnostico_lag.intervalo_s", Integer.toString(lagIntervaloS)}}) {
            if (!props.containsKey(chave[0])) {
                faltando = true;
                props.setProperty(chave[0], chave[1]);
            }
        }
        if (faltando) {
            salvar(caminho, props);
        }
    }

    private static void decidir(Modulo modulo, String valor) {
        Optional<String> conflito = modulo.modsConflitantes.stream().filter(Config::modInstalado).findFirst();
        boolean ativo;
        String motivo;
        switch (valor) {
            case "off", "false", "desligado" -> {
                ativo = false;
                motivo = "desligado na config";
            }
            case "on", "true", "ligado" -> {
                ativo = true;
                motivo = conflito.map(id -> "forcado na config (atencao: " + id + " tambem esta instalado)").orElse("ligado na config");
            }
            default -> {
                ativo = conflito.isEmpty();
                motivo = conflito.map(id -> "desligado automaticamente: " + id + " ja faz isso").orElse("ligado (auto)");
            }
        }
        if (modulo == Modulo.CORRECAO_FASTASYNCWORLDSAVE && ativo && !modInstalado("fastasyncworldsave")) {
            ativo = false;
            motivo = "nao necessario: fastasyncworldsave nao esta instalado";
        }
        ATIVOS.put(modulo, ativo);
        MOTIVOS.put(modulo, motivo);
    }

    public static boolean ativo(Modulo modulo) {
        carregar();
        return ATIVOS.getOrDefault(modulo, false);
    }

    public static String motivo(Modulo modulo) {
        carregar();
        return MOTIVOS.getOrDefault(modulo, "");
    }

    public static int fpsSemFoco() {
        carregar();
        return fpsSemFoco;
    }

    public static int fpsMinimizado() {
        carregar();
        return fpsMinimizado;
    }

    public static int lagLimiteMs() {
        carregar();
        return lagLimiteMs;
    }

    public static int lagIntervaloS() {
        carregar();
        return lagIntervaloS;
    }

    public static boolean modInstalado(String modId) {
        LoadingModList lista = LoadingModList.get();
        return lista != null && lista.getModFileById(modId) != null;
    }

    private static int lerInt(Properties props, String chave, int padrao, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(props.getProperty(chave, Integer.toString(padrao)).trim())));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private static Path pastaConfig() {
        try {
            return FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            return Path.of("config");
        }
    }

    private static void salvar(Path caminho, Properties props) {
        StringBuilder sb = new StringBuilder();
        sb.append("# HL Otimizacao\n");
        sb.append("# Valores dos modulos: auto (padrao), on, off.\n");
        sb.append("# auto = ligado, a menos que o mod original que faz o mesmo esteja instalado.\n");
        sb.append("# Mudancas so valem depois de reiniciar o jogo.\n\n");
        for (Modulo modulo : Modulo.values()) {
            sb.append("# ").append(modulo.descricao).append('\n');
            if (!modulo.modsConflitantes.isEmpty()) {
                sb.append("# Desliga sozinho (em auto) se estiver instalado: ").append(String.join(", ", modulo.modsConflitantes)).append('\n');
            }
            sb.append("modulo.").append(modulo.chave).append('=').append(props.getProperty("modulo." + modulo.chave)).append("\n\n");
        }
        sb.append("# FPS maximo com a janela sem foco e minimizada (modulo fps_sem_foco).\n");
        sb.append("fps_sem_foco.limite=").append(props.getProperty("fps_sem_foco.limite")).append('\n');
        sb.append("fps_sem_foco.limite_minimizado=").append(props.getProperty("fps_sem_foco.limite_minimizado")).append("\n\n");
        sb.append("# Tempo de tick (ms) a partir do qual o diagnostico_lag registra o travamento,\n");
        sb.append("# e intervalo minimo (s) entre dois registros.\n");
        sb.append("diagnostico_lag.limite_ms=").append(props.getProperty("diagnostico_lag.limite_ms")).append('\n');
        sb.append("diagnostico_lag.intervalo_s=").append(props.getProperty("diagnostico_lag.intervalo_s")).append('\n');
        try {
            Files.createDirectories(caminho.getParent());
            try (Writer writer = Files.newBufferedWriter(caminho, StandardCharsets.UTF_8)) {
                writer.write(sb.toString());
            }
        } catch (IOException e) {
            LOGGER.error("Nao foi possivel salvar {}", caminho, e);
        }
    }
}
