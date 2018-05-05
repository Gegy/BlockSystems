package net.gegy1000.blocksystems.server.world;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.IdentityHashMap;
import java.util.Map;

public class BlockSystemWorldAccess {
    private static final Map<World, ThreadLocal<Boolean>> BLOCK_ACCESS = new IdentityHashMap<>();

    public static Chunk getChunk(World world, int x, int z) {
        ThreadLocal<Boolean> access = BlockSystemWorldAccess.getAccess(world);
        access.set(true);
        Chunk chunk = world.getChunkFromChunkCoords(x, z);
        access.set(false);
        return chunk;
    }

    public static Chunk getChunkFromBlock(World world, BlockPos pos) {
        return BlockSystemWorldAccess.getChunk(world, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static IBlockState getBlockState(World world, BlockPos pos) {
        ThreadLocal<Boolean> access = BlockSystemWorldAccess.getAccess(world);
        access.set(true);
        IBlockState state = world.getBlockState(pos);
        access.set(false);
        return state;
    }

    public static boolean setBlockState(World world, BlockPos pos, IBlockState state) {
        return BlockSystemWorldAccess.setBlockState(world, pos, state, 3);
    }

    public static boolean setBlockState(World world, BlockPos pos, IBlockState state, int flags) {
        ThreadLocal<Boolean> access = BlockSystemWorldAccess.getAccess(world);
        access.set(true);
        boolean set = world.setBlockState(pos, state, flags);
        access.set(false);
        return set;
    }

    public static ThreadLocal<Boolean> getAccess(World world) {
        ThreadLocal<Boolean> access = BLOCK_ACCESS.get(world);
        if (access == null) {
            access = new ThreadLocal<>();
            access.set(false);
            BLOCK_ACCESS.put(world, access);
        }
        return access;
    }

    public static boolean canAccess(World world) {
        ThreadLocal<Boolean> access = BlockSystemWorldAccess.getAccess(world);
        Boolean canAccess = access.get();
        return canAccess != null && canAccess;
    }

    public static void unloadWorld(World world) {
        BLOCK_ACCESS.remove(world);
    }
}
