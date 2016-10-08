package net.gegy1000.blocksystems.server.blocksystem;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.chunk.BlockSystemChunk;
import net.gegy1000.blocksystems.server.entity.BlockSystemControlEntity;
import net.gegy1000.blocksystems.server.util.Matrix;
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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class BlockSystem extends World {
    public static int nextID = 0;

    public static BlockSystem transforming;

    public double posX;
    public double posY;
    public double posZ;
    public double prevPosX;
    public double prevPosY;
    public double prevPosZ;

    public float rotationX;
    public float rotationY;
    public float rotationZ;
    public float prevRotationX;
    public float prevRotationY;
    public float prevRotationZ;

    protected World mainWorld;

    protected Matrix transformMatrix = new Matrix(3);
    protected Matrix untransformMatrix = new Matrix(3);

    protected boolean deserializing;

    protected BlockSystemControlEntity boundEntity;

    protected Map<EntityPlayer, BlockSystemPlayerHandler> playerHandlers = new HashMap<>();
    protected boolean removed;

    protected int id;

    protected AxisAlignedBB bounds = new AxisAlignedBB(-64, -64, -64, 64, 64, 64);

    protected Map<ChunkPos, BlockSystemChunk> savedChunks = new HashMap<>();

    public BlockSystem(World mainWorld, int id, MinecraftServer server) {
        super(new BlockSystemSaveHandler(), mainWorld.getWorldInfo(), mainWorld.provider, mainWorld.theProfiler, mainWorld.isRemote);
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
        this.transformMatrix.rotate(Math.toRadians(this.rotationY), 0.0F, 1.0F, 0.0F);
        this.transformMatrix.rotate(Math.toRadians(this.rotationX), 1.0F, 0.0F, 0.0F);
        this.transformMatrix.rotate(Math.toRadians(this.rotationZ), 0.0F, 0.0F, 1.0F);

        this.untransformMatrix.setIdentity();
        this.untransformMatrix.multiply(this.transformMatrix);
        this.untransformMatrix.invert();
    }

    public void deserialize(NBTTagCompound compound) {
        this.deserializing = true;
        this.posX = compound.getDouble("PosX");
        this.posY = compound.getDouble("PosY");
        this.posZ = compound.getDouble("PosZ");
        this.rotationX = compound.getFloat("RotationX");
        this.rotationY = compound.getFloat("RotationY");
        this.rotationZ = compound.getFloat("RotationZ");
        NBTTagList chunksList = compound.getTagList("Chunks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < chunksList.tagCount(); i++) {
            NBTTagCompound chunkTag = chunksList.getCompoundTagAt(i);
            ChunkPos pos = new ChunkPos(chunkTag.getInteger("x"), chunkTag.getInteger("z"));
            BlockSystemChunk chunk = new BlockSystemChunk(this, pos.chunkXPos, pos.chunkXPos);
            chunk.deserialize(chunkTag);
            this.savedChunks.put(pos, chunk);
        }
        this.deserializing = false;
        this.recalculateMatrices();
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        compound.setDouble("PosX", this.posX);
        compound.setDouble("PosY", this.posY);
        compound.setDouble("PosZ", this.posZ);
        compound.setFloat("RotationX", this.rotationX);
        compound.setFloat("RotationY", this.rotationY);
        compound.setFloat("RotationZ", this.rotationZ);
        NBTTagList chunksList = new NBTTagList();
        for (Map.Entry<ChunkPos, BlockSystemChunk> entry : this.savedChunks.entrySet()) {
            BlockSystemChunk chunk = entry.getValue();
            if (!chunk.isEmpty()) {
                ChunkPos pos = entry.getKey();
                NBTTagCompound chunkTag = new NBTTagCompound();
                chunkTag.setInteger("x", pos.chunkXPos);
                chunkTag.setInteger("z", pos.chunkZPos);
                chunk.serialize(chunkTag);
                chunksList.appendTag(chunkTag);
            }
        }
        compound.setTag("Chunks", chunksList);
        return compound;
    }

    public Point3d getTransformedPosition(Point3d position) {
        position.sub(new Point3d(0.5, 0.0, 0.5));
        this.transformMatrix.transform(position);
        return position;
    }

    public Vec3d getTransformedPosition(Vec3d position) {
        Point3d point = new Point3d(position.xCoord - 0.5, position.yCoord, position.zCoord - 0.5);
        this.transformMatrix.transform(point);
        return new Vec3d(point.getX(), point.getY(), point.getZ());
    }

    public Point3d getUntransformedPosition(Point3d position) {
        this.untransformMatrix.transform(position);
        position.add(new Point3d(0.5, 0.0, 0.5));
        return position;
    }

    public Vec3d getUntransformedPosition(Vec3d position) {
        Point3d point = new Point3d(position.xCoord, position.yCoord, position.zCoord);
        this.untransformMatrix.transform(point);
        return new Vec3d(point.getX() + 0.5, point.getY(), point.getZ() + 0.5);
    }

    public Vec3d getTransformedVector(Vec3d vec) {
        Vector3d vector = new Vector3d(vec.xCoord, vec.yCoord, vec.zCoord);
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
        return false;
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
    public boolean spawnEntityInWorld(Entity entity) {
        entity.worldObj = this.mainWorld;
        Point3d transformedPosition = this.getTransformedPosition(new Point3d(entity.posX, entity.posY, entity.posZ));
        entity.posX = transformedPosition.getX();
        entity.posY = transformedPosition.getY();
        entity.posZ = transformedPosition.getZ();
        return this.mainWorld.spawnEntityInWorld(entity);
    }

    @Override
    public void spawnParticle(EnumParticleTypes particleType, double posX, double posY, double posZ, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        Point3d transformed = this.getTransformedPosition(new Point3d(posX, posY, posZ));
        posX = transformed.getX();
        posY = transformed.getY();
        posZ = transformed.getZ();
        if (particleType != EnumParticleTypes.REDSTONE) {
            Vec3d transformedVelocity = this.getTransformedVector(new Vec3d(xSpeed, ySpeed, zSpeed));
            xSpeed = transformedVelocity.xCoord;
            ySpeed = transformedVelocity.yCoord;
            zSpeed = transformedVelocity.zCoord;
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
            xSpeed = transformedVelocity.xCoord;
            ySpeed = transformedVelocity.yCoord;
            zSpeed = transformedVelocity.zCoord;
        }
        super.spawnParticle(particleType, ignoreRange, posX, posY, posZ, xSpeed, ySpeed, zSpeed, parameters);
    }

    @Override
    public void tick() {
        this.setBlockState(BlockPos.ORIGIN, Blocks.STONE.getDefaultState(), 3);

        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.prevRotationX = this.rotationX;
        this.prevRotationY = this.rotationY;
        this.prevRotationZ = this.rotationZ;

        super.tick();

        if (this.boundEntity != null) {
            this.setPositionAndRotation(this.boundEntity.posX, this.boundEntity.posY, this.boundEntity.posZ, this.boundEntity.rotationPitch, this.boundEntity.rotationYaw, this.rotationZ);
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

        if (this.posX != this.prevPosX || this.posY != this.prevPosY || this.posZ != this.prevPosZ || this.rotationY != this.prevRotationY || this.rotationX != this.prevRotationX || this.rotationZ != this.prevRotationZ) {
            this.recalculateMatrices();
        }

        this.tickUpdates(false);
    }

    @Override
    protected void updateWeather() {
    }

    @Override
    public RayTraceResult rayTraceBlocks(Vec3d start, Vec3d end, boolean traceLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
        if (!Double.isNaN(start.xCoord) && !Double.isNaN(start.yCoord) && !Double.isNaN(start.zCoord)) {
            if (!Double.isNaN(end.xCoord) && !Double.isNaN(end.yCoord) && !Double.isNaN(end.zCoord)) {
                start = this.getUntransformedPosition(start);
                end = this.getUntransformedPosition(end);
                int endX = MathHelper.floor_double(end.xCoord);
                int endY = MathHelper.floor_double(end.yCoord);
                int endZ = MathHelper.floor_double(end.zCoord);
                int traceX = MathHelper.floor_double(start.xCoord);
                int traceY = MathHelper.floor_double(start.yCoord);
                int traceZ = MathHelper.floor_double(start.zCoord);
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
                    if (Double.isNaN(start.xCoord) || Double.isNaN(start.yCoord) || Double.isNaN(start.zCoord)) {
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
                    double totalDeltaX = end.xCoord - start.xCoord;
                    double totalDeltaY = end.yCoord - start.yCoord;
                    double totalDeltaZ = end.zCoord - start.zCoord;
                    if (reachedX) {
                        deltaX = (targetX - start.xCoord) / totalDeltaX;
                    }
                    if (reachedY) {
                        deltaY = (targetY - start.yCoord) / totalDeltaY;
                    }
                    if (reachedZ) {
                        deltaZ = (targetZ - start.zCoord) / totalDeltaZ;
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
                        start = new Vec3d(targetX, start.yCoord + totalDeltaY * deltaX, start.zCoord + totalDeltaZ * deltaX);
                    } else if (deltaY < deltaZ) {
                        sideHit = endY > traceY ? EnumFacing.DOWN : EnumFacing.UP;
                        start = new Vec3d(start.xCoord + totalDeltaX * deltaY, targetY, start.zCoord + totalDeltaZ * deltaY);
                    } else {
                        sideHit = endZ > traceZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
                        start = new Vec3d(start.xCoord + totalDeltaX * deltaZ, start.yCoord + totalDeltaY * deltaZ, targetZ);
                    }
                    traceX = MathHelper.floor_double(start.xCoord) - (sideHit == EnumFacing.EAST ? 1 : 0);
                    traceY = MathHelper.floor_double(start.yCoord) - (sideHit == EnumFacing.UP ? 1 : 0);
                    traceZ = MathHelper.floor_double(start.zCoord) - (sideHit == EnumFacing.SOUTH ? 1 : 0);
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

    public List<IWorldEventListener> getListeners() {
        return this.eventListeners;
    }

    public World getMainWorld() {
        return this.mainWorld;
    }

    public void addSavedChunk(BlockSystemChunk chunk) {
        this.savedChunks.put(new ChunkPos(chunk.xPosition, chunk.zPosition), chunk);
    }

    public void removeSavedChunk(BlockSystemChunk chunk) {
        this.removeSavedChunk(new ChunkPos(chunk.xPosition, chunk.zPosition));
    }

    public void removeSavedChunk(ChunkPos pos) {
        this.savedChunks.remove(pos);
    }

    public BlockSystemChunk getSavedChunk(ChunkPos pos) {
        return this.savedChunks.get(pos);
    }

    public boolean isValid(BlockPos pos) {
        return this.bounds.isVecInside(new Vec3d(pos.getX(), pos.getY(), pos.getZ()));
    }

    public boolean isRemoved() {
        return this.removed;
    }

    public BlockSystem withBounds(AxisAlignedBB bounds) {
        this.bounds = bounds;
        return this;
    }

    public void setPositionAndRotation(double posX, double posY, double posZ, float rotationX, float rotationY, float rotationZ) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
        this.recalculateMatrices();
    }
}
