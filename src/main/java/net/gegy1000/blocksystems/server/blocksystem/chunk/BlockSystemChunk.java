package net.gegy1000.blocksystems.server.blocksystem.chunk;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
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

    protected BlockPos[] partionPositions = new BlockPos[16];

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
            BlockPos partionPosition = this.partionPositions[pos.getY() >> 4];
            if (partionPosition != null) {
                this.mainWorld.setBlockState(new BlockPos(partionPosition.getX() << 4 + pos.getX(), pos.getY(), partionPosition.getZ() << 4 + pos.getZ()), state);
            }
        }
        return super.setBlockState(pos, state);
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        NBTTagList partionPositions = new NBTTagList();
        for (int i = 0; i < this.partionPositions.length; i++) {
            if (this.partionPositions[i] != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("i", (byte) i);
                tag.setLong("p", this.partionPositions[i].toLong());
                partionPositions.appendTag(tag);
            }
        }
        compound.setTag("PartionPositions", partionPositions);
        return compound;
    }

    public void deserialize(NBTTagCompound compound) {
        this.loading = true;
        this.blockCount = 0;
        NBTTagList partionPositions = compound.getTagList("PartionPositions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < partionPositions.tagCount(); i++) {
            NBTTagCompound tag = partionPositions.getCompoundTagAt(i);
            this.partionPositions[tag.getByte("i")] = BlockPos.fromLong(tag.getLong("p"));
        }
        for (int partionY = 0; partionY < 16; partionY++) {
            BlockPos partionPosition = this.partionPositions[partionY];
            if (partionPosition != null) {
                BlockSystemSavedData.addPartionToQueue(this.mainWorld, partionPosition);
                Chunk chunk = this.mainWorld.getChunkFromChunkCoords(partionPosition.getX(), partionPosition.getZ());
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                BlockPos.MutableBlockPos chunkPos = new BlockPos.MutableBlockPos();
                int offsetY = partionPosition.getY() << 4;
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
                data.deletePartion(this.partionPositions[y]);
                this.clearSpace(y);
            }
        }
        this.blockSystem.removeSavedChunk(this);
    }

    private void clearSpace(int partionY) {
        BlockPos partionPosition = this.partionPositions[partionY];
        if (partionPosition != null) {
            int offsetX = partionPosition.getX() << 4;
            int offsetY = partionPosition.getY() << 4;
            int offsetZ = partionPosition.getZ() << 4;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        pos.setPos(offsetX + x, offsetY + y, offsetZ + z);
                        this.mainWorld.setBlockState(pos, Blocks.AIR.getDefaultState());
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
                if (storage != NULL_BLOCK_STORAGE && !storage.isEmpty() && this.partionPositions[y] == null) {
                    this.partionPositions[y] = ChunkPartionHandler.generateValidPartionPosition(this.mainWorld);
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
