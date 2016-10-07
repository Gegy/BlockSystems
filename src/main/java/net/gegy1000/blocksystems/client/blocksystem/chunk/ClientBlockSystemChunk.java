package net.gegy1000.blocksystems.client.blocksystem.chunk;

import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderHandler;
import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderer;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.chunk.BlockSystemChunk;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

public class ClientBlockSystemChunk extends BlockSystemChunk {
    public ClientBlockSystemChunk(BlockSystem blockSystem, int x, int z) {
        super(blockSystem, x, z);
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
    public void onChunkUnload() {
        super.onChunkUnload();
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
}
