package com.hlotimizacao.mixin.client.toast;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Deque;

/**
 * Porte 1.18.2: aqui os toasts visiveis ficam num array de ToastInstance (classe privada do jogo, que o mixin
 * nao consegue nomear), entao o array e lido por reflexao uma vez e guardado.
 */
@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {
    @Shadow
    @Final
    private Deque<Toast> queued;

    @Unique
    private Object[] hlotimizacao$visiveis;

    @Unique
    private boolean hlotimizacao$tentou;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$pularSemToasts(PoseStack ps, CallbackInfo ci) {
        if (!this.queued.isEmpty()) {
            return;
        }
        Object[] visiveis = hlotimizacao$visiveis();
        if (visiveis == null) {
            return;
        }
        for (Object o : visiveis) {
            if (o != null) {
                return;
            }
        }
        ci.cancel();
    }

    @Unique
    private Object[] hlotimizacao$visiveis() {
        if (!hlotimizacao$tentou) {
            hlotimizacao$tentou = true;
            for (Field f : ToastComponent.class.getDeclaredFields()) {
                if (f.getType().isArray() && !f.getType().getComponentType().isPrimitive()) {
                    try {
                        f.setAccessible(true);
                        hlotimizacao$visiveis = (Object[]) f.get(this);
                    } catch (Exception e) {
                        hlotimizacao$visiveis = null;
                    }
                    break;
                }
            }
        }
        return hlotimizacao$visiveis;
    }
}
