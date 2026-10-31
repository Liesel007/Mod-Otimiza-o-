package io.github.mosadie.exponentialpower.energy.storage;

import io.github.mosadie.exponentialpower.energy.EnergyMath;
import io.github.mosadie.exponentialpower.entities.BaseClasses.StorageBE;
import net.minecraft.core.Direction;
import net.minecraftforge.energy.IEnergyStorage;

public class ForgeEnergyConnection implements IEnergyStorage {

	private final StorageBE owner;
	private final boolean canExtract;
	private final boolean canReceive;
	private final Direction direction;

	public ForgeEnergyConnection(StorageBE owner, boolean canExtract, boolean canReceive, Direction dir) {
		this.owner = owner;
		this.canExtract = canExtract;
		this.canReceive = canReceive;
		direction = dir;
	}

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		// Porte 1.20.1: o original ignorava "simulate" e somava a energia mesmo em simulacoes. Cabos que
		// simulam antes de transferir (Mekanism, entre outros) duplicavam energia. Tambem passa a
		// respeitar a capacidade maxima e a recusar valores negativos.
		if (!canReceive || maxReceive <= 0) {
			return 0;
		}
		double space = owner.getMaxEnergy() - owner.energy;
		if (!(space > 0)) {
			return 0;
		}
		int accepted = space >= maxReceive ? maxReceive : (int) space;
		if (!simulate && accepted > 0) {
			owner.energy += accepted;
			owner.freezeExpend.put(direction, true);
			owner.setChanged();
		}
		return accepted;
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
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
		return EnergyMath.toForgeReading(owner.getMaxEnergy());
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
