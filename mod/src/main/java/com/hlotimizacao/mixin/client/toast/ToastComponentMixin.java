package com.hlotimizacao.mixin.client.toast;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;
import java.util.List;

@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {
    @Shadow
    @Final
    private List<?> visible;
    @Shadow
    @Final
    private Deque<Toast> queued;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$pularSemToasts(GuiGraphics graphics, CallbackInfo ci) {
        if (this.visible.isEmpty() && this.queued.isEmpty()) {
            ci.cancel();
        }
    }
}
