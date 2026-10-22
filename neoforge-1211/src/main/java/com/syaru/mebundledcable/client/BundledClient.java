package com.syaru.mebundledcable.client;

import appeng.util.InteractionUtil;
import com.syaru.mebundledcable.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid=BundledCableMod.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BundledClient {
    @SubscribeEvent public static void loaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(BundledCableMod.ID,"conduit"),(net.neoforged.neoforge.client.model.geometry.IGeometryLoader<BundledModel.Geometry>)(json,context)->new BundledModel.Geometry());
    }
    @SubscribeEvent public static void reload(ModelEvent.BakingCompleted event) { BundledModel.clear(); }
    @SubscribeEvent public static void parts(ModelEvent.RegisterAdditional event) {
        for(var part:BundledModel.PARTS)event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(part));
    }
    @SubscribeEvent public static void itemTint(ModelEvent.ModifyBakingResult event) {
        var key=new net.minecraft.client.resources.model.ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(BundledCableMod.ID,"cable"),"inventory");
        var original=event.getModels().get(key);
        if(original!=null)event.getModels().put(key,new net.neoforged.neoforge.client.model.BakedModelWrapper<>(original) {
            @Override public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
                return new net.minecraft.client.renderer.block.model.ItemOverrides() {
                    @Override public net.minecraft.client.resources.model.BakedModel resolve(net.minecraft.client.resources.model.BakedModel model,
                            net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed) {
                        var cable=BundledCableItem.unwrap(stack);
                        return cable.isEmpty()?original:Minecraft.getInstance().getItemRenderer().getModel(cable,level,entity,seed);
                    }
                };
            }
        });
    }
    @EventBusSubscriber(modid=BundledCableMod.ID,value=Dist.CLIENT)
    public static final class Selection {
        private static BundledCableEntity previous;
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            var mc=Minecraft.getInstance();BundledCableEntity current=null;int selected=-1;
            if(mc.level!=null&&mc.player!=null&&mc.screen==null
                    &&InteractionUtil.canWrenchDisassemble(mc.player.getMainHandItem())&&mc.hitResult instanceof BlockHitResult hit
                    &&mc.level.getBlockEntity(hit.getBlockPos()) instanceof BundledCableEntity cable) {
                current=cable;
                var target=ConduitGeometry.hit(cable.pieces(),mc.player.getEyePosition().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())),hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())));
                selected=target==null?-1:target.slot();
            }
            if(previous!=null&&previous!=current&&!previous.isRemoved())previous.selectVisual(-1);
            if(current!=null)current.selectVisual(selected);
            previous=current;
        }
    }
}
