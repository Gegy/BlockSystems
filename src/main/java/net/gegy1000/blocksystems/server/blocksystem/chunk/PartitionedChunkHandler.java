package net.gegy1000.blocksystems.server.blocksystem.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.gegy1000.blocksystems.server.world.BlockSystemWorldAccess;
import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Random;

public class PartitionedChunkHandler {
    private static final int WORLD_CHUNK_SIZE = 30000000 >> 4;
    private static final int MAX_SEARCH_ATTEMPTS = 1000;

    private static final Random RANDOM = new Random();

    private final BlockSystemServer blockSystem;
    private final WorldServer mainWorld;

    private final Long2ObjectMap<PartitionedChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<PartitionedChunk> partitionChunks = new Long2ObjectOpenHashMap<>();

    private boolean empty;

    public PartitionedChunkHandler(BlockSystemServer blockSystem, WorldServer mainWorld) {
        this.blockSystem = blockSystem;
        this.mainWorld = mainWorld;
    }

    @Nullable
    public ChunkPos allocateChunk(PartitionedChunk chunk) {
        this.chunks.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);

        BlockSystemSavedData data = BlockSystemSavedData.get(this.mainWorld);
        int attempts = 0;
        while (attempts < MAX_SEARCH_ATTEMPTS) {
            ChunkPos position = this.generatePartitionPosition();
            if (!data.hasPartition(position)) {
                this.clearPartition(position);
                data.addPartition(position, this.blockSystem);
                this.partitionChunks.put(ChunkPos.asLong(position.x, position.z), chunk);
                return position;
            }
            attempts++;
        }

        BlockSystems.LOGGER.warn("Failed to find position to store partition after {} retries!", MAX_SEARCH_ATTEMPTS);

        return null;
    }

    public void deallocateChunk(PartitionedChunk chunk) {
        this.chunks.remove(ChunkPos.asLong(chunk.x, chunk.z));
        if (chunk.getPartitionPos() != null) {
            this.removePartition(chunk.getPartitionPos());
        }

        if (this.chunks.size() <= 0 && this.mainWorld.isRemote) {
            this.empty = true;
        } else if (!this.mainWorld.isRemote) {
            this.empty = this.chunks.values().stream().allMatch(Chunk::isEmpty);
        }
    }

    private int populateFromPartition(PartitionedChunk chunk) {
        int blockCount = 0;
        chunk.tileEntities.clear();

        ChunkPos partitionPos = chunk.getPartitionPos();
        Chunk partition = chunk.getPartitionChunk();
        if (partitionPos != null && partition != null) {
            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            ExtendedBlockStorage[] partitionSections = partition.getBlockStorageArray();
            for (int sectionY = 0; sectionY < sections.length; sectionY++) {
                ExtendedBlockStorage partitionSection = partitionSections[sectionY];
                if (partitionSection != null) {
                    ExtendedBlockStorage section = sections[sectionY];
                    if (section == Chunk.NULL_BLOCK_STORAGE) {
                        section = new ExtendedBlockStorage(sectionY << 4, true);
                    }
                    for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(0, 0, 0, 15, 15, 15)) {
                        IBlockState state = partitionSection.get(pos.getX(), pos.getY(), pos.getZ());
                        section.set(pos.getX(), pos.getY(), pos.getZ(), state);
                        if (state.getBlock() != Blocks.AIR) {
                            blockCount++;
                        }
                    }
                    sections[sectionY] = section;
                }
            }

            for (Map.Entry<BlockPos, TileEntity> entry : partition.tileEntities.entrySet()) {
                TileEntity entity = entry.getValue();
                chunk.tileEntities.put(chunk.fromPartition(entry.getKey()), entity);
            }
        }

        return blockCount;
    }

    private void removePartition(ChunkPos partitionPos) {
        this.partitionChunks.remove(ChunkPos.asLong(partitionPos.x, partitionPos.z));
        BlockSystemSavedData.get(this.mainWorld).removePartition(partitionPos);
    }

    private void clearPartition(ChunkPos partitionPos) {
        Chunk chunk = BlockSystemWorldAccess.getChunk(this.mainWorld, partitionPos.x, partitionPos.z);
        ThreadLocal<Boolean> access = BlockSystemWorldAccess.getAccess(this.mainWorld);
        access.set(true);
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (int y = 0; y < sections.length; y++) {
            sections[y] = Chunk.NULL_BLOCK_STORAGE;
        }
        chunk.tileEntities.clear();
        chunk.tileEntityPosQueue.clear();
        chunk.setHeightMap(new int[16 * 16]);
        access.set(false);
    }

    private ChunkPos generatePartitionPosition() {
        int x = RANDOM.nextInt(WORLD_CHUNK_SIZE * 2) - WORLD_CHUNK_SIZE;
        int z = (RANDOM.nextBoolean() ? 1 : -1) * WORLD_CHUNK_SIZE;
        if (x >= WORLD_CHUNK_SIZE) {
            x = WORLD_CHUNK_SIZE - 1;
        }
        if (z >= WORLD_CHUNK_SIZE) {
            z = WORLD_CHUNK_SIZE - 1;
        }
        if (RANDOM.nextBoolean()) {
            return new ChunkPos(x, z);
        } else {
            return new ChunkPos(z, x);
        }
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        NBTTagList chunksList = new NBTTagList();
        for (PartitionedChunk chunk : this.chunks.values()) {
            ChunkPos partitionPos = chunk.getPartitionPos();
            if (!chunk.isEmpty() && partitionPos != null) {
                NBTTagCompound chunkTag = new NBTTagCompound();
                chunkTag.setInteger("x", chunk.x);
                chunkTag.setInteger("z", chunk.z);
                chunkTag.setInteger("partition_x", partitionPos.x);
                chunkTag.setInteger("partition_z", partitionPos.z);
                chunk.serialize(chunkTag);
                chunksList.appendTag(chunkTag);
            }
        }
        compound.setTag("chunks", chunksList);
        return compound;
    }

    public void deserialize(NBTTagCompound compound) {
        NBTTagList chunksList = compound.getTagList("chunks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < chunksList.tagCount(); i++) {
            NBTTagCompound chunkTag = chunksList.getCompoundTagAt(i);
            ChunkPos pos = new ChunkPos(chunkTag.getInteger("x"), chunkTag.getInteger("z"));
            ChunkPos partitionPos = new ChunkPos(chunkTag.getInteger("partition_x"), chunkTag.getInteger("partition_z"));

            ServerBlockSystemChunk chunk = new ServerBlockSystemChunk(this.blockSystem, pos.x, pos.z, partitionPos);
            chunk.blockCount = this.populateFromPartition(chunk);
            chunk.deserialize(chunkTag);

            BlockSystemSavedData.enqueuePartition(this.mainWorld, this.blockSystem, partitionPos);
            this.chunks.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
            this.partitionChunks.put(ChunkPos.asLong(partitionPos.x, partitionPos.z), chunk);
        }
    }

    @Nullable
    public PartitionedChunk getChunk(ChunkPos pos) {
        return this.chunks.get(ChunkPos.asLong(pos.x, pos.z));
    }

    @Nullable
    public PartitionedChunk getPartitionChunk(ChunkPos pos) {
        return this.partitionChunks.get(ChunkPos.asLong(pos.x, pos.z));
    }

    public boolean isEmpty() {
        return this.empty;
    }
}
