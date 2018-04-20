package net.gegy1000.blocksystems.server.util;

import com.google.common.base.Preconditions;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3d;
import java.nio.FloatBuffer;
import java.util.Stack;

/**
 * @author pau101
 */
public class Matrix {
    private static final FloatBuffer MAT_BUFFER = BufferUtils.createFloatBuffer(16);

    private Pool<Matrix4d> matrixPool;

    private Pool<Vector3d> vectorPool;

    private Pool<AxisAngle4d> axisAnglePool;

    private Stack<Matrix4d> matrixStack;

    public Matrix() {
        this(0);
    }

    public Matrix(int poolSize) {
        Preconditions.checkArgument(poolSize >= 0, "poolSize must be greater or equal to zero");
        this.matrixPool = new Pool<>(Matrix4d::new, poolSize);
        this.vectorPool = new Pool<>(Vector3d::new, poolSize);
        this.axisAnglePool = new Pool<>(AxisAngle4d::new, poolSize);
        this.matrixStack = new Stack<>();
        Matrix4d mat = new Matrix4d();
        mat.setIdentity();
        this.matrixStack.push(mat);
    }

    private Matrix4d getMatrix() {
        Matrix4d mat = this.matrixPool.getInstance();
        mat.setZero();
        return mat;
    }

    private void freeMatrix(Matrix4d mat) {
        this.matrixPool.freeInstance(mat);
    }

    private Vector3d getVector(double x, double y, double z) {
        Vector3d vector = this.vectorPool.getInstance();
        vector.set(x, y, z);
        return vector;
    }

    private void freeVector(Vector3d vector) {
        this.vectorPool.freeInstance(vector);
    }

    private AxisAngle4d getAxisAngle(double x, double y, double z, double angle) {
        AxisAngle4d axisAngle = this.axisAnglePool.getInstance();
        axisAngle.set(x, y, z, angle);
        return axisAngle;
    }

    private void freeAxisAngle(AxisAngle4d axisAngle) {
        this.axisAnglePool.freeInstance(axisAngle);
    }

    public void invert() {
        this.matrixStack.peek().invert();
    }

    public void push() {
        Matrix4d mat = this.getMatrix();
        mat.set(this.matrixStack.peek());
        this.matrixStack.push(mat);
    }

    public void pop() {
        if (this.matrixStack.size() < 2) {
            throw new Error("Stack underflow");
        }
        this.freeMatrix(this.matrixStack.pop());
    }

    public void setIdentity() {
        this.matrixStack.peek().setIdentity();
    }

    public void translate(double x, double y, double z) {
        Matrix4d mat = this.matrixStack.peek();
        Matrix4d translation = this.getMatrix();
        translation.setIdentity();
        Vector3d vector = this.getVector(x, y, z);
        translation.setTranslation(vector);
        this.freeVector(vector);
        mat.mul(translation);
        this.freeMatrix(translation);
    }

    public void rotate(double angle, double x, double y, double z) {
        Matrix4d mat = this.matrixStack.peek();
        Matrix4d rotation = this.getMatrix();
        rotation.setIdentity();
        AxisAngle4d axisAngle = this.getAxisAngle(x, y, z, angle);
        rotation.setRotation(axisAngle);
        this.freeAxisAngle(axisAngle);
        mat.mul(rotation);
        this.freeMatrix(rotation);
    }

    public void rotate(QuatRotation quat) {
        Matrix4d mat = this.matrixStack.peek();
        mat.mul(quat.getMatrix());
    }

    public void scale(double x, double y, double z) {
        Matrix4d mat = this.matrixStack.peek();
        Matrix4d scale = this.getMatrix();
        scale.m00 = x;
        scale.m11 = y;
        scale.m22 = z;
        scale.m33 = 1;
        mat.mul(scale);
        this.freeMatrix(scale);
    }

    public void transform(Point3d point) {
        Matrix4d mat = this.matrixStack.peek();
        mat.transform(point);
    }

    public Point3d transform(double x, double y, double z) {
        Point3d point = new Point3d(x, y, z);
        this.transform(point);
        return point;
    }

    public Vec3d transformPoint(Vec3d vec) {
        Point3d point = new Point3d(vec.x, vec.y, vec.z);
        this.transform(point);
        return new Vec3d(point.getX(), point.getY(), point.getZ());
    }

    public void transform(Vector3d point) {
        Matrix4d mat = this.matrixStack.peek();
        mat.transform(point);
    }

    public void multiply(Matrix matrix) {
        this.matrixStack.peek().mul(matrix.matrixStack.peek());
    }

    public Point3d getTranslation() {
        Matrix4d mat = this.matrixStack.peek();
        Point3d translation = new Point3d();
        mat.transform(translation);
        return translation;
    }

    public Quat4f getRotation() {
        Matrix4d mat = this.matrixStack.peek();
        Quat4f rotation = new Quat4f();
        mat.get(rotation);
        return rotation;
    }

    public Matrix4d getTransform() {
        return new Matrix4d(this.matrixStack.peek());
    }

    public static FloatBuffer toBuffer(Matrix4d matrix) {
        FloatBuffer buffer = MAT_BUFFER;

        buffer.clear();
        buffer.put((float) matrix.m00);
        buffer.put((float) matrix.m01);
        buffer.put((float) matrix.m02);
        buffer.put((float) matrix.m03);
        buffer.put((float) matrix.m10);
        buffer.put((float) matrix.m11);
        buffer.put((float) matrix.m12);
        buffer.put((float) matrix.m13);
        buffer.put((float) matrix.m20);
        buffer.put((float) matrix.m21);
        buffer.put((float) matrix.m22);
        buffer.put((float) matrix.m23);
        buffer.put((float) matrix.m30);
        buffer.put((float) matrix.m31);
        buffer.put((float) matrix.m32);
        buffer.put((float) matrix.m33);

        buffer.rewind();

        return buffer;
    }
}
