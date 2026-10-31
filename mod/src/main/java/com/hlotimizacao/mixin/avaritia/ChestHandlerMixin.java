package com.hlotimizacao.mixin.avaritia;

import committee.nova.mods.avaritia.core.chest.ChestHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Perdas de NBT no Infinity Chest do Re-Avaritia 1.4.2:
 * - takeItem/saveTakeItem apagam o NBT guardado mesmo quando so parte dos itens sai; o que fica no
 *   bau perde o NBT (livros encantados, ferramentas...);
 * - extractItem (usado por funis e canos) apaga o NBT antes de colocar no item quando tira os
 *   ultimos itens; eles saem sem NBT;
 * - o item que sai recebe o mesmo CompoundTag do cache; se o jogador muda o item (dano, nome), o
 *   NBT dos itens que ficaram muda junto.
 */
@Mixin(value = ChestHandler.class, remap = false)
public abstract class ChestHandlerMixin {
    @Shadow
    @Final
    public HashMap<String, Long> storageItems;

    @Shadow
    @Final
    protected Map<String, Tag> nbtDataCache;

    @Shadow
    private String[] itemKeys;

    @Unique
    private CompoundTag hlotimizacao$tagExtraido;

    /** So apaga o NBT guardado quando o item saiu inteiro do bau. */
    @Redirect(method = {"takeItem", "saveTakeItem"},
            at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object hlotimizacao$removerSoSeSaiuTudo(Map<String, Tag> cache, Object chave) {
        if (storageItems.containsKey(chave)) {
            return null;
        }
        return cache.remove(chave);
    }

    /** O item que sai recebe uma copia do NBT, nao o objeto do cache. */
    @Redirect(method = {"takeItem", "saveTakeItem", "extractItem"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setTag(Lnet/minecraft/nbt/CompoundTag;)V", remap = true))
    private void hlotimizacao$copiarTag(ItemStack pilha, CompoundTag tag) {
        pilha.setTag(tag == null ? null : tag.copy());
    }

    @Inject(method = "extractItem", at = @At("HEAD"))
    private void hlotimizacao$lembrarTag(int slot, int quantidade, boolean simular, CallbackInfoReturnable<ItemStack> cir) {
        hlotimizacao$tagExtraido = null;
        int indice = slot - 27;
        if (indice >= 0 && indice < itemKeys.length && nbtDataCache.get(itemKeys[indice]) instanceof CompoundTag tag) {
            hlotimizacao$tagExtraido = tag;
        }
    }

    @Inject(method = "extractItem", at = @At("RETURN"))
    private void hlotimizacao$restaurarTag(int slot, int quantidade, boolean simular, CallbackInfoReturnable<ItemStack> cir) {
        CompoundTag tag = hlotimizacao$tagExtraido;
        hlotimizacao$tagExtraido = null;
        ItemStack pilha = cir.getReturnValue();
        if (tag != null && pilha != null && !pilha.isEmpty() && !pilha.hasTag()) {
            pilha.setTag(tag.copy());
        }
    }
}
