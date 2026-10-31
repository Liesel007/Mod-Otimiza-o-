package io.github.mosadie.exponentialpower.blocks;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import io.github.mosadie.exponentialpower.setup.Registration;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.mosadie.exponentialpower.ExponentialPower;
import io.github.mosadie.exponentialpower.entities.AdvancedEnderStorageBE;
import io.github.mosadie.exponentialpower.entities.BaseClasses.StorageBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AdvancedEnderStorage extends Block implements EntityBlock {
	public AdvancedEnderStorage() {
		super(BlockManager.BLOCK_PROPERTIES);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AdvancedEnderStorageBE(pos, state);
	}

	// Porte 1.20.1: o original (1.19) nao registrava ticker, entao StorageBE.tick() nunca rodava e o
	// armazenamento nunca enviava energia aos vizinhos, apesar de ter o codigo para isso. So no servidor.
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide || type != Registration.ADV_ENDER_STORAGE_BE.get()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof StorageBE storage) {
				storage.tick(lvl, pos, st, storage);
			}
		};
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand handIn, BlockHitResult hit) {
		if (!level.isClientSide) {
			StorageBE te = (StorageBE) level.getBlockEntity(pos);
			double percent = ((int)(te.energy/te.getMaxEnergy() * 10000.00)) / 100.00;
			player.sendSystemMessage(Component.translatable("screen.exponentialpower.storage_total", te.energy, te.getMaxEnergy(), percent));
		}
		return InteractionResult.SUCCESS;
	}
}
