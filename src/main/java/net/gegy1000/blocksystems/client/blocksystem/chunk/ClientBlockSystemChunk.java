package net.gegy1000.blocksystems.client.blocksystem.chunk;

import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderHandler;
import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderer;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.chunk.BlockSystemChunk;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

public class ClientBlockSystemChunk extends BlockSystemChunk {
    public ClientBlockSystemChunk(BlockSystem blockSystem, int x, int z) {
        super(blockSystem, x, z);
    }

    @Override
    public IBlockState setBlockState(BlockPos pos, IBlockState state) {
        IBlockState previous = super.setBlockState(pos, state);
        if (previous != null) {
            if (!this.loading) {
                this.rebuild();
                this.rebuildNeighbours();
            }
        }
        return previous;
    }

    private void rebuildNeighbours() {
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            Chunk chunk = this.blockSystem.getChunkFromChunkCoords(this.xPosition + facing.getFrontOffsetX(), this.zPosition + facing.getFrontOffsetZ());
            if (chunk instanceof ClientBlockSystemChunk && !chunk.isEmpty()) {
                ((ClientBlockSystemChunk) chunk).rebuild();
            }
        }
    }

    @Override
    public void deserialize(NBTTagCompound compound) {
        super.deserialize(compound);
        this.rebuild();
        this.rebuildNeighbours();
    }

    @Override
    public void remove() {
        super.remove();
        BlockSystemRenderer renderer = BlockSystemRenderHandler.get(this.blockSystem);
        if (renderer != null) {
            renderer.deleteChunk(this.xPosition, this.zPosition);
        }
    }

    @Override
    public TileEntity getTileEntity(BlockPos position, Chunk.EnumCreateEntityType type) {
        TileEntity tile = this.chunkTileEntityMap.get(position);
        if (tile != null && tile.isInvalid()) {
            this.chunkTileEntityMap.remove(position);
            tile = null;
        }
        if (tile == null) {
            if (type == Chunk.EnumCreateEntityType.IMMEDIATE) {
                tile = this.createNewTileEntity(position);
                this.mainWorld.setTileEntity(position, tile);
            } else if (type == Chunk.EnumCreateEntityType.QUEUED) {
                this.tileEntityPosQueue.add(position.toImmutable());
            }
        }
        return tile;
    }

    public void rebuild() {
        BlockSystemRenderer renderer = BlockSystemRenderHandler.get(this.blockSystem);
        if (renderer != null) {
            renderer.queueChunkRenderUpdate(this.xPosition, this.zPosition);
        }
    }
}
