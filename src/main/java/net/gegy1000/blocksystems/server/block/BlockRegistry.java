package net.gegy1000.blocksystems.server.block;

import net.gegy1000.blocksystems.BlockSystems;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mod.EventBusSubscriber(modid = BlockSystems.MODID)
public class BlockRegistry {
    private static final List<Block> BLOCKS = new ArrayList<>();

    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        BlockRegistry.register(event, new BlockSystemBlock(), new ResourceLocation(BlockSystems.MODID, "block_system"));
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        for (Block block : BLOCKS) {
            ItemBlock item = new ItemBlock(block);
            item.setRegistryName(block.getRegistryName());
            event.getRegistry().register(item);
        }
    }

    private static void register(RegistryEvent.Register<Block> event, Block block, ResourceLocation identifier) {
        block.setUnlocalizedName(identifier.getResourceDomain() + "." + identifier.getResourcePath());
        block.setRegistryName(identifier);
        event.getRegistry().register(block);
        BLOCKS.add(block);
    }

    public static List<Block> getRegisteredBlocks() {
        return Collections.unmodifiableList(BLOCKS);
    }
}
