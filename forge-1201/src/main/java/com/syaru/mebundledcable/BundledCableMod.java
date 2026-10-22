package com.syaru.mebundledcable;

import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(BundledCableMod.ID)
public final class BundledCableMod {
    public static final String ID = "mebundledcable";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final RegistryObject<BundledCableBlock> BLOCK = BLOCKS.register("cable", BundledCableBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("cable", BundledCableItem::new);
    public static final RegistryObject<BundledControllerBlock> CONTROLLER = BLOCKS.register("controller",BundledControllerBlock::new);
    public static final RegistryObject<Item> CONTROLLER_ITEM = ITEMS.register("controller",
            ()->new appeng.block.AEBaseBlockItem(CONTROLLER.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<appeng.blockentity.networking.ControllerBlockEntity>> CONTROLLER_ENTITY =
            ENTITIES.register("controller",BundledControllerBlock::createEntityType);
    public static final RegistryObject<BlockEntityType<BundledCableEntity>> ENTITY = ENTITIES.register("cable",
            () -> BlockEntityType.Builder.of(BundledCableEntity::new, BLOCK.get()).build(null));
    public BundledCableMod() {
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
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::useCable);
        MinecraftForge.EVENT_BUS.addListener(this::chunkLoaded);
    }
    private void chunkLoaded(net.minecraftforge.event.level.ChunkEvent.Load event) {
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
        if(event.getEntity().isSpectator()||event.getUseBlock()==net.minecraftforge.eventbus.api.Event.Result.DENY)return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof BundledCableEntity)) return;
        var result = BLOCK.get().use(event.getLevel().getBlockState(event.getPos()), event.getLevel(), event.getPos(),
                event.getEntity(), event.getHand(), event.getHitVec());
        if (result != net.minecraft.world.InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
