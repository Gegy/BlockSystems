package net.gegy1000.blocksystems.server.blocksystem.chunk;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.world.BlockSystemWorldAccess;
import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.Map;

public class BlockSystemChunk extends Chunk {
    protected BlockSystem blockSystem;
    protected World mainWorld;

    protected boolean loading;

    protected BlockPos partitionPosition = null;

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
            this.updatePartition();
            if (this.partitionPosition != null) {
                int offsetX = this.partitionPosition.getX() << 4;
                int offsetZ = this.partitionPosition.getZ() << 4;
                BlockSystemWorldAccess.setBlockState(this.mainWorld, new BlockPos((pos.getX() & 15) + offsetX, pos.getY(), (pos.getZ() & 15) + offsetZ), state);
            }
            if (this.blockCount <= 0) {
                this.remove();
            }
        }
        return super.setBlockState(pos, state);
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        if (this.partitionPosition != null) {
            compound.setLong("PartitionPosition", this.partitionPosition.toLong());
        }
        return compound;
    }

    public void deserialize(NBTTagCompound compound) {
        this.loading = true;
        this.blockCount = 0;
        if (compound.hasKey("PartitionPosition")) {
            this.partitionPosition = BlockPos.fromLong(compound.getLong("PartitionPosition"));
            BlockSystemSavedData.enqueuePartition(this.mainWorld, this.blockSystem, this.partitionPosition);
            Chunk chunk = BlockSystemWorldAccess.getChunk(this.mainWorld, this.partitionPosition.getX(), this.partitionPosition.getZ());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
            int offsetX = this.partitionPosition.getX() << 4;
            int offsetZ = this.partitionPosition.getZ() << 4;
            for (int storageY = 0; storageY < 16; storageY++) {
                int baseY = storageY << 4;
                int count = 0;
                ExtendedBlockStorage storage = this.getBlockStorageArray()[storageY];
                if (storage == NULL_BLOCK_STORAGE) {
                    storage = new ExtendedBlockStorage(baseY, !this.blockSystem.provider.isNether());
                }
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            pos.setPos(x, y + baseY, z);
                            worldPos.setPos(pos.getX() + offsetX, pos.getY(), pos.getZ() + offsetZ);
                            IBlockState state = chunk.getBlockState(pos);
                            storage.set(x, y & 15, z, state);
                            if (state.getBlock() != Blocks.AIR) {
                                count++;
                            }
                        }
                    }
                }
                if (count > 0) {
                    this.getBlockStorageArray()[storageY] = storage;
                }
                this.blockCount += count;
            }
            for (Map.Entry<BlockPos, TileEntity> entry : chunk.tileEntities.entrySet()) {
                this.addTileEntity(entry.getValue());
            }
        }
        this.loading = false;
    }

    public void remove() {
        if (!this.mainWorld.isRemote) {
            BlockSystemSavedData data = BlockSystemSavedData.get(this.mainWorld);
            this.clearPartition();
            data.deletePartition(this.partitionPosition);
        }
        this.blockSystem.removeSavedChunk(this);
    }

    private void clearPartition() {
        if (this.partitionPosition != null) {
            Chunk chunk = BlockSystemWorldAccess.getChunk(this.mainWorld, this.partitionPosition.getX(), this.partitionPosition.getZ());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            ThreadLocal<Boolean> access = BlockSystemWorldAccess.getAccess(this.mainWorld);
            access.set(true);
            ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
            for (int y = 0; y < 16; y++) {
                chunk.setBlockState(pos, Blocks.AIR.getDefaultState());
                storages[y] = NULL_BLOCK_STORAGE;
            }
            access.set(false);
        }
    }

    @Override
    public void onTick(boolean fast) {
        super.onTick(fast);
        if (!this.mainWorld.isRemote && !this.loading) {
            this.updatePartition();
        }
    }

    private void updatePartition() {
        if (this.partitionPosition == null && !this.isEmpty()) {
            this.partitionPosition = ChunkPartitionHandler.generateValidPartitionPosition(this.mainWorld, this.blockSystem);
            this.clearPartition();
        } else if (this.partitionPosition != null && this.isEmpty()) {
            this.clearPartition();
            BlockSystemSavedData.get(this.mainWorld).deletePartition(this.partitionPosition);
            this.partitionPosition = null;
        }
    }

    protected TileEntity createNewTileEntity(BlockPos pos) {
        IBlockState state = this.getBlockState(pos);
        Block block = state.getBlock();
        return !block.hasTileEntity(state) ? null : block.createTileEntity(this.mainWorld, state);
    }

    @Override
    public void addTileEntity(BlockPos pos, TileEntity tile) {
        BlockPos partitionPos = new BlockPos((pos.getX() & 0xF) + this.partitionPosition.getX(), pos.getY(), (pos.getZ() & 0xF) + this.partitionPosition.getZ());
        this.mainWorld.setTileEntity(partitionPos, tile);
    }

    public void setPartitionPosition(BlockPos partitionPosition) {
        this.partitionPosition = partitionPosition;
    }

    public BlockPos getPartitionPosition() {
        return this.partitionPosition;
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
