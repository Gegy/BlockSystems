package net.gegy1000.blocksystems.server.blocksystem.chunk;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.gegy1000.blocksystems.server.world.BlockSystemWorldAccess;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ServerBlockSystemChunk extends PartitionedChunk {
    protected final World mainWorld;
    protected final BlockSystemServer blockSystem;

    protected ChunkPos partitionPos;

    protected int blockCount;

    public ServerBlockSystemChunk(BlockSystemServer blockSystem, int x, int z, @Nullable ChunkPos partitionPos) {
        super(blockSystem, x, z);
        this.mainWorld = blockSystem.getMainWorld();
        this.blockSystem = blockSystem;
        this.partitionPos = partitionPos;
    }

    public ServerBlockSystemChunk(BlockSystemServer blockSystem, int x, int z) {
        super(blockSystem, x, z);
        this.mainWorld = blockSystem.getMainWorld();
        this.blockSystem = blockSystem;
    }

    @Override
    public IBlockState setBlockState(BlockPos pos, IBlockState state) {
        IBlockState prev = this.getBlockState(pos);
        if (prev.getBlock() != Blocks.AIR) {
            this.blockCount--;
        }
        if (state.getBlock() != Blocks.AIR) {
            this.blockCount++;
        }
        if (this.isEmpty()) {
            this.deallocate();
        } else if (this.partitionPos == null) {
            this.partitionPos = this.blockSystem.getChunkHandler().allocateChunk(this);
        }
        if (this.partitionPos != null) {
            BlockSystemWorldAccess.setBlockState(this.mainWorld, this.fromLocal(pos), state);
        }
        return super.setBlockState(pos, state);
    }

    @Override
    public NBTTagCompound serialize(NBTTagCompound compound) {
        NBTTagList sectionList = new NBTTagList();
        for (ExtendedBlockStorage section : this.getBlockStorageArray()) {
            if (section != NULL_BLOCK_STORAGE && !section.isEmpty()) {
                NBTTagCompound sectionCompound = new NBTTagCompound();
                sectionCompound.setShort("index", (short) (section.getYLocation() >> 4));
                sectionCompound.setByteArray("block_light", section.getBlockLight().getData());
                sectionCompound.setByteArray("sky_light", section.getSkyLight().getData());
                sectionList.appendTag(sectionCompound);
            }
        }
        compound.setTag("sections", sectionList);

        return compound;
    }

    @Override
    public void deserialize(NBTTagCompound compound) {
        NBTTagList sectionList = compound.getTagList("sections", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < sectionList.tagCount(); i++) {
            NBTTagCompound sectionTag = sectionList.getCompoundTagAt(i);
            int index = sectionTag.getShort("index");
            ExtendedBlockStorage section = this.getBlockStorageArray()[index];
            if (section != NULL_BLOCK_STORAGE && !section.isEmpty()) {
                section.setBlockLight(new NibbleArray(sectionTag.getByteArray("block_light")));
                section.setSkyLight(new NibbleArray(sectionTag.getByteArray("sky_light")));
            } else {
                BlockSystems.LOGGER.warn("Tried to populate light data for non-existent chunk section at {} {} {}", this.x, this.z, index);
            }
        }
    }

    @Override
    public void addTileEntity(BlockPos pos, TileEntity tile) {
        BlockPos partitionPos = this.fromLocal(pos);
        if (tile.getWorld() != this.mainWorld) {
            tile.setWorld(this.mainWorld);
        }
        tile.setPos(partitionPos);

        IBlockState state = this.getBlockState(pos);
        if (state.getBlock().hasTileEntity(state)) {
            if (this.tileEntities.containsKey(pos)) {
                this.tileEntities.get(pos).invalidate();
            }
            tile.validate();
            this.tileEntities.put(pos, tile);
        }

        this.mainWorld.setTileEntity(partitionPos, tile);
    }

    @Override
    @Nullable
    public ChunkPos getPartitionPos() {
        return this.partitionPos;
    }

    @Nullable
    @Override
    public Chunk getPartitionChunk() {
        if (this.partitionPos == null) {
            return null;
        }
        return BlockSystemWorldAccess.getChunk(this.mainWorld, this.partitionPos.x, this.partitionPos.z);
    }

    @Override
    public void deallocate() {
        this.blockSystem.getChunkHandler().deallocateChunk(this);
        this.partitionPos = null;
    }

    @Override
    public boolean isPopulated() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return this.blockCount <= 0;
    }
}
