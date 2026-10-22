package com.syaru.mebundledcable;

import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(BundledCableMod.ID)
public final class BundledCableMod {
    public static final String ID = "mebundledcable";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredHolder<Block,BundledCableBlock> BLOCK = BLOCKS.register("cable", BundledCableBlock::new);
    public static final DeferredHolder<Item,Item> ITEM = ITEMS.register("cable", BundledCableItem::new);
    public static final DeferredHolder<Block,BundledControllerBlock> CONTROLLER = BLOCKS.register("controller",BundledControllerBlock::new);
    public static final DeferredHolder<Item,Item> CONTROLLER_ITEM = ITEMS.register("controller",
            ()->new appeng.block.AEBaseBlockItem(CONTROLLER.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<appeng.blockentity.networking.ControllerBlockEntity>> CONTROLLER_ENTITY =
            ENTITIES.register("controller",BundledControllerBlock::createEntityType);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<BundledCableEntity>> ENTITY = ENTITIES.register("cable",
            () -> BlockEntityType.Builder.of(BundledCableEntity::new, BLOCK.get()).build(null));
    public BundledCableMod(IEventBus bus) {
        TABS.register("cables", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.mebundledcable"))
                .icon(() -> new net.minecraft.world.item.ItemStack(ITEM.get()))
                .displayItems((parameters, output) -> {
                    output.accept(CONTROLLER_ITEM.get());
                    for (var color : AEColor.values()) {
                        output.accept(BundledCableItem.wrap(AEParts.GLASS_CABLE.stack(color, 1)));
                        output.accept(BundledCableItem.wrap(AEParts.COVERED_CABLE.stack(color, 1)));
                        output.accept(BundledCableItem.wrap(AEParts.SMART_CABLE.stack(color, 1)));
                        output.accept(BundledCableItem.wrap(AEParts.COVERED_DENSE_CABLE.stack(color, 1)));
                        output.accept(BundledCableItem.wrap(AEParts.SMART_DENSE_CABLE.stack(color, 1)));
                    }
                }).build());
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event)->{
            event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,ENTITY.get(),(entity,context)->entity);
            event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,CONTROLLER_ENTITY.get(),(entity,context)->entity);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                    CONTROLLER_ENTITY.get(),appeng.blockentity.networking.ControllerBlockEntity::getEnergyStorage);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::useCable);
        NeoForge.EVENT_BUS.addListener(this::chunkLoaded);
    }
    private void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
        var pos=event.getChunk().getPos();
        level.getServer().execute(()->{
            for(var side:net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var chunk=level.getChunkSource().getChunkNow(pos.x+side.getStepX(),pos.z+side.getStepZ());
                if(chunk==null)continue;
                for(var entity:chunk.getBlockEntities().values())if(entity instanceof BundledCableEntity cable)cable.schedule();
            }
        });
    }
    private void useCable(PlayerInteractEvent.RightClickBlock event) {
        if(event.getEntity().isSpectator()||event.getUseBlock()==net.neoforged.neoforge.common.util.TriState.FALSE)return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof BundledCableEntity)) return;
        var result = BLOCK.get().use(event.getLevel().getBlockState(event.getPos()), event.getLevel(), event.getPos(),
                event.getEntity(), event.getHand(), event.getHitVec());
        if (result != net.minecraft.world.InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
