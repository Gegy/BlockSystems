package net.gegy1000.blocksystems.server.core;

import net.minecraft.client.particle.Particle;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;

import javax.vecmath.Point3d;

public class BlockSystemHooks {
    public static void transformEffect(Particle particle) {
        BlockSystem transforming = BlockSystem.transforming;
        if (transforming != null) {
            Point3d transformed = transforming.getTransformedPosition(new Point3d(particle.posX, particle.posY, particle.posZ));
            particle.setPosition(transformed.getX(), transformed.getY(), transformed.getZ());
            particle.prevPosX = transformed.getX();
            particle.prevPosY = transformed.getY();
            particle.prevPosZ = transformed.getZ();
            Vec3d transformedVelocity = transforming.getTransformedVector(new Vec3d(particle.motionX, particle.motionY, particle.motionZ));
            particle.motionX = transformedVelocity.xCoord;
            particle.motionY = transformedVelocity.yCoord;
            particle.motionZ = transformedVelocity.zCoord;
        }
    }

    public static World getMainWorld(World world) {
        if (world instanceof BlockSystem) {
            return ((BlockSystem) world).getMainWorld();
        }
        return world;
    }
}
