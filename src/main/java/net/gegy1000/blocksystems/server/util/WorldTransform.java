package net.gegy1000.blocksystems.server.util;

import net.gegy1000.blocksystems.server.util.math.Matrix;
import net.gegy1000.blocksystems.server.util.math.QuatRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

public class WorldTransform {
    private final Matrix toGlobalMatrix = new Matrix(3);
    private final Matrix toLocalMatrix = new Matrix(3);

    private final Matrix prevToGlobal = new Matrix(1);
    private final Matrix prevToLocal = new Matrix(1);

    public void calculate(double posX, double posY, double posZ, QuatRotation rotation) {
        this.prevToGlobal.setIdentity();
        this.prevToGlobal.multiply(this.toGlobalMatrix);

        this.prevToLocal.setIdentity();
        this.prevToLocal.multiply(this.toLocalMatrix);

        this.toGlobalMatrix.setIdentity();
        this.toGlobalMatrix.translate(posX, posY, posZ);
        // TODO: No idea why but this only works with the inverse?
        this.toGlobalMatrix.rotateInverse(rotation);
        this.toGlobalMatrix.translate(-0.5, 0.0, -0.5);

        this.toLocalMatrix.setIdentity();
        this.toLocalMatrix.multiply(this.toGlobalMatrix);
        this.toLocalMatrix.invert();
    }

    public Point3d toGlobalPos(Point3d position) {
        this.toGlobalMatrix.transform(position);
        return position;
    }

    public Vec3d toGlobalPos(Vec3d position) {
        Point3d point = new Point3d(position.x, position.y, position.z);
        this.toGlobalMatrix.transform(point);
        return new Vec3d(point.getX(), point.getY(), point.getZ());
    }

    public BlockPos toGlobalPos(BlockPos pos) {
        Point3d transformed = this.toGlobalPos(new Point3d(pos.getX(), pos.getY(), pos.getZ()));
        return new BlockPos(transformed.x, transformed.y, transformed.z);
    }

    public Point3d toLocalPos(Point3d position) {
        this.toLocalMatrix.transform(position);
        return position;
    }

    public Point3d toLocalPrevPos(Point3d position) {
        this.prevToLocal.transform(position);
        return position;
    }

    public Vec3d toLocalPos(Vec3d position) {
        Point3d point = new Point3d(position.x, position.y, position.z);
        this.toLocalMatrix.transform(point);
        return new Vec3d(point.getX(), point.getY(), point.getZ());
    }

    public BlockPos toLocalPos(BlockPos pos) {
        Point3d untransformed = this.toLocalPos(new Point3d(pos.getX(), pos.getY(), pos.getZ()));
        return new BlockPos(untransformed.x, untransformed.y, untransformed.z);
    }

    public Vec3d toGlobalVector(Vec3d vec) {
        Vector3d vector = new Vector3d(vec.x, vec.y, vec.z);
        this.toGlobalMatrix.transform(vector);
        return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
    }

    public Matrix toGlobal() {
        return this.toGlobalMatrix;
    }

    public Matrix toLocal() {
        return this.toLocalMatrix;
    }
}
