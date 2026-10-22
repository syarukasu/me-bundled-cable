package com.syaru.mebundledcable;

import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class BundledCableItem extends BlockItem {
    public BundledCableItem() { super(BundledCableMod.BLOCK.get(), new Properties()); }
    public static ItemStack unwrap(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("cable", 10)) return ItemStack.of(stack.getTag().getCompound("cable"));
        return AEParts.SMART_CABLE.stack(AEColor.TRANSPARENT, 1);
    }
    public static ItemStack wrap(ItemStack cable) {
        var stack = new ItemStack(BundledCableMod.ITEM.get());
        stack.getOrCreateTag().put("cable", cable.copyWithCount(1).save(new CompoundTag()));
        return stack;
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable("item.mebundledcable.cable.typed", unwrap(stack).getHoverName());
    }
}
