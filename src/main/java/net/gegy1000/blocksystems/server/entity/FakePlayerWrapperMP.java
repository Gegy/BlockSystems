package net.gegy1000.blocksystems.server.entity;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;

public class FakePlayerWrapperMP extends EntityPlayerMP {
    private final EntityPlayerMP original;

    public FakePlayerWrapperMP(EntityPlayerMP player, BlockSystem system, BlockPos targetPos) {
        super(player.getServer(), player.getServerWorld(), player.getGameProfile(), player.interactionManager);
        this.original = player;
        this.capabilities = this.original.capabilities;
        this.posX = system.posX + targetPos.getX();
        this.posY = system.posY + targetPos.getY();
        this.posZ = system.posZ + targetPos.getZ();
        float pitchHorizontalFactor = -MathHelper.cos((float) -Math.toRadians(this.original.rotationPitch));
        float deltaX = MathHelper.sin((float) -Math.toRadians(this.original.rotationYaw - 180.0F)) * pitchHorizontalFactor;
        float deltaY = MathHelper.sin((float) -Math.toRadians(this.original.rotationPitch));
        float deltaZ = MathHelper.cos((float) -Math.toRadians(this.original.rotationYaw - 180.0F)) * pitchHorizontalFactor;
        Vector3f vec = new Vector3f(deltaX, deltaY, deltaZ);
        system.rotation.getMatrix().transform(vec);
        this.rotationYaw = (float) -Math.atan2(vec.x, vec.z) * 180.0F / (float) Math.PI;
        this.rotationPitch = (float) (-Math.asin(vec.y) * (180 / Math.PI));
        this.connection = this.original.connection;
        this.inventory = this.original.inventory;
        this.inventoryContainer = this.original.inventoryContainer;
        this.swingingHand = this.original.swingingHand;
        this.activeItemStack = this.original.getActiveItemStack();
        this.activeItemStackUseCount = this.original.getItemInUseCount();
    }

    @Override
    public ItemStack getItemStackFromSlot(EntityEquipmentSlot slot) {
        return this.original.getItemStackFromSlot(slot);
    }

    @Override
    public void setItemStackToSlot(EntityEquipmentSlot slot, ItemStack stack) {
        this.original.setItemStackToSlot(slot, stack);
    }

    @Override
    public void addExperienceLevel(int levels) {
        this.original.addExperienceLevel(levels);
    }

    @Override
    public void addExperience(int amount) {
        this.original.addExperience(amount);
    }

    @Override
    public void onItemPickup(Entity entityIn, int quantity) {
        this.original.onItemPickup(entityIn, quantity);
    }

    @Nullable
    @Override
    public Entity changeDimension(int dimensionIn) {
        return this.original.changeDimension(dimensionIn);
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        return this.original.isEntityInvulnerable(source);
    }

    @Override
    public boolean isSpectator() {
        return this.original.isSpectator();
    }

    @Override
    public boolean isCreative() {
        return this.original.isCreative();
    }
}
