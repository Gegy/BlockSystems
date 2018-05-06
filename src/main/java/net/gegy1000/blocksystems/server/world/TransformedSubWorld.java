package net.gegy1000.blocksystems.server.world;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import net.gegy1000.blocksystems.server.util.WorldTransform;
import net.gegy1000.blocksystems.server.util.math.QuatRotation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.vecmath.Point3d;
import java.util.List;

public abstract class TransformedSubWorld extends World {
    public static final ThreadLocal<TransformedSubWorld> TRANSFORMING = new ThreadLocal<>();

    protected final World parentWorld;

    protected final WorldTransform transform = new WorldTransform();

    public double posX;
    public double posY;
    public double posZ;
    public double prevPosX;
    public double prevPosY;
    public double prevPosZ;

    public QuatRotation rotation = new QuatRotation();
    public QuatRotation prevRotation = new QuatRotation();

    protected TransformedSubWorld(World parentWorld, ISaveHandler saveHandler) {
        super(saveHandler, parentWorld.getWorldInfo(), parentWorld.provider, parentWorld.profiler, parentWorld.isRemote);
        this.parentWorld = parentWorld;
    }

    @Override
    public void tick() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.prevRotation = this.rotation.copy();

        super.tick();

        if (this.posX != this.prevPosX || this.posY != this.prevPosY || this.posZ != this.prevPosZ || !this.rotation.equals(this.prevRotation)) {
            this.recalculateTransform();
        }
    }

    protected void recalculateTransform() {
        this.transform.calculate(this.posX, this.posY, this.posZ, this.rotation);
    }

    public void setPositionAndRotation(double posX, double posY, double posZ, QuatRotation rotation) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotation = rotation;
        this.recalculateTransform();
    }

    @Override
    public EntityPlayer getClosestPlayer(double posX, double posY, double posZ, double distance, boolean spectator) {
        Point3d transformed = this.transform.toGlobalPos(new Point3d(posX, posY, posZ));
        posX = transformed.getX();
        posY = transformed.getY();
        posZ = transformed.getZ();
        return this.parentWorld.getClosestPlayer(posX, posY, posZ, distance, spectator);
    }

    @Override
    public EntityPlayer getNearestAttackablePlayer(double posX, double posY, double posZ, double maxXZDistance, double maxYDistance, Function<EntityPlayer, Double> serializer, Predicate<EntityPlayer> selector) {
        Point3d transformed = this.transform.toGlobalPos(new Point3d(posX, posY, posZ));
        posX = transformed.getX();
        posY = transformed.getY();
        posZ = transformed.getZ();
        return this.parentWorld.getNearestAttackablePlayer(posX, posY, posZ, maxXZDistance, maxYDistance, serializer, selector);
    }

    @Override
    public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity, AxisAlignedBB bounds) {
        //TODO Transform bounds (or ignore? will blocksystems have entities)
        return this.parentWorld.getEntitiesWithinAABBExcludingEntity(entity, bounds);
    }

    @Override
    public List<Entity> getEntitiesInAABBexcluding(Entity entity, AxisAlignedBB bounds, Predicate<? super Entity> selector) {
        return this.parentWorld.getEntitiesInAABBexcluding(entity, bounds, selector);
    }

    @Override
    public boolean spawnEntity(Entity entity) {
        entity.world = this.parentWorld;
        Point3d transformedPosition = this.transform.toGlobalPos(new Point3d(entity.posX, entity.posY, entity.posZ));
        entity.setPosition(transformedPosition.x, transformedPosition.y, transformedPosition.z);
        return this.parentWorld.spawnEntity(entity);
    }

    @Override
    public RayTraceResult rayTraceBlocks(Vec3d start, Vec3d end, boolean traceLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
        return super.rayTraceBlocks(this.transform.toLocalPos(start), this.transform.toLocalPos(end), traceLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock);
    }

    @Override
    public void spawnParticle(EnumParticleTypes particleType, double posX, double posY, double posZ, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        Point3d transformed = this.transform.toGlobalPos(new Point3d(posX, posY, posZ));
        posX = transformed.getX();
        posY = transformed.getY();
        posZ = transformed.getZ();
        if (particleType != EnumParticleTypes.REDSTONE) {
            Vec3d transformedVelocity = this.transform.toGlobalVector(new Vec3d(xSpeed, ySpeed, zSpeed));
            xSpeed = transformedVelocity.x;
            ySpeed = transformedVelocity.y;
            zSpeed = transformedVelocity.z;
        }
        super.spawnParticle(particleType, posX, posY, posZ, xSpeed, ySpeed, zSpeed, parameters);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void spawnParticle(EnumParticleTypes particleType, boolean ignoreRange, double posX, double posY, double posZ, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        Point3d transformed = this.transform.toGlobalPos(new Point3d(posX, posY, posZ));
        posX = transformed.getX();
        posY = transformed.getY();
        posZ = transformed.getZ();
        if (particleType != EnumParticleTypes.REDSTONE) {
            Vec3d transformedVelocity = this.transform.toGlobalVector(new Vec3d(xSpeed, ySpeed, zSpeed));
            xSpeed = transformedVelocity.x;
            ySpeed = transformedVelocity.y;
            zSpeed = transformedVelocity.z;
        }
        super.spawnParticle(particleType, ignoreRange, posX, posY, posZ, xSpeed, ySpeed, zSpeed, parameters);
    }

    @Override
    public void playSound(EntityPlayer player, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        Point3d transformed = this.transform.toGlobalPos(new Point3d(x, y, z));
        x = transformed.getX();
        y = transformed.getY();
        z = transformed.getZ();
        super.playSound(player, x, y, z, sound, category, volume, pitch);
    }

    public void playEventServer(EntityPlayer player, int type, BlockPos pos, int data) {
        super.playEvent(player, type, pos, data);
    }

    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
        TRANSFORMING.set(this);
        super.playEvent(player, type, pos, data);
        TRANSFORMING.set(null);
    }

    public WorldTransform getTransform() {
        return this.transform;
    }

    public World getParentWorld() {
        return this.parentWorld;
    }

    public void deserialize(NBTTagCompound compound) {
        this.posX = compound.getDouble("pos_x");
        this.posY = compound.getDouble("pos_y");
        this.posZ = compound.getDouble("pos_z");
        this.rotation.deserialize(compound.getCompoundTag("rot"));
        this.recalculateTransform();
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        compound.setDouble("pos_x", this.posX);
        compound.setDouble("pos_y", this.posY);
        compound.setDouble("pos_z", this.posZ);
        compound.setTag("rot", this.rotation.serialize(new NBTTagCompound()));
        return compound;
    }
}
