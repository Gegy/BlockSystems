package net.gegy1000.blocksystems.server.blocksystem;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.interaction.BlockSystemInteractionHandler;
import net.gegy1000.blocksystems.server.blocksystem.interaction.ServerBlockSystemInteractionHandler;
import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ServerBlockSystemHandler implements BlockSystemHandler {
    private final WorldServer world;
    private final Int2ObjectMap<BlockSystem> blockSystems = new Int2ObjectOpenHashMap<>();
    private final IntList removalQueue = new IntArrayList();

    private final Object lock = new Object();

    private final Map<EntityPlayer, BlockSystemInteractionHandler> interactionHandlers = new HashMap<>();

    public ServerBlockSystemHandler(WorldServer world) {
        this.world = world;
    }

    @Override
    public void update() {
        for (BlockSystem blockSystem : this.blockSystems.values()) {
            if (!blockSystem.isRemoved()) {
                blockSystem.tick();
            } else {
                this.removalQueue.add(blockSystem.getId());
            }
        }

        synchronized (this.lock) {
            for (int system : this.removalQueue) {
                this.blockSystems.remove(system);
                BlockSystemSavedData.get(this.world).removeBlockSystem(system);
            }

            this.removalQueue.clear();
        }

        for (BlockSystemInteractionHandler interactionHandler : this.interactionHandlers.values()) {
            interactionHandler.update();
        }
    }

    @Override
    public void addBlockSystem(BlockSystem blockSystem) {
        if (!(blockSystem instanceof BlockSystemServer)) {
            BlockSystems.LOGGER.warn("Tried to add non-server blocksystem ({}) to server handler!", blockSystem);
            return;
        }
        this.blockSystems.put(blockSystem.getId(), blockSystem);
        BlockSystemSavedData.get(this.world).addBlockSystem((BlockSystemServer) blockSystem);
    }

    @Override
    public void loadBlockSystem(BlockSystem blockSystem) {
        this.blockSystems.put(blockSystem.getId(), blockSystem);
    }

    @Override
    public void removeBlockSystem(BlockSystem blockSystem) {
        this.removeBlockSystem(blockSystem.getId());
    }

    @Override
    public void removeBlockSystem(int id) {
        synchronized (this.lock) {
            this.removalQueue.add(id);
        }
    }

    @Nullable
    @Override
    public BlockSystem getBlockSystem(int id) {
        return this.blockSystems.get(id);
    }

    @Override
    public Collection<BlockSystem> getBlockSystems() {
        return this.blockSystems.values();
    }

    @Override
    public void addPlayer(EntityPlayer player) {
        this.interactionHandlers.put(player, new ServerBlockSystemInteractionHandler((EntityPlayerMP) player));
    }

    @Override
    public void removePlayer(EntityPlayer player) {
        this.interactionHandlers.remove(player);
        if (player instanceof EntityPlayerMP) {
            for (BlockSystem blockSystem : this.blockSystems.values()) {
                // TODO: Abstract this?
                if (blockSystem instanceof BlockSystemServer) {
                    ((BlockSystemServer) blockSystem).getChunkTracker().removePlayer((EntityPlayerMP) player);
                }
            }
        }
    }

    @Nullable
    @Override
    public BlockSystemInteractionHandler getInteractionHandler(EntityPlayer player) {
        return this.interactionHandlers.get(player);
    }

    @Override
    public void unload() {
        this.blockSystems.clear();
        this.interactionHandlers.clear();
    }
}
