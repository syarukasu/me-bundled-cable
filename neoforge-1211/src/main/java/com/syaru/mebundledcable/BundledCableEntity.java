package com.syaru.mebundledcable;

import appeng.api.networking.*;
import appeng.api.util.AECableType;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class BundledCableEntity extends BlockEntity implements IInWorldGridNodeHost {
    public static final int LIMIT = 8;
    public static final ModelProperty<RenderSnapshot> RENDER = new ModelProperty<>();
    public record RenderLane(int slot, AECableType type, appeng.api.util.AEColor color, int channels, boolean powered, int connected, int external, List<Integer> targets, List<Integer> visualChannels) { }
    public record RenderFacade(Direction side, BlockState state) { }
    public record RenderSnapshot(List<RenderLane> lanes, List<RenderFacade> facades, int selected) { }
    private static final class Lane {
        final CableSpec spec;
        final int[] targets = new int[6];
        int enabled = 63;
        IManagedGridNode managed;
        CompoundTag savedNode = new CompoundTag();
        int pendingOwner = -1;
        final IGridConnection[] links = new IGridConnection[6];
        int channels, connected, external;
        int capacity;
        final int[] sideChannels = new int[6];
        final int[] visualChannels = new int[6];
        boolean powered;
        appeng.integration.modules.igtooltip.GridNodeState state = appeng.integration.modules.igtooltip.GridNodeState.OFFLINE;
        Lane(CableSpec spec, int slot) { this.spec=spec; this.capacity=spec.type().isDense()?32:8; Arrays.fill(targets,slot); }
    }
    private final Lane[] lanes = new Lane[LIMIT];
    private final int[] external = {-1,-1,-1,-1,-1,-1};
    private final ItemStack[] facades = new ItemStack[6];
    private boolean initialized, queued, destroying;
    private int selected = -1;
    private int controllerFaces;
    private List<ConduitGeometry.Piece> pieces = List.of();
    private VoxelShape shape = Shapes.empty();
    private RenderSnapshot snapshot = new RenderSnapshot(List.of(),List.of(),-1);
    private static final IGridNodeListener<BundledCableEntity> LISTENER = new IGridNodeListener<>() {
        @Override public void onSaveChanges(BundledCableEntity owner,IGridNode node) { owner.setChanged(); }
        @Override public void onStateChanged(BundledCableEntity owner,IGridNode node,State state) { owner.schedule(); }
        @Override public void onInWorldConnectionChanged(BundledCableEntity owner,IGridNode node) { owner.schedule(); }
        @Override public void onGridChanged(BundledCableEntity owner,IGridNode node) { owner.schedule(); }
    };
    public BundledCableEntity(BlockPos pos,BlockState state) {
        super(BundledCableMod.ENTITY.get(),pos,state);
        Arrays.fill(facades,ItemStack.EMPTY);
    }
    public int count() { int count=0; for(var lane:lanes)if(lane!=null)count++; return count; }
    public CableSpec spec(int slot) { return valid(slot)&&lanes[slot]!=null?lanes[slot].spec:null; }
    public IGridNode node(int slot) { return valid(slot)&&lanes[slot]!=null&&lanes[slot].managed!=null?lanes[slot].managed.getNode():null; }
    public List<ConduitGeometry.Piece> pieces() { return pieces; }
    public VoxelShape shape(boolean hideFacades) {
        var result=shape;
        if(!hideFacades)for(var side:Direction.values())if(!facades[side.ordinal()].isEmpty())
            result=Shapes.or(result,Shapes.create(ConduitGeometry.facade(side)));
        return result.isEmpty()?Block.box(6.5,6.5,6.5,9.5,9.5,9.5):result;
    }
    private static boolean valid(int slot) { return slot>=0&&slot<LIMIT; }
    private boolean bundleAt(Direction side) {
        return level!=null && level.hasChunkAt(worldPosition.relative(side))
                && level.getBlockEntity(worldPosition.relative(side)) instanceof BundledCableEntity;
    }
    private boolean bundledControllerAt(Direction side) {
        return level!=null&&level.hasChunkAt(worldPosition.relative(side))
                &&level.getBlockState(worldPosition.relative(side)).getBlock() instanceof BundledControllerBlock;
    }
    @Override public IGridNode getGridNode(Direction side) {
        if(side==null||isRemoved()||bundleAt(side)||bundledControllerAt(side))return null;
        int slot=external[side.ordinal()];
        return valid(slot)&&lanes[slot]!=null&&(lanes[slot].enabled&(1<<side.ordinal()))!=0?node(slot):null;
    }
    @Override public AECableType getCableConnectionType(Direction side) {
        int slot=side==null?-1:external[side.ordinal()];
        return spec(slot)==null?AECableType.NONE:spec(slot).type();
    }
    @Override public void onLoad() { super.onLoad(); queueInit(); }
    @Override public void clearRemoved() { super.clearRemoved(); queueInit(); }
    private void queueInit() {
        if(level!=null&&!level.isClientSide&&!isRemoved()&&!queued&&!initialized) {
            queued=true;
            GridHelper.onFirstTick(this,be->{
                be.queued=false;
                if(be.isRemoved()||be.initialized)return;
                be.initialized=true;
                for(int i=0;i<LIMIT;i++)be.createNode(i,null);
                be.reconcile();be.notifyNeighbors();be.schedule();
            });
        }
    }
    private void createNode(int slot,Player player) {
        var lane=lanes[slot];
        if(lane==null||lane.managed!=null||!initialized||level==null||level.isClientSide)return;
        lane.managed=GridHelper.createManagedNode(this,LISTENER).setTagName("node")
                .setInWorldNode(true).setExposedOnSides(EnumSet.noneOf(Direction.class))
                .setFlags(lane.spec.type().isDense()?GridFlags.DENSE_CAPACITY:GridFlags.PREFERRED)
                .setGridColor(lane.spec.color()).setIdlePowerUsage(0).setVisualRepresentation(lane.spec.item());
        if(!lane.savedNode.isEmpty())lane.managed.loadFromNBT(lane.savedNode);
        if(lane.pendingOwner>=0)lane.managed.setOwningPlayerId(lane.pendingOwner);
        if(player!=null)lane.managed.setOwningPlayer(player);
        lane.managed.create(level,worldPosition);
        lane.pendingOwner=-1;
    }
    public int add(ItemStack stack,Player player) {
        var spec=CableSpec.of(stack);
        if(level==null||level.isClientSide||spec==null)return -1;
        for(int i=0;i<LIMIT;i++)if(lanes[i]==null) {
            boolean first=count()==0;
            lanes[i]=new Lane(spec,i);
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                lanes[i].pendingOwner=appeng.api.features.IPlayerRegistry.getPlayerId(serverPlayer);
            if(first)Arrays.fill(external,i);
            createNode(i,player);
            changed();queueInit();return i;
        }
        return -1;
    }
    public ItemStack remove(int slot) {
        if(level==null||level.isClientSide||!valid(slot)||lanes[slot]==null)return ItemStack.EMPTY;
        Lane old=lanes[slot];lanes[slot]=null;
        if(old.managed!=null)old.managed.destroy();
        for(int i=0;i<6;i++)if(external[i]==slot)external[i]=-1;
        changed();return old.spec.item().copy();
    }
    public void toggle(int slot,Direction side) {
        if(!valid(slot)||lanes[slot]==null)return;
        lanes[slot].enabled^=1<<side.ordinal();changed();
    }
    public void selectExternal(int slot,Direction side) {
        if(!valid(slot)||lanes[slot]==null)return;
        // Detach every published face before changing which node owns it.
        detachExternal();
        external[side.ordinal()]=external[side.ordinal()]==slot?-1:slot;
        lanes[slot].enabled|=1<<side.ordinal();changed();
    }
    public boolean mapTo(int slot,Direction side,BundledCableEntity other,int target) {
        if(other.level!=level||!other.worldPosition.equals(worldPosition.relative(side))
                ||spec(slot)==null||other.spec(target)==null||!spec(slot).compatible(other.spec(target)))return false;
        lanes[slot].targets[side.ordinal()]=target;
        other.lanes[target].targets[side.getOpposite().ordinal()]=slot;
        lanes[slot].enabled|=1<<side.ordinal();
        other.lanes[target].enabled|=1<<side.getOpposite().ordinal();
        changed();other.changed();return true;
    }
    public ItemStack facade(Direction side) { return facades[side.ordinal()]; }
    public boolean addFacade(Direction side,ItemStack item) {
        if(!facade(side).isEmpty()||!(item.getItem() instanceof appeng.api.implementations.items.IFacadeItem facade))return false;
        if(facade.getTextureBlockState(item).isAir())return false;
        facades[side.ordinal()]=item.copyWithCount(1);changed();return true;
    }
    public ItemStack removeFacade(Direction side) {
        ItemStack old=facades[side.ordinal()];facades[side.ordinal()]=ItemStack.EMPTY;changed();return old;
    }
    public List<ItemStack> drops() {
        var result=new ArrayList<ItemStack>();
        for(var lane:lanes)if(lane!=null)result.add(lane.spec.item().copy());
        for(var facade:facades)if(!facade.isEmpty())result.add(facade.copy());
        return result;
    }
    public void schedule() {
        if(level!=null&&!level.isClientSide&&!isRemoved()&&!destroying)level.scheduleTick(worldPosition,getBlockState().getBlock(),1);
    }
    private void changed() { setChanged();rebuildGeometry();schedule();notifyNeighbors();sync(); }
    private void notifyNeighbors() {
        if(level==null||level.isClientSide||isRemoved())return;
        for(var side:Direction.values())if(level.hasChunkAt(worldPosition.relative(side))
                &&level.getBlockEntity(worldPosition.relative(side)) instanceof BundledCableEntity other)other.schedule();
        level.updateNeighborsAt(worldPosition,getBlockState().getBlock());
    }
    private void detachExternal() {
        for(var lane:lanes)if(lane!=null&&lane.managed!=null)lane.managed.setExposedOnSides(EnumSet.noneOf(Direction.class));
    }
    private IGridNode expected(int slot,Direction side) {
        var lane=lanes[slot];
        if((lane.enabled&(1<<side.ordinal()))==0||!level.hasChunkAt(worldPosition.relative(side)))return null;
        if(bundledControllerAt(side)
                &&level.getBlockEntity(worldPosition.relative(side)) instanceof appeng.blockentity.networking.ControllerBlockEntity controller
                &&!controller.isRemoved())return controller.getGridNode(side.getOpposite());
        if(!(level.getBlockEntity(worldPosition.relative(side)) instanceof BundledCableEntity other)||other.isRemoved())return null;
        int target=lane.targets[side.ordinal()];
        if(!valid(target)||other.lanes[target]==null)return null;
        var peer=other.lanes[target];
        if((peer.enabled&(1<<side.getOpposite().ordinal()))==0||peer.targets[side.getOpposite().ordinal()]!=slot
                ||!lane.spec.compatible(peer.spec))return null;
        return other.node(target);
    }
    public void reconcile() {
        if(level==null||level.isClientSide||isRemoved()||!initialized)return;
        // Remove stale edges on all lanes before making any new ones: no transient cross-grid union.
        for(int i=0;i<LIMIT;i++)if(node(i)!=null)for(var side:Direction.values()) {
            var lane=lanes[i];var edge=lane.links[side.ordinal()];
            if(edge!=null) {
                boolean live=node(i).getConnections().contains(edge);
                if(!live||edge.getOtherSide(node(i))!=expected(i,side)) {
                    lane.links[side.ordinal()]=null;
                    if(live)edge.destroy();
                }
            }
        }
        for(int i=0;i<LIMIT;i++)if(node(i)!=null) {
            var exposed=EnumSet.noneOf(Direction.class);
            for(var side:Direction.values())if(level.hasChunkAt(worldPosition.relative(side))&&!bundleAt(side)&&!bundledControllerAt(side)&&external[side.ordinal()]==i
                    &&(lanes[i].enabled&(1<<side.ordinal()))!=0)exposed.add(side);
            lanes[i].managed.setExposedOnSides(exposed);
        }
        for(int i=0;i<LIMIT;i++)if(node(i)!=null)for(var side:Direction.values()) {
            var target=expected(i,side);
            if(target==null||lanes[i].links[side.ordinal()]!=null)continue;
            IGridConnection edge=null;
            for(var connection:node(i).getConnections())if(connection.getOtherSide(node(i))==target){edge=connection;break;}
            if(edge==null)edge=GridHelper.createConnection(node(i),target);
            lanes[i].links[side.ordinal()]=edge;
            if(level.getBlockEntity(worldPosition.relative(side)) instanceof BundledCableEntity other) {
                other.lanes[lanes[i].targets[side.ordinal()]].links[side.getOpposite().ordinal()]=edge;
                other.schedule();
            }
        }
        int controllers=0;
        for(var side:Direction.values())if(bundledControllerAt(side))controllers|=1<<side.ordinal();
        boolean update=controllers!=controllerFaces;
        controllerFaces=controllers;
        for(int i=0;i<LIMIT;i++)if(lanes[i]!=null) {
            var lane=lanes[i];var node=node(i);
            int connected=0,ext=0,used=node==null?0:node.getUsedChannels();
            int capacity=node==null?(lane.spec.type().isDense()?32:8):node.getMaxChannels();
            boolean infinite=node!=null&&node.getGrid().getPathingService().getChannelMode()==appeng.api.networking.pathing.ChannelMode.INFINITE;
            for(var side:Direction.values()) {
                int index=side.ordinal();
                IGridConnection edge=node==null?null:node.getInWorldConnections().get(side);
                if(edge==null&&node!=null&&node.getConnections().contains(lane.links[index]))edge=lane.links[index];
                int sideUsed=edge==null?0:edge.getUsedChannels();
                int visual=visualChannels(lane.spec.type(),sideUsed,capacity,infinite);
                update|=lane.sideChannels[index]!=sideUsed||lane.visualChannels[index]!=visual;
                lane.sideChannels[index]=sideUsed;lane.visualChannels[index]=visual;
            }
            if(node!=null) {
                for(var side:Direction.values()) {
                    if(lane.links[side.ordinal()]!=null&&node.getConnections().contains(lane.links[side.ordinal()]))connected|=1<<side.ordinal();
                    if(node.getInWorldConnections().containsKey(side)){connected|=1<<side.ordinal();ext|=1<<side.ordinal();}
                }
            }
            boolean power=node!=null&&node.isPowered();
            var state=appeng.integration.modules.igtooltip.GridNodeState.fromNode(node);
            update|=lane.state!=state;
            lane.state=state;
            update|=lane.connected!=connected||lane.external!=ext||lane.channels!=used||lane.powered!=power||lane.capacity!=capacity;
            lane.connected=connected;lane.external=ext;lane.channels=used;lane.powered=power;
            lane.capacity=capacity;
        }
        if(update){rebuildGeometry();sync();}
    }
    private void rebuildGeometry() {
        var all=new ArrayList<ConduitGeometry.Piece>();var visual=new ArrayList<RenderLane>();var panels=new ArrayList<RenderFacade>();
        for(int i=0;i<LIMIT;i++)if(lanes[i]!=null) {
            var lane=lanes[i];var renderTargets=lane.targets.clone();
            for(var side:Direction.values())if((controllerFaces&(1<<side.ordinal()))!=0)renderTargets[side.ordinal()]=i;
            var targets=Arrays.stream(renderTargets).boxed().toList();
            all.addAll(ConduitGeometry.pieces(i,lane.connected,lane.external,targets));
            visual.add(new RenderLane(i,lane.spec.type(),lane.spec.color(),lane.channels,lane.powered,lane.connected,lane.external,targets,Arrays.stream(lane.visualChannels).boxed().toList()));
        }
        for(var side:Direction.values())if(facades[side.ordinal()].getItem() instanceof appeng.api.implementations.items.IFacadeItem facade)
            panels.add(new RenderFacade(side,facade.getTextureBlockState(facades[side.ordinal()])));
        pieces=List.copyOf(all);shape=ConduitGeometry.shape(pieces);
        snapshot=new RenderSnapshot(List.copyOf(visual),List.copyOf(panels),selected);
    }
    public void selectVisual(int slot) {
        if(selected==slot)return;selected=slot;rebuildGeometry();requestModelDataUpdate();
        if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    @Override public ModelData getModelData() { return ModelData.builder().with(RENDER,snapshot).build(); }
    public Component status(int slot) {
        var lane=valid(slot)?lanes[slot]:null;
        if(lane==null)return Component.empty();
        return Component.translatable("message.mebundledcable.status",slot+1,lane.spec.item().getHoverName(),lane.channels,
                lane.capacity==Integer.MAX_VALUE?Component.translatable("message.mebundledcable.unlimited"):Component.literal(Integer.toString(lane.capacity)));
    }
    // Match CablePart's display scaling; the native model groups dense channels into four.
    public static int visualChannels(AECableType type,int used,int capacity,boolean infinite) {
        int max=type==AECableType.NONE?0:type.isDense()?32:8;
        if(used<=0||max==0)return 0;
        if(infinite)return max;
        if(capacity<=0)return 0;
        return Math.max(1,(int)Math.min(max,(long)used*max/capacity));
    }
    public int channels(int slot,Direction side) {
        return valid(slot)&&lanes[slot]!=null?(side==null?lanes[slot].channels:lanes[slot].sideChannels[side.ordinal()]):0;
    }
    public int capacity(int slot) { return valid(slot)&&lanes[slot]!=null?lanes[slot].capacity:0; }
    public Component nodeState(int slot) {
        return (valid(slot)&&lanes[slot]!=null?lanes[slot].state:appeng.integration.modules.igtooltip.GridNodeState.OFFLINE).textComponent();
    }
    private CompoundTag laneTag(int i,boolean network,HolderLookup.Provider registries) {
        var lane=lanes[i];var tag=new CompoundTag();
        tag.putInt("slot",i);tag.put("item",lane.spec.item().save(registries));
        tag.putString("cableType",lane.spec.type().name());tag.putString("color",lane.spec.color().name());
        tag.putInt("enabled",lane.enabled);tag.putIntArray("connections",lane.targets);
        if(network) {
            tag.putInt("connected",lane.connected);tag.putInt("external",lane.external);tag.putInt("channels",lane.channels);tag.putBoolean("powered",lane.powered);
            tag.putInt("capacity",lane.capacity);tag.putIntArray("sideChannels",lane.sideChannels);tag.putIntArray("visualChannels",lane.visualChannels);
            tag.putString("state",lane.state.name());
        } else {
            if(lane.managed!=null) { lane.savedNode=new CompoundTag();lane.managed.saveToNBT(lane.savedNode); }
            tag.put("nodeData",lane.savedNode.copy());
            if(lane.pendingOwner>=0)tag.putInt("pendingOwner",lane.pendingOwner);
        }
        return tag;
    }
    private CompoundTag write(boolean network,HolderLookup.Provider registries) {
        var tag=new CompoundTag();tag.putInt("version",1);var list=new ListTag();
        for(int i=0;i<LIMIT;i++)if(lanes[i]!=null)list.add(laneTag(i,network,registries));
        tag.put("cables",list);tag.putIntArray("external",external);
        if(network)tag.putInt("controllerFaces",controllerFaces);
        var panels=new ListTag();for(int i=0;i<6;i++)if(!facades[i].isEmpty()){var panel=(CompoundTag)facades[i].save(registries);panel.putInt("side",i);panels.add(panel);}tag.put("facades",panels);
        return tag;
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.saveAdditional(tag,registries);tag.merge(write(false,registries)); }
    @Override public void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.loadAdditional(tag,registries);destroyNodes();read(tag,false,registries);queueInit(); }
    private void read(CompoundTag tag,boolean network,HolderLookup.Provider registries) {
        Arrays.fill(lanes,null);Arrays.fill(external,-1);Arrays.fill(facades,ItemStack.EMPTY);
        controllerFaces=network?tag.getInt("controllerFaces")&63:0;
        for(var entry:tag.getList("cables",Tag.TAG_COMPOUND)) {
            var cable=(CompoundTag)entry;int slot=cable.getInt("slot");var spec=CableSpec.of(ItemStack.parseOptional(registries,cable.getCompound("item")));
            if(!valid(slot)||lanes[slot]!=null||spec==null)continue;
            var lane=new Lane(spec,slot);lane.enabled=cable.getInt("enabled")&63;
            int[] targets=cable.getIntArray("connections");
            for(int side=0;side<6;side++)if(side<targets.length&&valid(targets[side]))lane.targets[side]=targets[side];
            lane.savedNode=cable.getCompound("nodeData").copy();
            if(cable.contains("pendingOwner"))lane.pendingOwner=cable.getInt("pendingOwner");
            if(network){lane.connected=cable.getInt("connected")&63;lane.external=cable.getInt("external")&63;lane.channels=Math.max(0,cable.getInt("channels"));lane.powered=cable.getBoolean("powered");}
            if(network) {
                if(cable.contains("capacity"))lane.capacity=Math.max(0,cable.getInt("capacity"));
                for(var state:appeng.integration.modules.igtooltip.GridNodeState.values())
                    if(state.name().equals(cable.getString("state")))lane.state=state;
                int[] used=cable.getIntArray("sideChannels"),visual=cable.getIntArray("visualChannels");
                for(int side=0;side<6;side++) {
                    lane.sideChannels[side]=side<used.length?Math.max(0,used[side]):0;
                    lane.visualChannels[side]=side<visual.length?Math.max(0,Math.min(lane.spec.type().isDense()?32:8,visual[side])):0;
                }
            }
            lanes[slot]=lane;
        }
        int[] ports=tag.getIntArray("external");for(int side=0;side<6;side++)if(side<ports.length&&spec(ports[side])!=null)external[side]=ports[side];
        for(var entry:tag.getList("facades",Tag.TAG_COMPOUND)) {
            var panel=(CompoundTag)entry;int side=panel.getInt("side");if(!panel.contains("id"))continue;
            var item=ItemStack.parseOptional(registries,panel);
            if(side>=0&&side<6&&item.getItem() instanceof appeng.api.implementations.items.IFacadeItem)facades[side]=item.copyWithCount(1);
        }
        rebuildGeometry();
    }
    private void destroyNodes() {
        destroying=true;
        for(var lane:lanes)if(lane!=null&&lane.managed!=null) {
            lane.savedNode=new CompoundTag();lane.managed.saveToNBT(lane.savedNode);
            var detached=lane.managed;lane.managed=null;Arrays.fill(lane.links,null);detached.destroy();
        }
        initialized=false;queued=false;destroying=false;
    }
    @Override public void setRemoved() { super.setRemoved();destroyNodes(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded();destroyNodes(); }
    private void sync() { if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return write(true,registries); }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries) { read(tag,true,registries);requestModelDataUpdate();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries) { handleUpdateTag(packet.getTag(),registries); }
}
