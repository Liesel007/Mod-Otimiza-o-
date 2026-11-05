package com.hlotimizacao.benchmark;

import com.hlotimizacao.recursos.ContadoresCliente;
import com.hlotimizacao.recursos.DiagnosticoFps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Teste do render_enxame (HLBENCH_ENXAME=1): 300 vacas paradas na frente do jogador (5 a 30 blocos), uma delas com
 * nome. Mede o tempo medio de frame por 15 s. Rodar com modulo.render_enxame=on e =off para comparar.
 */
final class TesteRenderEnxame {
    private static int ticks;
    private static long frames0;
    private static long ns0;
    private static long pulados0;

    private TesteRenderEnxame() {
    }

    static void registrar() {
        MinecraftForge.EVENT_BUS.addListener(TesteRenderEnxame::aoTick);
    }

    private static void aoTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        ticks++;
        if (ticks == 100) {
            var server = mc.getSingleplayerServer();
            var p = mc.player.position();
            mc.player.setYRot(0); // olhando para +Z
            mc.player.setXRot(10);
            server.execute(() -> {
                ServerLevel level = server.overworld();
                var jogador = server.getPlayerList().getPlayers().get(0);
                jogador.setYRot(0);
                jogador.setXRot(10);
                int n = 0;
                for (int dz = 5; dz < 35 && n < 300; dz += 2) {
                    for (int dx = -10; dx < 10 && n < 300; dx++) {
                        Cow vaca = EntityType.COW.create(level);
                        vaca.moveTo(p.x + dx, p.y, p.z + dz, 180, 0);
                        vaca.setNoAi(true);
                        vaca.setPersistenceRequired();
                        if (n == 299) {
                            vaca.setCustomName(Component.literal("Mimosa"));
                        }
                        level.addFreshEntity(vaca);
                        n++;
                    }
                }
                HLBench.LOGGER.info("HLBENCH enxame_render vacas_criadas={}", n);
                // mesas de encantamento (renderer animado): 3 a 12 blocos de lado, 3 a 50 blocos de distancia
                for (int i = 0; i < 3; i++) {
                    level.setBlockAndUpdate(net.minecraft.core.BlockPos.containing(p.x - 12, p.y, p.z + 4 + i * 2),
                            net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState());
                    level.setBlockAndUpdate(net.minecraft.core.BlockPos.containing(p.x - 12, p.y, p.z + 50 + i * 2),
                            net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState());
                }
            });
        }
        if (ticks == 400) {
            pulados0 = ContadoresCliente.blockEntitiesPulados;
            frames0 = DiagnosticoFps.frames();
            ns0 = System.nanoTime();
        }
        if (ticks == 700) {
            long frames = DiagnosticoFps.frames() - frames0;
            double ms = (System.nanoTime() - ns0) / 1e6 / Math.max(1, frames);
            HLBench.LOGGER.info("HLBENCH enxame_render frames={} ms_por_frame={} desenhadas={} existentes={} por_tipo={}", frames,
                    String.format("%.1f", ms), ContadoresCliente.enxameDesenhadas, ContadoresCliente.enxameExistentes,
                    ContadoresCliente.enxamePorTipo.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()[0] + "/" + e.getValue()[1]).toList());
            var nomeada = mc.level.getEntitiesOfClass(Cow.class, mc.player.getBoundingBox().inflate(40), Cow::hasCustomName);
            boolean desenhavel = !nomeada.isEmpty() && mc.getEntityRenderDispatcher().shouldRender(nomeada.get(0), new net.minecraft.client.renderer.culling.Frustum(
                    new org.joml.Matrix4f().identity(), new org.joml.Matrix4f().identity()) {
                @Override
                public boolean isVisible(net.minecraft.world.phys.AABB caixa) {
                    return true;
                }
            }, mc.player.getX(), mc.player.getY(), mc.player.getZ());
            HLBench.LOGGER.info("HLBENCH enxame_render nomeada_encontrada={} nomeada_desenhada={}", !nomeada.isEmpty(), desenhavel);
            var pos = mc.player.position();
            var perto = mc.level.getBlockEntity(net.minecraft.core.BlockPos.containing(pos.x - 12, pos.y, pos.z + 4));
            var longe = mc.level.getBlockEntity(net.minecraft.core.BlockPos.containing(pos.x - 12, pos.y, pos.z + 50));
            var buffers = mc.renderBuffers().bufferSource();
            long a = ContadoresCliente.blockEntitiesPulados;
            if (perto != null) {
                mc.getBlockEntityRenderDispatcher().render(perto, 0, new com.mojang.blaze3d.vertex.PoseStack(), buffers);
            }
            long b = ContadoresCliente.blockEntitiesPulados;
            if (longe != null) {
                mc.getBlockEntityRenderDispatcher().render(longe, 0, new com.mojang.blaze3d.vertex.PoseStack(), buffers);
            }
            long c = ContadoresCliente.blockEntitiesPulados;
            buffers.endBatch();
            HLBench.LOGGER.info("HLBENCH be_distancia mesa_perto={} pulada_perto={} mesa_longe={} pulada_longe={}", perto != null, b - a, longe != null, c - b);
            HLBench.LOGGER.info("HLBENCH be_distancia pulados_por_frame={} (todos os block entities do mundo alem da distancia, nao so as mesas)",
                    String.format("%.1f", (double) (ContadoresCliente.blockEntitiesPulados - pulados0) / Math.max(1, frames)));
            Screenshot.grab(mc.gameDirectory, "hl_enxame.png", mc.getMainRenderTarget(), msg -> HLBench.LOGGER.info("HLBENCH enxame captura: {}", msg.getString()));
        }
        if (ticks == 740) {
            mc.stop();
        }
    }
}
