package net.gegy1000.blocksystems.server.util.collision;

import net.gegy1000.blocksystems.server.util.math.Matrix;
import net.minecraft.util.math.AxisAlignedBB;

// TODO: In future support OBB collision instead of using encompassing bounds
public class RotatedAABB implements BoundingBox {
    private final AxisAlignedBB encompassing;

    public RotatedAABB(AxisAlignedBB bounds, Matrix transformationMatrix) {
        EncompassedAABB encompassing = new EncompassedAABB(bounds);
        encompassing.calculate(transformationMatrix);
        this.encompassing = encompassing.getAabb();
    }

    @Override
    public boolean intersects(AxisAlignedBB box) {
        return this.encompassing.intersects(box);
    }

    @Override
    public AxisAlignedBB getAabb() {
        return this.encompassing;
    }
}
