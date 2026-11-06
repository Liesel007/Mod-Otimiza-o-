package com.hlotimizacao.cliente.tela;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.rede.DadosPainel;
import net.minecraft.client.gui.components.EditBox;

/**
 * Uma chave da aba Config e o controle que a edita: numero = controle deslizante (nunca sai da faixa que o
 * servidor aceita), true/false = interruptor, listas e textos = caixa de texto conferida antes de enviar.
 * Guarda o valor editado ate salvar, para a atualizacao de 1 s do servidor nao apagar o que o jogador mexeu.
 */
final class ControleConfig {
    DadosPainel.ItemConfig item;
    final Config.TipoChave tipo;
    /** Valor no servidor (o que "Desfazer" devolve). */
    String original;
    /** Valor na tela. */
    String valor;
    /** Ultimo numero escolhido, para voltar a ele ao desligar o "auto". */
    int ultimoNumero;
    EditBox caixa;

    ControleConfig(DadosPainel.ItemConfig item) {
        this.item = item;
        Config.TipoChave[] tipos = Config.TipoChave.values();
        this.tipo = item.tipo() >= 0 && item.tipo() < tipos.length ? tipos[item.tipo()] : Config.TipoChave.TEXTO;
        this.original = item.valor();
        this.valor = item.valor();
        this.ultimoNumero = numeroOuEmUso();
    }

    /** Dados novos do servidor: so troca o valor se o jogador nao tinha mexido nele. */
    void atualizar(DadosPainel.ItemConfig novo) {
        boolean intacto = valor.equals(original);
        item = novo;
        if (!novo.valor().equals(original)) {
            original = novo.valor();
            if (intacto) {
                valor = novo.valor();
                if (caixa != null) {
                    caixa.setValue(valor);
                }
            }
        }
    }

    boolean mudou() {
        return !valor.equals(original);
    }

    boolean auto() {
        return item.aceitaAuto() && valor.equals("auto");
    }

    /** null = valido; senao o motivo (mesma regra do servidor). */
    String erro() {
        return Config.validar(item.chave(), valor.trim());
    }

    /** Numero escrito, ou o valor em uso quando esta "auto" (ou invalido). */
    int numeroOuEmUso() {
        try {
            return limitar(Integer.parseInt(valor.trim()));
        } catch (NumberFormatException e) {
            return limitar(item.emUso() >= 0 ? item.emUso() : item.min());
        }
    }

    int limitar(int v) {
        return Math.max(item.min(), Math.min(item.max(), v));
    }

    void definirNumero(int v) {
        ultimoNumero = limitar(v);
        valor = Integer.toString(ultimoNumero);
    }

    void alternarAuto() {
        if (auto()) {
            definirNumero(ultimoNumero);
        } else {
            ultimoNumero = numeroOuEmUso();
            valor = "auto";
        }
    }

    // ---------- escala do controle deslizante ----------

    /** Faixas grandes (ex.: 1 a 1.000.000 slots) usam escala logaritmica, senao o comeco fica impossivel de acertar. */
    boolean logaritmica() {
        return (item.max() - item.min()) >= 400;
    }

    float fracao(int v) {
        int amplitude = item.max() - item.min();
        if (amplitude <= 0) {
            return 0;
        }
        if (logaritmica()) {
            return (float) (Math.log(v - item.min() + 1) / Math.log(amplitude + 1));
        }
        return (float) (v - item.min()) / amplitude;
    }

    /** Valor para uma posicao do controle (0..1), arredondado para numeros "redondos" na escala logaritmica. */
    int valorDe(float f) {
        f = Math.max(0, Math.min(1, f));
        int amplitude = item.max() - item.min();
        if (!logaritmica()) {
            return limitar(Math.round(item.min() + f * amplitude));
        }
        double bruto = Math.exp(f * Math.log(amplitude + 1)) - 1 + item.min();
        return limitar((int) arredondar(Math.round(bruto)));
    }

    /** Dois algarismos significativos a partir de 100 (1234 -> 1200), para o arrasto parar em valores legiveis. */
    static long arredondar(long v) {
        if (v < 100) {
            return v;
        }
        long passo = (long) Math.pow(10, (long) Math.log10(v) - 1);
        return Math.round((double) v / passo) * passo;
    }

    /** Passo dos botoes < e >: 1 em faixas pequenas, ~1% do valor nas logaritmicas (10 em 1.000). */
    int tamanhoPasso(int v) {
        if (!logaritmica() || v < 1000) {
            return 1;
        }
        return (int) Math.pow(10, (int) Math.log10(v) - 2);
    }

    void passo(int direcao) {
        int atual = numeroOuEmUso();
        definirNumero(atual + direcao * tamanhoPasso(direcao > 0 ? atual : Math.max(item.min(), atual - 1)));
    }
}
