package net.gegy1000.blocksystems.server.entity;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;

public class FakePlayerWrapper extends EntityPlayerMP {
    private EntityPlayerMP original;
    private BlockSystem system;
    private BlockPos targetPos;
    public FakePlayerWrapper(EntityPlayerMP player, BlockSystem system, BlockPos targetPos) {
        super(player.getServer(), player.getServerWorld(), player.getGameProfile(), player.interactionManager);
        this.original = player;
        this.system = system;
        this.targetPos = targetPos;
        this.capabilities = original.capabilities;
        this.posX = system.posX + targetPos.getX();
        this.posY = system.posY + targetPos.getY();
        this.posZ = system.posZ + targetPos.getZ();
        float pitchHorizontalFactor = -MathHelper.cos((float) -Math.toRadians(original.rotationPitch));
        float deltaX = MathHelper.sin((float) -Math.toRadians(original.rotationYaw - 180.0F)) * pitchHorizontalFactor;
        float deltaY = MathHelper.sin((float) -Math.toRadians(original.rotationPitch));
        float deltaZ = MathHelper.cos((float) -Math.toRadians(original.rotationYaw - 180.0F)) * pitchHorizontalFactor;
        Vector3f vec = new Vector3f(deltaX,deltaY,deltaZ);
        system.rotation.getMatrix().transform(vec);
        this.rotationYaw = (float)-Math.atan2(vec.x, vec.z) * 180.0F / (float)Math.PI;
        this.rotationPitch = (float)(-Math.asin(vec.y) * (180 / Math.PI));
        this.connection = original.connection;
        this.inventory = original.inventory;
        this.inventoryContainer = original.inventoryContainer;
        this.swingingHand = original.swingingHand;
        this.activeItemStack = original.getActiveItemStack();
        this.activeItemStackUseCount = original.getItemInUseCount();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
    }

    @Override
    public void addExperienceLevel(int levels) {
        original.addExperienceLevel(levels);
    }

    @Override
    public void addExperience(int amount) {
        original.addExperience(amount);
    }

    @Override
    public void onItemPickup(Entity entityIn, int quantity) {
        original.onItemPickup(entityIn,quantity);
    }

    @Nullable
    @Override
    public Entity changeDimension(int dimensionIn) {
        return original.changeDimension(dimensionIn);
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        return original.isEntityInvulnerable(source);
    }

    @Override
    public boolean isSpectator() {
        return original.isSpectator();
    }

    @Override
    public boolean isCreative() {
        return original.isCreative();
    }
}
