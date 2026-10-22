package com.syaru.mebundledcable;

import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import net.minecraft.world.item.ItemStack;

public record CableSpec(AECableType type, AEColor color, ItemStack item) {
    public static CableSpec of(ItemStack stack) {
        if (stack.getItem() instanceof BundledCableItem) stack = BundledCableItem.unwrap(stack);
        for (var color : AEColor.values()) {
            if (stack.is(AEParts.GLASS_CABLE.item(color))) return new CableSpec(AECableType.GLASS, color, stack.copyWithCount(1));
            if (stack.is(AEParts.COVERED_CABLE.item(color))) return new CableSpec(AECableType.COVERED, color, stack.copyWithCount(1));
            if (stack.is(AEParts.SMART_CABLE.item(color))) return new CableSpec(AECableType.SMART, color, stack.copyWithCount(1));
            if (stack.is(AEParts.COVERED_DENSE_CABLE.item(color))) return new CableSpec(AECableType.DENSE_COVERED, color, stack.copyWithCount(1));
            if (stack.is(AEParts.SMART_DENSE_CABLE.item(color))) return new CableSpec(AECableType.DENSE_SMART, color, stack.copyWithCount(1));
        }
        return null;
    }
    public boolean compatible(CableSpec other) {
        return color == AEColor.TRANSPARENT || other.color == AEColor.TRANSPARENT || color == other.color;
    }
}
