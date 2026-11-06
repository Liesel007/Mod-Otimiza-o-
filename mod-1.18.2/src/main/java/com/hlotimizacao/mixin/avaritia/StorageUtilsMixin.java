package com.hlotimizacao.mixin.avaritia;

import com.hlotimizacao.recursos.ChavesInfinityChest;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O StorageUtils do Re-Avaritia guarda chaves "id#hash" no cache item -> id, e dai as chaves crescem
 * ("id#hash#hash..."). Troca os dois caches por caches que so conhecem o id puro e sao thread-safe.
 */
@Pseudo
@Mixin(targets = "committee.nova.mods.avaritia.util.StorageUtils", remap = false)
public abstract class StorageUtilsMixin {
    @Inject(method = "getItemId(Lnet/minecraft/world/item/Item;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hlotimizacao$idPuro(Item item, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(ChavesInfinityChest.idDoItem(item));
    }

    @Inject(method = "getItem(Ljava/lang/String;)Lnet/minecraft/world/item/Item;", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hlotimizacao$itemSemPoluirCache(String chave, CallbackInfoReturnable<Item> cir) {
        cir.setReturnValue(ChavesInfinityChest.itemDaChave(chave));
    }
}
