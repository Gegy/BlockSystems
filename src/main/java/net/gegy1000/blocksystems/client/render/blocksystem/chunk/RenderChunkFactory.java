package net.gegy1000.blocksystems.client.render.blocksystem.chunk;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderer;

public interface RenderChunkFactory {
    BlockSystemRenderChunk create(BlockSystem blockSystem, BlockSystemRenderer renderer);
}
