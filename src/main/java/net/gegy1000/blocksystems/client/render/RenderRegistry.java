package net.gegy1000.blocksystems.client.render;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.render.entity.BlockSystemControlRenderer;
import net.gegy1000.blocksystems.server.entity.BlockSystemControlEntity;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class RenderRegistry {
    private static final Minecraft MC = Minecraft.getMinecraft();

    public static void onPreInit() {
        RenderingRegistry.registerEntityRenderingHandler(BlockSystemControlEntity.class, BlockSystemControlRenderer::new);
    }

    public static void onInit() {
    }

    public static void onPostInit() {
    }

    private static void registerRenderer(Item item) {
        ModelResourceLocation resource = new ModelResourceLocation(BlockSystems.MODID + ":" + item.getUnlocalizedName().substring("item.".length()), "inventory");
        ModelLoader.setCustomModelResourceLocation(item, 0, resource);
    }

    private static void registerRenderer(Block block) {
        registerRenderer(Item.getItemFromBlock(block));
    }
}
