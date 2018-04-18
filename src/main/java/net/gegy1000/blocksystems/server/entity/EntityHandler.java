package net.gegy1000.blocksystems.server.entity;

import net.gegy1000.blocksystems.BlockSystems;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

@Mod.EventBusSubscriber(modid = BlockSystems.MODID)
public class EntityHandler {
    @SubscribeEvent
    public static void onRegisterEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.create()
                .name("block_system_control")
                .entity(BlockSystemControlEntity.class)
                .id(new ResourceLocation(BlockSystems.MODID, "block_system_control"), 0)
                .tracker(1024, 1, true)
                .build());
    }
}
