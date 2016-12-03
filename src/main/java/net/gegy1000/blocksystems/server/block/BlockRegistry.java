package net.gegy1000.blocksystems.server.block;

import net.gegy1000.blocksystems.BlockSystems;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;

public class BlockRegistry {
    public static final BlockSystemBlock BLOCK_SYSTEM = new BlockSystemBlock();

    public static final List<Block> BLOCKS = new ArrayList<>();

    public static void onPreInit() {
        BlockRegistry.register(BLOCK_SYSTEM, new ResourceLocation(BlockSystems.MODID, "block_system"));
    }

    private static void register(Block block, ResourceLocation identifier) {
        block.setUnlocalizedName(identifier.getResourcePath());
        GameRegistry.register(block, identifier);
        GameRegistry.register(new ItemBlock(block), identifier);
        BLOCKS.add(block);
    }

    private static void register(Block block, ResourceLocation identifier, String oreDict) {
        BlockRegistry.register(block, identifier);
        OreDictionary.registerOre(oreDict, block);
    }
}
