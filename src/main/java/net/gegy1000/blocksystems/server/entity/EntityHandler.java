package net.gegy1000.blocksystems.server.entity;

import net.gegy1000.blocksystems.BlockSystems;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public class EntityHandler {
    public static void onPreInit() {
        EntityRegistry.registerModEntity(BlockSystemControlEntity.class, "block_system_control", 0, BlockSystems.INSTANCE, 1024, 1, true);
    }
}
