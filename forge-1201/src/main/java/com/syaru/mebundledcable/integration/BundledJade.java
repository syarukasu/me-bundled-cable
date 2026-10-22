package com.syaru.mebundledcable.integration;

import com.syaru.mebundledcable.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

@WailaPlugin
public final class BundledJade implements IWailaPlugin {
    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Provider.INSTANCE,BundledCableBlock.class);
        registration.registerBlockIcon(Provider.INSTANCE,BundledCableBlock.class);
    }
    public enum Provider implements IBlockComponentProvider {
        INSTANCE;
        @Override public ResourceLocation getUid() { return new ResourceLocation("mebundledcable","channels"); }
        private ConduitGeometry.Hit target(BlockAccessor accessor,BundledCableEntity cable) {
            var origin=Vec3.atLowerCornerOf(accessor.getPosition());
            return ConduitGeometry.hit(cable.pieces(),accessor.getPlayer().getEyePosition().subtract(origin),
                    accessor.getHitResult().getLocation().subtract(origin));
        }
        @Override public void appendTooltip(ITooltip tooltip,BlockAccessor accessor,IPluginConfig config) {
            if(!(accessor.getBlockEntity() instanceof BundledCableEntity cable))return;
            var hit=target(accessor,cable);
            if(hit==null)return;
            tooltip.add(cable.status(hit.slot()));
            if(hit.side()!=null)tooltip.add(Component.translatable("message.mebundledcable.face_channels",
                    Component.translatable("message.mebundledcable.side."+hit.side().getSerializedName()),cable.channels(hit.slot(),hit.side())));
        }
        @Override public IElement getIcon(BlockAccessor accessor,IPluginConfig config,IElement current) {
            if(!(accessor.getBlockEntity() instanceof BundledCableEntity cable))return current;
            var hit=target(accessor,cable);
            return hit==null?current:IElementHelper.get().item(cable.spec(hit.slot()).item());
        }
    }
}
