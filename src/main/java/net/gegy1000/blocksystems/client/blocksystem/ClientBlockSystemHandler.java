package net.gegy1000.blocksystems.client.blocksystem;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderHandler;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemHandler;
import net.gegy1000.blocksystems.server.blocksystem.interaction.BlockSystemInteractionHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Collection;

@SideOnly(Side.CLIENT)
public class ClientBlockSystemHandler implements BlockSystemHandler {
    private final Int2ObjectMap<BlockSystem> blockSystems = new Int2ObjectOpenHashMap<>();

    private final BlockSystemInteractionHandler interactionHandler;

    public ClientBlockSystemHandler(EntityPlayer player) {
        this.interactionHandler = new ClientBlockSystemInteractionHandler(player);
    }

    @Override
    public void update() {
        IntList removalQueue = new IntArrayList();
        for (BlockSystem blockSystem : this.blockSystems.values()) {
            if (!blockSystem.isRemoved()) {
                blockSystem.tick();
            } else {
                removalQueue.add(blockSystem.getId());
            }
        }

        for (int system : removalQueue) {
            this.blockSystems.remove(system);
        }

        this.interactionHandler.update();
    }

    @Override
    public void addBlockSystem(BlockSystem blockSystem) {
        if (!(blockSystem instanceof BlockSystemClient)) {
            BlockSystems.LOGGER.warn("Tried to add non-client blocksystem ({}) to client handler!", blockSystem);
            return;
        }

        this.blockSystems.put(blockSystem.getId(), blockSystem);
        BlockSystemRenderHandler.addBlockSystem((BlockSystemClient) blockSystem);
    }

    @Override
    public void removeBlockSystem(BlockSystem blockSystem) {
        this.removeBlockSystem(blockSystem.getId());
    }

    @Override
    public void removeBlockSystem(int id) {
        BlockSystem blockSystem = this.blockSystems.remove(id);
        if (blockSystem != null) {
            BlockSystemRenderHandler.removeBlockSystem(blockSystem);
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
    }

    @Override
    public void removePlayer(EntityPlayer player) {
    }

    @Nullable
    @Override
    public BlockSystemInteractionHandler getInteractionHandler(EntityPlayer player) {
        return this.interactionHandler;
    }

    @Override
    public void unload() {
        BlockSystemRenderHandler.unload();
    }
}
