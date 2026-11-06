package com.hlotimizacao.config;

import java.util.Locale;

/**
 * Textos do mod. Os modelos ficam no codigo em portugues (a lingua do projeto); a traducao para ingles
 * esta em MensagensEn. Ingles e o padrao; portugues quando log.idioma=pt_br, ou com log.idioma=auto e o
 * Java em portugues.
 */
public final class Msg {
    private static volatile Boolean portugues;

    private Msg() {
    }

    static void definirIdioma(String valor) {
        String v = valor == null ? "auto" : valor.trim().toLowerCase(Locale.ROOT);
        portugues = switch (v) {
            case "pt_br", "pt", "pt-br" -> true;
            case "en_us", "en", "en-us" -> false;
            default -> Locale.getDefault().getLanguage().equals("pt");
        };
    }

    public static boolean portugues() {
        Boolean p = portugues;
        return p != null ? p : Locale.getDefault().getLanguage().equals("pt");
    }

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    /** Locale dos numeros: o do idioma do mod (1.204,5 em portugues; 1,204.5 em ingles). */
    public static Locale locale() {
        return portugues() ? PT_BR : Locale.US;
    }

    /** Traduz um modelo (texto fixo, pode ter {} ou %s/%d). */
    public static String t(String modelo) {
        if (modelo == null || portugues()) {
            return modelo;
        }
        return MensagensEn.MAPA.getOrDefault(modelo, modelo);
    }

    /** Traduz o modelo e troca cada {} pelo argumento, na ordem. */
    public static String f(String modelo, Object... args) {
        String m = t(modelo);
        StringBuilder sb = new StringBuilder(m.length() + 32);
        int arg = 0;
        for (int i = 0; i < m.length(); i++) {
            if (m.charAt(i) == '{' && i + 1 < m.length() && m.charAt(i + 1) == '}' && arg < args.length) {
                sb.append(args[arg++]);
                i++;
            } else {
                sb.append(m.charAt(i));
            }
        }
        return sb.toString();
    }
}
