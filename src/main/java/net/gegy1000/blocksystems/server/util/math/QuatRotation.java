package net.gegy1000.blocksystems.server.util.math;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.server.util.Pool;
import net.minecraft.nbt.NBTTagCompound;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Quat4d;

public class QuatRotation {
    private final Quat4d rotation = new Quat4d(0.0, 0.0, 0.0, 1.0);
    private final Matrix4d matrix = new Matrix4d();

    private final Pool<Quat4d> quatPool;
    private final Pool<AxisAngle4d> anglePool;

    public QuatRotation() {
        this.matrix.set(this.rotation);

        this.quatPool = new Pool<>(Quat4d::new, 1);
        this.anglePool = new Pool<>(AxisAngle4d::new, 1);
    }

    public void rotate(double angle, double x, double y, double z) {
        Quat4d quat = this.quatPool.getInstance();
        AxisAngle4d axisAngle = this.anglePool.getInstance();

        axisAngle.set(x, y, z, Math.toRadians(angle));
        quat.set(axisAngle);
        this.rotation.mul(quat);

        this.matrix.set(this.rotation);

        this.quatPool.freeInstance(quat);
        this.anglePool.freeInstance(axisAngle);
    }

    public Matrix4d getMatrix() {
        return this.matrix;
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        compound.setDouble("rot_x", this.rotation.x);
        compound.setDouble("rot_y", this.rotation.y);
        compound.setDouble("rot_z", this.rotation.z);
        compound.setDouble("rot_w", this.rotation.w);
        return compound;
    }

    public void serialize(ByteBuf buf) {
        buf.writeDouble(this.rotation.x);
        buf.writeDouble(this.rotation.y);
        buf.writeDouble(this.rotation.z);
        buf.writeDouble(this.rotation.w);
    }

    public void deserialize(NBTTagCompound compound) {
        this.rotation.x = compound.getDouble("rot_x");
        this.rotation.y = compound.getDouble("rot_y");
        this.rotation.z = compound.getDouble("rot_z");
        this.rotation.w = compound.getDouble("rot_w");
        this.matrix.set(this.rotation);
    }

    public void deserialize(ByteBuf buf) {
        this.rotation.x = buf.readDouble();
        this.rotation.y = buf.readDouble();
        this.rotation.z = buf.readDouble();
        this.rotation.w = buf.readDouble();
        this.matrix.set(this.rotation);
    }

    public double getX() {
        return this.rotation.x;
    }

    public double getY() {
        return this.rotation.y;
    }

    public double getZ() {
        return this.rotation.z;
    }

    public double getW() {
        return this.rotation.w;
    }

    public double toYaw() {
        double x = this.rotation.x;
        double y = this.rotation.y;
        double z = this.rotation.z;
        return Math.toDegrees(Math.atan2(2.0 * y - 2 * x * z, 1.0 - 2.0 * (y * y) - 2.0 * (z * z)));
    }

    public double toPitch() {
        double x = this.rotation.getX();
        double y = this.rotation.getY();
        double z = this.rotation.getZ();
        return Math.toDegrees(Math.asin(2.0 * x * y + 2.0 * z));
    }

    public QuatRotation slerp(QuatRotation rotation, double intermediate) {
        QuatRotation slerp = new QuatRotation();
        slerp.rotation.interpolate(this.rotation, rotation.rotation, intermediate);
        slerp.matrix.set(slerp.rotation);
        return slerp;
    }

    public QuatRotation difference(QuatRotation other) {
        QuatRotation difference = new QuatRotation();
        difference.rotation.mulInverse(this.rotation, other.rotation);
        difference.matrix.set(difference.rotation);
        return difference;
    }

    public QuatRotation copy() {
        QuatRotation quatRotation = new QuatRotation();
        quatRotation.rotation.set(this.rotation);
        quatRotation.matrix.set(this.matrix);
        return quatRotation;
    }
}
