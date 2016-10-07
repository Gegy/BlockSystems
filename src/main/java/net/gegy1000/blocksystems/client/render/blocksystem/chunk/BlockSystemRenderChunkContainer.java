package net.gegy1000.blocksystems.client.render.blocksystem.chunk;

import com.google.common.collect.Lists;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

@SideOnly(Side.CLIENT)
public abstract class BlockSystemRenderChunkContainer {
    protected List<BlockSystemRenderChunk> renderChunks = Lists.newArrayListWithCapacity(0x4410);
    protected boolean initialized;

    public void initialize() {
        this.initialized = true;
        this.renderChunks.clear();
    }

    public void preRender(BlockSystemRenderChunk chunk) {
        BlockPos pos = chunk.getPosition();
        GlStateManager.translate(pos.getX(), pos.getY(), pos.getZ());
    }

    public void addChunk(BlockSystemRenderChunk chunk) {
        this.renderChunks.add(chunk);
    }

    public abstract void renderLayer(BlockRenderLayer layer);
}