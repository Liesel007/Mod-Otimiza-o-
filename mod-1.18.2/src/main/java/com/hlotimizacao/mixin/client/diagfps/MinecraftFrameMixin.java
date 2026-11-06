package com.hlotimizacao.mixin.client.diagfps;

import com.hlotimizacao.recursos.DiagnosticoFps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * diagnostico_fps: marca o comeco e o fim de cada frame (Minecraft.runTick) e o sono do limitador de FPS.
 * Cada gancho so le o relogio e escreve campos; a analise fica na vigia (DiagnosticoFps).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
    @Shadow
    public ClientLevel level;

    @Shadow
    public Screen screen;

    @Shadow
    public abstract boolean isWindowActive();

    @Inject(method = "runTick", at = @At("HEAD"))
    private void hlotimizacao$inicioFrame(boolean tick, CallbackInfo ci) {
        DiagnosticoFps.inicioFrame();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"), require = 0)
    private void hlotimizacao$antesLimitador(boolean tick, CallbackInfo ci) {
        DiagnosticoFps.inicioLimitador();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V", shift = At.Shift.AFTER), require = 0)
    private void hlotimizacao$depoisLimitador(boolean tick, CallbackInfo ci) {
        DiagnosticoFps.fimLimitador();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void hlotimizacao$fimFrame(boolean tick, CallbackInfo ci) {
        // Fora do mundo, em tela de carregamento ou sem foco nao e travadinha de jogo.
        boolean valido = level != null && isWindowActive()
                && !(screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)
                && !(screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen)
                && !(screen instanceof net.minecraft.client.gui.screens.ProgressScreen)
                && !(screen instanceof net.minecraft.client.gui.screens.GenericDirtMessageScreen);
        DiagnosticoFps.fimFrame(valido);
    }
}
