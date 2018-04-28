package net.gegy1000.blocksystems.server.blocksystem.chunk;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemPlayerHandler;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;
import javax.vecmath.Point3d;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class BlockSystemChunkTracker {
    private static final Predicate<EntityPlayerMP> NOT_SPECTATOR = player -> player != null && !player.isSpectator();
    private static final Predicate<EntityPlayerMP> CAN_GENERATE_CHUNKS = player -> player != null && (!player.isSpectator() || player.getServerWorld().getGameRules().getBoolean("spectatorsGenerateChunks"));

    private final BlockSystemServer blockSystem;
    private final List<EntityPlayerMP> players = Lists.newArrayList();
    private final Long2ObjectMap<BlockSystemPlayerTracker> playerTrackers = new Long2ObjectOpenHashMap<>(4096);
    private final Set<BlockSystemPlayerTracker> updateQueue = Sets.newHashSet();
    private final List<BlockSystemPlayerTracker> sendQueue = Lists.newLinkedList();
    private final List<BlockSystemPlayerTracker> requestQueue = Lists.newLinkedList();
    private final List<BlockSystemPlayerTracker> playerTrackerList = Lists.newArrayList();

    private int playerViewRadius;
    private long previousTotalWorldTime;
    private boolean sortMissingChunks = true;
    private boolean sortSendToPlayers = true;

    public BlockSystemChunkTracker(BlockSystemServer blockSystem) {
        this.blockSystem = blockSystem;
        this.setPlayerViewRadius(blockSystem.getMinecraftServer().getPlayerList().getViewDistance());
    }

    public BlockSystemServer getBlockSystem() {
        return this.blockSystem;
    }

    public Iterator<Chunk> getChunkIterator() {
        final Iterator<BlockSystemPlayerTracker> iterator = this.playerTrackerList.iterator();
        return new AbstractIterator<Chunk>() {
            @Override
            protected Chunk computeNext() {
                while (true) {
                    if (iterator.hasNext()) {
                        BlockSystemPlayerTracker tracker = iterator.next();
                        Chunk chunk = tracker.getProvidingChunk();
                        if (chunk == null) {
                            continue;
                        }
                        if (!chunk.isLightPopulated() && chunk.isTerrainPopulated() || !chunk.wasTicked()) {
                            return chunk;
                        }
                        if (!tracker.hasPlayerMatchingInRange(128.0, BlockSystemChunkTracker.NOT_SPECTATOR)) {
                            continue;
                        }
                        return chunk;
                    }
                    return this.endOfData();
                }
            }
        };
    }

    public void tick() {
        long worldTime = this.blockSystem.getMainWorld().getTotalWorldTime();

        if (worldTime - this.previousTotalWorldTime > 8000) {
            this.previousTotalWorldTime = worldTime;
            for (BlockSystemPlayerTracker tracker : this.playerTrackerList) {
                tracker.update();
                tracker.updateChunkInhabitedTime();
            }
        }

        if (!this.updateQueue.isEmpty()) {
            for (BlockSystemPlayerTracker tracker : this.updateQueue) {
                tracker.update();
            }
            this.updateQueue.clear();
        }

        if (this.sortMissingChunks && worldTime % 4 == 0) {
            this.sortMissingChunks = false;
            this.requestQueue.sort((tracker1, tracker2) -> ComparisonChain.start().compare(tracker1.getClosestPlayerDistance(), tracker2.getClosestPlayerDistance()).result());
        }

        if (this.sortSendToPlayers && worldTime % 4 == 2) {
            this.sortSendToPlayers = false;
            this.sendQueue.sort((tracker1, tracker2) -> ComparisonChain.start().compare(tracker1.getClosestPlayerDistance(), tracker2.getClosestPlayerDistance()).result());
        }

        if (!this.requestQueue.isEmpty()) {
            long stopTime = System.nanoTime() + 50000000;
            int updates = 49;
            Iterator<BlockSystemPlayerTracker> iterator = this.requestQueue.iterator();
            while (iterator.hasNext()) {
                BlockSystemPlayerTracker tracker = iterator.next();
                if (tracker.getProvidingChunk() == null) {
                    boolean canGenerate = tracker.hasPlayerMatching(CAN_GENERATE_CHUNKS);
                    if (tracker.providePlayerChunk(canGenerate)) {
                        iterator.remove();
                        if (tracker.sendToPlayers()) {
                            this.sendQueue.remove(tracker);
                        }
                        if (--updates < 0 || System.nanoTime() > stopTime) {
                            break;
                        }
                    }
                }
            }
        }

        if (!this.sendQueue.isEmpty()) {
            int sent = 81;
            Iterator<BlockSystemPlayerTracker> iterator = this.sendQueue.iterator();
            while (iterator.hasNext()) {
                BlockSystemPlayerTracker tracker = iterator.next();
                if (tracker.sendToPlayers()) {
                    iterator.remove();
                    if (--sent < 0) {
                        break;
                    }
                }
            }
        }

        if (this.players.isEmpty()) {
            WorldProvider provider = this.blockSystem.provider;
            if (!provider.canRespawnHere()) {
                this.blockSystem.getChunkProvider().queueUnloadAll();
            }
        }
    }

    public boolean contains(int chunkX, int chunkZ) {
        return this.playerTrackers.get(getChunkIndex(chunkX, chunkZ)) != null;
    }

    @Nullable
    public BlockSystemPlayerTracker getTracker(int x, int z) {
        return this.playerTrackers.get(getChunkIndex(x, z));
    }

    private BlockSystemPlayerTracker getOrCreateTracker(int chunkX, int chunkZ) {
        long index = getChunkIndex(chunkX, chunkZ);
        BlockSystemPlayerTracker tracker = this.playerTrackers.get(index);
        if (tracker == null) {
            tracker = new BlockSystemPlayerTracker(this, chunkX, chunkZ);
            this.playerTrackers.put(index, tracker);
            this.playerTrackerList.add(tracker);
            if (tracker.getProvidingChunk() == null || tracker.getProvidingChunk().isEmpty()) {
                this.requestQueue.add(tracker);
            }
            if (!tracker.sendToPlayers()) {
                this.sendQueue.add(tracker);
            }
        }
        return tracker;
    }

    public void markBlockForUpdate(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        BlockSystemPlayerTracker tracker = this.getTracker(chunkX, chunkZ);
        if (tracker != null) {
            tracker.blockChanged(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        }
    }

    public void addPlayer(EntityPlayerMP player) {
        BlockSystemPlayerHandler handler = BlockSystems.PROXY.getBlockSystemHandler(player.world).get(this.blockSystem, player);
        Point3d playerPosition = this.getUntransformedPosition(player);
        int playerChunkX = (int) playerPosition.getX() >> 4;
        int playerChunkZ = (int) playerPosition.getZ() >> 4;
        handler.setManagedPosX(playerPosition.getX());
        handler.setManagedPosZ(playerPosition.getZ());
        for (int chunkX = playerChunkX - this.playerViewRadius; chunkX <= playerChunkX + this.playerViewRadius; ++chunkX) {
            for (int chunkZ = playerChunkZ - this.playerViewRadius; chunkZ <= playerChunkZ + this.playerViewRadius; ++chunkZ) {
                this.getOrCreateTracker(chunkX, chunkZ).addPlayer(player);
            }
        }
        this.players.add(player);
        this.markSortPending();
    }

    public void removePlayer(EntityPlayerMP player) {
        BlockSystemPlayerHandler handler = BlockSystems.PROXY.getBlockSystemHandler(player.world).get(this.blockSystem, player);
        int managedChunkX = (int) handler.getManagedPosX() >> 4;
        int managedChunkZ = (int) handler.getManagedPosZ() >> 4;
        for (int chunkX = managedChunkX - this.playerViewRadius; chunkX <= managedChunkX + this.playerViewRadius; ++chunkX) {
            for (int chunkZ = managedChunkZ - this.playerViewRadius; chunkZ <= managedChunkZ + this.playerViewRadius; ++chunkZ) {
                BlockSystemPlayerTracker tracker = this.getTracker(chunkX, chunkZ);
                if (tracker != null) {
                    tracker.removePlayer(player);
                }
            }
        }
        this.players.remove(player);
        this.markSortPending();
    }

    private boolean intersects(int x1, int z1, int x2, int z2, int radius) {
        int deltaX = x1 - x2;
        int deltaZ = z1 - z2;
        return (deltaX >= -radius && deltaX <= radius) && (deltaZ >= -radius && deltaZ <= radius);
    }

    public void setPlayerViewRadius(int radius) {
        radius = MathHelper.clamp(radius, 3, 32);
        if (radius != this.playerViewRadius) {
            int deltaRadius = radius - this.playerViewRadius;
            for (EntityPlayerMP player : Lists.newArrayList(this.players)) {
                Point3d playerPosition = this.getUntransformedPosition(player);
                int playerChunkX = (int) playerPosition.getX() >> 4;
                int playerChunkZ = (int) playerPosition.getZ() >> 4;
                if (deltaRadius > 0) {
                    for (int chunkX = playerChunkX - radius; chunkX <= playerChunkX + radius; ++chunkX) {
                        for (int chunkZ = playerChunkZ - radius; chunkZ <= playerChunkZ + radius; ++chunkZ) {
                            BlockSystemPlayerTracker tracker = this.getOrCreateTracker(chunkX, chunkZ);
                            if (!tracker.containsPlayer(player)) {
                                tracker.addPlayer(player);
                            }
                        }
                    }
                } else {
                    for (int chunkX = playerChunkX - this.playerViewRadius; chunkX <= playerChunkX + this.playerViewRadius; ++chunkX) {
                        for (int chunkZ = playerChunkZ - this.playerViewRadius; chunkZ <= playerChunkZ + this.playerViewRadius; ++chunkZ) {
                            if (!this.intersects(chunkX, chunkZ, playerChunkX, playerChunkZ, radius)) {
                                this.getOrCreateTracker(chunkX, chunkZ).removePlayer(player);
                            }
                        }
                    }
                }
            }
            this.playerViewRadius = radius;
            this.markSortPending();
        }
    }

    private void markSortPending() {
        this.sortMissingChunks = true;
        this.sortSendToPlayers = true;
    }

    private static long getChunkIndex(int x, int z) {
        return (long) x + 0x7FFFFFFFL | (long) z + 0x7FFFFFFFL << 32;
    }

    public void addTracker(BlockSystemPlayerTracker tracker) {
        this.updateQueue.add(tracker);
    }

    public void removeTracker(BlockSystemPlayerTracker tracker) {
        ChunkPos pos = tracker.getChunkPosition();
        long index = getChunkIndex(pos.x, pos.z);
        tracker.updateChunkInhabitedTime();
        this.playerTrackers.remove(index);
        this.playerTrackerList.remove(tracker);
        this.updateQueue.remove(tracker);
        this.sendQueue.remove(tracker);
        this.requestQueue.remove(tracker);
        Chunk chunk = tracker.getProvidingChunk();
        if (chunk != null) {
            this.getBlockSystem().getChunkProvider().queueUnload(chunk);
        }
    }

    public Point3d getUntransformedPosition(EntityPlayer player) {
        return this.blockSystem.getUntransformedPosition(new Point3d(player.posX, player.posY, player.posZ));
    }
}
