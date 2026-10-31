package io.github.mosadie.exponentialpower.items;

import net.minecraft.world.item.Item;

import net.minecraft.world.item.Item.Properties;

public class EnderCell extends Item {

	private final static Properties properties = new Properties()
			.fireResistant()
			.stacksTo(64);

	public EnderCell() {
		super(properties);
	}
}
