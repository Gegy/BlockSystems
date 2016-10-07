package net.gegy1000.blocksystems.client.blocksystem;

import com.google.common.base.Objects;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.gegy1000.blocksystems.client.blocksystem.chunk.EmptyBlockSystemChunk;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.chunk.BlockSystemChunk;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SideOnly(Side.CLIENT)
public class MultiplayerChunkCacheBlockSystem implements IChunkProvider {
    private static final Logger LOGGER = LogManager.getLogger();
    private final BlockSystemChunk blankChunk;
    private final Long2ObjectMap<Chunk> chunkMapping = new Long2ObjectOpenHashMap<Chunk>(8192) {
        @Override
        protected void rehash(int newN) {
            if (newN > this.key.length) {
                super.rehash(newN);
            }
        }
    };

    private final BlockSystem blockSystem;

    public MultiplayerChunkCacheBlockSystem(BlockSystem blockSystem) {
        this.blankChunk = new EmptyBlockSystemChunk(blockSystem, 0, 0);
        this.blockSystem = blockSystem;
    }

    public void unloadChunk(int x, int z) {
        BlockSystemChunk chunk = (BlockSystemChunk) this.provideChunk(x, z);
        if (!chunk.isEmpty()) {
            chunk.onChunkUnload();
        }
        this.chunkMapping.remove(ChunkPos.asLong(x, z));
    }

    @Override
    public Chunk getLoadedChunk(int x, int z) {
        return this.chunkMapping.get(ChunkPos.asLong(x, z));
    }

    public BlockSystemChunk loadChunk(int chunkX, int chunkZ) {
        BlockSystemChunk chunk = new BlockSystemChunk(this.blockSystem, chunkX, chunkZ);
        this.chunkMapping.put(ChunkPos.asLong(chunkX, chunkZ), chunk);
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk));
        chunk.setChunkLoaded(true);
        return chunk;
    }

    @Override
    public Chunk provideChunk(int x, int z) {
        return Objects.firstNonNull(this.getLoadedChunk(x, z), this.blankChunk);
    }

    @Override
    public boolean unloadQueuedChunks() {
        long time = System.currentTimeMillis();
        for (Chunk chunk : this.chunkMapping.values()) {
            chunk.onTick(System.currentTimeMillis() - time > 5L);
        }
        if (System.currentTimeMillis() - time > 100L) {
            LOGGER.info("Warning: Clientside chunk ticking took {} ms", System.currentTimeMillis() - time);
        }
        return false;
    }

    @Override
    public String makeString() {
        return "MultiplayerChunkCache: " + this.chunkMapping.size() + ", " + this.chunkMapping.size();
    }
}