package net.gegy1000.blocksystems.client.blocksystem.chunk;

import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderHandler;
import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderer;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.chunk.BlockSystemChunk;
import net.minecraft.init.Blocks;
import net.minecraft.network.PacketBuffer;
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
            renderer.deleteChunk(this.x, this.z);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        BlockSystemRenderer renderer = BlockSystemRenderHandler.get(this.blockSystem);
        if (renderer != null) {
            renderer.deleteChunk(this.x, this.z);
        }
    }

    @Override
    public TileEntity getTileEntity(BlockPos position, Chunk.EnumCreateEntityType type) {
        TileEntity tile = this.tileEntities.get(position);
        if (tile != null && tile.isInvalid()) {
            this.tileEntities.remove(position);
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

    @Override
    public void read(PacketBuffer buf, int available, boolean loadChunk) {
        super.read(buf, available, loadChunk);
        this.blockCount = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 256; y++) {
                for (int z = 0; z < 16; z++) {
                    if (this.getBlockState(x, y, z).getBlock() != Blocks.AIR) {
                        this.blockCount++;
                    }
                }
            }
        }
    }
}
