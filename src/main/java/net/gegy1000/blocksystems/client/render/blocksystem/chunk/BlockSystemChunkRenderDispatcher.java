package net.gegy1000.blocksystems.client.render.blocksystem.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.primitives.Doubles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.VertexBufferUploader;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;

@SideOnly(Side.CLIENT)
public class BlockSystemChunkRenderDispatcher {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ThreadFactory THREAD_FACTORY = (new ThreadFactoryBuilder()).setNameFormat("Chunk Batcher %d").setDaemon(true).build();
    private final int renderBuilderCount;
    private final List<Thread> workerThreads = Lists.newArrayList();
    private final List<BlockSystemChunkRenderWorker> workers = Lists.newArrayList();
    private final PriorityBlockingQueue<BlockSystemChunkCompileTaskGenerator> queueChunkUpdates = Queues.newPriorityBlockingQueue();
    private final BlockingQueue<RegionRenderCacheBuilder> queueFreeRenderBuilders;
    private final WorldVertexBufferUploader worldBufferUploader = new WorldVertexBufferUploader();
    private final VertexBufferUploader bufferUploader = new VertexBufferUploader();
    private final Queue<BlockSystemChunkRenderDispatcher.PendingUpload> queueChunkUploads = Queues.newPriorityQueue();
    private final BlockSystemChunkRenderWorker renderWorker;

    public BlockSystemChunkRenderDispatcher() {
        this(-1);
    }

    public BlockSystemChunkRenderDispatcher(int builderCount) {
        int memoryAllocation = Math.max(1, (int) (Runtime.getRuntime().maxMemory() * 0.3) / 10485760);
        int threadCount = Math.max(1, MathHelper.clamp_int(Runtime.getRuntime().availableProcessors(), 1, memoryAllocation / 5));
        if (builderCount < 0) {
            builderCount = MathHelper.clamp_int(threadCount * 10, 1, memoryAllocation);
        }
        this.renderBuilderCount = builderCount;
        if (threadCount > 1) {
            for (int i = 0; i < threadCount; ++i) {
                BlockSystemChunkRenderWorker renderWorker = new BlockSystemChunkRenderWorker(this);
                Thread thread = THREAD_FACTORY.newThread(renderWorker);
                thread.start();
                this.workers.add(renderWorker);
                this.workerThreads.add(thread);
            }
        }
        this.queueFreeRenderBuilders = Queues.newArrayBlockingQueue(this.renderBuilderCount);
        for (int i = 0; i < this.renderBuilderCount; ++i) {
            this.queueFreeRenderBuilders.add(new RegionRenderCacheBuilder());
        }
        this.renderWorker = new BlockSystemChunkRenderWorker(this, new RegionRenderCacheBuilder());
    }

    public String getDebugInfo() {
        return this.workerThreads.isEmpty() ? String.format("pC: %03d, single-threaded", this.queueChunkUpdates.size()) : String.format("pC: %03d, pU: %1d, aB: %1d", Integer.valueOf(this.queueChunkUpdates.size()), Integer.valueOf(this.queueChunkUploads.size()), Integer.valueOf(this.queueFreeRenderBuilders.size()));
    }

    public boolean runChunkUploads(long finishTime) {
        boolean uploaded = false;
        while (true) {
            boolean run = false;
            if (this.workerThreads.isEmpty()) {
                BlockSystemChunkCompileTaskGenerator generator = this.queueChunkUpdates.poll();
                if (generator != null) {
                    try {
                        this.renderWorker.processTask(generator);
                        run = true;
                    } catch (InterruptedException var8) {
                        LOGGER.warn("Skipped task due to interrupt");
                    }
                }
            }
            synchronized (this.queueChunkUploads) {
                if (!this.queueChunkUploads.isEmpty()) {
                    this.queueChunkUploads.poll().uploadTask.run();
                    run = true;
                    uploaded = true;
                }
            }
            if (finishTime == 0 || !run || finishTime < System.nanoTime()) {
                break;
            }
        }
        return uploaded;
    }

    public boolean updateChunkLater(BlockSystemRenderChunk chunk) {
        chunk.getLockCompileTask().lock();
        boolean outcome;
        try {
            final BlockSystemChunkCompileTaskGenerator generator = chunk.makeCompileTaskChunk();
            generator.addFinishRunnable(() -> BlockSystemChunkRenderDispatcher.this.queueChunkUpdates.remove(generator));
            boolean offered = this.queueChunkUpdates.offer(generator);
            if (!offered) {
                generator.finish();
            }
            outcome = offered;
        } finally {
            chunk.getLockCompileTask().unlock();
        }
        return outcome;
    }

    public boolean updateChunkNow(BlockSystemRenderChunk chunk) {
        chunk.getLockCompileTask().lock();
        boolean outcome;
        try {
            BlockSystemChunkCompileTaskGenerator generator = chunk.makeCompileTaskChunk();
            try {
                this.renderWorker.processTask(generator);
            } catch (InterruptedException e) {
            }
            outcome = true;
        } finally {
            chunk.getLockCompileTask().unlock();
        }
        return outcome;
    }

    public void stopChunkUpdates() {
        this.clearChunkUpdates();
        List<RegionRenderCacheBuilder> queue = Lists.newArrayList();
        while (queue.size() != this.renderBuilderCount) {
            this.runChunkUploads(Long.MAX_VALUE);
            try {
                queue.add(this.allocateRenderBuilder());
            } catch (InterruptedException e) {
            }
        }
        this.queueFreeRenderBuilders.addAll(queue);
    }

    public void freeRenderBuilder(RegionRenderCacheBuilder builder) {
        this.queueFreeRenderBuilders.add(builder);
    }

    public RegionRenderCacheBuilder allocateRenderBuilder() throws InterruptedException {
        return this.queueFreeRenderBuilders.take();
    }

    public BlockSystemChunkCompileTaskGenerator getNextChunkUpdate() throws InterruptedException {
        return this.queueChunkUpdates.take();
    }

    public boolean updateTransparencyLater(BlockSystemRenderChunk chunk) {
        chunk.getLockCompileTask().lock();
        boolean outcome;
        try {
            final BlockSystemChunkCompileTaskGenerator generator = chunk.makeCompileTaskTransparency();
            if (generator == null) {
                return true;
            }
            generator.addFinishRunnable(() -> BlockSystemChunkRenderDispatcher.this.queueChunkUpdates.remove(generator));
            outcome = this.queueChunkUpdates.offer(generator);
        } finally {
            chunk.getLockCompileTask().unlock();
        }
        return outcome;
    }

    public ListenableFuture<Object> uploadChunk(final BlockRenderLayer layer, final VertexBuffer buffer, final BlockSystemRenderChunk chunk, final CompiledChunk compiledChunk, final double distance) {
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            if (OpenGlHelper.useVbo()) {
                this.uploadVertexBuffer(buffer, chunk.getLayerBuilder(layer.ordinal()));
            } else {
                this.uploadDisplayList(buffer, ((ListedBlockSystemRenderChunk) chunk).getDisplayList(layer, compiledChunk), chunk);
            }
            buffer.setTranslation(0.0D, 0.0D, 0.0D);
            return Futures.immediateFuture(null);
        } else {
            ListenableFutureTask<Object> futureTask = ListenableFutureTask.create(() -> BlockSystemChunkRenderDispatcher.this.uploadChunk(layer, buffer, chunk, compiledChunk, distance), null);
            synchronized (this.queueChunkUploads) {
                this.queueChunkUploads.add(new BlockSystemChunkRenderDispatcher.PendingUpload(futureTask, distance));
                return futureTask;
            }
        }
    }

    private void uploadDisplayList(VertexBuffer builder, int list, BlockSystemRenderChunk chunkRenderer) {
        GlStateManager.glNewList(list, GL11.GL_COMPILE);
        GlStateManager.pushMatrix();
        chunkRenderer.applyModelViewMatrix();
        this.worldBufferUploader.draw(builder);
        GlStateManager.popMatrix();
        GlStateManager.glEndList();
    }

    private void uploadVertexBuffer(VertexBuffer buffer, net.minecraft.client.renderer.vertex.VertexBuffer builder) {
        this.bufferUploader.setVertexBuffer(builder);
        this.bufferUploader.draw(buffer);
    }

    public void clearChunkUpdates() {
        while (!this.queueChunkUpdates.isEmpty()) {
            BlockSystemChunkCompileTaskGenerator taskGenerator = this.queueChunkUpdates.poll();
            if (taskGenerator != null) {
                taskGenerator.finish();
            }
        }
    }

    public boolean hasChunkUpdates() {
        return this.queueChunkUpdates.isEmpty() && this.queueChunkUploads.isEmpty();
    }

    public void stopWorkerThreads() {
        this.clearChunkUpdates();
        for (BlockSystemChunkRenderWorker worker : this.workers) {
            worker.notifyToStop();
        }
        for (Thread thread : this.workerThreads) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException interruptedexception) {
                LOGGER.warn("Interrupted whilst waiting for worker to die", interruptedexception);
            }
        }
        this.queueFreeRenderBuilders.clear();
    }

    public boolean hasNoFreeRenderBuilders() {
        return this.queueFreeRenderBuilders.size() == 0;
    }

    @SideOnly(Side.CLIENT)
    class PendingUpload implements Comparable<BlockSystemChunkRenderDispatcher.PendingUpload> {
        private final ListenableFutureTask<Object> uploadTask;
        private final double distanceSq;

        public PendingUpload(ListenableFutureTask<Object> uploadTask, double distance) {
            this.uploadTask = uploadTask;
            this.distanceSq = distance;
        }

        @Override
        public int compareTo(BlockSystemChunkRenderDispatcher.PendingUpload upload) {
            return Doubles.compare(this.distanceSq, upload.distanceSq);
        }
    }
}