package net.gegy1000.blocksystems.client.render.blocksystem.chunk;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ListRenderChunkContainer extends BlockSystemRenderChunkContainer {
    @Override
    public void renderLayer(BlockRenderLayer layer) {
        if (this.initialized) {
            for (BlockSystemRenderChunk chunk : this.renderChunks) {
                ListedBlockSystemRenderChunk listedChunk = (ListedBlockSystemRenderChunk) chunk;
                GlStateManager.pushMatrix();
                this.preRender(chunk);
                GlStateManager.callList(listedChunk.getDisplayList(layer, listedChunk.getCompiledChunk()));
                GlStateManager.popMatrix();
            }
            GlStateManager.resetColor();
            this.renderChunks.clear();
        }
    }
}