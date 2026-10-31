package com.hlotimizacao.mixin.avaritia;

import committee.nova.mods.avaritia.util.StorageUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bug do Re-Avaritia 1.4.2: getItem("mod:item#hash") guarda "mod:item#hash" no cache item -> id.
 * Dai em diante getItemId(item) devolve o id com o hash, e cada item com NBT que entra no Infinity
 * Chest ganha mais um "#hash" na chave ("mod:item#h1#h2#h3..."). Nada mais se junta numa mesma
 * entrada, o baú ganha uma entrada nova a cada item com NBT, e itens sem NBT caem dentro da
 * entrada de um item com NBT. Os caches originais tambem sao HashMap comuns usados ao mesmo
 * tempo pela thread do cliente e do servidor no singleplayer.
 *
 * Aqui os dois metodos usam caches proprios e thread-safe, e o cache item -> id so guarda o id
 * do registro (sem hash).
 */
@Mixin(value = StorageUtils.class, remap = false)
public abstract class StorageUtilsMixin {
    @Unique
    private static final Map<Item, String> hlotimizacao$idPorItem = new ConcurrentHashMap<>();
    @Unique
    private static final Map<String, Item> hlotimizacao$itemPorChave = new ConcurrentHashMap<>();

    @Inject(method = "getItemId(Lnet/minecraft/world/item/Item;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void hlotimizacao$getItemId(Item item, CallbackInfoReturnable<String> cir) {
        String id = hlotimizacao$idPorItem.get(item);
        if (id == null) {
            ResourceLocation chave = ForgeRegistries.ITEMS.getKey(item);
            id = String.valueOf(chave);
            hlotimizacao$idPorItem.put(item, id);
        }
        cir.setReturnValue(id);
    }

    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private static void hlotimizacao$getItem(String chave, CallbackInfoReturnable<Item> cir) {
        Item item = hlotimizacao$itemPorChave.get(chave);
        if (item == null) {
            Item registrado = ForgeRegistries.ITEMS.getValue(new ResourceLocation(StorageUtils.getBaseItemId(chave)));
            if (registrado == null || registrado == Items.AIR) {
                cir.setReturnValue(Items.AIR);
                return;
            }
            item = registrado;
            hlotimizacao$itemPorChave.put(chave, item);
        }
        cir.setReturnValue(item);
    }
}
