package net.gegy1000.blocksystems.server.util;

import net.minecraft.util.math.AxisAlignedBB;

import javax.vecmath.Point3d;

public class RotatedAABB extends AxisAlignedBB {
    protected Matrix transformMatrix = new Matrix(3);
    protected double offsetX;
    protected double offsetY;
    protected double offsetZ;
    protected float rotationX;
    protected float rotationY;
    protected float rotationZ;
    protected AxisAlignedBB aabb;

    public RotatedAABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void recalculate() {
        this.transformMatrix.setIdentity();
        this.transformMatrix.translate(this.offsetX, this.offsetY, this.offsetZ);
        this.transformMatrix.rotate(Math.toRadians(this.rotationY), 0.0F, 1.0F, 0.0F);
        this.transformMatrix.rotate(Math.toRadians(this.rotationX), 1.0F, 0.0F, 0.0F);
        this.transformMatrix.rotate(Math.toRadians(this.rotationZ), 0.0F, 0.0F, 1.0F);

        this.aabb = this.toAABB();
    }

    public RotatedAABB move(double offsetX, double offsetY, double offsetZ, float rotationX, float rotationY, float rotationZ) {
        if (this.rotationX != rotationX || this.rotationY != rotationY || this.rotationZ != rotationZ || this.offsetX != offsetX || this.offsetY != offsetY || this.offsetZ != offsetZ) {
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.recalculate();
        }
        return this;
    }

    protected AxisAlignedBB toAABB() {
        Point3d min = new Point3d(this.minX, this.minY, this.minZ);
        Point3d max = new Point3d(this.maxX, this.maxY, this.maxZ);
        this.transformMatrix.transform(min);
        this.transformMatrix.transform(max);
        return new AxisAlignedBB(Math.min(min.x, this.minX  + this.offsetX), Math.min(min.y, this.minY + this.offsetY), Math.min(min.z, this.minZ + this.offsetZ), Math.max(max.x, this.maxX + this.offsetX), Math.max(max.y, this.maxY + this.offsetY), Math.max(max.z, this.maxZ  + this.offsetZ));
    }

    public AxisAlignedBB aabb() {
        return this.aabb;
    }
}
