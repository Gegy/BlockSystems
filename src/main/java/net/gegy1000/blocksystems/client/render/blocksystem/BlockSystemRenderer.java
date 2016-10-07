package net.gegy1000.blocksystems.client.render.blocksystem;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.render.blocksystem.chunk.BlockSystemRenderChunk;
import net.gegy1000.blocksystems.client.render.blocksystem.chunk.VBORenderChunkContainer;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.gegy1000.blocksystems.client.render.blocksystem.chunk.BlockSystemChunkRenderDispatcher;
import net.gegy1000.blocksystems.client.render.blocksystem.chunk.BlockSystemRenderChunkContainer;
import net.gegy1000.blocksystems.client.render.blocksystem.chunk.ListedBlockSystemRenderChunk;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemPlayerHandler;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;

import javax.vecmath.Point3d;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class BlockSystemRenderer implements IWorldEventListener {
    private static final Minecraft MC = Minecraft.getMinecraft();

    private BlockSystem blockSystem;

    private double lastViewEntityX = Double.MIN_VALUE;
    private double lastViewEntityY = Double.MIN_VALUE;
    private double lastViewEntityZ = Double.MIN_VALUE;
    private double lastViewEntityPitch = Double.MIN_VALUE;
    private double lastViewEntityYaw = Double.MIN_VALUE;

    private double frustumUpdatePosX = Double.MIN_VALUE;
    private double frustumUpdatePosY = Double.MIN_VALUE;
    private double frustumUpdatePosZ = Double.MIN_VALUE;
    private int frustumUpdatePosChunkX = Integer.MIN_VALUE;
    private int frustumUpdatePosChunkY = Integer.MIN_VALUE;
    private int frustumUpdatePosChunkZ = Integer.MIN_VALUE;

    private BlockSystemViewFrustum viewFrustum;

    private final Set<TileEntity> blockEntities = Sets.newHashSet();
    private Set<BlockSystemRenderChunk> queuedChunkUpdates = Sets.newLinkedHashSet();
    private List<ChunkRenderInformation> chunkRenderInformation = Lists.newArrayListWithCapacity(0x11040);

    private boolean displayListEntitiesDirty = true;

    private BlockSystemChunkRenderDispatcher renderDispatcher;
    private BlockSystemRenderChunkContainer chunkContainer;

    private int viewDistance;
    private boolean vbosEnabled;

    private double prevRenderSortX;
    private double prevRenderSortY;
    private double prevRenderSortZ;

    private int frameCount;

    public BlockSystemRenderer(BlockSystem blockSystem) {
        this.blockSystem = blockSystem;
        this.viewDistance = -1;
        this.chunkContainer = new VBORenderChunkContainer();
        this.renderDispatcher = new BlockSystemChunkRenderDispatcher();
        this.updateFrustrum(this.getUntransformedPosition(MC.thePlayer), MC.gameSettings.renderDistanceChunks, OpenGlHelper.useVbo());
        blockSystem.addEventListener(this);
    }

    public void renderBlockSystem(Entity viewEntity, double x, double y, double z, float rotationX, float rotationY, float rotationZ, float partialTicks, long finishTimeNano) {
        Point3d untransformed = this.getUntransformedPosition(viewEntity);

        this.setup(viewEntity, partialTicks, untransformed);
        this.updateChunks(finishTimeNano);

        MC.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(rotationY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rotationX, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(rotationZ, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(-0.5, 0.0, -0.5);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.pushMatrix();
        GlStateManager.disableAlpha();
        this.renderBlockLayer(BlockRenderLayer.SOLID, untransformed);
        GlStateManager.enableAlpha();
        this.renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED, untransformed);
        MC.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
        this.renderBlockLayer(BlockRenderLayer.CUTOUT, untransformed);
        MC.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
        GlStateManager.popMatrix();

        /*GlStateManager.disableLighting();
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            for (BlockSystemRenderChunk chunk : this.viewFrustum.chunks) {
                if (!chunk.isEmpty()) {
                    BlockPos chunkPosition = chunk.getPosition();
                    int chunkX = chunkPosition.getX() << 4;
                    int chunkY = chunkPosition.getY() << 4;
                    int chunkZ = chunkPosition.getZ() << 4;
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(chunkX - 0.5, chunkY, chunkZ - 0.5);
                    chunk.renderLayer(layer);
                    GlStateManager.popMatrix();
                }
            }
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(-0.5, 0.0, -0.5);
        for (BlockSystemRenderChunk chunk : this.viewFrustum.chunks) {
            chunk.render(partialTicks);
        }
        GlStateManager.popMatrix();*/
        GlStateManager.enableLighting();
        RenderHelper.enableStandardItemLighting();
        ServerBlockSystemHandler structureHandler = BlockSystems.PROXY.getBlockSystemHandler(this.blockSystem.getMainWorld());
        if (viewEntity instanceof EntityPlayer && structureHandler.getMousedOver((EntityPlayer) viewEntity) == this.blockSystem) {
            BlockSystemPlayerHandler handler = structureHandler.get(this.blockSystem, MC.thePlayer);
            if (handler != null) {
                RayTraceResult result = handler.getMouseOver();
                BlockPos pos = result.getBlockPos();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                GlStateManager.glLineWidth(2.0F);
                GlStateManager.disableTexture2D();
                GlStateManager.depthMask(false);
                IBlockState state = this.blockSystem.getBlockState(pos);
                if (state.getMaterial() != Material.AIR) {
                    RenderGlobal.drawSelectionBoundingBox(state.getSelectedBoundingBox(this.blockSystem, pos).expandXyz(0.002), 0.0F, 0.0F, 0.0F, 0.4F);
                }
                GlStateManager.depthMask(true);
                GlStateManager.enableTexture2D();
                GlStateManager.disableBlend();
            }
        }
        GlStateManager.popMatrix();
    }

    private void setup(Entity viewEntity, float partialTicks, Point3d untransformed) {
        boolean playerSpectator = viewEntity instanceof EntityPlayer && ((EntityPlayer) viewEntity).isSpectator();

        int frameCount = this.frameCount++;

        int viewDistance = MC.gameSettings.renderDistanceChunks;
        boolean vbos = OpenGlHelper.useVbo();
        this.updateFrustrum(untransformed, viewDistance, vbos);

        double deltaX = untransformed.x - this.frustumUpdatePosX;
        double deltaY = untransformed.y - this.frustumUpdatePosY;
        double deltaZ = untransformed.z - this.frustumUpdatePosZ;
        double delta = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;

        if (this.frustumUpdatePosChunkX != viewEntity.chunkCoordX || this.frustumUpdatePosChunkY != viewEntity.chunkCoordY || this.frustumUpdatePosChunkZ != viewEntity.chunkCoordZ || delta > 16.0D) {
            this.frustumUpdatePosX = untransformed.x;
            this.frustumUpdatePosY = untransformed.y;
            this.frustumUpdatePosZ = untransformed.z;
            this.frustumUpdatePosChunkX = (int) (untransformed.x) >> 4;
            this.frustumUpdatePosChunkY = (int) (untransformed.y) >> 4;
            this.frustumUpdatePosChunkZ = (int) (untransformed.z) >> 4;
            this.viewFrustum.updateChunkPositions(untransformed.x, untransformed.z);
        }

        Point3d untransformedLastTick = this.getUntransformedPosition(viewEntity.lastTickPosX, viewEntity.lastTickPosY, viewEntity.lastTickPosZ);

        double viewRenderX = untransformedLastTick.x + (untransformed.x - untransformedLastTick.x) * partialTicks;
        double viewRenderY = untransformedLastTick.y + (untransformed.y - untransformedLastTick.y) * partialTicks;
        double viewRenderZ = untransformedLastTick.z + (untransformed.z - untransformedLastTick.z) * partialTicks;
        this.chunkContainer.initialize();

        BlockPos eyePosition = new BlockPos(viewRenderX, viewRenderY + viewEntity.getEyeHeight(), viewRenderZ);
        BlockSystemRenderChunk eyeChunk = this.viewFrustum.getChunk(eyePosition);
        BlockPos viewChunkCorner = new BlockPos(MathHelper.floor_double(viewRenderX / 16.0D) * 16, MathHelper.floor_double(viewRenderY / 16.0D) * 16, MathHelper.floor_double(viewRenderZ / 16.0D) * 16);
        this.displayListEntitiesDirty = this.displayListEntitiesDirty || !this.queuedChunkUpdates.isEmpty() || untransformed.x != this.lastViewEntityX || untransformed.y != this.lastViewEntityY || untransformed.z != this.lastViewEntityZ || (double) viewEntity.rotationPitch != this.lastViewEntityPitch || (double) viewEntity.rotationYaw != this.lastViewEntityYaw;
        this.lastViewEntityX = untransformed.x;
        this.lastViewEntityY = untransformed.y;
        this.lastViewEntityZ = untransformed.z;
        this.lastViewEntityPitch = (double) viewEntity.rotationPitch;
        this.lastViewEntityYaw = (double) viewEntity.rotationYaw;
        if (this.displayListEntitiesDirty) {
            this.displayListEntitiesDirty = false;
            this.chunkRenderInformation = Lists.newArrayList();
            Queue<ChunkRenderInformation> queue = Queues.newArrayDeque();
            Entity.setRenderDistanceWeight(MathHelper.clamp_double(this.viewDistance / 8.0D, 1.0D, 2.5D));
            boolean renderChunksMany = MC.renderChunksMany;
            if (eyeChunk != null) {
                boolean hidden = false;
                ChunkRenderInformation chunkInformation = new ChunkRenderInformation(eyeChunk, null, 0);
                Set<EnumFacing> visibleFacings = this.getVisibleFacings(eyePosition);
                if (visibleFacings.size() == 1) {
                    Vector3f viewVector = this.getViewVector(viewEntity, partialTicks);
                    EnumFacing facing = EnumFacing.getFacingFromVector(viewVector.x, viewVector.y, viewVector.z).getOpposite();
                    visibleFacings.remove(facing);
                }
                if (visibleFacings.isEmpty()) {
                    hidden = true;
                }
                if (hidden && !playerSpectator) {
                    this.chunkRenderInformation.add(chunkInformation);
                } else {
                    if (playerSpectator && this.blockSystem.getBlockState(eyePosition).isOpaqueCube()) {
                        renderChunksMany = false;
                    }
                    eyeChunk.setFrameIndex(frameCount);
                    queue.add(chunkInformation);
                }
            } else {
                int chunkY = eyePosition.getY() > 0 ? 248 : 8;
                for (int chunkX = -this.viewDistance; chunkX <= this.viewDistance; ++chunkX) {
                    for (int chunkZ = -this.viewDistance; chunkZ <= this.viewDistance; ++chunkZ) {
                        BlockSystemRenderChunk chunk = this.viewFrustum.getChunk(new BlockPos((chunkX << 4) + 8, chunkY, (chunkZ << 4) + 8));
                        if (chunk != null/* && ((ICamera) camera).isBoundingBoxInFrustum(chunk.boundingBox)*/) { //TODO Camera Culling
                            chunk.setFrameIndex(frameCount);
                            queue.add(new ChunkRenderInformation(chunk, null, 0));
                        }
                    }
                }
            }
            while (!queue.isEmpty()) {
                ChunkRenderInformation renderInformation = queue.poll();
                BlockSystemRenderChunk chunk = renderInformation.chunk;
                EnumFacing facing = renderInformation.facing;
                this.chunkRenderInformation.add(renderInformation);
                for (EnumFacing offset : EnumFacing.values()) {
                    BlockSystemRenderChunk offsetChunk = this.getOffsetChunk(viewChunkCorner, chunk, offset);
                    if ((!renderChunksMany || !renderInformation.hasDirection(offset.getOpposite())) && (!renderChunksMany || facing == null || chunk.getCompiledChunk().isVisible(facing.getOpposite(), offset)) && offsetChunk != null && offsetChunk.setFrameIndex(frameCount)/* && ((ICamera) camera).isBoundingBoxInFrustum(offsetChunk.boundingBox)*/) { //TODO Camera culling
                        ChunkRenderInformation offsetRenderInformation = new ChunkRenderInformation(offsetChunk, offset, renderInformation.index + 1);
                        offsetRenderInformation.setDirection(renderInformation.setFacing, offset);
                        queue.add(offsetRenderInformation);
                    }
                }
            }
        }

        Set<BlockSystemRenderChunk> newQueuedChunkUpdates = this.queuedChunkUpdates;
        this.queuedChunkUpdates = Sets.newLinkedHashSet();
        for (ChunkRenderInformation renderInformation : this.chunkRenderInformation) {
            BlockSystemRenderChunk chunk = renderInformation.chunk;
            if (chunk.isNeedsUpdate() || newQueuedChunkUpdates.contains(chunk)) {
                this.displayListEntitiesDirty = true;
                BlockPos centerPos = chunk.getPosition().add(8, 8, 8);
                boolean distance = centerPos.distanceSq(eyePosition) < 768.0D;
                if (!chunk.doesNeedUpdateNow() && !distance) {
                    this.queuedChunkUpdates.add(chunk);
                } else {
                    this.renderDispatcher.updateChunkNow(chunk);
                    chunk.clearNeedsUpdate();
                }
            }
        }
        this.queuedChunkUpdates.addAll(newQueuedChunkUpdates);
    }

    private void updateFrustrum(Point3d untransformed, int viewDistance, boolean vbos) {
        if (this.viewDistance != viewDistance || this.vbosEnabled != vbos) {
            this.viewDistance = viewDistance;
            this.vbosEnabled = vbos;
            if (this.viewFrustum != null) {
                this.viewFrustum.delete();
            }
            this.viewFrustum = new BlockSystemViewFrustum(this, this.blockSystem, viewDistance, vbos ? BlockSystemRenderChunk::new : ListedBlockSystemRenderChunk::new);
            this.viewFrustum.updateChunkPositions(untransformed.x, untransformed.z);
        }
    }

    public int renderBlockLayer(BlockRenderLayer layer, Point3d untransformed) {
        RenderHelper.disableStandardItemLighting();
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            double deltaX = untransformed.x - this.prevRenderSortX;
            double deltaY = untransformed.y - this.prevRenderSortY;
            double deltaZ = untransformed.z - this.prevRenderSortZ;
            if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > 1.0D) {
                this.prevRenderSortX = untransformed.x;
                this.prevRenderSortY = untransformed.y;
                this.prevRenderSortZ = untransformed.z;
                int count = 0;
                for (ChunkRenderInformation renderInformation : this.chunkRenderInformation) {
                    if (renderInformation.chunk.compiledChunk.isLayerStarted(layer) && count++ < 15) {
                        this.renderDispatcher.updateTransparencyLater(renderInformation.chunk);
                    }
                }
            }
        }
        int count = 0;
        boolean isTranslucent = layer == BlockRenderLayer.TRANSLUCENT;
        int start = isTranslucent ? this.chunkRenderInformation.size() - 1 : 0;
        int target = isTranslucent ? -1 : this.chunkRenderInformation.size();
        int increment = isTranslucent ? -1 : 1;
        for (int i = start; i != target; i += increment) {
            BlockSystemRenderChunk chunk = this.chunkRenderInformation.get(i).chunk;
            if (!chunk.getCompiledChunk().isLayerEmpty(layer)) {
                ++count;
                this.chunkContainer.addChunk(chunk);
            }
        }
        this.renderBlockLayer(layer);
        return count;
    }

    private void renderBlockLayer(BlockRenderLayer layer) {
        MC.entityRenderer.enableLightmap();
        if (OpenGlHelper.useVbo()) {
            GlStateManager.glEnableClientState(32884);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.glEnableClientState(32888);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.glEnableClientState(32888);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.glEnableClientState(32886);
        }
        this.chunkContainer.renderLayer(layer);
        if (OpenGlHelper.useVbo()) {
            for (VertexFormatElement element : DefaultVertexFormats.BLOCK.getElements()) {
                VertexFormatElement.EnumUsage usage = element.getUsage();
                int index = element.getIndex();
                switch (usage) {
                    case POSITION:
                        GlStateManager.glDisableClientState(32884);
                        break;
                    case UV:
                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + index);
                        GlStateManager.glDisableClientState(32888);
                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                        break;
                    case COLOR:
                        GlStateManager.glDisableClientState(32886);
                        GlStateManager.resetColor();
                }
            }
        }
        MC.entityRenderer.disableLightmap();
    }

    public void queueRenderUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean requiresUpdate) {
        this.viewFrustum.queueRenderUpdate(minX, minY, minZ, maxX, maxY, maxZ, requiresUpdate);
    }

    public void deleteChunk(int xPosition, int zPosition) {
        int x = xPosition << 4;
        int z = zPosition << 4;
        this.queueRenderUpdate(x, 0, z, x + 16, 256, z + 16, true);
    }

    public void queueChunkRenderUpdate(int xPosition, int zPosition) {
        int x = xPosition << 4;
        int z = zPosition << 4;
        this.queueRenderUpdate(x, 0, z, x + 16, 256, z + 16, false);
    }

    public void updateChunks(long finishTimeNano) {
        this.displayListEntitiesDirty |= this.renderDispatcher.runChunkUploads(finishTimeNano);
        if (!this.queuedChunkUpdates.isEmpty()) {
            Iterator<BlockSystemRenderChunk> iterator = this.queuedChunkUpdates.iterator();
            while (iterator.hasNext()) {
                BlockSystemRenderChunk chunk = iterator.next();
                boolean updated;
                if (chunk.doesNeedUpdateNow()) {
                    updated = this.renderDispatcher.updateChunkNow(chunk);
                } else {
                    updated = this.renderDispatcher.updateChunkLater(chunk);
                }
                if (!updated) {
                    break;
                }
                chunk.clearNeedsUpdate();
                iterator.remove();
                long timeLeft = finishTimeNano - System.nanoTime();
                if (timeLeft < 0) {
                    break;
                }
            }
        }
    }

    public void delete() {
        this.viewFrustum.delete();
        this.renderDispatcher.stopWorkerThreads();
    }

    public void updateTileEntities(Set<TileEntity> remove, Set<TileEntity> add) {
        synchronized (this.blockEntities) {
            this.blockEntities.removeAll(remove);
            this.blockEntities.addAll(add);
        }
    }

    private Set<EnumFacing> getVisibleFacings(BlockPos pos) {
        VisGraph visibility = new VisGraph();
        BlockPos cornerPos = new BlockPos(pos.getX() >> 4 << 4, pos.getY() >> 4 << 4, pos.getZ() >> 4 << 4);
        Chunk chunk = this.blockSystem.getChunkFromBlockCoords(cornerPos);
        for (BlockPos.MutableBlockPos blockPos : BlockPos.getAllInBoxMutable(cornerPos, cornerPos.add(15, 15, 15))) {
            if (chunk.getBlockState(blockPos).isOpaqueCube()) {
                visibility.setOpaqueCube(blockPos);
            }
        }
        return visibility.getVisibleFacings(pos);
    }

    private BlockSystemRenderChunk getOffsetChunk(BlockPos pos, BlockSystemRenderChunk chunk, EnumFacing facing) {
        BlockPos offset = chunk.getOffset(facing);
        if (!(MathHelper.abs_int(pos.getX() - offset.getX()) > this.viewDistance * 16) && offset.getY() >= 0 && offset.getY() < 256) {
            return this.viewFrustum.getChunk(offset);
        } else {
            return null;
        }
    }

    private Vector3f getViewVector(Entity entity, double partialTicks) {
        float pitch = (float) (entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks);
        float yaw = (float) (entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks);
        if (MC.gameSettings.thirdPersonView == 2) {
            pitch += 180.0F;
        }
        float z = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float x = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float xzScale = -MathHelper.cos(-pitch * 0.017453292F);
        float y = MathHelper.sin(-pitch * 0.017453292F);
        Vec3d vec = this.blockSystem.getTransformedVector(new Vec3d(x * xzScale, y, z * xzScale));
        return new Vector3f((float) vec.xCoord, (float) vec.yCoord, (float) vec.zCoord);
    }

    private Point3d getUntransformedPosition(Entity entity) {
        return this.getUntransformedPosition(entity.posX, entity.posY, entity.posZ);
    }

    private Point3d getUntransformedPosition(double x, double y, double z) {
        return this.blockSystem.getUntransformedPosition(new Point3d(x, y, z));
    }

    @Override
    public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
    }

    @Override
    public void notifyLightSet(BlockPos pos) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.queueRenderUpdate(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1, false);
    }

    @Override
    public void playSoundToAllNearExcept(EntityPlayer player, SoundEvent sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
    }

    @Override
    public void playRecord(SoundEvent sound, BlockPos pos) {
    }

    @Override
    public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
    }

    @Override
    public void onEntityAdded(Entity entityIn) {
    }

    @Override
    public void onEntityRemoved(Entity entityIn) {
    }

    @Override
    public void broadcastSound(int soundID, BlockPos pos, int data) {
    }

    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
    }

    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
    }

    @SideOnly(Side.CLIENT)
    private class ChunkRenderInformation {
        private final BlockSystemRenderChunk chunk;
        private final EnumFacing facing;
        private byte setFacing;
        private final int index;

        private ChunkRenderInformation(BlockSystemRenderChunk chunk, EnumFacing facing, int index) {
            this.chunk = chunk;
            this.facing = facing;
            this.index = index;
        }

        public void setDirection(byte setFacing, EnumFacing facing) {
            this.setFacing = (byte) (this.setFacing | setFacing | 1 << facing.ordinal());
        }

        public boolean hasDirection(EnumFacing facing) {
            return (this.setFacing & 1 << facing.ordinal()) > 0;
        }
    }
}
