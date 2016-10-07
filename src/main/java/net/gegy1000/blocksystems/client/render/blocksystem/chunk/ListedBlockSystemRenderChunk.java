package net.gegy1000.blocksystems.client.render.blocksystem.chunk;

import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderer;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ListedBlockSystemRenderChunk extends BlockSystemRenderChunk {
    private final int baseDisplayList = GLAllocation.generateDisplayLists(BlockRenderLayer.values().length);

    public ListedBlockSystemRenderChunk(BlockSystem blockSystem, BlockSystemRenderer renderer) {
        super(blockSystem, renderer);
    }

    public int getDisplayList(BlockRenderLayer layer, CompiledChunk compiledChunk) {
        return !compiledChunk.isLayerEmpty(layer) ? this.baseDisplayList + layer.ordinal() : -1;
    }

    @Override
    public void delete() {
        super.delete();
        GLAllocation.deleteDisplayLists(this.baseDisplayList, BlockRenderLayer.values().length);
    }
}