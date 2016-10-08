package net.gegy1000.blocksystems.server.world.data;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockSystemSavedData extends WorldSavedData {
    private static final ThreadLocal<World> READING_WORLD = new ThreadLocal<>();
    private static final Map<World, List<BlockPos>> QUEUED_PARTIONS = new HashMap<>();

    public static final String KEY = "block_systems";

    private World world;

    private Map<Integer, BlockSystem> blockSystems = new HashMap<>();
    private Set<BlockPos> partions = new HashSet<>();

    public BlockSystemSavedData() {
        this(KEY);
    }

    public BlockSystemSavedData(String name) {
        super(name);
    }

    public static BlockSystemSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        READING_WORLD.set(world);
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
        this.partions.clear();
        this.blockSystems.clear();
        NBTTagList blockSystemsList = compound.getTagList("BlockSystems", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < blockSystemsList.tagCount(); i++) {
            NBTTagCompound tag = blockSystemsList.getCompoundTagAt(i);
            BlockSystem system = BlockSystems.PROXY.createBlockSystem(this.world, BlockSystem.nextID++);
            system.deserialize(tag);
            BlockSystems.PROXY.getBlockSystemHandler(this.world).loadBlockSystem(system);
            this.addBlockSystem(system);
        }
        List<BlockPos> queuedPartions = QUEUED_PARTIONS.remove(this.world);
        for (BlockPos partian : queuedPartions) {
            this.addPartion(partian);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList blockSystemsList = new NBTTagList();
        for (Map.Entry<Integer, BlockSystem> entry : this.blockSystems.entrySet()) {
            NBTTagCompound tag = entry.getValue().serialize(new NBTTagCompound());
            blockSystemsList.appendTag(tag);
        }
        compound.setTag("BlockSystems", blockSystemsList);
        return compound;
    }

    public void addPartion(BlockPos pos) {
        if (!this.partions.contains(pos)) {
            this.partions.add(pos);
        }
    }

    public void deletePartion(BlockPos pos) {
        this.partions.remove(pos);
    }

    public boolean hasPartion(BlockPos pos) {
        return this.partions.contains(pos);
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

    public static void addPartionToQueue(World world, BlockPos pos) {
        List<BlockPos> partions = QUEUED_PARTIONS.get(world);
        if (partions == null) {
            partions = new ArrayList<>();
            QUEUED_PARTIONS.put(world, partions);
        }
        partions.add(pos);
    }
}
