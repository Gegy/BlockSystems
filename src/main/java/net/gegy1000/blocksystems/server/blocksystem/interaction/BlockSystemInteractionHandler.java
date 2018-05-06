package net.gegy1000.blocksystems.server.blocksystem.interaction;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import javax.vecmath.Point3d;

public interface BlockSystemInteractionHandler {
    EntityPlayer getPlayer();

    void update();

    void updateTarget(BlockSystem blockSystem, BlockPos pos, EnumHand hand, EnumFacing side);

    void resetTarget();

    void handleClick();

    void handleHarvest();

    EnumActionResult handleInteract(BlockSystem blockSystem, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ);

    void handlePick(BlockSystem blockSystem, RayTraceResult mouseOver, EnumHand hand);

    default boolean canInteract(BlockSystem blockSystem, BlockPos pos) {
        EntityPlayer player = this.getPlayer();

        Point3d interactPoint = new Point3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Point3d globalPos = blockSystem.getTransform().toGlobalPos(interactPoint);
        double reachDistance = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 2.0;

        return player.getDistanceSq(globalPos.x, globalPos.y, globalPos.z) < reachDistance * reachDistance;
    }
}
