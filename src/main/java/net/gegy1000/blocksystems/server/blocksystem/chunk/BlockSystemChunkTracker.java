package net.gegy1000.blocksystems.server.blocksystem.chunk;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ComparisonChain;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class BlockSystemChunkTracker {
    private static final Predicate<EntityPlayerMP> NOT_SPECTATOR = player -> player != null && !player.isSpectator();
    private static final Predicate<EntityPlayerMP> CAN_GENERATE_CHUNKS = player -> player != null && (!player.isSpectator() || player.getServerWorld().getGameRules().getBoolean("spectatorsGenerateChunks"));
    public static final double MOVE_RECALCULATE_RANGE = 8.0;

    private final BlockSystemServer blockSystem;
    private final Int2ObjectMap<PlayerHandler> players = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectMap<BlockSystemPlayerTracker> playerTrackers = new Long2ObjectOpenHashMap<>(4096);
    private final Set<BlockSystemPlayerTracker> updateQueue = new HashSet<>();
    private final List<BlockSystemPlayerTracker> sendQueue = new LinkedList<>();
    private final List<BlockSystemPlayerTracker> requestQueue = new LinkedList<>();
    private final List<BlockSystemPlayerTracker> playerTrackerList = new ArrayList<>();

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
        long worldTime = this.blockSystem.getParentWorld().getTotalWorldTime();

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

        if (!this.players.isEmpty()) {
            if (worldTime % 10 == 0) {
                for (PlayerHandler handler : this.players.values()) {
                    this.updatePlayer(handler, handler.player);
                }
            }
        } else {
            WorldProvider provider = this.blockSystem.provider;
            if (!provider.canRespawnHere()) {
                this.blockSystem.getChunkProvider().queueUnloadAll();
            }
        }
    }

    private void updatePlayer(PlayerHandler handler, EntityPlayerMP player) {
        int playerChunkX = MathHelper.floor(player.posX) >> 4;
        int playerChunkZ = MathHelper.floor(player.posZ) >> 4;

        double movedX = handler.managedX - player.posX;
        double movedZ = handler.managedZ - player.posZ;
        double moved = movedX * movedX + movedZ * movedZ;

        if (moved >= MOVE_RECALCULATE_RANGE * MOVE_RECALCULATE_RANGE) {
            int managedChunkX = MathHelper.floor(handler.managedX) >> 4;
            int managedChunkZ = MathHelper.floor(handler.managedZ) >> 4;
            int movedChunkX = playerChunkX - managedChunkX;
            int movedChunkZ = playerChunkZ - managedChunkZ;

            int range = this.playerViewRadius;
            if (movedChunkX != 0 || movedChunkZ != 0) {
                for (int chunkZ = playerChunkZ - range; chunkZ <= playerChunkZ + range; ++chunkZ) {
                    for (int chunkX = playerChunkX - range; chunkX <= playerChunkX + range; ++chunkX) {
                        if (!this.inRange(chunkX, chunkZ, managedChunkX, managedChunkZ, range)) {
                            this.getOrCreateTracker(chunkX, chunkZ).addPlayer(player);
                        }

                        if (!this.inRange(chunkX - movedChunkX, chunkZ - movedChunkZ, playerChunkX, playerChunkZ, range)) {
                            BlockSystemPlayerTracker tracker = this.getTracker(chunkX - movedChunkX, chunkZ - movedChunkZ);
                            if (tracker != null) {
                                tracker.removePlayer(player);
                            }
                        }
                    }
                }

                handler.managedX = player.posX;
                handler.managedZ = player.posZ;
                this.markSortPending();
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

    private boolean inRange(int originX, int originZ, int targetX, int targetZ, int range) {
        int deltaX = originX - targetX;
        int deltaZ = originZ - targetZ;
        if (deltaX >= -range && deltaX <= range) {
            return deltaZ >= -range && deltaZ <= range;
        } else {
            return false;
        }
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
        Point3d playerPosition = this.getLocalPos(player);
        PlayerHandler handler = new PlayerHandler(player, playerPosition.getX(), playerPosition.getZ());
        this.players.put(player.getEntityId(), handler);

        int playerChunkX = (int) playerPosition.getX() >> 4;
        int playerChunkZ = (int) playerPosition.getZ() >> 4;
        for (int chunkX = playerChunkX - this.playerViewRadius; chunkX <= playerChunkX + this.playerViewRadius; ++chunkX) {
            for (int chunkZ = playerChunkZ - this.playerViewRadius; chunkZ <= playerChunkZ + this.playerViewRadius; ++chunkZ) {
                this.getOrCreateTracker(chunkX, chunkZ).addPlayer(player);
            }
        }

        this.markSortPending();
    }

    public void removePlayer(EntityPlayerMP player) {
        PlayerHandler handler = this.players.remove(player.getEntityId());
        if (handler != null) {
            int managedChunkX = MathHelper.floor(handler.managedX) >> 4;
            int managedChunkZ = MathHelper.floor(handler.managedZ) >> 4;
            for (int chunkX = managedChunkX - this.playerViewRadius; chunkX <= managedChunkX + this.playerViewRadius; ++chunkX) {
                for (int chunkZ = managedChunkZ - this.playerViewRadius; chunkZ <= managedChunkZ + this.playerViewRadius; ++chunkZ) {
                    BlockSystemPlayerTracker tracker = this.getTracker(chunkX, chunkZ);
                    if (tracker != null) {
                        tracker.removePlayer(player);
                    }
                }
            }
            this.markSortPending();
        }
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
            for (PlayerHandler handler : this.players.values()) {
                EntityPlayerMP player = handler.player;
                Point3d playerPosition = this.getLocalPos(player);
                int playerChunkX = MathHelper.floor(playerPosition.getX()) >> 4;
                int playerChunkZ = MathHelper.floor(playerPosition.getZ()) >> 4;
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

    public Point3d getLocalPos(EntityPlayer player) {
        return this.blockSystem.getTransform().toLocalPos(new Point3d(player.posX, player.posY, player.posZ));
    }

    private static class PlayerHandler {
        private final EntityPlayerMP player;
        private double managedX;
        private double managedZ;

        private PlayerHandler(EntityPlayerMP player, double managedX, double managedZ) {
            this.player = player;
            this.managedX = managedX;
            this.managedZ = managedZ;
        }
    }
}
