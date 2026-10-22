package com.syaru.mebundledcable;

import appeng.block.networking.ControllerBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class BundledControllerBlock extends ControllerBlock {
    public static BlockEntityType<ControllerBlockEntity> createEntityType() {
        // AE2 indexes pathing roots by their exact owner class, not by subclasses.
        var holder=new AtomicReference<BlockEntityType<ControllerBlockEntity>>();
        var type=BlockEntityType.Builder.of((pos,state)->new ControllerBlockEntity(holder.get(),pos,state),
                BundledCableMod.CONTROLLER.get()).build(null);
        holder.set(type);
        AEBaseBlockEntity.registerBlockEntityItem(type,BundledCableMod.CONTROLLER_ITEM.get());
        BundledCableMod.CONTROLLER.get().setBlockEntity(ControllerBlockEntity.class,type,null,null);
        return type;
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving) {
        super.onPlace(state,level,pos,old,moving);
        if(!level.isClientSide&&!old.is(this))level.scheduleTick(pos,this,1);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(!(level.getBlockEntity(pos) instanceof ControllerBlockEntity controller))return;
        if(controller.getMainNode().getNode()==null) {
            level.scheduleTick(pos,this,1);
            return;
        }
        for(var side:Direction.values())if(level.hasChunkAt(pos.relative(side))
                &&level.getBlockEntity(pos.relative(side)) instanceof BundledCableEntity cable)cable.schedule();
    }
}
