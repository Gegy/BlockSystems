package net.gegy1000.blocksystems.client.render;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.render.entity.BlockSystemControlRenderer;
import net.gegy1000.blocksystems.server.entity.BlockSystemControlEntity;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class RenderRegistry {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static ItemModelMesher MODEL_MESHER;

    public static void onPreInit() {
        RenderingRegistry.registerEntityRenderingHandler(BlockSystemControlEntity.class, BlockSystemControlRenderer::new);
    }

    public static void onInit() {
        MODEL_MESHER = MC.getRenderItem().getItemModelMesher();
    }

    public static void onPostInit() {
    }

    private static void registerRenderer(Item item) {
        MODEL_MESHER.register(item, stack -> new ModelResourceLocation(BlockSystems.MODID + ":" + item.getUnlocalizedName().substring("item.".length()), "inventory"));
    }

    private static void registerRenderer(Block block) {
        registerRenderer(Item.getItemFromBlock(block));
    }
}
