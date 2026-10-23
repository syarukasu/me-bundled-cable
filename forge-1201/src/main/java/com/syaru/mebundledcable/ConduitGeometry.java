package com.syaru.mebundledcable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Adapted from Ender IO 1.20.1 OffsetHelper and ConduitShape; see NOTICE.md. */
public final class ConduitGeometry {
    private static final int[][] OFFSETS = {{0,-1},{-1,0},{0,1},{1,0},{1,-1},{-1,-1},{-1,1},{1,1}};
    public record Piece(int slot, Direction side, AABB box, boolean core, String model) {
        public Piece(int slot,Direction side,AABB box,boolean core) { this(slot,side,box,core,core?"core":"arm"); }
    }
    public record Hit(int slot, Direction side) { }
    public static List<Piece> handles(int slot) {
        var result=new ArrayList<Piece>();
        for(var side:Direction.values()) {
            var center=new Vec3(.5,.5,.5).add(offset(slot,side.getAxis()))
                    .add(side.getStepX()*6.0/16,side.getStepY()*6.0/16,side.getStepZ()*6.0/16);
            result.add(new Piece(slot,side,cube(center,1.0/16),false,"handle"));
        }
        return List.copyOf(result);
    }
    private ConduitGeometry() { }
    public static Vec3 offset(int slot, Direction.Axis axis) {
        int x = OFFSETS[slot][0], y = OFFSETS[slot][1];
        return switch (axis) {
            case X -> new Vec3(0, y*3.0/16, x*3.0/16);
            case Y -> new Vec3(x*3.0/16, 0, y*3.0/16);
            case Z -> new Vec3(x*3.0/16, y*3.0/16, 0);
        };
    }
    public static List<Piece> pieces(int slot, int connected, int external, List<Integer> targets) {
        var result = new ArrayList<Piece>();
        Direction.Axis axis=Direction.Axis.Z;
        for(var direction:Direction.values())if((connected&(1<<direction.ordinal()))!=0)axis=direction.getAxis();
        Vec3 center = new Vec3(.5,.5,.5).add(offset(slot, axis));
        result.add(new Piece(slot, null, cube(center, 1.5/16), true));
        for (var side : Direction.values()) {
            if ((connected & (1 << side.ordinal())) == 0) continue;
            int portSlot=side.getAxisDirection()==Direction.AxisDirection.NEGATIVE?targets.get(side.ordinal()):slot;
            Vec3 port = new Vec3(.5,.5,.5).add(offset(portSlot, side.getAxis()));
            if ((external & (1 << side.ordinal())) != 0) port = new Vec3(.5,.5,.5);
            Vec3 end = port.add(side.getStepX()*.5, side.getStepY()*.5, side.getStepZ()*.5);
            // Ender IO-style offset arms; short orthogonal elbows join each fixed core.
            Vec3 elbow = new Vec3(port.x, center.y, center.z);
            add(result, slot, side, center, elbow);
            Vec3 elbow2 = new Vec3(port.x, port.y, center.z);
            add(result, slot, side, elbow, elbow2);
            add(result, slot, side, elbow2, port);
            var inner=port.add(side.getStepX()*1.5/16,side.getStepY()*1.5/16,side.getStepZ()*1.5/16);
            add(result, slot, side, inner, end);
            if((external&(1<<side.ordinal()))!=0) {
                result.add(new Piece(slot,side,rotate(new AABB(2.5/16,0,2.5/16,13.5/16,1.0/16,13.5/16),side),false,"connector"));
                result.add(new Piece(slot,side,rotate(new AABB(6.5/16,0,6.5/16,9.5/16,4.0/16,9.5/16),side),false,"connection_box"));
            }
        }
        return List.copyOf(result);
    }
    private static void add(List<Piece> list, int slot, Direction side, Vec3 a, Vec3 b) {
        if (a.distanceToSqr(b) < .000001) return;
        double r=1.0/16;
        double rx=a.x==b.x?r:0,ry=a.y==b.y?r:0,rz=a.z==b.z?r:0;
        list.add(new Piece(slot, side, new AABB(Math.max(0,Math.min(a.x,b.x)-rx),Math.max(0,Math.min(a.y,b.y)-ry),
                Math.max(0,Math.min(a.z,b.z)-rz),Math.min(1,Math.max(a.x,b.x)+rx),Math.min(1,Math.max(a.y,b.y)+ry),
                Math.min(1,Math.max(a.z,b.z)+rz)), false));
    }
    private static AABB cube(Vec3 c, double r) { return new AABB(c.x-r,c.y-r,c.z-r,c.x+r,c.y+r,c.z+r); }
    private static AABB rotate(AABB b,Direction side) {
        return switch(side) {
            case DOWN -> b;
            case UP -> new AABB(b.minX,1-b.maxY,b.minZ,b.maxX,1-b.minY,b.maxZ);
            case NORTH -> new AABB(b.minX,b.minZ,b.minY,b.maxX,b.maxZ,b.maxY);
            case SOUTH -> new AABB(b.minX,b.minZ,1-b.maxY,b.maxX,b.maxZ,1-b.minY);
            case WEST -> new AABB(b.minY,b.minX,b.minZ,b.maxY,b.maxX,b.maxZ);
            case EAST -> new AABB(1-b.maxY,b.minX,b.minZ,1-b.minY,b.maxX,b.maxZ);
        };
    }
    public static AABB facade(Direction side) {
        return switch (side) {
            case DOWN -> new AABB(0,0,0,1,1.0/16,1);
            case UP -> new AABB(0,15.0/16,0,1,1,1);
            case NORTH -> new AABB(0,0,0,1,1,1.0/16);
            case SOUTH -> new AABB(0,0,15.0/16,1,1,1);
            case WEST -> new AABB(0,0,0,1.0/16,1,1);
            case EAST -> new AABB(15.0/16,0,0,1,1,1);
        };
    }
    public static Hit hit(List<Piece> pieces, Vec3 point) {
        // Prefer a core over overlapping elbows; slot identities never compact on removal.
        for (var piece : pieces) if (piece.core && piece.box.inflate(.0001).contains(point)) return new Hit(piece.slot,null);
        for (var piece : pieces) if (piece.box.inflate(.0001).contains(point)) return new Hit(piece.slot,piece.side);
        return null;
    }
    public static Hit hit(List<Piece> pieces,Vec3 eye,Vec3 point) {
        Vec3 end=point.add(point.subtract(eye).normalize().scale(.001));
        Piece nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(var piece:pieces) {
            var intersection=piece.box.clip(eye,end);
            if(intersection.isEmpty())continue;
            double candidate=intersection.get().distanceToSqr(eye);
            if(candidate<distance-1e-9||(Math.abs(candidate-distance)<1e-9&&piece.core)) {
                nearest=piece;distance=candidate;
            }
        }
        return nearest==null?hit(pieces,point):new Hit(nearest.slot,nearest.side);
    }
    public static VoxelShape shape(List<Piece> pieces) {
        VoxelShape shape = Shapes.empty();
        for (var piece : pieces) shape = Shapes.or(shape, Shapes.create(piece.box));
        return shape;
    }
}
