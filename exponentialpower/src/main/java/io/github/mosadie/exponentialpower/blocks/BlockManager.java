package io.github.mosadie.exponentialpower.blocks;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class BlockManager {

	// 1.20 removeu Material. O material original era: cor azul no mapa, solido, bloqueia movimento,
	// nao inflamavel, nao substituivel e destruido ao ser empurrado por pistao. O som padrao de um
	// Material sem som definido era o de pedra.
	public static final BlockBehaviour.Properties BLOCK_PROPERTIES = BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLUE)
			.sound(SoundType.STONE)
			.pushReaction(PushReaction.DESTROY)
			.strength(2.5f, 15.0f);

}
