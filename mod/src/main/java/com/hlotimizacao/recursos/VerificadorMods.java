package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Config;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Verificacoes do relatorio_conflitos que olham so a lista de mods (nao precisam do servidor). */
public final class VerificadorMods {
    private static final ArtifactVersion MC = new DefaultArtifactVersion("1.20.1");

    /**
     * Versoes do Minecraft no nome do arquivo. Pega "1.20.1", "1.20", "1.20.x", "1.18x", "mc1.19.4",
     * "1.20.[0-4]". So considera 1.16 a 1.21, para nao confundir com a versao do proprio mod
     * (ex.: "ImmediatelyFast-Forge-1.5.5+1.20.4").
     */
    private static final Pattern VERSAO_MC = Pattern.compile(
            "(?<![\\d.])1\\.(1[6-9]|2[01])(?:\\.(\\d+|x|\\[(\\d)-(\\d)\\])|(x))?(?![\\d])");
    private static final Pattern SEPARADOR_FAIXA = Pattern.compile("^\\s*(-|_|to|~)\\s*$");

    private VerificadorMods() {
    }

    /** Resultado da analise do nome de um arquivo. */
    public record AnaliseNome(boolean mencionaVersao, boolean compativel, List<String> versoes) {
    }

    /** Analisa o nome do arquivo: ele indica uma versao do Minecraft que inclui a 1.20.1? */
    public static AnaliseNome analisarNome(String nomeArquivo) {
        Matcher m = VERSAO_MC.matcher(nomeArquivo);
        List<int[]> faixas = new ArrayList<>(); // {min_menor, min_patch, max_menor, max_patch, inicio, fim}
        List<String> textos = new ArrayList<>();
        while (m.find()) {
            int menor = Integer.parseInt(m.group(1));
            int patchMin;
            int patchMax;
            if (m.group(3) != null) { // 1.20.[0-4]
                patchMin = Integer.parseInt(m.group(3));
                patchMax = Integer.parseInt(m.group(4));
            } else if (m.group(2) == null || "x".equals(m.group(2)) || m.group(5) != null) { // 1.20, 1.20.x, 1.18x
                patchMin = 0;
                patchMax = 99;
            } else {
                patchMin = patchMax = Integer.parseInt(m.group(2));
            }
            faixas.add(new int[]{menor, patchMin, menor, patchMax, m.start(), m.end()});
            textos.add(m.group());
        }
        if (faixas.isEmpty()) {
            return new AnaliseNome(false, true, List.of());
        }
        // "1.18-1.20.4": duas versoes separadas por "-" formam uma faixa.
        int encontradas = faixas.size();
        for (int i = 0; i + 1 < encontradas; i++) {
            int[] a = faixas.get(i);
            int[] b = faixas.get(i + 1);
            if (SEPARADOR_FAIXA.matcher(nomeArquivo.substring(a[5], b[4])).matches()) {
                faixas.add(new int[]{a[0], a[1], b[2], b[3], a[4], b[5]});
            }
        }
        boolean compativel = faixas.stream().anyMatch(f -> contem(f, 20, 1));
        return new AnaliseNome(true, compativel, textos);
    }

    /** Todas as versoes do nome sao exatas e mais novas que 1.20.1 (ex.: "+1.20.4")? */
    static boolean maisNovaQue1201(List<String> versoes) {
        if (versoes.isEmpty()) {
            return false;
        }
        for (String v : versoes) {
            String[] partes = v.split("\\.");
            if (partes.length != 3 || !partes[1].matches("\\d+") || !partes[2].matches("\\d+")) {
                return false; // "1.20", "1.20.x", "1.18x": nao e uma versao exata
            }
            int menor = Integer.parseInt(partes[1]);
            int patch = Integer.parseInt(partes[2]);
            if (menor < 20 || (menor == 20 && patch <= 1)) {
                return false;
            }
        }
        return true;
    }

    private static boolean contem(int[] f, int menor, int patch) {
        boolean depoisDoInicio = menor > f[0] || (menor == f[0] && patch >= f[1]);
        boolean antesDoFim = menor < f[2] || (menor == f[2] && patch <= f[3]);
        return depoisDoInicio && antesDoFim;
    }

    public static int verificar() {
        LoadingModList lista = LoadingModList.get();
        if (lista == null) {
            return 0;
        }
        int avisos = 0;
        avisos += versoesNoNome(lista);
        avisos += dependenciasOpcionais(lista);
        avisos += pares();
        return avisos;
    }

    /**
     * Arquivos cujo nome sugere outra versao do Minecraft. O Forge ja recusa mods cuja faixa declarada
     * nao inclui a 1.20.1; por isso so avisa quando o mod nao declara faixa ou declara uma que nao inclui.
     */
    private static int versoesNoNome(LoadingModList lista) {
        int avisos = 0;
        for (ModFileInfo arquivo : lista.getModFiles()) {
            String nome = arquivo.getFile().getFileName();
            AnaliseNome analise = analisarNome(nome);
            if (!analise.mencionaVersao() || analise.compativel()) {
                continue;
            }
            Optional<String> faixaDeclarada = arquivo.getMods().stream()
                    .flatMap(mod -> mod.getDependencies().stream())
                    .filter(dep -> "minecraft".equals(dep.getModId()))
                    .map(dep -> dep.getVersionRange().toString())
                    .findFirst();
            boolean declaraCompativel = arquivo.getMods().stream()
                    .flatMap(mod -> mod.getDependencies().stream())
                    .filter(dep -> "minecraft".equals(dep.getModId()))
                    .anyMatch(dep -> dep.getVersionRange().containsVersion(MC));
            if (declaraCompativel && maisNovaQue1201(analise.versoes())) {
                Config.LOGGER.warn("[Versao] {}: o arquivo e a versao feita para {} (mais nova que a 1.20.1). O mod declara {} e por isso carrega, "
                        + "mas pode usar codigo que nao existe na 1.20.1. Procure o arquivo feito para 1.20.1.",
                        nome, analise.versoes(), faixaDeclarada.orElse("?"));
                avisos++;
            } else if (declaraCompativel) {
                Config.LOGGER.info("[Versao] {}: o nome indica {}, mas o mod declara {} (inclui 1.20.1). Provavelmente ok.",
                        nome, analise.versoes(), faixaDeclarada.orElse("?"));
            } else {
                Config.LOGGER.warn("[Versao] {}: o nome indica {} e o mod {}. Pode ser a versao errada do mod.",
                        nome, analise.versoes(), faixaDeclarada.map(f -> "declara " + f).orElse("nao declara a versao do Minecraft"));
                avisos++;
            }
        }
        return avisos;
    }

    /** Dependencias opcionais presentes, mas fora da versao exigida (ex.: Polymorph com JEI antigo). */
    private static int dependenciasOpcionais(LoadingModList lista) {
        Map<String, IModInfo> instalados = lista.getMods().stream()
                .collect(Collectors.toMap(IModInfo::getModId, Function.identity(), (a, b) -> a));
        int avisos = 0;
        for (IModInfo mod : lista.getMods()) {
            for (IModInfo.ModVersion dep : mod.getDependencies()) {
                if (dep.isMandatory() || "minecraft".equals(dep.getModId()) || "forge".equals(dep.getModId())) {
                    continue;
                }
                IModInfo alvo = instalados.get(dep.getModId());
                if (alvo != null && !dep.getVersionRange().containsVersion(alvo.getVersion())) {
                    Config.LOGGER.warn("[Dependencia] {} pede {} {}, mas a versao instalada e {}. A integracao entre os dois pode falhar.",
                            mod.getModId(), dep.getModId(), dep.getVersionRange(), alvo.getVersion());
                    avisos++;
                }
            }
        }
        return avisos;
    }

    private static int pares() {
        int avisos = 0;
        boolean endEnchanting = Config.modInstalado("end_enchanting_table") || Config.modInstalado("endenchantingtable")
                || arquivoInstalado("end_enchanting_table");
        if (endEnchanting && (Config.modInstalado("apotheosis") || Config.modInstalado("toolleveling"))) {
            Config.LOGGER.warn("[Conflito] End Enchanting Table junto com {}: os dois mexem em como o nivel de encantamento e guardado, "
                            + "e o End Enchanting Table pode derrubar o jogo ao abrir a mesa. Remova um deles.",
                    Config.modInstalado("apotheosis") ? (Config.modInstalado("toolleveling") ? "Apotheosis e Tool Leveling+" : "Apotheosis") : "Tool Leveling+");
            avisos++;
        }
        if (Config.modInstalado("rechiseledcreate") && !Config.modInstalado("rechiseled")) {
            Config.LOGGER.warn("[Dependencia] Rechiseled: Create esta instalado sem o Rechiseled. Instale o Rechiseled ou remova o addon.");
            avisos++;
        }
        return avisos;
    }

    private static boolean arquivoInstalado(String prefixo) {
        LoadingModList lista = LoadingModList.get();
        if (lista == null) {
            return false;
        }
        String p = prefixo.toLowerCase(Locale.ROOT);
        return lista.getModFiles().stream().anyMatch(f -> f.getFile().getFileName().toLowerCase(Locale.ROOT).startsWith(p));
    }
}
