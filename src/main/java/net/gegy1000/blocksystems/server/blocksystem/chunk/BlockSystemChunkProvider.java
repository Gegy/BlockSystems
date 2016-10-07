package net.gegy1000.blocksystems.server.blocksystem.chunk;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class BlockSystemChunkProvider implements IChunkProvider {
    @Override
    public Chunk getLoadedChunk(int x, int z) {
        return null;
    }

    @Override
    public Chunk provideChunk(int x, int z) {
        return null;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public String makeString() {
        return null;
    }
}
