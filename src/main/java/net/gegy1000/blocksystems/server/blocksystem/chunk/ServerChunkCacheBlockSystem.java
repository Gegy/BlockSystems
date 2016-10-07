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

public class ServerChunkCacheBlockSystem extends ChunkProviderServer {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Set<Long> droppedChunksSet = Sets.newHashSet();
    public final BlockSystemServer world;

    public ServerChunkCacheBlockSystem(BlockSystemServer world, IChunkLoader chunkLoader) {
        super((WorldServer) world.getMainWorld(), chunkLoader, new BlankChunkGenerator(world));
        this.world = world;
    }

    @Override
    public Collection<Chunk> getLoadedChunks() {
        return this.id2ChunkMap.values();
    }

    @Override
    public void unload(Chunk chunk) {
        if (this.world.provider.canDropChunk(chunk.xPosition, chunk.zPosition)) {
            this.droppedChunksSet.add(ChunkPos.asLong(chunk.xPosition, chunk.zPosition));
            chunk.unloaded = true;
        }
    }

    @Override
    public void unloadAllChunks() {
        for (Chunk chunk : this.id2ChunkMap.values()) {
            this.unload(chunk);
        }
    }

    @Override
    public Chunk getLoadedChunk(int x, int z) {
        BlockSystemChunk chunk = (BlockSystemChunk) this.id2ChunkMap.get(ChunkPos.asLong(x, z));
        if (chunk != null) {
            chunk.unloaded = false;
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
            long pos = ChunkPos.asLong(x, z);
            chunk = new BlockSystemChunk(this.world, x, z);
            this.id2ChunkMap.put(pos, chunk);
            chunk.onChunkLoad();
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
            this.id2ChunkMap.put(ChunkPos.asLong(x, z), chunk = new BlockSystemChunk(this.world, x, z));
            chunk.onChunkLoad();
        }
        return chunk;
    }

    private void saveChunkExtraData(Chunk chunkIn) {
        try {
            this.chunkLoader.saveExtraChunkData(this.world, chunkIn);
        } catch (Exception exception) {
            LOGGER.error("Couldn\'t save entities", exception);
        }
    }

    private void saveChunkData(Chunk chunkIn) {
        try {
            chunkIn.setLastSaveTime(this.world.getTotalWorldTime());
            this.chunkLoader.saveChunk(this.world, chunkIn);
        } catch (IOException ioexception) {
            LOGGER.error("Couldn\'t save chunk", ioexception);
        } catch (MinecraftException minecraftexception) {
            LOGGER.error("Couldn\'t save chunk; already in use by another instance of Minecraft?", minecraftexception);
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
    public void saveExtraData() {
        this.chunkLoader.saveExtraData();
    }

    @Override
    public boolean unloadQueuedChunks() {
        if (!this.world.disableLevelSaving) {
            if (!this.droppedChunksSet.isEmpty()) {
                for (ChunkPos forced : this.world.getPersistentChunks().keySet()) {
                    this.droppedChunksSet.remove(ChunkPos.asLong(forced.chunkXPos, forced.chunkZPos));
                }
                Iterator<Long> iterator = this.droppedChunksSet.iterator();
                for (int i = 0; i < 100 && iterator.hasNext(); iterator.remove()) {
                    Long id = iterator.next();
                    Chunk chunk = this.id2ChunkMap.get(id);
                    if (chunk != null && chunk.unloaded) {
                        chunk.onChunkUnload();
                        this.saveChunkData(chunk);
                        this.saveChunkExtraData(chunk);
                        this.id2ChunkMap.remove(id);
                        ++i;
                        if (this.id2ChunkMap.size() == 0 && ForgeChunkManager.getPersistentChunksFor(this.world).size() == 0 && !this.world.provider.getDimensionType().shouldLoadSpawn()) {
                            DimensionManager.unloadWorld(this.world.provider.getDimension());
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
        return !this.world.disableLevelSaving;
    }

    @Override
    public String makeString() {
        return "ServerChunkCache: " + this.id2ChunkMap.size() + " Drop: " + this.droppedChunksSet.size();
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        return Lists.newArrayList();
    }

    @Override
    @Nullable
    public BlockPos getStrongholdGen(World world, String structureName, BlockPos position) {
        return null;
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