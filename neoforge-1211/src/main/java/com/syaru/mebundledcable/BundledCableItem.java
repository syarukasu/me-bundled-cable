package com.syaru.mebundledcable;

import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;

public final class BundledCableItem extends BlockItem {
    public BundledCableItem() { super(BundledCableMod.BLOCK.get(), new Properties()); }
    public static ItemStack unwrap(ItemStack stack) {
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(data.contains("cable")) {
            var id=ResourceLocation.tryParse(data.getString("cable"));
            if(id==null || !BuiltInRegistries.ITEM.containsKey(id))return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        return AEParts.SMART_CABLE.stack(AEColor.TRANSPARENT, 1);
    }
    public static ItemStack wrap(ItemStack cable) {
        var stack = new ItemStack(BundledCableMod.ITEM.get());
        var data=new CompoundTag();data.putString("cable",BuiltInRegistries.ITEM.getKey(cable.getItem()).toString());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        return stack;
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable("item.mebundledcable.cable.typed", unwrap(stack).getHoverName());
    }
}
