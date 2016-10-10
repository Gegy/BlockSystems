package net.gegy1000.blocksystems.server.blocksystem.chunk;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.world.BlockSystemWorldAccess;
import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.util.Constants;

public class BlockSystemChunk extends Chunk {
    protected BlockSystem blockSystem;
    protected World mainWorld;

    protected boolean loading;

    protected BlockPos[] partitionPositions = new BlockPos[16];

    protected int blockCount;

    public BlockSystemChunk(BlockSystem blockSystem, int chunkX, int chunkZ) {
        super(blockSystem, chunkX, chunkZ);
        this.blockSystem = blockSystem;
        this.mainWorld = blockSystem.getMainWorld();
        this.blockSystem.addSavedChunk(this);
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
        if (!this.loading) {
            BlockPos partitionPosition = this.partitionPositions[pos.getY() >> 4];
            if (partitionPosition != null) {
                int offsetX = partitionPosition.getX() << 4;
                int offsetY = partitionPosition.getY() << 4;
                int offsetZ = partitionPosition.getZ() << 4;
                BlockSystemWorldAccess.setBlockState(this.mainWorld, pos.add(offsetX, offsetY, offsetZ), state);
            }
        }
        return super.setBlockState(pos, state);
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        NBTTagList partitionPositions = new NBTTagList();
        for (int i = 0; i < this.partitionPositions.length; i++) {
            if (this.partitionPositions[i] != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("i", (byte) i);
                tag.setLong("p", this.partitionPositions[i].toLong());
                partitionPositions.appendTag(tag);
            }
        }
        compound.setTag("PartitionPositions", partitionPositions);
        return compound;
    }

    public void deserialize(NBTTagCompound compound) {
        this.loading = true;
        this.blockCount = 0;
        NBTTagList partitionPositions = compound.getTagList("PartitionPositions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < partitionPositions.tagCount(); i++) {
            NBTTagCompound tag = partitionPositions.getCompoundTagAt(i);
            this.partitionPositions[tag.getByte("i")] = BlockPos.fromLong(tag.getLong("p"));
        }
        for (int partitionY = 0; partitionY < 16; partitionY++) {
            BlockPos partitionPosition = this.partitionPositions[partitionY];
            if (partitionPosition != null) {
                BlockSystemSavedData.addPartitionToQueue(this.mainWorld, partitionPosition);
                Chunk chunk = BlockSystemWorldAccess.getChunk(this.mainWorld, partitionPosition.getX(), partitionPosition.getZ());
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                BlockPos.MutableBlockPos chunkPos = new BlockPos.MutableBlockPos();
                int offsetY = partitionPosition.getY() << 4;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            pos.setPos(x, offsetY + y, z);
                            chunkPos.setPos(x, y, z);
                            this.setBlockState(chunkPos, chunk.getBlockState(pos));
                        }
                    }
                }
            }
        }
        this.loading = false;
    }

    public void remove() {
        if (!this.mainWorld.isRemote) {
            for (int y = 0; y < 16; y++) {
                BlockSystemSavedData data = BlockSystemSavedData.get(this.mainWorld);
                data.deletePartition(this.partitionPositions[y]);
                this.clearSpace(y);
            }
        }
        this.blockSystem.removeSavedChunk(this);
    }

    private void clearSpace(int partitionY) {
        BlockPos partitionPosition = this.partitionPositions[partitionY];
        if (partitionPosition != null) {
            Chunk chunk = BlockSystemWorldAccess.getChunk(this.mainWorld, partitionPosition.getX(), partitionPosition.getZ());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int offsetY = partitionPosition.getY() << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        pos.setPos(x, offsetY + y, z);
                        chunk.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }

    @Override
    public void onTick(boolean fast) {
        super.onTick(fast);
        if (!this.mainWorld.isRemote) {
            for (int y = 0; y < 16; y++) {
                ExtendedBlockStorage storage = this.getBlockStorageArray()[y];
                if (this.partitionPositions[y] == null && storage != NULL_BLOCK_STORAGE && !storage.isEmpty()) {
                    this.partitionPositions[y] = ChunkPartitionHandler.generateValidPartitionPosition(this.mainWorld);
                    this.clearSpace(y);
                }
            }
        }
    }

    protected TileEntity createNewTileEntity(BlockPos pos) {
        IBlockState state = this.getBlockState(pos);
        Block block = state.getBlock();
        return !block.hasTileEntity(state) ? null : block.createTileEntity(this.mainWorld, state);
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
