package net.gegy1000.blocksystems.server.blocksystem.chunk;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

public abstract class PartitionedChunk extends Chunk {
    public PartitionedChunk(World world, int x, int z) {
        super(world, x, z);
    }

    @Nullable
    public abstract ChunkPos getPartitionPos();

    @Nullable
    public abstract Chunk getPartitionChunk();

    public abstract void deallocate();

    public abstract NBTTagCompound serialize(NBTTagCompound compound);

    public abstract void deserialize(NBTTagCompound compound);

    public BlockPos fromPartition(BlockPos partition) {
        return new BlockPos((partition.getX() & 0xF) + (this.x << 4), partition.getY() & 0xFF, (partition.getZ() & 0xF) + (this.z << 4));
    }

    public BlockPos fromLocal(BlockPos local) {
        ChunkPos partitionPos = this.getPartitionPos();
        if (partitionPos == null) {
            return local;
        }
        return new BlockPos((local.getX() & 0xF) + partitionPos.getXStart(), local.getY() & 0xFF, (local.getZ() & 0xFF) + partitionPos.getZStart());
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        Chunk partitionChunk = this.getPartitionChunk();
        if (partitionChunk == null) {
            return false;
        }
        return partitionChunk.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        Chunk partitionChunk = this.getPartitionChunk();
        if (partitionChunk == null) {
            return null;
        }
        return partitionChunk.getCapability(capability, facing);
    }
}
