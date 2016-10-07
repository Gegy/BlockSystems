package net.gegy1000.blocksystems.client.render.blocksystem.chunk;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

@SideOnly(Side.CLIENT)
public class BlockSystemChunkRenderWorker implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger();
    private final BlockSystemChunkRenderDispatcher dispatcher;
    private final RegionRenderCacheBuilder regionCache;
    private boolean shouldRun;

    public BlockSystemChunkRenderWorker(BlockSystemChunkRenderDispatcher dispatcher) {
        this(dispatcher, null);
    }

    public BlockSystemChunkRenderWorker(BlockSystemChunkRenderDispatcher dispatcher, RegionRenderCacheBuilder regionCache) {
        this.shouldRun = true;
        this.dispatcher = dispatcher;
        this.regionCache = regionCache;
    }

    @Override
    public void run() {
        while (this.shouldRun) {
            try {
                this.processTask(this.dispatcher.getNextChunkUpdate());
            } catch (InterruptedException var3) {
                LOGGER.debug("Stopping chunk worker due to interrupt");
                return;
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Batching chunks");
                Minecraft.getMinecraft().crashed(Minecraft.getMinecraft().addGraphicsAndWorldToCrashReport(crashreport));
                return;
            }
        }
    }

    protected void processTask(final BlockSystemChunkCompileTaskGenerator generator) throws InterruptedException {
        generator.getLock().lock();
        try {
            if (generator.getStatus() != BlockSystemChunkCompileTaskGenerator.Status.PENDING) {
                if (!generator.isFinished()) {
                    LOGGER.warn("Chunk render task was {} when I expected it to be pending; ignoring task", generator.getStatus());
                }
                return;
            }
            BlockPos playerPos = new BlockPos(Minecraft.getMinecraft().thePlayer);
            BlockPos chunkPosition = generator.getChunk().getPosition();
            if (chunkPosition.add(8, 8, 8).distanceSq(playerPos) > 576.0D) {
                BlockSystem blockSystem = generator.getChunk().getBlockSystem();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(chunkPosition);
                if (!this.isChunkExisting(pos.setPos(chunkPosition).move(EnumFacing.WEST, 16), blockSystem) || !this.isChunkExisting(pos.setPos(chunkPosition).move(EnumFacing.NORTH, 16), blockSystem) || !this.isChunkExisting(pos.setPos(chunkPosition).move(EnumFacing.EAST, 16), blockSystem) || !this.isChunkExisting(pos.setPos(chunkPosition).move(EnumFacing.SOUTH, 16), blockSystem)) {
                    return;
                }
            }
            generator.setStatus(BlockSystemChunkCompileTaskGenerator.Status.COMPILING);
        } finally {
            generator.getLock().unlock();
        }

        Entity renderEntity = Minecraft.getMinecraft().getRenderViewEntity();

        if (renderEntity == null) {
            generator.finish();
        } else {
            generator.setRegionRenderCacheBuilder(this.getRegionCache());
            float x = (float) renderEntity.posX;
            float y = (float) renderEntity.posY + renderEntity.getEyeHeight();
            float z = (float) renderEntity.posZ;
            BlockSystemChunkCompileTaskGenerator.Type type = generator.getType();
            if (type == BlockSystemChunkCompileTaskGenerator.Type.REBUILD_CHUNK) {
                generator.getChunk().rebuildChunk(x, y, z, generator);
            } else if (type == BlockSystemChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY) {
                generator.getChunk().resortTransparency(x, y, z, generator);
            }
            generator.getLock().lock();
            try {
                if (generator.getStatus() != BlockSystemChunkCompileTaskGenerator.Status.COMPILING) {
                    if (!generator.isFinished()) {
                        LOGGER.warn("Chunk render task was {} when I expected it to be compiling; aborting task", generator.getStatus());
                    }
                    this.freeRenderBuilder(generator);
                    return;
                }
                generator.setStatus(BlockSystemChunkCompileTaskGenerator.Status.UPLOADING);
            } finally {
                generator.getLock().unlock();
            }
            final CompiledChunk compiledChunk = generator.getCompiledChunk();
            ArrayList<ListenableFuture<Object>> futures = Lists.newArrayList();
            if (type == BlockSystemChunkCompileTaskGenerator.Type.REBUILD_CHUNK) {
                for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                    if (compiledChunk.isLayerStarted(layer)) {
                        futures.add(this.dispatcher.uploadChunk(layer, generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(layer), generator.getChunk(), compiledChunk, generator.getDistance()));
                    }
                }
            } else if (type == BlockSystemChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY) {
                futures.add(this.dispatcher.uploadChunk(BlockRenderLayer.TRANSLUCENT, generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(BlockRenderLayer.TRANSLUCENT), generator.getChunk(), compiledChunk, generator.getDistance()));
            }
            final ListenableFuture<List<Object>> futuresList = Futures.allAsList(futures);
            generator.addFinishRunnable(() -> futuresList.cancel(false));
            Futures.addCallback(futuresList, new FutureCallback<List<Object>>() {
                @Override
                public void onSuccess(@Nullable List<Object> objects) {
                    BlockSystemChunkRenderWorker.this.freeRenderBuilder(generator);
                    generator.getLock().lock();
                    upload:
                    {
                        try {
                            if (generator.getStatus() == BlockSystemChunkCompileTaskGenerator.Status.UPLOADING) {
                                generator.setStatus(BlockSystemChunkCompileTaskGenerator.Status.DONE);
                                break upload;
                            }
                            if (!generator.isFinished()) {
                                BlockSystemChunkRenderWorker.LOGGER.warn("Chunk render task was {} when I expected it to be uploading; aborting task", generator.getStatus());
                            }
                        } finally {
                            generator.getLock().unlock();
                        }
                        return;
                    }
                    generator.getChunk().setCompiledChunk(compiledChunk);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    BlockSystemChunkRenderWorker.this.freeRenderBuilder(generator);
                    if (!(throwable instanceof CancellationException) && !(throwable instanceof InterruptedException)) {
                        Minecraft.getMinecraft().crashed(CrashReport.makeCrashReport(throwable, "Rendering chunk"));
                    }
                }
            });
        }
    }

    private boolean isChunkExisting(BlockPos pos, World world) {
        return !world.getChunkFromChunkCoords(pos.getX() >> 4, pos.getZ() >> 4).isEmpty();
    }

    private RegionRenderCacheBuilder getRegionCache() throws InterruptedException {
        return this.regionCache != null ? this.regionCache : this.dispatcher.allocateRenderBuilder();
    }

    private void freeRenderBuilder(BlockSystemChunkCompileTaskGenerator taskGenerator) {
        if (this.regionCache == null) {
            this.dispatcher.freeRenderBuilder(taskGenerator.getRegionRenderCacheBuilder());
        }
    }

    public void notifyToStop() {
        this.shouldRun = false;
    }
}