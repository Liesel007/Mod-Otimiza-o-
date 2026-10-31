package io.github.mosadie.exponentialpower.datagen;

import io.github.mosadie.exponentialpower.setup.Registration;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.DynamicLoot;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyNameFunction;
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction;
import net.minecraft.world.level.storage.loot.functions.SetContainerContents;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 1.20: LootTableProvider recebe uma lista de sub-providers; os blocos usam BlockLootSubProvider.
// As tabelas geradas sao as mesmas da 2.1.2.
public class DataLootTables extends LootTableProvider {

    public DataLootTables(PackOutput output) {
        super(output, Set.of(), List.of(new SubProviderEntry(BlockTables::new, LootContextParamSets.BLOCK)));
    }

    public static class BlockTables extends BlockLootSubProvider {
        private static final List<RegistryObject<Block>> BLOCKS = List.of(
                Registration.ENDER_GENERATOR, Registration.ADV_ENDER_GENERATOR,
                Registration.ENDER_STORAGE, Registration.ADV_ENDER_STORAGE);

        protected BlockTables() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            add(Registration.ENDER_GENERATOR.get(), createEnderGeneratorTable("endergenerator", Registration.ENDER_GENERATOR.get(), Registration.ENDER_GENERATOR_BE.get()));
            add(Registration.ADV_ENDER_GENERATOR.get(), createEnderGeneratorTable("advancedendergenerator", Registration.ADV_ENDER_GENERATOR.get(), Registration.ADV_ENDER_GENERATOR_BE.get()));

            add(Registration.ENDER_STORAGE.get(), createEnderStorageTable("enderstorage", Registration.ENDER_STORAGE.get()));
            add(Registration.ADV_ENDER_STORAGE.get(), createEnderStorageTable("advancedenderstorage", Registration.ADV_ENDER_STORAGE.get()));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return BLOCKS.stream().map(RegistryObject::get).collect(Collectors.toList());
        }

        protected LootTable.Builder createEnderGeneratorTable(String name, Block block, BlockEntityType<?> type) {
            LootPool.Builder builder = LootPool.lootPool()
                    .name(name)
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(block)
                            .apply(CopyNameFunction.copyName(CopyNameFunction.NameSource.BLOCK_ENTITY))
                            .apply(CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY)
                                    .copy("inv", "BlockEntityTag.inv", CopyNbtFunction.MergeStrategy.REPLACE)
                                    .copy("energy", "BlockEntityTag.energy", CopyNbtFunction.MergeStrategy.REPLACE))
                            .apply(SetContainerContents.setContents(type)
                                    .withEntry(DynamicLoot.dynamicEntry(new ResourceLocation("minecraft", "contents"))))
                    );
            return LootTable.lootTable().withPool(builder);
        }

        protected LootTable.Builder createEnderStorageTable(String name, Block block) {
            LootPool.Builder builder = LootPool.lootPool()
                    .name(name)
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(block)
                            .apply(CopyNameFunction.copyName(CopyNameFunction.NameSource.BLOCK_ENTITY))
                            .apply(CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY)
                                    .copy("energy", "BlockEntityTag.energy", CopyNbtFunction.MergeStrategy.REPLACE))
                    );
            return LootTable.lootTable().withPool(builder);
        }
    }
}
