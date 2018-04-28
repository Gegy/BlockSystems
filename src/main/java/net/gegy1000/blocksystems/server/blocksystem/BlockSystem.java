package net.gegy1000.blocksystems.server.blocksystem;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.chunk.BlockSystemChunk;
import net.gegy1000.blocksystems.server.entity.BlockSystemControlEntity;
import net.gegy1000.blocksystems.server.util.collision.EncompassedAABB;
import net.gegy1000.blocksystems.server.util.math.Matrix;
import net.gegy1000.blocksystems.server.util.math.QuatRotation;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.*;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.*;

public abstract class BlockSystem extends World {
    public static int nextID = 0;

    public static BlockSystem transforming;

    public double posX;
    public double posY;
    public double posZ;
    public double prevPosX;
    public double prevPosY;
    public double prevPosZ;

    public QuatRotation rotation = new QuatRotation();
    public QuatRotation prevRotation = new QuatRotation();

    protected World mainWorld;

    protected Matrix transformMatrix = new Matrix(3);
    protected Matrix untransformMatrix = new Matrix(3);

    protected boolean deserializing;

    protected BlockSystemControlEntity boundEntity;

    protected Map<EntityPlayer, BlockSystemPlayerHandler> playerHandlers = new HashMap<>();
    protected boolean removed;

    protected int id;

    protected AxisAlignedBB maximumBounds = new AxisAlignedBB(-64, 0, -64, 64, 128, 64);
    protected EncompassedAABB rotatedBounds = new EncompassedAABB(this.maximumBounds);

    protected Map<ChunkPos, BlockSystemChunk> savedChunks = new HashMap<>();

    public BlockSystem(World mainWorld, int id, MinecraftServer server) {
        super(new BlockSystemSaveHandler(), mainWorld.getWorldInfo(), mainWorld.provider, mainWorld.profiler, mainWorld.isRemote);
        this.mainWorld = mainWorld;
        this.id = id;
        this.chunkProvider = this.createChunkProvider();
        this.initializeBlockSystem(server);
        this.recalculateMatrices();
    }

    public abstract void initializeBlockSystem(MinecraftServer server);

    public void setID(int id) {
        this.id = id;
    }

    public int getID() {
        return this.id;
    }

    private void recalculateMatrices() {
        this.transformMatrix.setIdentity();
        this.transformMatrix.translate(this.posX, this.posY, this.posZ);
        // TODO: No idea why but this only works with the inverse?
        this.transformMatrix.rotateInverse(this.rotation);
        this.transformMatrix.translate(-0.5, 0.0, -0.5);

        this.untransformMatrix.setIdentity();
        this.untransformMatrix.multiply(this.transformMatrix);
        this.untransformMatrix.invert();

        this.rotatedBounds.calculate(this.transformMatrix);
    }

    public void deserialize(NBTTagCompound compound) {
        this.deserializing = true;
        this.posX = compound.getDouble("pos_x");
        this.posY = compound.getDouble("pos_y");
        this.posZ = compound.getDouble("pos_z");
        this.rotation.deserialize(compound.getCompoundTag("rot"));
        NBTTagList chunksList = compound.getTagList("chunks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < chunksList.tagCount(); i++) {
            NBTTagCompound chunkTag = chunksList.getCompoundTagAt(i);
            ChunkPos pos = new ChunkPos(chunkTag.getInteger("x"), chunkTag.getInteger("z"));
            BlockSystemChunk chunk = new BlockSystemChunk(this, pos.x, pos.z);
            chunk.deserialize(chunkTag);
            this.savedChunks.put(pos, chunk);
        }
        this.deserializing = false;
        this.recalculateMatrices();
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        compound.setDouble("pos_x", this.posX);
        compound.setDouble("pos_y", this.posY);
        compound.setDouble("pos_z", this.posZ);
        compound.setTag("rot", this.rotation.serialize(new NBTTagCompound()));
        NBTTagList chunksList = new NBTTagList();
        for (Map.Entry<ChunkPos, BlockSystemChunk> entry : this.savedChunks.entrySet()) {
            BlockSystemChunk chunk = entry.getValue();
            if (!chunk.isEmpty()) {
                ChunkPos pos = entry.getKey();
                NBTTagCompound chunkTag = new NBTTagCompound();
                chunkTag.setInteger("x", pos.x);
                chunkTag.setInteger("z", pos.z);
                chunk.serialize(chunkTag);
                chunksList.appendTag(chunkTag);
            }
        }
        compound.setTag("chunks", chunksList);
        return compound;
    }

    public Point3d getTransformedPosition(Point3d position) {
        this.transformMatrix.transform(position);
        return position;
    }

    public Vec3d getTransformedPosition(Vec3d position) {
        Point3d point = new Point3d(position.x, position.y, position.z);
        this.transformMatrix.transform(point);
        return new Vec3d(point.getX(), point.getY(), point.getZ());
    }

    public BlockPos getTransformedPosition(BlockPos pos) {
        Point3d transformed = this.getTransformedPosition(new Point3d(pos.getX(), pos.getY(), pos.getZ()));
        return new BlockPos(transformed.x, transformed.y, transformed.z);
    }

    public Point3d getUntransformedPosition(Point3d position) {
        this.untransformMatrix.transform(position);
        return position;
    }

    public Vec3d getUntransformedPosition(Vec3d position) {
        Point3d point = new Point3d(position.x, position.y, position.z);
        this.untransformMatrix.transform(point);
        return new Vec3d(point.getX(), point.getY(), point.getZ());
    }

    public BlockPos getUntransformedPosition(BlockPos pos) {
        Point3d untransformed = this.getUntransformedPosition(new Point3d(pos.getX(), pos.getY(), pos.getZ()));
        return new BlockPos(untransformed.x, untransformed.y, untransformed.z);
    }

    public Vec3d getTransformedVector(Vec3d vec) {
        Vector3d vector = new Vector3d(vec.x, vec.y, vec.z);
        this.transformMatrix.transform(vector);
        return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
    }

    public void remove() {
        this.removed = true;
        BlockSystems.PROXY.getBlockSystemHandler(this.mainWorld).removeBlockSystem(this);
    }

    public BlockSystemPlayerHandler addPlayerHandler(EntityPlayer player) {
        BlockSystemPlayerHandler handler = new BlockSystemPlayerHandler(this, player);
        this.playerHandlers.put(player, handler);
        return handler;
    }

    public void removePlayerHandler(EntityPlayer player) {
        this.playerHandlers.remove(player);
    }

    public BlockSystemPlayerHandler getPlayerHandler(EntityPlayer player) {
        return this.playerHandlers.get(player);
    }

    public Map<EntityPlayer, BlockSystemPlayerHandler> getPlayerHandlers() {
        return this.playerHandlers;
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
        return this.mainWorld.getEntityByID(id);
    }

    @Override
    public EntityPlayer getClosestPlayer(double posX, double posY, double posZ, double distance, boolean spectator) {
        Point3d transformed = this.getTransformedPosition(new Point3d(posX, posY, posZ));
        posX = transformed.getX();
        posY = transformed.getY();
        posZ = transformed.getZ();
        return this.mainWorld.getClosestPlayer(posX, posY, posZ, distance, spectator);
    }

    @Override
    public EntityPlayer getNearestAttackablePlayer(double posX, double posY, double posZ, double maxXZDistance, double maxYDistance, Function<EntityPlayer, Double> serializer, Predicate<EntityPlayer> selector) {
        Point3d transformed = this.getTransformedPosition(new Point3d(posX, posY, posZ));
        posX = transformed.getX();
        posY = transformed.getY();
        posZ = transformed.getZ();
        return this.mainWorld.getNearestAttackablePlayer(posX, posY, posZ, maxXZDistance, maxYDistance, serializer, selector);
    }

    @Override
    public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity, AxisAlignedBB bounds) {
        //TODO Transform bounds
        return this.mainWorld.getEntitiesWithinAABBExcludingEntity(entity, bounds);
    }

    @Override
    public List<Entity> getEntitiesInAABBexcluding(Entity entity, AxisAlignedBB bounds, Predicate<? super Entity> selector) {
        return this.mainWorld.getEntitiesInAABBexcluding(entity, bounds, selector);
    }

    @Override
    public List<Entity> getLoadedEntityList() {
        return this.mainWorld.getLoadedEntityList();
    }

    @Override
    public EntityPlayer getPlayerEntityByUUID(UUID uuid) {
        return this.mainWorld.getPlayerEntityByUUID(uuid);
    }

    @Override
    public EntityPlayer getPlayerEntityByName(String name) {
        return this.mainWorld.getPlayerEntityByName(name);
    }

    @Override
    public boolean spawnEntity(Entity entity) {
        entity.world = this.mainWorld;
        Point3d transformedPosition = this.getTransformedPosition(new Point3d(entity.posX, entity.posY, entity.posZ));
        entity.setPosition(transformedPosition.x, transformedPosition.y, transformedPosition.z);
        return this.mainWorld.spawnEntity(entity);
    }

    @Override
    public void spawnParticle(EnumParticleTypes particleType, double posX, double posY, double posZ, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        Point3d transformed = this.getTransformedPosition(new Point3d(posX, posY, posZ));
        posX = transformed.getX();
        posY = transformed.getY();
        posZ = transformed.getZ();
        if (particleType != EnumParticleTypes.REDSTONE) {
            Vec3d transformedVelocity = this.getTransformedVector(new Vec3d(xSpeed, ySpeed, zSpeed));
            xSpeed = transformedVelocity.x;
            ySpeed = transformedVelocity.y;
            zSpeed = transformedVelocity.z;
        }
        super.spawnParticle(particleType, posX, posY, posZ, xSpeed, ySpeed, zSpeed, parameters);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void spawnParticle(EnumParticleTypes particleType, boolean ignoreRange, double posX, double posY, double posZ, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        Point3d transformed = this.getTransformedPosition(new Point3d(posX, posY, posZ));
        posX = transformed.getX();
        posY = transformed.getY();
        posZ = transformed.getZ();
        if (particleType != EnumParticleTypes.REDSTONE) {
            Vec3d transformedVelocity = this.getTransformedVector(new Vec3d(xSpeed, ySpeed, zSpeed));
            xSpeed = transformedVelocity.x;
            ySpeed = transformedVelocity.y;
            zSpeed = transformedVelocity.z;
        }
        super.spawnParticle(particleType, ignoreRange, posX, posY, posZ, xSpeed, ySpeed, zSpeed, parameters);
    }

    @Override
    public void tick() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.prevRotation = this.rotation.copy();

        super.tick();

        boolean rotate=true;

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

        if(rotate)
        this.rotation.rotate(0.5, 0.0, 1.0, 0.0);

        if (this.boundEntity != null) {
            this.setPositionAndRotation(this.boundEntity.posX, this.boundEntity.posY, this.boundEntity.posZ, this.rotation);
        }

        List<EntityPlayer> remove = new ArrayList<>();

        for (Map.Entry<EntityPlayer, BlockSystemPlayerHandler> entry : this.playerHandlers.entrySet()) {
            entry.getValue().update();
            if (entry.getKey().isDead) {
                remove.add(entry.getKey());
            }
        }

        for (EntityPlayer player : remove) {
            this.playerHandlers.remove(player);
        }

        if (this.posX != this.prevPosX || this.posY != this.prevPosY || this.posZ != this.prevPosZ || !this.rotation.equals(this.prevRotation)) {
            this.recalculateMatrices();
        }

        this.tickUpdates(false);
    }

    @Override
    protected void updateWeather() {
    }

    @Override
    public RayTraceResult rayTraceBlocks(Vec3d start, Vec3d end, boolean traceLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
        if (!Double.isNaN(start.x) && !Double.isNaN(start.y) && !Double.isNaN(start.z)) {
            if (!Double.isNaN(end.x) && !Double.isNaN(end.y) && !Double.isNaN(end.z)) {
                start = this.getUntransformedPosition(start);
                end = this.getUntransformedPosition(end);
                int endX = MathHelper.floor(end.x);
                int endY = MathHelper.floor(end.y);
                int endZ = MathHelper.floor(end.z);
                int traceX = MathHelper.floor(start.x);
                int traceY = MathHelper.floor(start.y);
                int traceZ = MathHelper.floor(start.z);
                BlockPos tracePos = new BlockPos(traceX, traceY, traceZ);
                IBlockState startState = this.getBlockState(tracePos);
                Block startBlock = startState.getBlock();
                if ((!ignoreBlockWithoutBoundingBox || startState.getCollisionBoundingBox(this, tracePos) != Block.NULL_AABB) && startBlock.canCollideCheck(startState, traceLiquid)) {
                    RayTraceResult result = startState.collisionRayTrace(this, tracePos, start, end);
                    if (result != null) {
                        return result;
                    }
                }
                RayTraceResult result = null;
                int ray = 200;
                while (ray-- >= 0) {
                    if (Double.isNaN(start.x) || Double.isNaN(start.y) || Double.isNaN(start.z)) {
                        return null;
                    }
                    if (traceX == endX && traceY == endY && traceZ == endZ) {
                        return returnLastUncollidableBlock ? result : null;
                    }
                    boolean reachedX = true;
                    boolean reachedY = true;
                    boolean reachedZ = true;
                    double targetX = 999.0;
                    double targetY = 999.0;
                    double targetZ = 999.0;
                    if (endX > traceX) {
                        targetX = traceX + 1.0;
                    } else if (endX < traceX) {
                        targetX = traceX;
                    } else {
                        reachedX = false;
                    }
                    if (endY > traceY) {
                        targetY = traceY + 1.0;
                    } else if (endY < traceY) {
                        targetY = traceY;
                    } else {
                        reachedY = false;
                    }
                    if (endZ > traceZ) {
                        targetZ = traceZ + 1.0;
                    } else if (endZ < traceZ) {
                        targetZ = traceZ;
                    } else {
                        reachedZ = false;
                    }
                    double deltaX = 999.0;
                    double deltaY = 999.0;
                    double deltaZ = 999.0;
                    double totalDeltaX = end.x - start.x;
                    double totalDeltaY = end.y - start.y;
                    double totalDeltaZ = end.z - start.z;
                    if (reachedX) {
                        deltaX = (targetX - start.x) / totalDeltaX;
                    }
                    if (reachedY) {
                        deltaY = (targetY - start.y) / totalDeltaY;
                    }
                    if (reachedZ) {
                        deltaZ = (targetZ - start.z) / totalDeltaZ;
                    }
                    if (deltaX == -0.0) {
                        deltaX = -1.0E-4D;
                    }
                    if (deltaY == -0.0) {
                        deltaY = -1.0E-4D;
                    }
                    if (deltaZ == -0.0) {
                        deltaZ = -1.0E-4D;
                    }
                    EnumFacing sideHit;
                    if (deltaX < deltaY && deltaX < deltaZ) {
                        sideHit = endX > traceX ? EnumFacing.WEST : EnumFacing.EAST;
                        start = new Vec3d(targetX, start.y + totalDeltaY * deltaX, start.z + totalDeltaZ * deltaX);
                    } else if (deltaY < deltaZ) {
                        sideHit = endY > traceY ? EnumFacing.DOWN : EnumFacing.UP;
                        start = new Vec3d(start.x + totalDeltaX * deltaY, targetY, start.z + totalDeltaZ * deltaY);
                    } else {
                        sideHit = endZ > traceZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
                        start = new Vec3d(start.x + totalDeltaX * deltaZ, start.y + totalDeltaY * deltaZ, targetZ);
                    }
                    traceX = MathHelper.floor(start.x) - (sideHit == EnumFacing.EAST ? 1 : 0);
                    traceY = MathHelper.floor(start.y) - (sideHit == EnumFacing.UP ? 1 : 0);
                    traceZ = MathHelper.floor(start.z) - (sideHit == EnumFacing.SOUTH ? 1 : 0);
                    tracePos = new BlockPos(traceX, traceY, traceZ);
                    IBlockState traceState = this.getBlockState(tracePos);
                    Block traceBlock = traceState.getBlock();
                    if (!ignoreBlockWithoutBoundingBox || traceState.getMaterial() == Material.PORTAL || traceState.getCollisionBoundingBox(this, tracePos) != Block.NULL_AABB) {
                        if (traceBlock.canCollideCheck(traceState, traceLiquid)) {
                            RayTraceResult finalResult = traceState.collisionRayTrace(this, tracePos, start, end);
                            if (finalResult != null) {
                                return finalResult;
                            }
                        } else {
                            result = new RayTraceResult(RayTraceResult.Type.MISS, start, sideHit, tracePos);
                        }
                    }
                }
                return returnLastUncollidableBlock ? result : null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public void playEventServer(EntityPlayer player, int type, BlockPos pos, int data) {
        super.playEvent(player, type, pos, data);
    }

    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
        BlockSystem.transforming = this;
        super.playEvent(player, type, pos, data);
        BlockSystem.transforming = null;
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        if (!this.isValid(pos.offset(side))) {
            return _default;
        }
        return super.isSideSolid(pos, side, _default);
    }

    @Override
    public void playSound(EntityPlayer player, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        Point3d transformed = this.getTransformedPosition(new Point3d(x, y, z));
        x = transformed.getX();
        y = transformed.getY();
        z = transformed.getZ();
        super.playSound(player, x, y, z, sound, category, volume, pitch);
    }

    @Override
    public List<AxisAlignedBB> getCollisionBoxes(@Nullable Entity entity, AxisAlignedBB aabb) {
        List<AxisAlignedBB> collisionBoxes = new ArrayList<>();
        this.getCollisionBoxes(entity, aabb, collisionBoxes);
        MinecraftForge.EVENT_BUS.post(new GetCollisionBoxesEvent(this, entity, aabb, collisionBoxes));
        return collisionBoxes;
    }

    private void getCollisionBoxes(Entity entity, AxisAlignedBB aabb, List<AxisAlignedBB> collisionBoxes) {
        AxisAlignedBB transformedAabb = new EncompassedAABB(aabb, this.untransformMatrix).getAabb();

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
                            AxisAlignedBB transformed = new EncompassedAABB(generated, this.transformMatrix).getAabb();
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

    public World getMainWorld() {
        return this.mainWorld;
    }

    public void addSavedChunk(BlockSystemChunk chunk) {
        this.savedChunks.put(new ChunkPos(chunk.x, chunk.z), chunk);
    }

    public void removeSavedChunk(BlockSystemChunk chunk) {
        this.removeSavedChunk(new ChunkPos(chunk.x, chunk.z));
    }

    public void removeSavedChunk(ChunkPos pos) {
        this.savedChunks.remove(pos);
        if (this.savedChunks.size() <= 0 && this.mainWorld.isRemote) {
            this.remove();
        } else if (!this.mainWorld.isRemote) {
            boolean allEmpty = true;
            for (Map.Entry<ChunkPos, BlockSystemChunk> entry : this.savedChunks.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) {
                this.remove();
            }
        }
    }

    public BlockSystemChunk getSavedChunk(ChunkPos pos) {
        return this.savedChunks.get(pos);
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

    public void setPositionAndRotation(double posX, double posY, double posZ, QuatRotation rotation) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotation = rotation;
        this.recalculateMatrices();
    }

    public AxisAlignedBB getMaximumBounds() {
        return this.maximumBounds;
    }

    public EncompassedAABB getRotatedBounds() {
        return this.rotatedBounds;
    }

    public BlockSystemChunk getChunkFromPartition(BlockPos pos) {
        for (Map.Entry<ChunkPos, BlockSystemChunk> entry : this.savedChunks.entrySet()) {
            BlockSystemChunk chunk = entry.getValue();
            if (chunk.getPartitionPosition().equals(pos)) {
                return chunk;
            }
        }
        return null;
    }
}
