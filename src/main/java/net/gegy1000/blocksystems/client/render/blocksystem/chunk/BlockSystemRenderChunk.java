package net.gegy1000.blocksystems.client.render.blocksystem.chunk;

import com.google.common.collect.Sets;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderer;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@SideOnly(Side.CLIENT)
public class BlockSystemRenderChunk {
    private BlockSystem blockSystem;
    private final BlockSystemRenderer renderer;
    public CompiledChunk compiledChunk = CompiledChunk.DUMMY;
    private final ReentrantLock lockCompileTask = new ReentrantLock();
    private final ReentrantLock lockCompiledChunk = new ReentrantLock();
    private BlockSystemChunkCompileTaskGenerator compileTask;
    private final Set<TileEntity> blockEntities = Sets.newHashSet();
    private final FloatBuffer viewMatrix = GLAllocation.createDirectFloatBuffer(16);
    private final VertexBuffer[] layerBuffers = new VertexBuffer[BlockRenderLayer.values().length];
    public AxisAlignedBB boundingBox;
    private int frameIndex = -1;
    private boolean needsUpdate = true;
    private BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(-1, -1, -1);
    private BlockPos.MutableBlockPos[] offsetSides = new BlockPos.MutableBlockPos[6];
    private boolean needsUpdateCustom;
    private ChunkCache region;

    public BlockSystemRenderChunk(BlockSystem blockSystem, BlockSystemRenderer renderer) {
        for (int i = 0; i < this.offsetSides.length; ++i) {
            this.offsetSides[i] = new BlockPos.MutableBlockPos();
        }
        this.blockSystem = blockSystem;
        this.renderer = renderer;
        if (OpenGlHelper.useVbo()) {
            for (int i = 0; i < BlockRenderLayer.values().length; ++i) {
                this.layerBuffers[i] = new VertexBuffer(DefaultVertexFormats.BLOCK);
            }
        }
    }

    public boolean setFrameIndex(int frameIndex) {
        if (this.frameIndex == frameIndex) {
            return false;
        } else {
            this.frameIndex = frameIndex;
            return true;
        }
    }

    public VertexBuffer getLayerBuilder(int layer) {
        return this.layerBuffers[layer];
    }

    public void setPosition(int x, int y, int z) {
        if (x != this.position.getX() || y != this.position.getY() || z != this.position.getZ()) {
            this.stopCompileTask();
            this.position.setPos(x, y, z);
            this.boundingBox = new AxisAlignedBB(x, y, z, x + 16, y + 16, z + 16);
            for (EnumFacing facing : EnumFacing.values()) {
                this.offsetSides[facing.ordinal()].setPos(this.position).move(facing, 16);
            }
            this.initViewMatrix();
        }
    }

    public void resortTransparency(float x, float y, float z, BlockSystemChunkCompileTaskGenerator generator) {
        CompiledChunk compiledChunk = generator.getCompiledChunk();
        if (compiledChunk.getState() != null && !compiledChunk.isLayerEmpty(BlockRenderLayer.TRANSLUCENT)) {
            this.preRenderBlocks(generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(BlockRenderLayer.TRANSLUCENT), this.position);
            generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(BlockRenderLayer.TRANSLUCENT).setVertexState(compiledChunk.getState());
            this.postRenderBlocks(BlockRenderLayer.TRANSLUCENT, x, y, z, generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(BlockRenderLayer.TRANSLUCENT), compiledChunk);
        }
    }

    public void rebuildChunk(float x, float y, float z, BlockSystemChunkCompileTaskGenerator generator) {
        CompiledChunk compiledChunk = new CompiledChunk();
        BlockPos lowerCorner = this.position;
        BlockPos upperCorner = lowerCorner.add(15, 15, 15);
        generator.getLock().lock();
        try {
            if (generator.getStatus() != BlockSystemChunkCompileTaskGenerator.Status.COMPILING) {
                return;
            }
            generator.setCompiledChunk(compiledChunk);
        } finally {
            generator.getLock().unlock();
        }
        VisGraph visibilityGraph = new VisGraph();
        HashSet<TileEntity> blockEntities = Sets.newHashSet();
        if (!this.region.extendedLevelsInChunkCache()) {
            boolean[] layerVisibilities = new boolean[BlockRenderLayer.values().length];
            BlockRendererDispatcher renderDispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
            for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(lowerCorner, upperCorner)) {
                IBlockState state = this.region.getBlockState(pos);
                Block block = state.getBlock();
                if (state.isOpaqueCube()) {
                    visibilityGraph.setOpaqueCube(pos);
                }
                if (block.hasTileEntity(state)) {
                    TileEntity blockEntity = this.region.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
                    if (blockEntity != null) {
                        TileEntitySpecialRenderer<TileEntity> specialRenderer = TileEntityRendererDispatcher.instance.getSpecialRenderer(blockEntity);
                        if (specialRenderer != null) {
                            compiledChunk.addTileEntity(blockEntity);
                            if (specialRenderer.isGlobalRenderer(blockEntity)) {
                                blockEntities.add(blockEntity);
                            }
                        }
                    }
                }
                for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                    if (!block.canRenderInLayer(state, layer)) {
                        continue;
                    }
                    ForgeHooksClient.setRenderLayer(layer);
                    int layerIndex = layer.ordinal();
                    if (block.getDefaultState().getRenderType() != EnumBlockRenderType.INVISIBLE) {
                        net.minecraft.client.renderer.VertexBuffer builder = generator.getRegionRenderCacheBuilder().getWorldRendererByLayerId(layerIndex);
                        if (!compiledChunk.isLayerStarted(layer)) {
                            compiledChunk.setLayerStarted(layer);
                            this.preRenderBlocks(builder, lowerCorner);
                        }
                        layerVisibilities[layerIndex] |= renderDispatcher.renderBlock(state, pos, this.region, builder);
                    }
                }
                ForgeHooksClient.setRenderLayer(null);
            }
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (layerVisibilities[layer.ordinal()]) {
                    compiledChunk.setLayerUsed(layer);
                }
                if (compiledChunk.isLayerStarted(layer)) {
                    this.postRenderBlocks(layer, x, y, z, generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(layer), compiledChunk);
                }
            }
        }
        compiledChunk.setVisibility(visibilityGraph.computeVisibility());
        this.lockCompileTask.lock();
        try {
            Set<TileEntity> newBlockEntities = Sets.newHashSet(blockEntities);
            Set<TileEntity> oldBlockEntities = Sets.newHashSet(this.blockEntities);
            newBlockEntities.removeAll(this.blockEntities);
            oldBlockEntities.removeAll(blockEntities);
            this.blockEntities.clear();
            this.blockEntities.addAll(blockEntities);
            this.renderer.updateTileEntities(oldBlockEntities, newBlockEntities);
        } finally {
            this.lockCompileTask.unlock();
        }
    }

    protected void finishCompileTask() {
        this.lockCompileTask.lock();
        try {
            if (this.compileTask != null && this.compileTask.getStatus() != BlockSystemChunkCompileTaskGenerator.Status.DONE) {
                this.compileTask.finish();
                this.compileTask = null;
            }
        } finally {
            this.lockCompileTask.unlock();
        }
    }

    public ReentrantLock getLockCompileTask() {
        return this.lockCompileTask;
    }

    public BlockSystemChunkCompileTaskGenerator makeCompileTaskChunk() {
        this.lockCompileTask.lock();
        BlockSystemChunkCompileTaskGenerator generator;
        try {
            this.finishCompileTask();
            this.compileTask = new BlockSystemChunkCompileTaskGenerator(this, BlockSystemChunkCompileTaskGenerator.Type.REBUILD_CHUNK, this.getDistanceSq());
            this.resetChunkCache();
            generator = this.compileTask;
        } finally {
            this.lockCompileTask.unlock();
        }

        return generator;
    }

    private void resetChunkCache() {
        ChunkCache cache = new ChunkCache(this.blockSystem, this.position.add(-1, -1, -1), this.position.add(16, 16, 16), 1);
        MinecraftForgeClient.onRebuildChunk(this.blockSystem, this.position, cache);
        this.region = cache;
    }

    @Nullable
    public BlockSystemChunkCompileTaskGenerator makeCompileTaskTransparency() {
        this.lockCompileTask.lock();
        BlockSystemChunkCompileTaskGenerator generator;
        try {
            if (this.compileTask == null || this.compileTask.getStatus() != BlockSystemChunkCompileTaskGenerator.Status.PENDING) {
                if (this.compileTask != null && this.compileTask.getStatus() != BlockSystemChunkCompileTaskGenerator.Status.DONE) {
                    this.compileTask.finish();
                    this.compileTask = null;
                }
                this.compileTask = new BlockSystemChunkCompileTaskGenerator(this, BlockSystemChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY, this.getDistanceSq());
                this.compileTask.setCompiledChunk(this.compiledChunk);
                generator = this.compileTask;
                return generator;
            }
            generator = null;
        } finally {
            this.lockCompileTask.unlock();
        }
        return generator;
    }

    protected double getDistanceSq() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        double deltaX = this.boundingBox.minX + 8.0D - player.posX;
        double deltaY = this.boundingBox.minY + 8.0D - player.posY;
        double deltaZ = this.boundingBox.minZ + 8.0D - player.posZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private void preRenderBlocks(net.minecraft.client.renderer.VertexBuffer builder, BlockPos pos) {
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        builder.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());
    }

    private void postRenderBlocks(BlockRenderLayer layer, float x, float y, float z, net.minecraft.client.renderer.VertexBuffer builder, CompiledChunk compiledChunk) {
        if (layer == BlockRenderLayer.TRANSLUCENT && !compiledChunk.isLayerEmpty(layer)) {
            builder.sortVertexData(x, y, z);
            compiledChunk.setState(builder.getVertexState());
        }
        builder.finishDrawing();
    }

    private void initViewMatrix() {
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        float scale = 1.000001F;
        GlStateManager.translate(-8.0F, -8.0F, -8.0F);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(8.0F, 8.0F, 8.0F);
        GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, this.viewMatrix);
        GlStateManager.popMatrix();
    }

    public void applyModelViewMatrix() {
        GlStateManager.multMatrix(this.viewMatrix);
    }

    public CompiledChunk getCompiledChunk() {
        return this.compiledChunk;
    }

    public void setCompiledChunk(CompiledChunk compiledChunk) {
        this.lockCompiledChunk.lock();
        try {
            this.compiledChunk = compiledChunk;
        } finally {
            this.lockCompiledChunk.unlock();
        }
    }

    public void stopCompileTask() {
        this.finishCompileTask();
        this.compiledChunk = CompiledChunk.DUMMY;
    }

    public void delete() {
        this.stopCompileTask();
        this.blockSystem = null;
        for (int i = 0; i < BlockRenderLayer.values().length; ++i) {
            if (this.layerBuffers[i] != null) {
                this.layerBuffers[i].deleteGlBuffers();
            }
        }
    }

    public BlockPos getPosition() {
        return this.position;
    }

    public void setNeedsUpdate(boolean needsUpdate) {
        if (this.needsUpdate) {
            needsUpdate |= this.needsUpdateCustom;
        }
        this.needsUpdate = true;
        this.needsUpdateCustom = needsUpdate;
    }

    public void clearNeedsUpdate() {
        this.needsUpdate = false;
        this.needsUpdateCustom = false;
    }

    public boolean isNeedsUpdate() {
        return this.needsUpdate;
    }

    public boolean doesNeedUpdateNow() {
        return this.needsUpdate && this.needsUpdateCustom;
    }

    public BlockPos getOffset(EnumFacing facing) {
        return this.offsetSides[facing.ordinal()];
    }

    public BlockSystem getBlockSystem() {
        return this.blockSystem;
    }
}