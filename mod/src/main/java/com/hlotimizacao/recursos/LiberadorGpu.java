package com.hlotimizacao.recursos;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;

import java.lang.ref.Cleaner;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Libera objetos OpenGL de RenderTargets que foram coletados pelo GC sem destroyBuffers().
 *
 * O GPU Memory Leak Fix faz isso com finalize(), que esta depreciado e roda numa thread sem
 * contexto OpenGL. Aqui o Cleaner so enfileira os IDs; a liberacao acontece no tick do cliente,
 * na thread de render.
 */
public final class LiberadorGpu {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final ConcurrentLinkedQueue<int[]> PENDENTES = new ConcurrentLinkedQueue<>();
    private static final int MAX_POR_TICK = 20;

    private LiberadorGpu() {
    }

    /** IDs atuais de um RenderTarget. Nao guarda referencia ao RenderTarget, senao ele nunca seria coletado. */
    public static final class Ids implements Runnable {
        private volatile int framebuffer = -1;
        private volatile int cor = -1;
        private volatile int profundidade = -1;

        public void atualizar(int framebuffer, int cor, int profundidade) {
            this.framebuffer = framebuffer;
            this.cor = cor;
            this.profundidade = profundidade;
        }

        @Override
        public void run() {
            if (framebuffer > -1 || cor > -1 || profundidade > -1) {
                PENDENTES.add(new int[]{framebuffer, cor, profundidade});
            }
        }
    }

    public static Ids registrar(Object dono) {
        Ids ids = new Ids();
        CLEANER.register(dono, ids);
        return ids;
    }

    /** Chamado na thread de render. */
    public static void liberarPendentes() {
        for (int i = 0; i < MAX_POR_TICK; i++) {
            int[] ids = PENDENTES.poll();
            if (ids == null) {
                return;
            }
            // Nao mexe no que esta vinculado: um objeto que vazou nao esta em uso, e o OpenGL ja desvincula
            // sozinho um objeto apagado. TextureUtil.releaseTextureId tambem limpa o cache de texturas
            // vinculadas do GlStateManager. Antes, vincular 0 aqui podia trocar o estado de quem estava
            // desenhando.
            if (ids[0] > -1) {
                GlStateManager._glDeleteFramebuffers(ids[0]);
                Estatisticas.GPU_FRAMEBUFFERS.incrementAndGet();
            }
            if (ids[1] > -1) {
                TextureUtil.releaseTextureId(ids[1]);
                Estatisticas.GPU_TEXTURAS.incrementAndGet();
            }
            if (ids[2] > -1) {
                TextureUtil.releaseTextureId(ids[2]);
                Estatisticas.GPU_TEXTURAS.incrementAndGet();
            }
        }
    }
}
