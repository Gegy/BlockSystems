package net.gegy1000.blocksystems.server.blocksystem;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.entity.BlockSystemControlEntity;
import net.gegy1000.blocksystems.server.util.collision.EncompassedAABB;
import net.gegy1000.blocksystems.server.world.TransformedSubWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class BlockSystem extends TransformedSubWorld {
    public static int nextID = 0;

    protected boolean deserializing;

    protected BlockSystemControlEntity boundEntity;

    protected boolean removed;

    protected int id;

    protected AxisAlignedBB maximumBounds = new AxisAlignedBB(-64, 0, -64, 64, 128, 64);
    protected EncompassedAABB rotatedBounds = new EncompassedAABB(this.maximumBounds);

    public BlockSystem(World parentWorld, int id, MinecraftServer server) {
        super(parentWorld, new BlockSystemSaveHandler());
        this.id = id;
        this.chunkProvider = this.createChunkProvider();
        this.initializeBlockSystem(server);
        this.recalculateTransform();
    }

    public abstract void initializeBlockSystem(MinecraftServer server);

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    @Override
    protected void recalculateTransform() {
        super.recalculateTransform();
        this.rotatedBounds.calculate(this.transform.toGlobal());
    }

    public void remove() {
        this.removed = true;
        BlockSystems.PROXY.getBlockSystemHandler(this.parentWorld).removeBlockSystem(this);
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return true;
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
        return this.isValid(pos) && super.setBlockState(pos, newState, flags);
    }

    @Override
    public void markAndNotifyBlock(BlockPos pos, Chunk chunk, IBlockState oldState, IBlockState newState, int flags) {
        if (this.isValid(pos)) {
            super.markAndNotifyBlock(pos, chunk, oldState, newState, flags);
        }
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        if (this.isValid(pos)) {
            return super.getBlockState(pos);
        }
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return Biomes.OCEAN;
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing side) {
        return this.getBlockState(pos).getStrongPower(this, pos, side);
    }

    @Override
    public WorldType getWorldType() {
        return WorldType.DEFAULT;
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return !this.isValid(pos) || super.isAirBlock(pos);
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        if (!this.isValid(pos)) {
            return null;
        }
        return super.getTileEntity(pos);
    }

    @Override
    public Entity getEntityByID(int id) {
        return this.parentWorld.getEntityByID(id);
    }

    @Override
    public List<Entity> getLoadedEntityList() {
        return this.parentWorld.getLoadedEntityList();
    }

    @Override
    public EntityPlayer getPlayerEntityByUUID(UUID uuid) {
        return this.parentWorld.getPlayerEntityByUUID(uuid);
    }

    @Override
    public EntityPlayer getPlayerEntityByName(String name) {
        return this.parentWorld.getPlayerEntityByName(name);
    }

    @Override
    public void tick() {
        super.tick();

        // TODO: Big performance loss with this enabled -- optimize!
        /*boolean rotate=true;

        List<AxisAlignedBB> bbs=getCollisionBoxes(null,maximumBounds.offset(posX,posY,posZ  ));
        for(AxisAlignedBB bb : bbs) {
            for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable((int) Math.floor(bb.minX), (int) Math.floor(bb.minY), (int) Math.floor(bb.minZ), (int) Math.ceil(bb.maxX), (int) Math.ceil(bb.maxY), (int) Math.ceil(bb.maxZ))) {
                IBlockState state = mainWorld.getBlockState(pos);
                AxisAlignedBB aabb = state.getCollisionBoundingBox(mainWorld, pos);
                if (aabb == null)
                    continue;
                aabb = aabb.shrink(0.02).offset(pos);
                if (bb.intersects(aabb)) {
                    rotate = false;
                    break;
                }
            }
        }

        if(rotate)*/
        this.rotation.rotate(0.5, 0.0, 1.0, 0.0);

        if (this.boundEntity != null) {
            this.setPositionAndRotation(this.boundEntity.posX, this.boundEntity.posY, this.boundEntity.posZ, this.rotation);
        }

        this.tickUpdates(false);
    }

    @Override
    protected void updateWeather() {
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        if (!this.isValid(pos.offset(side))) {
            return _default;
        }
        return super.isSideSolid(pos, side, _default);
    }

    @Override
    public List<AxisAlignedBB> getCollisionBoxes(@Nullable Entity entity, AxisAlignedBB aabb) {
        List<AxisAlignedBB> collisionBoxes = new ArrayList<>();
        this.getCollisionBoxes(entity, aabb, collisionBoxes);
        MinecraftForge.EVENT_BUS.post(new GetCollisionBoxesEvent(this, entity, aabb, collisionBoxes));
        return collisionBoxes;
    }

    private void getCollisionBoxes(Entity entity, AxisAlignedBB aabb, List<AxisAlignedBB> collisionBoxes) {
        AxisAlignedBB transformedAabb = new EncompassedAABB(aabb, this.transform.toLocal()).getAabb();

        int minX = MathHelper.floor(transformedAabb.minX) - 1;
        int maxX = MathHelper.ceil(transformedAabb.maxX) + 1;
        int minY = MathHelper.floor(transformedAabb.minY) - 1;
        int maxY = MathHelper.ceil(transformedAabb.maxY) + 1;
        int minZ = MathHelper.floor(transformedAabb.minZ) - 1;
        int maxZ = MathHelper.ceil(transformedAabb.maxZ) + 1;

        BlockPos.MutableBlockPos currentPos = new BlockPos.MutableBlockPos();

        List<AxisAlignedBB> generatedBoxes = new ArrayList<>();

        for (int z = minZ; z < maxZ; z++) {
            for (int x = minX; x < maxX; x++) {
                if (this.isChunkLoaded(x >> 4, z >> 4, false)) {
                    for (int y = minY; y < maxY; y++) {
                        currentPos.setPos(x, y, z);

                        IBlockState state = this.getBlockState(currentPos);

                        state.addCollisionBoxToList(this, currentPos, TileEntity.INFINITE_EXTENT_AABB, generatedBoxes, entity, false);
                        for (AxisAlignedBB generated : generatedBoxes) {
                            AxisAlignedBB transformed = new EncompassedAABB(generated, this.transform.toGlobal()).getAabb();
                            if (aabb.intersects(transformed)) {
                                collisionBoxes.add(transformed);
                            }
                        }

                        generatedBoxes.clear();
                    }
                }
            }
        }
    }

    public List<IWorldEventListener> getListeners() {
        return this.eventListeners;
    }

    @Override
    public boolean isValid(BlockPos pos) {
        return this.maximumBounds.grow(1).contains(new Vec3d(pos.getX(), pos.getY(), pos.getZ())) && pos.getY() >= 0 && pos.getY() < 256;
    }

    public boolean isRemoved() {
        return this.removed;
    }

    public BlockSystem withBounds(AxisAlignedBB bounds) {
        this.maximumBounds = bounds;
        return this;
    }

    public AxisAlignedBB getMaximumBounds() {
        return this.maximumBounds;
    }

    public EncompassedAABB getRotatedBounds() {
        return this.rotatedBounds;
    }

    @Override
    public int hashCode() {
        return this.id * 31;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BlockSystem && ((BlockSystem) obj).id == this.id;
    }
}
