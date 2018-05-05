package net.gegy1000.blocksystems.server.blocksystem.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.gegy1000.blocksystems.server.world.generator.BlankChunkGenerator;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

// TODO: A lot of this is directly copied: why?
public class ServerChunkCacheBlockSystem extends ChunkProviderServer {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Set<Long> droppedChunksSet = Sets.newHashSet();
    public final BlockSystemServer blockSystem;

    public ServerChunkCacheBlockSystem(BlockSystemServer blockSystem, IChunkLoader chunkLoader) {
        super((WorldServer) blockSystem.getMainWorld(), chunkLoader, new BlankChunkGenerator(blockSystem));
        this.blockSystem = blockSystem;
    }

    @Override
    public Collection<Chunk> getLoadedChunks() {
        return this.id2ChunkMap.values();
    }

    @Override
    public void queueUnload(Chunk chunk) {
        if (this.blockSystem.provider.canDropChunk(chunk.x, chunk.z)) {
            this.droppedChunksSet.add(ChunkPos.asLong(chunk.x, chunk.z));
            chunk.unloadQueued = true;
        }
    }

    @Override
    public void queueUnloadAll() {
        for (Chunk chunk : this.id2ChunkMap.values()) {
            this.queueUnload(chunk);
        }
    }

    @Override
    public Chunk getLoadedChunk(int x, int z) {
        ServerBlockSystemChunk chunk = (ServerBlockSystemChunk) this.id2ChunkMap.get(ChunkPos.asLong(x, z));
        if (chunk != null) {
            chunk.unloadQueued = false;
        }
        return chunk;
    }

    @Override
    @Nullable
    public Chunk loadChunk(int x, int z) {
        return this.loadChunk(x, z, null);
    }

    @Override
    public Chunk loadChunk(int x, int z, Runnable runnable) {
        Chunk chunk = this.getLoadedChunk(x, z);
        if (chunk == null) {
            chunk = this.blockSystem.getChunkHandler().getChunk(new ChunkPos(x, z));
            if (chunk == null) {
                long pos = ChunkPos.asLong(x, z);
                chunk = new ServerBlockSystemChunk(this.blockSystem, x, z);
                this.id2ChunkMap.put(pos, chunk);
                chunk.onLoad();
            }
        }
        if (runnable != null) {
            runnable.run();
        }
        return chunk;
    }

    @Override
    public Chunk provideChunk(int x, int z) {
        Chunk chunk = this.loadChunk(x, z);
        if (chunk == null) {
            chunk = new ServerBlockSystemChunk(this.blockSystem, x, z);
            this.id2ChunkMap.put(ChunkPos.asLong(x, z), chunk);
            chunk.onLoad();
        }
        return chunk;
    }

    private void saveChunkExtraData(Chunk chunk) {
        try {
            this.chunkLoader.saveExtraChunkData(this.blockSystem, chunk);
        } catch (Exception exception) {
            LOGGER.error("Couldn't save entities", exception);
        }
    }

    private void saveChunkData(Chunk chunk) {
        try {
            chunk.setLastSaveTime(this.blockSystem.getTotalWorldTime());
            this.chunkLoader.saveChunk(this.blockSystem, chunk);
        } catch (IOException e) {
            LOGGER.error("Couldn't save chunk", e);
        } catch (MinecraftException e) {
            LOGGER.error("Couldn't save chunk; already in use by another instance of Minecraft?", e);
        }
    }

    @Override
    public boolean saveChunks(boolean saveExtraData) {
        int i = 0;
        List<Chunk> chunks = Lists.newArrayList(this.id2ChunkMap.values());
        for (Chunk chunk : chunks) {
            if (saveExtraData) {
                this.saveChunkExtraData(chunk);
            }
            if (chunk.needsSaving(saveExtraData)) {
                this.saveChunkData(chunk);
                chunk.setModified(false);
                ++i;
                if (i == 24 && !saveExtraData) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void flushToDisk() {
        this.chunkLoader.flush();
    }

    @Override
    public boolean tick() {
        if (!this.blockSystem.disableLevelSaving) {
            if (!this.droppedChunksSet.isEmpty()) {
                for (ChunkPos forced : this.blockSystem.getPersistentChunks().keySet()) {
                    this.droppedChunksSet.remove(ChunkPos.asLong(forced.x, forced.z));
                }
                Iterator<Long> iterator = this.droppedChunksSet.iterator();
                for (int i = 0; i < 100 && iterator.hasNext(); iterator.remove()) {
                    Long id = iterator.next();
                    Chunk chunk = this.id2ChunkMap.get(id);
                    if (chunk != null && chunk.unloadQueued) {
                        chunk.onUnload();
                        this.saveChunkData(chunk);
                        this.saveChunkExtraData(chunk);
                        this.id2ChunkMap.remove(id);
                        ++i;
                        if (this.id2ChunkMap.size() == 0 && ForgeChunkManager.getPersistentChunksFor(this.blockSystem).size() == 0 && !this.blockSystem.provider.getDimensionType().shouldLoadSpawn()) {
                            DimensionManager.unloadWorld(this.blockSystem.provider.getDimension());
                            break;
                        }
                    }
                }
            }
            this.chunkLoader.chunkTick();
        }
        return false;
    }

    @Override
    public boolean canSave() {
        return !this.blockSystem.disableLevelSaving;
    }

    @Override
    public String makeString() {
        return "ServerChunkCacheBlockSystem: " + this.id2ChunkMap.size() + " Drop: " + this.droppedChunksSet.size();
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World world, String structureName, BlockPos position, boolean findUnexplored) {
        return null;
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        return Lists.newArrayList();
    }

    @Override
    public int getLoadedChunkCount() {
        return this.id2ChunkMap.size();
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return this.id2ChunkMap.containsKey(ChunkPos.asLong(x, z));
    }
}
