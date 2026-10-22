package com.syaru.mebundledcable.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.syaru.mebundledcable.BundledCableEntity;
import com.syaru.mebundledcable.ConduitGeometry;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.*;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import com.mojang.math.Transformation;
import com.mojang.math.Axis;

public final class BundledModel implements IDynamicBakedModel {
    private record Mesh(List<BakedQuad> solid,List<BakedQuad> ghost) { }
    private static final Cache<BundledCableEntity.RenderSnapshot,Mesh> CACHE=CacheBuilder.newBuilder().maximumSize(1024).build();
    private static final ResourceLocation WHITE=new ResourceLocation("minecraft","block/white_concrete");
    private static final ResourceLocation MODEL=new ResourceLocation("mebundledcable","block/cable");
    public static final List<ResourceLocation> PARTS=List.of(part("core"),part("core_dense"),part("arm"),part("arm_dense"),part("connector"),part("connection_box"));
    private static ResourceLocation part(String name) { return new ResourceLocation("mebundledcable","block/"+name); }
    public static void clear() { CACHE.invalidateAll(); }
    public static BakedQuad remapNativeQuad(BakedQuad original) {
        var old=original.getSprite();var id=old.contents().name();
        if(!id.getNamespace().equals("ae2")||!id.getPath().startsWith("part/cable/"))
            return new BakedQuad(original.getVertices().clone(),original.getTintIndex(),original.getDirection(),old,original.isShade(),original.hasAmbientOcclusion());
        var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(new ResourceLocation("mebundledcable","ae2_1211/"+id.getPath()));
        int[] vertices=original.getVertices().clone();int stride=vertices.length/4;
        for(int i=0;i<4;i++) {
            float u=Float.intBitsToFloat(vertices[i*stride+4]),v=Float.intBitsToFloat(vertices[i*stride+5]);
            vertices[i*stride+4]=Float.floatToRawIntBits(sprite.getU0()+(u-old.getU0())/(old.getU1()-old.getU0())*(sprite.getU1()-sprite.getU0()));
            vertices[i*stride+5]=Float.floatToRawIntBits(sprite.getV0()+(v-old.getV0())/(old.getV1()-old.getV0())*(sprite.getV1()-sprite.getV0()));
        }
        return new BakedQuad(vertices,original.getTintIndex(),original.getDirection(),sprite,original.isShade(),original.hasAmbientOcclusion());
    }
    public static final class Geometry implements IUnbakedGeometry<Geometry> {
        @Override public BakedModel bake(IGeometryBakingContext context,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,
                ModelState state,ItemOverrides overrides,ResourceLocation location) { return new BundledModel(); }
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer) {
        if(side!=null)return List.of();
        var snapshot=data.get(BundledCableEntity.RENDER);if(snapshot==null)return List.of();
        var mesh=CACHE.getIfPresent(snapshot);
        if(mesh==null){mesh=build(snapshot);CACHE.put(snapshot,mesh);}
        if(layer==RenderType.translucent())return mesh.ghost;
        if(layer==null){var all=new ArrayList<>(mesh.solid);all.addAll(mesh.ghost);return all;}
        return mesh.solid;
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random) { return List.of(); }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout(),RenderType.translucent());
    }
    private static Mesh build(BundledCableEntity.RenderSnapshot snapshot) {
        var solid=new ArrayList<BakedQuad>();var ghost=new ArrayList<BakedQuad>();
        for(var lane:snapshot.lanes()) {
            boolean faint=snapshot.selected()>=0&&snapshot.selected()!=lane.slot();
            int alpha=faint?55:255;var out=faint?ghost:solid;
            for(var part:ConduitGeometry.pieces(lane.slot(),lane.connected(),lane.external(),lane.targets())) {
                int rgb=lane.color().mediumVariant;
                if(snapshot.selected()==lane.slot())rgb=brighten(rgb);
                if(part.model().equals("core")||part.model().equals("arm"))
                    nativeCablePart(out,part,lane,alpha,snapshot.selected()==lane.slot());
                else upstreamPart(out,part,lane.type().isDense(),rgb,alpha);
            }
        }
        if(snapshot.selected()<0)for(var facade:snapshot.facades())facade(solid,facade);
        return new Mesh(List.copyOf(solid),List.copyOf(ghost));
    }
    /** Use AE2's own 1.21.1 mesh, UVs and channel overlays, fitted to Ender IO conduit bounds. */
    private static void nativeCablePart(List<BakedQuad> out,ConduitGeometry.Piece piece,
            BundledCableEntity.RenderLane lane,int alpha,boolean selected) {
        var visual=new appeng.client.render.cablebus.CableBusRenderState();
        visual.setCableType(lane.type());visual.setCableColor(lane.color());
        visual.setCoreType(appeng.client.render.cablebus.CableCoreType.fromCableType(lane.type()));
        if(!piece.core()) {
            var b=piece.box();
            var axis=b.getXsize()>b.getYsize()&&b.getXsize()>b.getZsize()?Direction.EAST:
                    b.getZsize()>b.getYsize()?Direction.SOUTH:Direction.UP;
            for(var side:List.of(axis,axis.getOpposite())) {
                visual.getConnectionTypes().put(side,lane.type());
                visual.getCableBusAdjacent().add(side);
                visual.getChannelsOnSide().put(side,lane.powered()&&piece.side()!=null?lane.visualChannels().get(piece.side().ordinal()):0);
            }
        }
        var state=appeng.core.definitions.AEBlocks.CABLE_BUS.block().defaultBlockState();
        var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        var data=ModelData.builder().with(appeng.client.render.cablebus.CableBusRenderState.PROPERTY,visual).build();
        var source=model.getQuads(state,null,RandomSource.create(0),data,RenderType.cutout());
        if(source.isEmpty())throw new IllegalStateException("AE2 cable mesh is empty");
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var quad:source) {
            var vertices=quad.getVertices();int stride=vertices.length/4;
            for(int i=0;i<4;i++) {
                var point=new Vector3f(Float.intBitsToFloat(vertices[i*stride]),Float.intBitsToFloat(vertices[i*stride+1]),Float.intBitsToFloat(vertices[i*stride+2]));
                min.min(point);max.max(point);
            }
        }
        var b=piece.box();
        var transform=QuadTransformers.applying(new Transformation(new Matrix4f()
                .translation((float)b.minX,(float)b.minY,(float)b.minZ)
                .scale((float)b.getXsize()/(max.x-min.x),(float)b.getYsize()/(max.y-min.y),(float)b.getZsize()/(max.z-min.z))
                .translate(-min.x,-min.y,-min.z)));
        for(var original:source) {
            var quad=remapNativeQuad(original);
            transform.processInPlace(quad);
            var vertices=quad.getVertices();int stride=vertices.length/4;
            for(int i=0;i<4;i++) {
                int color=vertices[i*stride+3];
                if(selected)color=brighten(color&0xffffff);
                vertices[i*stride+3]=(color&0xffffff)|(alpha<<24);
            }
            out.add(quad);
        }
    }
    private static void upstreamPart(List<BakedQuad> out,ConduitGeometry.Piece piece,boolean dense,int rgb,int alpha) {
        boolean attachment=!piece.model().equals("core")&&!piece.model().equals("arm");
        var model=Minecraft.getInstance().getModelManager().getModel(part(piece.model()+(dense&&!attachment?"_dense":"")));
        if(model==Minecraft.getInstance().getModelManager().getMissingModel())throw new IllegalStateException("Ender IO conduit model part was not baked");
        var source=model.getQuads(null,null,RandomSource.create(0));
        AABB base=switch(piece.model()) {
            case "core" -> new AABB(6.5/16,6.5/16,6.5/16,9.5/16,9.5/16,9.5/16);
            case "connector" -> new AABB(2.5/16,0,2.5/16,13.5/16,1.0/16,13.5/16);
            case "connection_box" -> new AABB(6.5/16,0,6.5/16,9.5/16,4.0/16,9.5/16);
            default -> new AABB(7.0/16,0,7.0/16,9.0/16,6.5/16,9.0/16);
        };
        var rotation=new Matrix4f();
        if(!piece.core()) {
            var b=piece.box();
            Direction direction=b.getXsize()>b.getYsize()&&b.getXsize()>b.getZsize()?Direction.EAST:
                    b.getZsize()>b.getYsize()?Direction.SOUTH:Direction.DOWN;
            if(attachment)direction=piece.side();
            rotation=rotation(direction).getMatrix();
            var min=new Vector3f(Float.POSITIVE_INFINITY);
            var max=new Vector3f(Float.NEGATIVE_INFINITY);
            for(int corner=0;corner<8;corner++) {
                var vertex=new Vector3f((float)((corner&1)==0?base.minX:base.maxX),(float)((corner&2)==0?base.minY:base.maxY),(float)((corner&4)==0?base.minZ:base.maxZ));
                rotation.transformPosition(vertex);min.min(vertex);max.max(vertex);
            }
            base=new AABB(min.x,min.y,min.z,max.x,max.y,max.z);
        }
        var b=piece.box();
        var transform=new Matrix4f().translation((float)b.minX,(float)b.minY,(float)b.minZ)
                .scale((float)(b.getXsize()/base.getXsize()),(float)(b.getYsize()/base.getYsize()),(float)(b.getZsize()/base.getZsize()))
                .translate((float)-base.minX,(float)-base.minY,(float)-base.minZ).mul(rotation);
        var transformer=QuadTransformers.applying(new Transformation(transform));
        for(var original:source) {
            var quad=new BakedQuad(original.getVertices().clone(),-1,original.getDirection(),original.getSprite(),original.isShade());
            transformer.processInPlace(quad);
            for(int i=0;i<4;i++)quad.getVertices()[i*(quad.getVertices().length/4)+3]=abgr(attachment?0xffffff:rgb,alpha);
            out.add(quad);
        }
    }
    /** Ender IO ConduitBlockModel.rotateTransformation, pinned 1.20.1 reference. */
    private static Transformation rotation(Direction direction) {
        var quaternion=new Quaternionf();
        switch(direction) {
            case UP -> quaternion.mul(Axis.ZP.rotationDegrees(180));
            case NORTH -> quaternion.mul(Axis.XP.rotationDegrees(90));
            case SOUTH -> quaternion.mul(Axis.XN.rotationDegrees(90));
            case WEST -> quaternion.mul(Axis.ZN.rotationDegrees(90));
            case EAST -> quaternion.mul(Axis.ZP.rotationDegrees(90));
            default -> { }
        }
        return new Transformation(null,quaternion,null,null).applyOrigin(new Vector3f(.5f,.5f,.5f));
    }
    private static int brighten(int rgb) {
        return (Math.min(255,((rgb>>16)&255)+55)<<16)|(Math.min(255,((rgb>>8)&255)+55)<<8)|Math.min(255,(rgb&255)+55);
    }
    private static void facade(List<BakedQuad> out,BundledCableEntity.RenderFacade facade) {
        var mc=Minecraft.getInstance();var model=mc.getBlockRenderer().getBlockModel(facade.state());
        var random=RandomSource.create(0);var quads=new ArrayList<>(model.getQuads(facade.state(),null,random,ModelData.EMPTY,null));
        for(var side:Direction.values())quads.addAll(model.getQuads(facade.state(),side,random,ModelData.EMPTY,null));
        var bounds=ConduitGeometry.facade(facade.side());
        for(var original:quads) {
            int[] vertices=original.getVertices().clone();int stride=vertices.length/4;
            int color=original.isTinted()?mc.getBlockColors().getColor(facade.state(),null,null,original.getTintIndex()):0xffffff;
            if(color<0)color=0xffffff;
            for(int vertex=0;vertex<4;vertex++) {
                int base=vertex*stride;
                float x=Float.intBitsToFloat(vertices[base]),y=Float.intBitsToFloat(vertices[base+1]),z=Float.intBitsToFloat(vertices[base+2]);
                vertices[base]=Float.floatToRawIntBits((float)(bounds.minX+x*bounds.getXsize()));
                vertices[base+1]=Float.floatToRawIntBits((float)(bounds.minY+y*bounds.getYsize()));
                vertices[base+2]=Float.floatToRawIntBits((float)(bounds.minZ+z*bounds.getZsize()));
                vertices[base+3]=abgr(color,255);
            }
            out.add(new BakedQuad(vertices,-1,original.getDirection(),original.getSprite(),original.isShade()));
        }
    }
    private static void box(List<BakedQuad> out,AABB b,int rgb,int alpha,TextureAtlasSprite sprite,boolean lit) {
        var from=new Vector3f((float)b.minX*16,(float)b.minY*16,(float)b.minZ*16);
        var to=new Vector3f((float)b.maxX*16,(float)b.maxY*16,(float)b.maxZ*16);
        for(var side:Direction.values()) {
            var face=new BlockElementFace(null,-1,"",new BlockFaceUV(new float[]{0,0,16,16},0));
            var quad=new FaceBakery().bakeQuad(from,to,face,sprite,side,BlockModelRotation.X0_Y0,null,!lit,MODEL);
            int[] vertices=quad.getVertices();for(int i=0;i<4;i++)vertices[i*(vertices.length/4)+3]=abgr(rgb,alpha);
            if(lit)QuadTransformers.settingEmissivity(15).processInPlace(quad);
            out.add(quad);
        }
    }
    private static int abgr(int rgb,int alpha) { return alpha<<24|(rgb&255)<<16|(rgb&0xff00)|((rgb>>16)&255); }
    private static TextureAtlasSprite sprite() { return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WHITE); }
    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return sprite(); }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
