package io.github.mosadie.exponentialpower.datagen;

import io.github.mosadie.exponentialpower.ExponentialPower;
import io.github.mosadie.exponentialpower.setup.Registration;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public class DataBlockStates extends BlockStateProvider {

    public DataBlockStates(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ExponentialPower.MODID, existingFileHelper);
    }


    @Override
    protected void registerStatesAndModels() {
        ExponentialPower.LOGGER.info("Registering Block States and Models!");
        simpleBlock(Registration.ENDER_GENERATOR.get());
        simpleBlock(Registration.ADV_ENDER_GENERATOR.get());

        simpleBlock(Registration.ENDER_STORAGE.get());
        simpleBlock(Registration.ADV_ENDER_STORAGE.get());
    }
}
