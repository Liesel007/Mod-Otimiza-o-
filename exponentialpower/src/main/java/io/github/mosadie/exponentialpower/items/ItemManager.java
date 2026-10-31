package io.github.mosadie.exponentialpower.items;

import io.github.mosadie.exponentialpower.ExponentialPower;
import io.github.mosadie.exponentialpower.setup.Registration;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ItemManager {

	// 1.20: abas criativas viraram registro. A aba so define titulo e icone; o conteudo e adicionado
	// pelo BuildCreativeModeTabContentsEvent.
	public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExponentialPower.MODID);

	public static final RegistryObject<CreativeModeTab> ITEM_GROUP = CREATIVE_TABS.register("exponentialpower", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.exponentialpower"))
			.icon(() -> new ItemStack(Registration.ENDER_CELL.get()))
			.build());

	public static void addCreative(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == ITEM_GROUP.getKey()) {
			event.accept(Registration.ENDER_CELL);
			event.accept(Registration.ENDER_GENERATOR_ITEM);
			event.accept(Registration.ADV_ENDER_GENERATOR_ITEM);
			event.accept(Registration.ENDER_STORAGE_ITEM);
			event.accept(Registration.ADV_ENDER_STORAGE_ITEM);
		}
	}
}
