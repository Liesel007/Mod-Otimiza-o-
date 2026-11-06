package com.hlotimizacao.cliente.tela;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Porte 1.18.2: o GuiGraphics so existe a partir do 1.20. Esta classe imita os metodos que a tela usa,
 * em cima do PoseStack, para o codigo da tela ficar quase igual ao da versao 1.20.1.
 */
public final class GG {
    private final PoseStack ps;
    private final Screen tela;

    public GG(PoseStack ps, Screen tela) {
        this.ps = ps;
        this.tela = tela;
    }

    public PoseStack pose() {
        return ps;
    }

    public void fill(int x1, int y1, int x2, int y2, int cor) {
        GuiComponent.fill(ps, x1, y1, x2, y2, cor);
    }

    public void renderOutline(int x, int y, int largura, int altura, int cor) {
        fill(x, y, x + largura, y + 1, cor);
        fill(x, y + altura - 1, x + largura, y + altura, cor);
        fill(x, y + 1, x + 1, y + altura - 1, cor);
        fill(x + largura - 1, y + 1, x + largura, y + altura - 1, cor);
    }

    public int drawString(Font font, String texto, int x, int y, int cor, boolean sombra) {
        return sombra ? font.drawShadow(ps, texto, x, y, cor) : font.draw(ps, texto, x, y, cor);
    }

    public int drawString(Font font, Component texto, int x, int y, int cor, boolean sombra) {
        return sombra ? font.drawShadow(ps, texto, x, y, cor) : font.draw(ps, texto, x, y, cor);
    }

    public int drawString(Font font, FormattedCharSequence texto, int x, int y, int cor, boolean sombra) {
        return sombra ? font.drawShadow(ps, texto, x, y, cor) : font.draw(ps, texto, x, y, cor);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        Window janela = Minecraft.getInstance().getWindow();
        double escala = janela.getGuiScale();
        int largura = Math.max(0, (int) ((x2 - x1) * escala));
        int altura = Math.max(0, (int) ((y2 - y1) * escala));
        RenderSystem.enableScissor((int) (x1 * escala), (int) (janela.getHeight() - y2 * escala), largura, altura);
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    public void renderTooltip(Font font, List<? extends FormattedCharSequence> linhas, int x, int y) {
        tela.renderTooltip(ps, linhas, x, y);
    }
}
