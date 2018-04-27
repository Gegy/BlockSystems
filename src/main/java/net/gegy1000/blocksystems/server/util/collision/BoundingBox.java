package net.gegy1000.blocksystems.server.util.collision;

import net.minecraft.util.math.AxisAlignedBB;

public interface BoundingBox {
    boolean intersects(AxisAlignedBB box);

    AxisAlignedBB getAabb();
}
