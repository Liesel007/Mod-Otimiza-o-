package io.github.mosadie.exponentialpower.energy.generator;

import io.github.mosadie.exponentialpower.energy.EnergyMath;
import io.github.mosadie.exponentialpower.entities.BaseClasses.GeneratorBE;
import net.minecraftforge.energy.IEnergyStorage;

public class ForgeEnergyConnection implements IEnergyStorage {

	private final GeneratorBE owner;
	private final boolean canExtract;
	private final boolean canReceive;

	public ForgeEnergyConnection(GeneratorBE owner, boolean canExtract, boolean canReceive) {
		this.owner = owner;
		this.canExtract = canExtract;
		this.canReceive = canReceive;
	}

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		return 0;
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		// Porte 1.20.1: ignora pedidos invalidos (o original aceitava maxExtract negativo, o que fazia o
		// gerador ganhar energia) e nao marca o bloco como alterado numa simulacao.
		if (!canExtract || maxExtract <= 0) {
			return 0;
		}
		int extracted = Math.min(maxExtract, EnergyMath.toForgeOffer(owner.energy));
		if (!simulate && extracted > 0) {
			owner.energy -= extracted;
			owner.setChanged();
		}
		return extracted;
	}

	@Override
	public int getEnergyStored() {
		return EnergyMath.toForgeReading(owner.energy);
	}

	@Override
	public int getMaxEnergyStored() {
		return EnergyMath.toForgeReading(owner.currentOutput);
	}

	@Override
	public boolean canExtract() {
		return canExtract;
	}

	@Override
	public boolean canReceive() {
		return canReceive;
	}
}
