package net.gegy1000.blocksystems.server.core;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;
import net.gegy1000.blocksystems.server.world.BlockSystemWorldAccess;
import net.gegy1000.blocksystems.server.world.HookedChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import javax.vecmath.Point3d;
import java.util.HashMap;
import java.util.Map;

public class BlockSystemHooks {
    private static final Map<World, HookedChunk> HOOKED_CHUNKS = new HashMap<>();

    public static void onWorldLoad(World world) {
        HOOKED_CHUNKS.put(world, new HookedChunk(world, 0, 0));
    }

    public static void onWorldUnload(World world) {
        HOOKED_CHUNKS.remove(world);
    }

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

    public static boolean checkBlockAccess(World world, BlockPos pos) {
        boolean outsideWorldHeight = pos.getY() < 0 || pos.getY() >= 256;
        boolean partitionSpace = pos.getX() <= -30000000 || pos.getX() > 29999984 || pos.getZ() <= -30000000 || pos.getZ() > 29999984;
        return outsideWorldHeight || (partitionSpace && !BlockSystemWorldAccess.canAccess(world) && !(world instanceof BlockSystem));
    }

    public static boolean checkChunkAccess(World world, int x, int z, boolean override) {
        boolean partitionSpace = x <= -1875000 || x > 1874999 || z <= -1875000 || z > 1874999;
        return partitionSpace && (!BlockSystemWorldAccess.canAccess(world) || override) && !(world instanceof BlockSystem);
    }

    public static Chunk getChunk(World world, int x, int z) {
        if (BlockSystemHooks.checkBlockAccess(world, new BlockPos(x << 4, 0, z << 4))) {
            return HOOKED_CHUNKS.get(world);
        }
        return world.getChunkProvider().provideChunk(x, z);
    }

    public static void initChunkPrimer(World world, Chunk chunk, int x, int z) {
        if (BlockSystemHooks.checkChunkAccess(world, x, z, true)) {
            ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
            for (int i = 0; i < storage.length; i++) {
                storage[i] = Chunk.NULL_BLOCK_STORAGE;
            }
        }
    }

    public static void getMouseOver() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver != null && mc.thePlayer != null) {
            Vec3d eyePos = mc.thePlayer.getPositionEyes(1.0F);
            ServerBlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(mc.theWorld);
            BlockSystem blockSystem = handler.getMousedOver(mc.thePlayer);
            if (blockSystem != null) {
                RayTraceResult mousedOver = handler.getMousedOverResult(mc.thePlayer);
                double length = mc.objectMouseOver.hitVec.subtract(eyePos).lengthSquared();
                Vec3d blockSystemEyePos = blockSystem.getUntransformedPosition(eyePos);
                if (mousedOver.hitVec.subtract(blockSystemEyePos).lengthSquared() < length) {
                    mc.objectMouseOver = new RayTraceResult(RayTraceResult.Type.MISS, blockSystemEyePos, EnumFacing.DOWN, mousedOver.getBlockPos());
                }
            }
        }
    }
}
