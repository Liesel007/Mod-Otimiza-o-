package com.hlotimizacao.recursos;

import com.hlotimizacao.config.Msg;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Travamentos e frames lentos cuja causa e conhecida e e do proprio mod (o HLO nao tenta corrigir): o log e o resumo
 * ganham uma explicacao curta. Conferido no codigo de cada mod (versao 1.20.1).
 */
public final class CausasConhecidas {
    /** {trecho da classe.metodo na causa, explicacao} */
    private static final List<String[]> TABELA = List.of(
            new String[]{"BackpackStorage", Msg.t("primeira carga do armazenamento das mochilas (Sophisticated Backpacks); e do mod, acontece uma vez ao entrar no mundo")},
            new String[]{"Trackable$2.trim", Msg.t("limpeza do mapa de vazamentos do AllTheLeaks (fixa a cada 5 min e ao trocar de mundo; nenhuma opcao da config muda isso)")},
            new String[]{"MapRegionData.write", Msg.t("FTB Chunks gravando o mapa dele na thread do jogo ao liberar regioes (advanced.memory.region_release_time, a cada 300 s)")});
    private static final Map<String, int[]> VISTAS = new ConcurrentHashMap<>();

    private CausasConhecidas() {
    }

    /** " (explicacao)" ou "" se o texto da causa nao bate com nada conhecido. Conta para o resumo. */
    public static String nota(String causa) {
        if (causa == null || causa.isEmpty()) {
            return "";
        }
        for (String[] c : TABELA) {
            if (causa.contains(c[0])) {
                VISTAS.computeIfAbsent(c[1], k -> new int[1])[0]++;
                return " (" + c[1] + ")";
            }
        }
        return "";
    }

    /** "primeira carga ... 1x; limpeza ... 3x" ou "" */
    public static String resumo() {
        StringBuilder sb = new StringBuilder();
        VISTAS.forEach((k, v) -> sb.append(sb.length() > 0 ? "; " : "").append(k).append(": ").append(v[0]).append('x'));
        return sb.toString();
    }
}
