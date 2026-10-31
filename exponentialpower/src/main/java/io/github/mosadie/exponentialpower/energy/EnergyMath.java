package io.github.mosadie.exponentialpower.energy;

/**
 * Conversoes entre a energia interna do mod (double, pode chegar perto de Double.MAX_VALUE) e a API de
 * energia do Forge, que usa int.
 *
 * Adicionado no porte para 1.20.1: o original fazia essas conversoes espalhadas pelo codigo e confiava
 * no valor devolvido por outros mods.
 */
public final class EnergyMath {
    private EnergyMath() {
    }

    /** Quanto oferecer a outro bloco numa chamada: no maximo Integer.MAX_VALUE, nunca negativo. */
    public static int toForgeOffer(double energy) {
        if (!(energy > 0)) { // tambem cobre NaN
            return 0;
        }
        return energy >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) energy;
    }

    /**
     * Valor que outro mod diz ter aceitado, limitado a [0, oferecido]. Um mod com bug que devolva mais
     * do que recebeu, ou um numero negativo, nao pode criar nem destruir energia aqui.
     */
    public static int clampAccepted(int accepted, int offered) {
        if (accepted <= 0) {
            return 0;
        }
        return Math.min(accepted, offered);
    }

    /** Valor para getEnergyStored/getMaxEnergyStored: saturado em Integer.MAX_VALUE, nunca negativo. */
    public static int toForgeReading(double energy) {
        return toForgeOffer(energy);
    }

    /** Garante que um valor interno e finito e nao negativo (protege contra NaN e infinito vindos de NBT). */
    public static double sanitize(double energy) {
        if (Double.isNaN(energy) || energy < 0) {
            return 0;
        }
        return Math.min(energy, Double.MAX_VALUE);
    }
}
