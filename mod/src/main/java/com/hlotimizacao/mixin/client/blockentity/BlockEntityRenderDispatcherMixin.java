package com.hlotimizacao.mixin.client.blockentity;

import com.hlotimizacao.config.Config;
import com.hlotimizacao.config.Modulo;
import com.hlotimizacao.recursos.ModoSeguro;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * distancia_block_entities: alem de distancia_block_entities.distancia blocos da camera, o block entity nao e
 * desenhado (o vanilla usa a distancia de cada renderer, normalmente 64). Tipos em 'excecoes' ficam com a original.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Shadow
    public Camera camera;

    /** Tipo -> fica com a distancia original? (o id so e procurado uma vez por tipo). */
    @Unique
    private static final Map<BlockEntityType<?>, Boolean> hlotimizacao$excecao = new IdentityHashMap<>();
    @Unique
    private static int hlotimizacao$versao = -1;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void hlotimizacao$distancia(E be, float parcial, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        if (camera == null || ModoSeguro.desligado(Modulo.DISTANCIA_BLOCK_ENTITIES)) {
            return;
        }
        try {
            if (Config.versao() != hlotimizacao$versao) {
                hlotimizacao$excecao.clear(); // /hl recarregar ou config salva: rele as excecoes
                hlotimizacao$versao = Config.versao();
            }
            Boolean excecao = hlotimizacao$excecao.computeIfAbsent(be.getType(), tipo -> {
                ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(tipo);
                return id != null && Config.beExcecoes().contains(id.toString());
            });
            if (excecao) {
                return;
            }
            BlockPos pos = be.getBlockPos();
            Vec3 c = camera.getPosition();
            double dx = pos.getX() + 0.5 - c.x;
            double dy = pos.getY() + 0.5 - c.y;
            double dz = pos.getZ() + 0.5 - c.z;
            double limite = Config.beDistancia();
            if (dx * dx + dy * dy + dz * dz > limite * limite) {
                com.hlotimizacao.recursos.ContadoresCliente.blockEntitiesPulados++;
                ci.cancel();
            }
        } catch (RuntimeException e) {
            ModoSeguro.falhou(Modulo.DISTANCIA_BLOCK_ENTITIES, e);
        }
    }
}
