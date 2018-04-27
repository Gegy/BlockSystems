package net.gegy1000.blocksystems.server.util.collision;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.util.math.Matrix;
import net.minecraft.util.math.AxisAlignedBB;

import javax.vecmath.Point3d;

public class EncompassedAABB implements BoundingBox {
    private final AxisAlignedBB bounds;

    private AxisAlignedBB encompassingBounds;

    public EncompassedAABB(AxisAlignedBB bounds) {
        this.bounds = bounds;
    }

    public EncompassedAABB(AxisAlignedBB bounds, Matrix transformMatrix) {
        this(bounds);
        this.calculate(transformMatrix);
    }

    public void calculate(Matrix transformMatrix) {
        Point3d[] transformedPoints = BBVertices.toVertices(this.bounds, transformMatrix::transform);

        Point3d min = this.minPoint(transformedPoints);
        Point3d max = this.maxPoint(transformedPoints);

        this.encompassingBounds = new AxisAlignedBB(min.x, min.y, min.z, max.x, max.y, max.z);
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

    @Override
    public boolean intersects(AxisAlignedBB box) {
        return this.getAabb().intersects(box);
    }

    @Override
    public AxisAlignedBB getAabb() {
        if (this.encompassingBounds == null) {
            BlockSystems.LOGGER.warn("Tried to get encompassing bounds before calculated!");
            return this.bounds;
        }
        return this.encompassingBounds;
    }
}
