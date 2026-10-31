package io.github.mosadie.exponentialpower.datagen;

import io.github.mosadie.exponentialpower.ExponentialPower;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExponentialPower.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        ExponentialPower.LOGGER.info("Registering Data Providers!");

        // 1.20: os providers recebem PackOutput em vez de DataGenerator.
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        generator.addProvider(event.includeServer(), new DataRecipes(output));
        generator.addProvider(event.includeServer(), new DataLootTables(output));

        generator.addProvider(event.includeClient(), new DataBlockStates(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new DataItems(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new DataLang(output, "en_us"));
    }
}
