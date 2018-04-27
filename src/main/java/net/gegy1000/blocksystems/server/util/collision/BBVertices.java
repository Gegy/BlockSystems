package net.gegy1000.blocksystems.server.util.collision;

import net.minecraft.util.math.AxisAlignedBB;

import javax.vecmath.Point3d;
import java.util.function.Consumer;

public class BBVertices {
    public static Point3d[] toVertices(AxisAlignedBB box) {
        return toVertices(box, null);
    }

    public static Point3d[] toVertices(AxisAlignedBB box, Consumer<Point3d> processor) {
        Point3d[] points = new Point3d[] {
                new Point3d(box.minX, box.minY, box.minZ),
                new Point3d(box.minX, box.minY, box.maxZ),
                new Point3d(box.minX, box.maxY, box.minZ),
                new Point3d(box.minX, box.maxY, box.maxZ),
                new Point3d(box.maxX, box.minY, box.minZ),
                new Point3d(box.maxX, box.minY, box.maxZ),
                new Point3d(box.maxX, box.maxY, box.minZ),
                new Point3d(box.maxX, box.maxY, box.maxZ)
        };
        if (processor != null) {
            for (Point3d point : points) {
                processor.accept(point);
            }
        }
        return points;
    }
}
