package net.gegy1000.blocksystems.server.blocksystem;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.message.blocksystem.TrackMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.UntrackMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockSystemTrackingHandler {
    public static final Map<WorldServer, BlockSystemTrackingHandler> HANDLERS = new HashMap<>();
    public static final double RANGE = 1024.0;

    private WorldServer world;

    private Map<EntityPlayer, Set<BlockSystem>> tracking = new HashMap<>();

    private long lastUpdate;

    public BlockSystemTrackingHandler(WorldServer world) {
        this.world = world;
    }

    public void update() {
        long time = this.world.getTotalWorldTime();
        if (time - this.lastUpdate > 4) {
            Map<Integer, BlockSystem> blockSystems = BlockSystems.PROXY.getBlockSystemHandler(this.world).getBlockSystems();
            for (EntityPlayer p : this.world.playerEntities) {
                if (p instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) p;
                    for (Map.Entry<Integer, BlockSystem> entry : blockSystems.entrySet()) {
                        BlockSystem blockSystem = entry.getValue();
                        if (this.shouldTrack(player, blockSystem)) {
                            this.track(player, blockSystem);
                        }
                    }
                    Set<BlockSystem> tracking = this.tracking.get(player);
                    if (tracking != null) {
                        List<BlockSystem> untrack = new ArrayList<>(tracking.size());
                        for (BlockSystem blockSystem : tracking) {
                            if (blockSystem.removed || !this.shouldTrack(player, blockSystem)) {
                                untrack.add(blockSystem);
                            }
                        }
                        for (BlockSystem blockSystem : untrack) {
                            this.untrack(player, blockSystem);
                        }
                        untrack.clear();
                    }
                }
            }
            this.lastUpdate = time;
        }
    }

    public void track(EntityPlayerMP player, BlockSystem blockSystem) {
        Set<BlockSystem> tracking = this.tracking.get(player);
        if (tracking == null) {
            tracking = new HashSet<>();
            this.tracking.put(player, tracking);
        }
        if (!tracking.contains(blockSystem)) {
            tracking.add(blockSystem);
            BlockSystems.NETWORK_WRAPPER.sendTo(new TrackMessage(blockSystem), player);
            if (blockSystem instanceof BlockSystemServer) {
                ((BlockSystemServer) blockSystem).getChunkTracker().addPlayer(player);
            }
        }
    }

    public void untrack(EntityPlayerMP player, BlockSystem blockSystem) {
        Set<BlockSystem> tracking = this.tracking.get(player);
        if (tracking != null) {
            if (tracking.remove(blockSystem)) {
                if (blockSystem instanceof BlockSystemServer) {
                    ((BlockSystemServer) blockSystem).getChunkTracker().removePlayer(player);
                }
                BlockSystems.NETWORK_WRAPPER.sendTo(new UntrackMessage(blockSystem), player);
                if (tracking.size() <= 0) {
                    this.tracking.remove(player);
                }
            }
        }
    }

    public boolean shouldTrack(EntityPlayerMP player, BlockSystem blockSystem) {
        double deltaX = player.posX - blockSystem.posX;
        double deltaZ = player.posZ - blockSystem.posZ;
        return deltaX >= -RANGE && deltaX <= RANGE && deltaZ >= -RANGE && deltaZ <= RANGE;
    }

    public static void remove(WorldServer world) {
        HANDLERS.remove(world);
    }

    public static void add(WorldServer world) {
        HANDLERS.put(world, new BlockSystemTrackingHandler(world));
    }

    public static BlockSystemTrackingHandler get(WorldServer world) {
        return HANDLERS.get(world);
    }
}
