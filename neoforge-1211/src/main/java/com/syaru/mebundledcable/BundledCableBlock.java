package com.syaru.mebundledcable;

import appeng.api.implementations.items.IFacadeItem;
import appeng.util.InteractionUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;

public final class BundledCableBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    @Override protected com.mojang.serialization.MapCodec<BundledCableBlock> codec() { return com.mojang.serialization.MapCodec.unit(this); }
    public static final String SELECTION="mebundledcableSelection";
    public BundledCableBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(.5F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(BlockStateProperties.WATERLOGGED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);
    }
    @Override public FluidState getFluidState(BlockState state) { return state.getValue(BlockStateProperties.WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state); }
    @Override public BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other) {
        if(state.getValue(BlockStateProperties.WATERLOGGED))level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        if(level.getBlockEntity(pos) instanceof BundledCableEntity cable)cable.schedule();
        return state;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new BundledCableEntity(pos,state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        boolean hide=context instanceof EntityCollisionContext entity && entity.getEntity() instanceof Player player
                && InteractionUtil.canWrenchDisassemble(player.getMainHandItem())&&!player.isShiftKeyDown();
        if(level.getBlockEntity(pos) instanceof BundledCableEntity cable) {
            if(hide&&context instanceof EntityCollisionContext entity&&entity.getEntity() instanceof Player player)
                return ConduitGeometry.shape(cable.interactionPieces(selectedSlot(player,cable)));
            return cable.shape(false);
        }
        return Shapes.block();
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return level.getBlockEntity(pos) instanceof BundledCableEntity cable?cable.shape(false):Shapes.block();
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack) {
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof BundledCableEntity cable)cable.add(stack,entity instanceof Player player?player:null);
    }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos neighbor,boolean moving) {
        if(level.getBlockEntity(pos) instanceof BundledCableEntity cable)cable.schedule();
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(level.getBlockEntity(pos) instanceof BundledCableEntity cable)cable.reconcile();
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {
        return builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BundledCableEntity cable?cable.drops():List.of();
    }
    @Override public ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,LevelReader level,BlockPos pos,Player player) {
        if(level.getBlockEntity(pos) instanceof BundledCableEntity cable) {
            var hit=ConduitGeometry.hit(cable.interactionPieces(player.isShiftKeyDown()?-1:selectedSlot(player,cable)),
                    player.getEyePosition().subtract(Vec3.atLowerCornerOf(pos)),target.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
            if(hit!=null&&cable.spec(hit.slot())!=null)return BundledCableItem.wrap(cable.spec(hit.slot()).item());
        }
        return new ItemStack(BundledCableMod.ITEM.get());
    }
    public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!(level.getBlockEntity(pos) instanceof BundledCableEntity cable))return InteractionResult.PASS;
        var held=player.getItemInHand(hand);
        if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,hit.getDirection(),held))return InteractionResult.FAIL;
        if(CableSpec.of(held)!=null) {
            if(cable.count()>=BundledCableEntity.LIMIT) {
                if(!level.isClientSide)player.displayClientMessage(Component.translatable("message.mebundledcable.full"),true);
                return InteractionResult.FAIL;
            }
            if(!level.isClientSide&&cable.add(held,player)>=0&&!player.isCreative())held.shrink(1);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(held.getItem() instanceof IFacadeItem) {
            if(!level.isClientSide&&cable.addFacade(hit.getDirection(),held)&&!player.isCreative())held.shrink(1);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(!InteractionUtil.canWrenchDisassemble(held))return InteractionResult.PASS;
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(player.isShiftKeyDown()&&!cable.facade(hit.getDirection()).isEmpty()) {
            give(player,cable.removeFacade(hit.getDirection()));return InteractionResult.CONSUME;
        }
        var target=ConduitGeometry.hit(player.isShiftKeyDown()?cable.pieces():cable.interactionPieces(selectedSlot(player,cable)),
                player.getEyePosition().subtract(Vec3.atLowerCornerOf(pos)),hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
        if(target==null)return InteractionResult.FAIL;
        if(player.isShiftKeyDown()) {
            give(player,cable.remove(target.slot()));
            clearSelection(held);
            if(cable.count()==0) {
                for(var drop:cable.drops())give(player,drop);
                level.setBlock(pos,state.getValue(BlockStateProperties.WATERLOGGED)?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
            }
            return InteractionResult.CONSUME;
        }
        var previous=selection(held);
        boolean sameDimension=previous.getString("dimension").equals(level.dimension().location().toString());
        BlockPos previousPos=BlockPos.of(previous.getLong("pos"));int previousSlot=previous.getInt("slot");
        if(previous.getBoolean("routing")&&sameDimension&&!previousPos.equals(pos)&&previousPos.distManhattan(pos)==1&&level.hasChunkAt(previousPos)
                &&level.getBlockEntity(previousPos) instanceof BundledCableEntity source) {
            var side=Direction.fromDelta(pos.getX()-previousPos.getX(),pos.getY()-previousPos.getY(),pos.getZ()-previousPos.getZ());
            if(level.mayInteract(player,previousPos)&&player.mayUseItemAt(previousPos,side,held)
                    &&source.mapTo(previousSlot,side,cable,target.slot())) {
                clearSelection(held);
                player.displayClientMessage(Component.translatable("message.mebundledcable.mapped",previousSlot+1,target.slot()+1),true);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("message.mebundledcable.incompatible"),true);
            return InteractionResult.FAIL;
        }
        if(target.side()!=null) {
            var side=target.side();
            if(level.hasChunkAt(pos.relative(side))&&(level.getBlockEntity(pos.relative(side)) instanceof BundledCableEntity
                    ||level.getBlockState(pos.relative(side)).getBlock() instanceof BundledControllerBlock))cable.toggle(target.slot(),side);
            else cable.selectExternal(target.slot(),side);
            remember(held,level,pos,target.slot(),false);
            player.displayClientMessage(Component.translatable("message.mebundledcable.connection_changed",
                    target.slot()+1,Component.translatable("message.mebundledcable.side."+side.getName())),true);
            return InteractionResult.CONSUME;
        } else if(sameDimension&&previousPos.equals(pos)&&previousSlot==target.slot()&&previous.getBoolean("routing")) {
            clearSelection(held);
            player.displayClientMessage(Component.translatable("message.mebundledcable.selection_cleared"),true);
            return InteractionResult.CONSUME;
        } else {
            remember(held,level,pos,target.slot(),true);
        }
        var message=cable.status(target.slot());
        message=Component.translatable("message.mebundledcable.selected",target.slot()+1).append(" ").append(message);
        player.displayClientMessage(message,true);
        return InteractionResult.CONSUME;
    }
    private static void give(Player player,ItemStack stack) {
        if(!stack.isEmpty()&&!player.getInventory().add(stack))player.drop(stack,false);
    }
    private static CompoundTag selection(ItemStack held) {
        return held.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound(SELECTION);
    }
    public static int selectedSlot(Player player,BundledCableEntity cable) {
        var held=player.getMainHandItem();
        if(!InteractionUtil.canWrenchDisassemble(held)||cable.getLevel()==null)return -1;
        var tag=selection(held);
        int slot=tag.getInt("slot");
        return tag.getString("dimension").equals(cable.getLevel().dimension().location().toString())
                &&BlockPos.of(tag.getLong("pos")).equals(cable.getBlockPos())&&cable.spec(slot)!=null?slot:-1;
    }
    private static void remember(ItemStack held,Level level,BlockPos pos,int slot,boolean routing) {
        var tag=new CompoundTag();
        tag.putString("dimension",level.dimension().location().toString());
        tag.putLong("pos",pos.asLong());tag.putInt("slot",slot);tag.putBoolean("routing",routing);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,held,data->data.put(SELECTION,tag));
    }
    private static void clearSelection(ItemStack held) {
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,held,tag->tag.remove(SELECTION));
    }
}
