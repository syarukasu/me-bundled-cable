package com.syaru.mebundledcable.client;

import appeng.util.InteractionUtil;
import com.syaru.mebundledcable.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=BundledCableMod.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BundledClient {
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(()->net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                BundledCableMod.CONTROLLER.get(),net.minecraft.client.renderer.RenderType.cutout()));
    }
    private static final java.util.Map<net.minecraft.client.resources.model.BakedModel,net.minecraft.client.resources.model.BakedModel> ITEM_MODELS=new java.util.IdentityHashMap<>();
    @SubscribeEvent public static void loaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("conduit",(net.minecraftforge.client.model.geometry.IGeometryLoader<BundledModel.Geometry>)(json,context)->new BundledModel.Geometry());
    }
    @SubscribeEvent public static void reload(ModelEvent.BakingCompleted event) { BundledModel.clear();ITEM_MODELS.clear(); }
    @SubscribeEvent public static void parts(ModelEvent.RegisterAdditional event) {
        for(var part:BundledModel.PARTS)event.register(part);
    }
    @SubscribeEvent public static void itemTint(ModelEvent.ModifyBakingResult event) {
        var key=new net.minecraft.client.resources.model.ModelResourceLocation(BundledCableMod.ID,"cable","inventory");
        var original=event.getModels().get(key);
        if(original!=null)event.getModels().put(key,new net.minecraftforge.client.model.BakedModelWrapper<>(original) {
            @Override public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
                return new net.minecraft.client.renderer.block.model.ItemOverrides() {
                    @Override public net.minecraft.client.resources.model.BakedModel resolve(net.minecraft.client.resources.model.BakedModel model,
                            net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed) {
                        var cable=BundledCableItem.unwrap(stack);
                        if(CableSpec.of(cable)==null)return original;
                        var nativeModel=Minecraft.getInstance().getItemRenderer().getModel(cable,level,entity,seed);
                        return ITEM_MODELS.computeIfAbsent(nativeModel,BundledClient::restyleItem);
                    }
                };
            }
        });
    }
    private static net.minecraft.client.resources.model.BakedModel restyleItem(net.minecraft.client.resources.model.BakedModel original) {
        return new net.minecraftforge.client.model.BakedModelWrapper<>(original) {
            @Override public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state,
                    net.minecraft.core.Direction side,net.minecraft.util.RandomSource random) {
                return original.getQuads(state,side,random).stream().map(BundledModel::remapNativeQuad).toList();
            }
            @Override public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state,
                    net.minecraft.core.Direction side,net.minecraft.util.RandomSource random,net.minecraftforge.client.model.data.ModelData data,net.minecraft.client.renderer.RenderType layer) {
                return original.getQuads(state,side,random,data,layer).stream().map(BundledModel::remapNativeQuad).toList();
            }
            @Override public java.util.List<net.minecraft.client.resources.model.BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous) {
                return java.util.List.of(this);
            }
        };
    }
    @Mod.EventBusSubscriber(modid=BundledCableMod.ID,value=Dist.CLIENT)
    public static final class Selection {
        private static BundledCableEntity previous;
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if(event.phase!=TickEvent.Phase.END)return;
            var mc=Minecraft.getInstance();BundledCableEntity current=null;int selected=-1;int editing=-1;
            if(mc.level!=null&&mc.player!=null&&mc.screen==null
                    &&InteractionUtil.canWrenchDisassemble(mc.player.getMainHandItem())&&mc.hitResult instanceof BlockHitResult hit
                    &&mc.level.getBlockEntity(hit.getBlockPos()) instanceof BundledCableEntity cable) {
                current=cable;
                editing=mc.player.isShiftKeyDown()?-1:BundledCableBlock.selectedSlot(mc.player,cable);
                var target=ConduitGeometry.hit(cable.interactionPieces(editing),mc.player.getEyePosition().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())),hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())));
                selected=editing>=0?editing:target==null?-1:target.slot();
            }
            if(previous!=null&&previous!=current&&!previous.isRemoved())previous.selectVisual(-1);
            if(current!=null)current.selectVisual(selected,editing);
            previous=current;
        }
    }
}
