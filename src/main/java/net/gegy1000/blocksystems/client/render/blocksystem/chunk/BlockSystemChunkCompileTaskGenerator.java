package net.gegy1000.blocksystems.client.render.blocksystem.chunk;

import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@SideOnly(Side.CLIENT)
public class BlockSystemChunkCompileTaskGenerator implements Comparable<BlockSystemChunkCompileTaskGenerator> {
    private final BlockSystemRenderChunk chunk;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Runnable> listFinishRunnables = Lists.newArrayList();
    private final BlockSystemChunkCompileTaskGenerator.Type type;
    private final double distance;
    private RegionRenderCacheBuilder regionRenderCacheBuilder;
    private CompiledChunk compiledChunk;
    private BlockSystemChunkCompileTaskGenerator.Status status = BlockSystemChunkCompileTaskGenerator.Status.PENDING;
    private boolean finished;

    public BlockSystemChunkCompileTaskGenerator(BlockSystemRenderChunk chunk, BlockSystemChunkCompileTaskGenerator.Type type, double distance) {
        this.chunk = chunk;
        this.type = type;
        this.distance = distance;
    }

    public BlockSystemChunkCompileTaskGenerator.Status getStatus() {
        return this.status;
    }

    public BlockSystemRenderChunk getChunk() {
        return this.chunk;
    }

    public CompiledChunk getCompiledChunk() {
        return this.compiledChunk;
    }

    public void setCompiledChunk(CompiledChunk compiledChunk) {
        this.compiledChunk = compiledChunk;
    }

    public RegionRenderCacheBuilder getRegionRenderCacheBuilder() {
        return this.regionRenderCacheBuilder;
    }

    public void setRegionRenderCacheBuilder(RegionRenderCacheBuilder cacheBuilder) {
        this.regionRenderCacheBuilder = cacheBuilder;
    }

    public void setStatus(BlockSystemChunkCompileTaskGenerator.Status statusIn) {
        this.lock.lock();
        try {
            this.status = statusIn;
        } finally {
            this.lock.unlock();
        }
    }

    public void finish() {
        this.lock.lock();
        try {
            if (this.type == BlockSystemChunkCompileTaskGenerator.Type.REBUILD_CHUNK && this.status != BlockSystemChunkCompileTaskGenerator.Status.DONE) {
                this.chunk.setNeedsUpdate(false);
            }
            this.finished = true;
            this.status = BlockSystemChunkCompileTaskGenerator.Status.DONE;
            for (Runnable runnable : this.listFinishRunnables) {
                runnable.run();
            }
        } finally {
            this.lock.unlock();
        }
    }

    public void addFinishRunnable(Runnable runnable) {
        this.lock.lock();
        try {
            this.listFinishRunnables.add(runnable);
            if (this.finished) {
                runnable.run();
            }
        } finally {
            this.lock.unlock();
        }
    }

    public ReentrantLock getLock() {
        return this.lock;
    }

    public BlockSystemChunkCompileTaskGenerator.Type getType() {
        return this.type;
    }

    public boolean isFinished() {
        return this.finished;
    }

    @Override
    public int compareTo(BlockSystemChunkCompileTaskGenerator generator) {
        return Doubles.compare(this.distance, generator.distance);
    }

    public double getDistance() {
        return this.distance;
    }

    @SideOnly(Side.CLIENT)
    public enum Status {
        PENDING,
        COMPILING,
        UPLOADING,
        DONE
    }

    @SideOnly(Side.CLIENT)
    public enum Type {
        REBUILD_CHUNK,
        RESORT_TRANSPARENCY
    }
}