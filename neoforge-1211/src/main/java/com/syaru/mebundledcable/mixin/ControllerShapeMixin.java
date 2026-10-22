package com.syaru.mebundledcable.mixin;

import appeng.block.networking.ControllerBlock;
import com.syaru.mebundledcable.BundledControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ControllerBlock.class,remap=false)
public abstract class ControllerShapeMixin {
    @Inject(method="isController",at=@At("RETURN"),cancellable=true)
    private static void bundledControllerShape(LevelAccessor level,int x,int y,int z,CallbackInfoReturnable<Boolean> result) {
        if(!result.getReturnValue()&&level.getBlockState(new BlockPos(x,y,z)).getBlock() instanceof BundledControllerBlock)
            result.setReturnValue(true);
    }
}
