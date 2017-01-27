package net.gegy1000.blocksystems.server.blocksystem.chunk;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.ChunkMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.MultiBlockUpdateMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.SetBlockMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.UnloadChunkMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.UpdateBlockEntityMessage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.chunkio.ChunkIOExecutor;
import net.minecraftforge.event.world.ChunkWatchEvent.UnWatch;
import net.minecraftforge.event.world.ChunkWatchEvent.Watch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class BlockSystemPlayerTracker {
    private static final Logger LOGGER = LogManager.getLogger();
    private final BlockSystemChunkTracker trackManager;
    private final BlockSystemServer blockSystem;
    private final List<EntityPlayerMP> players = Lists.newArrayList();
    private final ChunkPos chunkPosition;
    private short[] changedBlocks = new short[64];
    private BlockSystemChunk providingChunk;
    private int changeCount;
    private int changedSectionMask;
    private long lastUpdateInhabitedTime;
    private boolean sentToPlayers;
    private Runnable loadedRunnable;
    private boolean loading = true;

    public BlockSystemPlayerTracker(BlockSystemChunkTracker trackManager, int chunkX, int chunkZ) {
        this.loadedRunnable = () -> {
            BlockSystemPlayerTracker.this.providingChunk = (BlockSystemChunk) BlockSystemPlayerTracker.this.blockSystem.getChunkProvider().loadChunk(BlockSystemPlayerTracker.this.chunkPosition.chunkXPos, BlockSystemPlayerTracker.this.chunkPosition.chunkZPos);
            BlockSystemPlayerTracker.this.loading = false;
        };
        this.trackManager = trackManager;
        this.chunkPosition = new ChunkPos(chunkX, chunkZ);
        this.blockSystem = trackManager.getBlockSystem();
        this.blockSystem.getChunkProvider().loadChunk(chunkX, chunkZ, this.loadedRunnable);
    }

    public ChunkPos getChunkPosition() {
        return this.chunkPosition;
    }

    public void addPlayer(EntityPlayerMP player) {
        if (this.players.contains(player)) {
            LOGGER.debug("Failed to add player. {} already is in chunk {}, {}", player, this.chunkPosition.chunkXPos, this.chunkPosition.chunkZPos);
        } else {
            if (this.players.isEmpty()) {
                this.lastUpdateInhabitedTime = this.blockSystem.getTotalWorldTime();
            }
            this.players.add(player);
            if (this.sentToPlayers) {
                this.sendNearbySpecialEntities(player);
                MinecraftForge.EVENT_BUS.post(new Watch(this.chunkPosition, player));
            }
        }
    }

    public void removePlayer(EntityPlayerMP player) {
        if (this.players.contains(player)) {
            if (this.providingChunk == null) {
                this.players.remove(player);
                if (this.players.isEmpty()) {
                    if (this.loading) {
                        ChunkIOExecutor.dropQueuedChunkLoad(this.blockSystem, this.chunkPosition.chunkXPos, this.chunkPosition.chunkZPos, this.loadedRunnable);
                    }
                    this.trackManager.removeTracker(this);
                }
                return;
            }
            if (this.sentToPlayers) {
                BlockSystems.NETWORK_WRAPPER.sendTo(new UnloadChunkMessage(this.blockSystem, this.chunkPosition.chunkXPos, this.chunkPosition.chunkZPos), player);
            }
            this.players.remove(player);
            World world = player.world;
            player.world = this.blockSystem;
            MinecraftForge.EVENT_BUS.post(new UnWatch(this.chunkPosition, player));
            player.world = world;
            if (this.players.isEmpty()) {
                this.trackManager.removeTracker(this);
            }
        }
    }

    public boolean providePlayerChunk(boolean canGenerate) {
        if (!this.loading) {
            if (this.providingChunk != null) {
                return true;
            } else {
                ChunkProviderServer chunkProvider = this.blockSystem.getChunkProvider();
                int x = this.chunkPosition.chunkXPos;
                int z = this.chunkPosition.chunkZPos;
                if (canGenerate) {
                    this.providingChunk = (BlockSystemChunk) chunkProvider.provideChunk(x, z);
                } else {
                    this.providingChunk = (BlockSystemChunk) chunkProvider.loadChunk(x, z);
                }
            }
        }
        return false;
    }

    public boolean sendToPlayers() {
        if (this.sentToPlayers) {
            return true;
        } else if (this.providingChunk == null || this.providingChunk.isEmpty()) {
            return false;
        } else {
            this.changeCount = 0;
            this.changedSectionMask = 0;
            this.sentToPlayers = true;
            ChunkMessage message = new ChunkMessage(this.blockSystem, this.providingChunk, 0xFFFF);
            for (EntityPlayerMP player : this.players) {
                BlockSystems.NETWORK_WRAPPER.sendTo(message, player);
//                this.blockSystem.getEntityTracker().sendLeashedEntitiesInChunk(player, this.providingChunk); TODO
                World world = player.world;
                player.world = this.blockSystem;
                MinecraftForge.EVENT_BUS.post(new Watch(this.chunkPosition, player));
                player.world = world;
            }
            return true;
        }
    }

    public void sendNearbySpecialEntities(EntityPlayerMP player) {
        if (this.sentToPlayers) {
            BlockSystems.NETWORK_WRAPPER.sendTo(new ChunkMessage(this.blockSystem, this.providingChunk, 0xFFFF), player);
//            this.blockSystem.getEntityTracker().sendLeashedEntitiesInChunk(player, this.providingChunk);
        }
    }

    public void updateChunkInhabitedTime() {
        long worldTime = this.blockSystem.getTotalWorldTime();
        if (this.providingChunk != null) {
            this.providingChunk.setInhabitedTime(this.providingChunk.getInhabitedTime() + worldTime - this.lastUpdateInhabitedTime);
        }
        this.lastUpdateInhabitedTime = worldTime;
    }

    public void blockChanged(int x, int y, int z) {
        if (this.sentToPlayers) {
            if (this.changeCount == 0) {
                this.trackManager.addTracker(this);
            }
            this.changedSectionMask |= 1 << (y >> 4);
            short blockIndex = (short) (x << 12 | z << 8 | y);
            for (int change = 0; change < this.changeCount; ++change) {
                if (this.changedBlocks[change] == blockIndex) {
                    return;
                }
            }
            if (this.changeCount == this.changedBlocks.length) {
                this.changedBlocks = Arrays.copyOf(this.changedBlocks, this.changedBlocks.length << 1);
            }
            this.changedBlocks[this.changeCount++] = blockIndex;
        }
    }

    public void sendMessage(BaseMessage<?> message) {
        if (this.sentToPlayers) {
            for (EntityPlayerMP player : this.players) {
                BlockSystems.NETWORK_WRAPPER.sendTo(message, player);
            }
        }
    }

    public void update() {
        if (this.sentToPlayers && this.providingChunk != null) {
            if (this.changeCount != 0) {
                if (this.changeCount == 1) {
                    int changedX = (this.changedBlocks[0] >> 12 & 15) + this.chunkPosition.chunkXPos * 16;
                    int changedY = this.changedBlocks[0] & 255;
                    int changedZ = (this.changedBlocks[0] >> 8 & 15) + this.chunkPosition.chunkZPos * 16;
                    BlockPos changedPos = new BlockPos(changedX, changedY, changedZ);
                    IBlockState state = this.blockSystem.getBlockState(changedPos);
                    this.sendMessage(new SetBlockMessage(this.blockSystem, changedPos, state));
                    if (state.getBlock().hasTileEntity(state)) {
                        this.updateBlockEntity(this.blockSystem.getTileEntity(changedPos));
                    }
                } else if (this.changeCount >= ForgeModContainer.clumpingThreshold) {
                    this.sendMessage(new ChunkMessage(this.blockSystem, this.providingChunk, this.changedSectionMask));
                } else {
                    this.sendMessage(new MultiBlockUpdateMessage(this.blockSystem, this.providingChunk, this.changeCount, this.changedBlocks));
                    for (int change = 0; change < this.changeCount; ++change) {
                        int x = (this.changedBlocks[change] >> 12 & 15) + this.chunkPosition.chunkXPos * 16;
                        int y = this.changedBlocks[change] & 255;
                        int z = (this.changedBlocks[change] >> 8 & 15) + this.chunkPosition.chunkZPos * 16;
                        BlockPos changedPos = new BlockPos(x, y, z);
                        IBlockState state = this.blockSystem.getBlockState(changedPos);
                        if (state.getBlock().hasTileEntity(state)) {
                            this.updateBlockEntity(this.blockSystem.getTileEntity(changedPos));
                        }
                    }
                }
                this.changeCount = 0;
                this.changedSectionMask = 0;
            }
        }
    }

    private void updateBlockEntity(TileEntity entity) {
        if (entity != null) {
            SPacketUpdateTileEntity packet = entity.getUpdatePacket();
            if (packet != null) {
                this.sendMessage(new UpdateBlockEntityMessage(this.blockSystem, packet));
            }
        }
    }

    public boolean containsPlayer(EntityPlayerMP player) {
        return this.players.contains(player);
    }

    public boolean hasPlayerMatching(Predicate<EntityPlayerMP> predicate) {
        return Iterables.tryFind(this.players, predicate::test).isPresent();
    }

    public boolean hasPlayerMatchingInRange(double range, Predicate<EntityPlayerMP> selector) {
        for (EntityPlayerMP player : this.players) {
            if (selector.test(player) && this.chunkPosition.getDistanceSq(player) < range * range) {
                return true;
            }
        }
        return false;
    }

    public boolean isSentToPlayers() {
        return this.sentToPlayers;
    }

    public Chunk getProvidingChunk() {
        return this.providingChunk;
    }

    public double getClosestPlayerDistance() {
        double closestDistance = Double.MAX_VALUE;
        for (EntityPlayerMP entityplayermp : this.players) {
            double distance = this.chunkPosition.getDistanceSq(entityplayermp);
            if (distance < closestDistance) {
                closestDistance = distance;
            }
        }
        return closestDistance;
    }
}