package com.hlotimizacao.mixin.avaritia;

import committee.nova.mods.avaritia.core.chest.ChestHandler;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = ChestHandler.class, remap = false)
public interface ChestHandlerAccessor {
    @Accessor("nbtDataCache")
    Map<String, Tag> hlotimizacao$nbt();
}
