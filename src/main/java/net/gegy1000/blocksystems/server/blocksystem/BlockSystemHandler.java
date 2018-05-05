package net.gegy1000.blocksystems.server.blocksystem;

import net.gegy1000.blocksystems.server.blocksystem.interaction.BlockSystemInteractionHandler;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;
import java.util.Collection;

public interface BlockSystemHandler {
    void update();

    void addBlockSystem(BlockSystem blockSystem);

    default void loadBlockSystem(BlockSystem blockSystem) {
        this.addBlockSystem(blockSystem);
    }

    void removeBlockSystem(BlockSystem blockSystem);

    void removeBlockSystem(int id);

    @Nullable
    BlockSystem getBlockSystem(int id);

    Collection<BlockSystem> getBlockSystems();

    void addPlayer(EntityPlayer player);

    void removePlayer(EntityPlayer player);

    @Nullable
    BlockSystemInteractionHandler getInteractionHandler(EntityPlayer player);

    void unload();
}
