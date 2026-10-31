package io.github.mosadie.eptest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/**
 * Bloco que recebe energia pela capability do Forge e registra cada chamada. No modo "mentiroso"
 * devolve valores invalidos (negativo ou maior que o oferecido), como um mod com bug faria.
 */
public final class Sonda {
    private Sonda() {
    }

    public static final class Bloco extends Block implements EntityBlock {
        public Bloco() {
            super(BlockBehaviour.Properties.of().strength(1f));
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new BE(pos, state);
        }
    }

    public static final class BE extends BlockEntity implements IEnergyStorage {
        public boolean mentirosa;
        public long chamadas;
        public long chamadasNegativas;
        public int maiorOferta = Integer.MIN_VALUE;
        public int menorOferta = Integer.MAX_VALUE;
        /** Soma do que foi oferecido, sem overflow (long). */
        public long totalOferecido;
        private long ultimoTick = -1;
        public int chamadasNoTick;
        public int maxChamadasPorTick;
        private final LazyOptional<IEnergyStorage> cap = LazyOptional.of(() -> this);

        public BE(BlockPos pos, BlockState state) {
            super(EPTest.SONDA_BE.get(), pos, state);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> c, @Nullable Direction side) {
            if (c == ForgeCapabilities.ENERGY) return cap.cast();
            return super.getCapability(c, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            cap.invalidate();
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (simulate) return mentirosa ? Integer.MAX_VALUE : maxReceive;
            long tick = level == null ? 0 : level.getGameTime();
            if (tick != ultimoTick) {
                ultimoTick = tick;
                chamadasNoTick = 0;
            }
            chamadasNoTick++;
            maxChamadasPorTick = Math.max(maxChamadasPorTick, chamadasNoTick);
            chamadas++;
            if (maxReceive < 0) chamadasNegativas++;
            maiorOferta = Math.max(maiorOferta, maxReceive);
            menorOferta = Math.min(menorOferta, maxReceive);
            totalOferecido += maxReceive;
            if (mentirosa) {
                // alterna: negativo e "aceitei mais do que voce ofereceu"
                return (chamadas % 2 == 0) ? -7 : Integer.MAX_VALUE;
            }
            return maxReceive;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
