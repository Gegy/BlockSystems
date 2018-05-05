package net.gegy1000.blocksystems.server.blocksystem;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.chunk.BlockSystemChunkTracker;
import net.gegy1000.blocksystems.server.blocksystem.chunk.PartitionedChunkHandler;
import net.gegy1000.blocksystems.server.blocksystem.chunk.ServerChunkCacheBlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.listener.ServerBlockSystemListener;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class BlockSystemServer extends BlockSystem {
    public boolean disableLevelSaving;

    private BlockSystemChunkTracker chunkTracker;
    private MinecraftServer server;

    protected final Set<NextTickListEntry> scheduledTicksSet = Sets.newHashSet();
    protected final TreeSet<NextTickListEntry> scheduledTicksTree = new TreeSet<>();
    protected final List<NextTickListEntry> currentScheduledTicks = new ArrayList<>();

    private final PartitionedChunkHandler chunkHandler;

    public BlockSystemServer(MinecraftServer server, WorldServer mainWorld, int id) {
        super(mainWorld, id, server);
        this.chunkHandler = new PartitionedChunkHandler(this, mainWorld);
    }

    @Override
    public void initializeBlockSystem(MinecraftServer server) {
        this.server = server;
        this.addEventListener(new ServerBlockSystemListener(this));
        this.chunkTracker = new BlockSystemChunkTracker(this);
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return this.getChunkProvider().chunkExists(x, z);
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        IChunkLoader chunkLoader = this.mainWorld.getSaveHandler().getChunkLoader(this.provider);
        return new ServerChunkCacheBlockSystem(this, chunkLoader);
    }

    @Override
    public ChunkProviderServer getChunkProvider() {
        return (ChunkProviderServer) this.chunkProvider;
    }

    @Override
    public void tick() {
        super.tick();
        this.updateBlocks();

        this.chunkTracker.tick();
        if (this.chunkHandler.isEmpty()) {
            this.remove();
        }
    }

    @Override
    protected void updateBlocks() {
        int randomTickSpeed = this.getGameRules().getInt("randomTickSpeed");
        this.profiler.startSection("pollingChunksBS");
        for (Iterator<Chunk> iterator = this.getPersistentChunkIterable(this.chunkTracker.getChunkIterator()); iterator.hasNext(); this.profiler.endSection()) {
            this.profiler.startSection("getChunkBS");
            Chunk chunk = iterator.next();
            int chunkX = chunk.x << 4;
            int chunkZ = chunk.z << 4;
            this.profiler.endStartSection("checkNextLightBS");
            chunk.enqueueRelightChecks();
            this.profiler.endStartSection("tickChunkBS");
            chunk.onTick(false);
            this.profiler.endStartSection("tickBlocksBS");
            if (randomTickSpeed > 0) {
                for (ExtendedBlockStorage storage : chunk.getBlockStorageArray()) {
                    if (storage != Chunk.NULL_BLOCK_STORAGE && storage.needsRandomTick()) {
                        for (int tick = 0; tick < randomTickSpeed; ++tick) {
                            this.updateLCG = this.updateLCG * 3 + 1013904223;
                            int position = this.updateLCG >> 2;
                            int x = position & 0xF;
                            int y = position >> 8 & 0xF;
                            int z = position >> 16 & 0xF;
                            IBlockState state = storage.get(x, z, y);
                            Block block = state.getBlock();
                            this.profiler.startSection("randomTickBS");
                            if (block.getTickRandomly()) {
                                block.randomTick(this, new BlockPos(x + chunkX, z + storage.getYLocation(), y + chunkZ), state, this.rand);
                            }
                            this.profiler.endSection();
                        }
                    }
                }
            }
        }
        this.profiler.endSection();
    }

    @Override
    public void updateBlockTick(BlockPos pos, Block block, int delay, int priority) {
        if (pos instanceof BlockPos.MutableBlockPos) {
            pos = pos.toImmutable();
            BlockSystems.LOGGER.warn("Tried to assign a mutable BlockPos to tick data...", new Error(pos.getClass().toString()));
        }
        Material material = block.getDefaultState().getMaterial();
        if (this.scheduledUpdatesAreImmediate && material != Material.AIR) {
            if (block.requiresUpdates()) {
                boolean isForced = this.getPersistentChunks().containsKey(new ChunkPos(pos));
                int range = isForced ? 0 : 8;
                if (this.isAreaLoaded(pos.add(-range, -range, -range), pos.add(range, range, range))) {
                    IBlockState state = this.getBlockState(pos);
                    if (state.getMaterial() != Material.AIR && state.getBlock() == block) {
                        state.getBlock().updateTick(this, pos, state, this.rand);
                    }
                }
                return;
            }
            delay = 1;
        }
        NextTickListEntry schedule = new NextTickListEntry(pos, block);
        if (this.isBlockLoaded(pos)) {
            if (material != Material.AIR) {
                schedule.setScheduledTime((long) delay + this.worldInfo.getWorldTotalTime());
                schedule.setPriority(priority);
            }
            if (!this.scheduledTicksSet.contains(schedule)) {
                this.scheduledTicksSet.add(schedule);
                this.scheduledTicksTree.add(schedule);
            }
        }
    }

    @Override
    public void scheduleBlockUpdate(BlockPos pos, Block block, int delay, int priority) {
        NextTickListEntry schedule = new NextTickListEntry(pos, block);
        schedule.setPriority(priority);
        Material material = block.getDefaultState().getMaterial();
        if (material != Material.AIR) {
            schedule.setScheduledTime((long) delay + this.worldInfo.getWorldTotalTime());
        }
        if (!this.scheduledTicksSet.contains(schedule)) {
            this.scheduledTicksSet.add(schedule);
            this.scheduledTicksTree.add(schedule);
        }
    }

    @Override
    public boolean tickUpdates(boolean checkTime) {
        int updates = this.scheduledTicksTree.size();
        if (updates != this.scheduledTicksSet.size()) {
            throw new IllegalStateException("TickNextTick list out of sync");
        } else {
            if (updates > 65536) {
                updates = 65536;
            }
            this.profiler.startSection("cleaning");
            for (int i = 0; i < updates; i++) {
                NextTickListEntry scheduledTick = this.scheduledTicksTree.first();
                if (!checkTime && scheduledTick.scheduledTime > this.worldInfo.getWorldTotalTime()) {
                    break;
                }
                this.scheduledTicksTree.remove(scheduledTick);
                this.scheduledTicksSet.remove(scheduledTick);
                this.currentScheduledTicks.add(scheduledTick);
            }
            this.profiler.endStartSection("ticking");
            for (NextTickListEntry scheduledTick : this.currentScheduledTicks) {
                if (this.isAreaLoaded(scheduledTick.position.add(0, 0, 0), scheduledTick.position.add(0, 0, 0))) {
                    IBlockState state = this.getBlockState(scheduledTick.position);
                    if (state.getMaterial() != Material.AIR && Block.isEqualTo(state.getBlock(), scheduledTick.getBlock())) {
                        try {
                            state.getBlock().updateTick(this, scheduledTick.position, state, this.rand);
                        } catch (Throwable throwable) {
                            CrashReport report = CrashReport.makeCrashReport(throwable, "Exception while ticking a block");
                            CrashReportCategory category = report.makeCategory("Block being ticked");
                            CrashReportCategory.addBlockInfo(category, scheduledTick.position, state);
                            throw new ReportedException(report);
                        }
                    }
                } else {
                    this.scheduleUpdate(scheduledTick.position, scheduledTick.getBlock(), 0);
                }
            }
            this.currentScheduledTicks.clear();
            this.profiler.endSection();
            return !this.scheduledTicksTree.isEmpty();
        }
    }

    @Override
    public List<NextTickListEntry> getPendingBlockUpdates(StructureBoundingBox bounds, boolean remove) {
        List<NextTickListEntry> updates = null;
        for (int i = 0; i < 2; i++) {
            Iterator<NextTickListEntry> iterator;
            if (i == 0) {
                iterator = this.scheduledTicksTree.iterator();
            } else {
                iterator = this.currentScheduledTicks.iterator();
            }
            while (iterator.hasNext()) {
                NextTickListEntry scheduledUpdate = iterator.next();
                BlockPos position = scheduledUpdate.position;
                if (position.getX() >= bounds.minX && position.getX() < bounds.maxX && position.getZ() >= bounds.minZ && position.getZ() < bounds.maxZ) {
                    if (remove) {
                        if (i == 0) {
                            this.scheduledTicksSet.remove(scheduledUpdate);
                        }
                        iterator.remove();
                    }
                    if (updates == null) {
                        updates = Lists.newArrayList();
                    }
                    updates.add(scheduledUpdate);
                }
            }
        }
        return updates;
    }

    @Override
    public boolean isBlockTickPending(BlockPos pos, Block block) {
        NextTickListEntry scheduledTick = new NextTickListEntry(pos, block);
        return this.currentScheduledTicks.contains(scheduledTick);
    }

    public void deserialize(NBTTagCompound compound) {
        this.deserializing = true;
        this.posX = compound.getDouble("pos_x");
        this.posY = compound.getDouble("pos_y");
        this.posZ = compound.getDouble("pos_z");
        this.rotation.deserialize(compound.getCompoundTag("rot"));
        this.chunkHandler.deserialize(compound.getCompoundTag("block_data"));
        this.deserializing = false;
        this.recalculateMatrices();
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        compound.setDouble("pos_x", this.posX);
        compound.setDouble("pos_y", this.posY);
        compound.setDouble("pos_z", this.posZ);
        compound.setTag("rot", this.rotation.serialize(new NBTTagCompound()));
        compound.setTag("block_data", this.chunkHandler.serialize(new NBTTagCompound()));
        return compound;
    }

    @Override
    public MinecraftServer getMinecraftServer() {
        return this.server;
    }

    public BlockSystemChunkTracker getChunkTracker() {
        return this.chunkTracker;
    }

    public PartitionedChunkHandler getChunkHandler() {
        return this.chunkHandler;
    }
}
