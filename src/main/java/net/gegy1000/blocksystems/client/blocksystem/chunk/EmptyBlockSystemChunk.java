package net.gegy1000.blocksystems.client.blocksystem.chunk;

import com.google.common.base.Predicate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

@SideOnly(Side.CLIENT)
public class EmptyBlockSystemChunk extends ClientBlockSystemChunk {
    public EmptyBlockSystemChunk(BlockSystem blockSystem, int x, int z) {
        super(blockSystem, x, z);
    }

    @Override
    public boolean isAtLocation(int x, int z) {
        return x == this.xPosition && z == this.zPosition;
    }

    @Override
    public int getHeightValue(int x, int z) {
        return 0;
    }

    @Override
    public void generateHeightMap() {
    }

    @Override
    public void generateSkylightMap() {
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public int getBlockLightOpacity(BlockPos pos) {
        return 255;
    }

    @Override
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        return type.defaultLightValue;
    }

    @Override
    public void setLightFor(EnumSkyBlock type, BlockPos pos, int value) {
    }

    @Override
    public int getLightSubtracted(BlockPos pos, int amount) {
        return 0;
    }

    @Override
    public void addEntity(Entity entity) {
    }

    @Override
    public void removeEntity(Entity entity) {
    }

    @Override
    public void removeEntityAtIndex(Entity entity, int index) {
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return false;
    }

    @Nullable
    public TileEntity getTileEntity(BlockPos pos, Chunk.EnumCreateEntityType type) {
        return null;
    }

    @Override
    public void addTileEntity(TileEntity tile) {
    }

    @Override
    public void addTileEntity(BlockPos pos, TileEntity tile) {
    }

    @Override
    public void removeTileEntity(BlockPos pos) {
    }

    @Override
    public void onChunkLoad() {
    }

    @Override
    public void onChunkUnload() {
    }

    @Override
    public void setChunkModified() {
    }

    @Override
    public void getEntitiesWithinAABBForEntity(Entity entity, AxisAlignedBB aabb, List<Entity> entities, Predicate<? super Entity> selector) {
    }

    @Override
    public <T extends Entity> void getEntitiesOfTypeWithinAAAB(Class<? extends T> entityClass, AxisAlignedBB aabb, List<T> entities, Predicate<? super T> selector) {
    }

    @Override
    public boolean needsSaving(boolean needsModified) {
        return false;
    }

    @Override
    public Random getRandomWithSeed(long seed) {
        return new Random(this.getWorld().getSeed() + (long) (this.xPosition * this.xPosition * 4987142) + (long) (this.xPosition * 5947611) + (long) (this.zPosition * this.zPosition) * 4392871L + (long) (this.zPosition * 389711) ^ seed);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean getAreLevelsEmpty(int startY, int endY) {
        return true;
    }
}