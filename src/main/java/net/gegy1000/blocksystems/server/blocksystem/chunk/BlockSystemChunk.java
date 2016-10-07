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
import net.minecraftforge.common.util.Constants;

public class BlockSystemChunk extends Chunk {
    protected BlockSystem blockSystem;
    protected World mainWorld;

    protected boolean loading;

    protected BlockPos[] partionPositions = new BlockPos[16];

    public BlockSystemChunk(BlockSystem blockSystem, int x, int z) {
        super(blockSystem, x, z);
        this.blockSystem = blockSystem;
        this.mainWorld = blockSystem.getMainWorld();
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        NBTTagList partionPositions = new NBTTagList();
        for (int i = 0; i < this.partionPositions.length; i++) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByte("i", (byte) i);
            tag.setLong("p", this.partionPositions[i].toLong());
            partionPositions.appendTag(tag);
        }
        compound.setTag("PartionPositions", partionPositions);
        return compound;
    }

    public void deserialize(NBTTagCompound compound) {
        this.loading = true;
        NBTTagList partionPositions = compound.getTagList("PartionPositions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < partionPositions.tagCount(); i++) {
            NBTTagCompound tag = partionPositions.getCompoundTagAt(i);
            this.partionPositions[tag.getByte("i")] = BlockPos.fromLong(tag.getLong("p"));
        }
        for (int y = 0; y < 256; y++) {
            BlockPos partionPosition = this.partionPositions[y >> 4];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos chunkPos = new BlockPos.MutableBlockPos();
            int offsetX = partionPosition.getX() << 4;
            int offsetY = partionPosition.getY() << 4;
            int offsetZ = partionPosition.getZ() << 4;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.setPos(offsetX + x, offsetY + (y & 15), offsetZ + z);
                    chunkPos.setPos(x, y, z);
                    this.setBlockState(chunkPos, this.mainWorld.getBlockState(pos));
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
                if (this.partionPositions[y] == null) {
                    this.partionPositions[y] = ChunkPartionHandler.generateValidPartionPosition(this.mainWorld);
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
}
