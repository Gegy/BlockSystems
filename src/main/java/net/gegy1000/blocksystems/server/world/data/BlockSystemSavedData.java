package net.gegy1000.blocksystems.server.world.data;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockSystemSavedData extends WorldSavedData {
    private static final ThreadLocal<WorldServer> READING_WORLD = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LOAD = ThreadLocal.withInitial(() -> true);
    private static final ThreadLocal<BlockSystemServer> CURRENTLY_LOADING = new ThreadLocal<>();
    private static final Map<World, List<Tuple<ChunkPos, BlockSystemServer>>> QUEUED_PARTITIONS = new HashMap<>();

    public static final String KEY = "block_systems";

    private WorldServer world;

    private final Int2ObjectMap<BlockSystemServer> blockSystems = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectMap<BlockSystemServer> partitions = new Long2ObjectOpenHashMap<>();

    public BlockSystemSavedData() {
        this(KEY);
    }

    public BlockSystemSavedData(String name) {
        super(name);
    }

    public static BlockSystemSavedData get(WorldServer world) {
        return BlockSystemSavedData.get(world, true);
    }

    public static BlockSystemSavedData get(WorldServer world, boolean load) {
        MapStorage storage = world.getPerWorldStorage();
        READING_WORLD.set(world);
        LOAD.set(load);
        BlockSystemSavedData data = (BlockSystemSavedData) storage.getOrLoadData(BlockSystemSavedData.class, KEY);
        if (data == null) {
            data = new BlockSystemSavedData();
            storage.setData(KEY, data);
        }
        data.world = world;
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        this.world = READING_WORLD.get();
        if (LOAD.get()) {
            this.partitions.clear();
            this.blockSystems.clear();
            int highestId = 0;
            try {
                NBTTagList blockSystemsList = compound.getTagList("block_systems", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < blockSystemsList.tagCount(); i++) {
                    NBTTagCompound tag = blockSystemsList.getCompoundTagAt(i);
                    BlockSystemServer system = new BlockSystemServer(this.world.getMinecraftServer(), this.world, BlockSystem.nextID++);
                    CURRENTLY_LOADING.set(system);
                    system.deserialize(tag);
                    BlockSystems.PROXY.getBlockSystemHandler(this.world).loadBlockSystem(system);
                    CURRENTLY_LOADING.set(null);
                    if (system.getId() > highestId) {
                        highestId = system.getId();
                    }
                }
            } finally {
                List<Tuple<ChunkPos, BlockSystemServer>> queuedPartitions = QUEUED_PARTITIONS.remove(this.world);
                if (queuedPartitions != null) {
                    for (Tuple<ChunkPos, BlockSystemServer> partition : queuedPartitions) {
                        this.addPartition(partition.getFirst(), partition.getSecond());
                    }
                }
            }
            BlockSystem.nextID = highestId;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList blockSystemsList = new NBTTagList();
        for (BlockSystemServer system : this.blockSystems.values()) {
            blockSystemsList.appendTag(system.serialize(new NBTTagCompound()));
        }
        compound.setTag("block_systems", blockSystemsList);
        return compound;
    }

    public void addPartition(ChunkPos pos, BlockSystemServer owner) {
        long key = ChunkPos.asLong(pos.x, pos.z);
        if (!this.partitions.containsKey(key)) {
            this.partitions.put(key, owner);
            this.markDirty();
        }
    }

    public void removePartition(ChunkPos pos) {
        BlockSystemServer remove = this.partitions.remove(ChunkPos.asLong(pos.x, pos.z));
        if (remove != null) {
            this.markDirty();
        }
    }

    public BlockSystemServer getBlockSystem(ChunkPos partition) {
        if (CURRENTLY_LOADING.get() != null) {
            return CURRENTLY_LOADING.get();
        }
        return this.partitions.get(ChunkPos.asLong(partition.x, partition.z));
    }

    public boolean hasPartition(ChunkPos pos) {
        return this.partitions.containsKey(ChunkPos.asLong(pos.x, pos.z));
    }

    public void addBlockSystem(BlockSystemServer blockSystem) {
        if (!this.blockSystems.containsKey(blockSystem.getId())) {
            this.blockSystems.put(blockSystem.getId(), blockSystem);
            this.markDirty();
        }
    }

    public void removeBlockSystem(BlockSystemServer blockSystem) {
        this.removeBlockSystem(blockSystem.getId());
    }

    public void removeBlockSystem(int blockSystem) {
        this.blockSystems.remove(blockSystem);
        this.markDirty();
    }

    public static void enqueuePartition(World world, BlockSystemServer blockSystem, ChunkPos pos) {
        List<Tuple<ChunkPos, BlockSystemServer>> partitions = QUEUED_PARTITIONS.computeIfAbsent(world, key -> new ArrayList<>());
        partitions.add(new Tuple<>(pos, blockSystem));
    }
}
