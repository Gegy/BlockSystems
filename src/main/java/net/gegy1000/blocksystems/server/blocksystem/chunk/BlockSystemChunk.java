package net.gegy1000.blocksystems.server.blocksystem.chunk;

import net.gegy1000.blocksystems.BlockSystems;
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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.util.Constants;

import java.util.Map;

public class BlockSystemChunk extends Chunk {
    protected BlockSystem blockSystem;
    protected World mainWorld;

    protected boolean loading;

    protected ChunkPos partitionPosition = null;

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
                int offsetX = this.partitionPosition.getXStart();
                int offsetZ = this.partitionPosition.getZStart();
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
            NBTTagCompound partitionTag = new NBTTagCompound();
            partitionTag.setInteger("x", this.partitionPosition.x);
            partitionTag.setInteger("z", this.partitionPosition.z);
            compound.setTag("partition", partitionTag);
        }
        NBTTagList sectionList = new NBTTagList();
        for (ExtendedBlockStorage section : this.getBlockStorageArray()) {
            if (section != NULL_BLOCK_STORAGE && !section.isEmpty()) {
                NBTTagCompound sectionCompound = new NBTTagCompound();
                sectionCompound.setShort("index", (short) section.getYLocation());
                sectionCompound.setByteArray("block_light", section.getBlockLight().getData());
                sectionCompound.setByteArray("sky_light", section.getSkyLight().getData());
                sectionList.appendTag(sectionCompound);
            }
        }
        compound.setTag("sections", sectionList);
        return compound;
    }

    public void deserialize(NBTTagCompound compound) {
        this.loading = true;
        this.blockCount = 0;
        if (compound.hasKey("partition")) {
            NBTTagCompound partitionTag = compound.getCompoundTag("partition");
            this.setPartitionPosition(new ChunkPos(partitionTag.getInteger("x"), partitionTag.getInteger("z")));
            BlockSystemSavedData.enqueuePartition(this.mainWorld, this.blockSystem, this.partitionPosition);
            Chunk chunk = BlockSystemWorldAccess.getChunk(this.mainWorld, this.partitionPosition.x, this.partitionPosition.z);
            BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
            int offsetX = this.partitionPosition.getXStart();
            int offsetZ = this.partitionPosition.getZStart();
            for (int storageY = 0; storageY < 16; storageY++) {
                int baseY = storageY << 4;
                int count = 0;
                ExtendedBlockStorage storage = this.getBlockStorageArray()[storageY];
                if (storage == NULL_BLOCK_STORAGE) {
                    storage = new ExtendedBlockStorage(baseY, !this.blockSystem.provider.isNether());
                }
                for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(0, baseY, 0, 15, baseY + 15, 15)) {
                    worldPos.setPos(pos.getX() + offsetX, pos.getY(), pos.getZ() + offsetZ);
                    IBlockState state = chunk.getBlockState(pos);
                    storage.set(pos.getX(), pos.getY() & 15, pos.getZ(), state);
                    if (state.getBlock() != Blocks.AIR) {
                        count++;
                    }
                }
                if (count > 0) {
                    this.getBlockStorageArray()[storageY] = storage;
                }
                this.blockCount += count;
            }
            for (Map.Entry<BlockPos, TileEntity> entry : chunk.tileEntities.entrySet()) {
                BlockPos pos = entry.getKey();
                TileEntity entity = entry.getValue();
                this.tileEntities.put(new BlockPos((pos.getX() & 0xF) + (this.x << 4), pos.getY() & 0xFF, (pos.getZ() & 0xF) + (this.z << 4)), entity);
            }
        }
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
            Chunk chunk = BlockSystemWorldAccess.getChunk(this.mainWorld, this.partitionPosition.x, this.partitionPosition.z);
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
            this.setPartitionPosition(ChunkPartitionHandler.generateValidPartitionPosition(this.mainWorld, this.blockSystem));
            this.clearPartition();
        } else if (this.partitionPosition != null && this.isEmpty()) {
            this.clearPartition();
            BlockSystemSavedData.get(this.mainWorld).deletePartition(this.partitionPosition);
            this.setPartitionPosition(null);
        }
    }

    protected TileEntity createNewTileEntity(BlockPos pos) {
        IBlockState state = this.getBlockState(pos);
        Block block = state.getBlock();
        return !block.hasTileEntity(state) ? null : block.createTileEntity(this.mainWorld, state);
    }

    @Override
    public void addTileEntity(BlockPos pos, TileEntity tile) {
        BlockPos partitionPos = new BlockPos((pos.getX() & 0xF) + this.partitionPosition.getXStart(), pos.getY(), (pos.getZ() & 0xF) + this.partitionPosition.getZStart());
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

    public void setPartitionPosition(ChunkPos partitionPosition) {
        if (this.partitionPosition != null) {
            this.blockSystem.removePartition(partitionPosition);
        }
        this.partitionPosition = partitionPosition;
        if (partitionPosition != null) {
            this.blockSystem.addPartition(this);
        }
    }

    public ChunkPos getPartitionPosition() {
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
