package net.gegy1000.blocksystems.server.world.data;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockSystemSavedData extends WorldSavedData {
    private static final ThreadLocal<World> READING_WORLD = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LOAD = ThreadLocal.withInitial(() -> true);
    private static final ThreadLocal<BlockSystem> CURRENTLY_LOADING = new ThreadLocal<>();
    private static final Map<World, List<Tuple<BlockPos, BlockSystem>>> QUEUED_PARTITIONS = new HashMap<>();

    public static final String KEY = "block_systems";

    private World world;

    private Map<Integer, BlockSystem> blockSystems = new HashMap<>();
    private Map<BlockPos, BlockSystem> partitions = new HashMap<>();

    public BlockSystemSavedData() {
        this(KEY);
    }

    public BlockSystemSavedData(String name) {
        super(name);
    }

    public static BlockSystemSavedData get(World world) {
        return BlockSystemSavedData.get(world, true);
    }

    public static BlockSystemSavedData get(World world, boolean load) {
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
            BlockSystem.nextID = compound.getInteger("NextID");
            try {
                NBTTagList blockSystemsList = compound.getTagList("BlockSystems", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < blockSystemsList.tagCount(); i++) {
                    NBTTagCompound tag = blockSystemsList.getCompoundTagAt(i);
                    BlockSystem system = BlockSystems.PROXY.createBlockSystem(this.world, BlockSystem.nextID++);
                    CURRENTLY_LOADING.set(system);
                    system.deserialize(tag);
                    BlockSystems.PROXY.getBlockSystemHandler(this.world).loadBlockSystem(system);
                    this.addBlockSystem(system);
                    CURRENTLY_LOADING.set(null);
                }
            } finally {
                List<Tuple<BlockPos, BlockSystem>> queuedPartitions = QUEUED_PARTITIONS.remove(this.world);
                if (queuedPartitions != null) {
                    for (Tuple<BlockPos, BlockSystem> partition : queuedPartitions) {
                        this.addPartition(partition.getFirst(), partition.getSecond());
                    }
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("NextID", BlockSystem.nextID);
        NBTTagList blockSystemsList = new NBTTagList();
        for (Map.Entry<Integer, BlockSystem> entry : this.blockSystems.entrySet()) {
            NBTTagCompound tag = entry.getValue().serialize(new NBTTagCompound());
            blockSystemsList.appendTag(tag);
        }
        compound.setTag("BlockSystems", blockSystemsList);
        return compound;
    }

    public void addPartition(BlockPos pos, BlockSystem owner) {
        if (!this.partitions.containsKey(pos)) {
            this.partitions.put(pos, owner);
            this.markDirty();
        }
    }

    public void deletePartition(BlockPos pos) {
        BlockSystem remove = this.partitions.remove(pos);
        if (remove != null) {
            this.markDirty();
        }
    }

    public BlockSystem getBlockSystem(BlockPos partition) {
        if (CURRENTLY_LOADING.get() == null) {
            BlockPos partitionBasePos = new BlockPos(partition.getX() & ~0xF, 0, partition.getZ() & ~0xF);
            return this.partitions.get(partitionBasePos);
        }
        return CURRENTLY_LOADING.get();
    }

    public boolean hasPartition(BlockPos pos) {
        return this.partitions.containsKey(pos);
    }

    public void addBlockSystem(BlockSystem blockSystem) {
        this.blockSystems.put(blockSystem.getID(), blockSystem);
        this.markDirty();
    }

    public void removeBlockSystem(BlockSystem blockSystem) {
        this.removeBlockSystem(blockSystem.getID());
    }

    public void removeBlockSystem(int blockSystem) {
        this.blockSystems.remove(blockSystem);
        this.markDirty();
    }

    public static void enqueuePartition(World world, BlockSystem blockSystem, BlockPos pos) {
        List<Tuple<BlockPos, BlockSystem>> partitions = QUEUED_PARTITIONS.computeIfAbsent(world, key -> new ArrayList<>());
        partitions.add(new Tuple<>(pos, blockSystem));
    }
}
