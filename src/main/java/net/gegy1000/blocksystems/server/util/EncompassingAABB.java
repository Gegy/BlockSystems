package net.gegy1000.blocksystems.server.util;

import net.minecraft.util.math.AxisAlignedBB;

import javax.vecmath.Point3d;

public class EncompassingAABB {
    private final Matrix transformMatrix;
    private final AxisAlignedBB bounds;

    private AxisAlignedBB encompassingBounds;

    public EncompassingAABB(Matrix transformMatrix, AxisAlignedBB bounds) {
        this.transformMatrix = transformMatrix;
        this.bounds = bounds;
    }

    public void recalculate() {
        Point3d[] transformedPoints = new Point3d[] {
                this.transformMatrix.transform(this.bounds.minX, this.bounds.minY, this.bounds.minZ),
                this.transformMatrix.transform(this.bounds.minX, this.bounds.minY, this.bounds.maxZ),
                this.transformMatrix.transform(this.bounds.minX, this.bounds.maxY, this.bounds.minZ),
                this.transformMatrix.transform(this.bounds.minX, this.bounds.maxY, this.bounds.maxZ),
                this.transformMatrix.transform(this.bounds.maxX, this.bounds.minY, this.bounds.minZ),
                this.transformMatrix.transform(this.bounds.maxX, this.bounds.minY, this.bounds.maxZ),
                this.transformMatrix.transform(this.bounds.maxX, this.bounds.maxY, this.bounds.minZ),
                this.transformMatrix.transform(this.bounds.maxX, this.bounds.maxY, this.bounds.maxZ)
        };

        Point3d min = this.minPoint(transformedPoints);
        Point3d max = this.maxPoint(transformedPoints);

        this.encompassingBounds = new AxisAlignedBB(min.x, min.y, min.z, max.x, max.y, max.z);
    }

    public AxisAlignedBB getEncompassing() {
        if (this.encompassingBounds == null) {
            this.recalculate();
        }
        return this.encompassingBounds;
    }

    private Point3d minPoint(Point3d... points) {
        double minX = points[0].x;
        double minY = points[0].y;
        double minZ = points[0].z;

        for (int i = 1; i < points.length; i++) {
            Point3d point = points[i];
            if (point.x < minX) {
                minX = point.x;
            }
            if (point.y < minY) {
                minY = point.y;
            }
            if (point.z < minZ) {
                minZ = point.z;
            }
        }

        return new Point3d(minX, minY, minZ);
    }

    private Point3d maxPoint(Point3d... points) {
        double maxX = points[0].x;
        double maxY = points[0].y;
        double maxZ = points[0].z;

        for (int i = 1; i < points.length; i++) {
            Point3d point = points[i];
            if (point.x > maxX) {
                maxX = point.x;
            }
            if (point.y > maxY) {
                maxY = point.y;
            }
            if (point.z > maxZ) {
                maxZ = point.z;
            }
        }

        return new Point3d(maxX, maxY, maxZ);
    }
}
