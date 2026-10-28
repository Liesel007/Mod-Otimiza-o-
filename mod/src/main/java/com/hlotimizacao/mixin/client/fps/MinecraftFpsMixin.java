package com.hlotimizacao.mixin.client.fps;

import com.hlotimizacao.config.Config;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Versao simples do Dynamic FPS: menos FPS quando ninguem esta olhando para o jogo. */
@Mixin(Minecraft.class)
public abstract class MinecraftFpsMixin {
    @Shadow
    @Final
    private Window window;

    @Shadow
    public abstract boolean isWindowActive();

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void hlotimizacao$limitarSemFoco(CallbackInfoReturnable<Integer> cir) {
        if (GLFW.glfwGetWindowAttrib(this.window.getWindow(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
            cir.setReturnValue(Config.fpsMinimizado());
        } else if (!this.isWindowActive()) {
            cir.setReturnValue(Math.min(this.window.getFramerateLimit(), Config.fpsSemFoco()));
        }
    }
}
