package net.gegy1000.blocksystems.server.blocksystem;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.message.blocksystem.BreakBlockMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.InteractBlockMessage;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.ForgeEventFactory;

import javax.vecmath.Point3d;

public class BlockSystemPlayerHandler {
    private final BlockSystem blockSystem;
    private final EntityPlayer player;

    private BlockPos lastBroken;

    private BlockPos breaking;
    private boolean isHittingBlock;

    private float breakProgress;
    private int interactCooldown;
    private int breakDelay;
    private RayTraceResult mouseOver;

    private float breakSoundTimer;

    private double managedPosX;
    private double managedPosZ;

    public BlockSystemPlayerHandler(BlockSystem blockSystem, EntityPlayer player) {
        this.blockSystem = blockSystem;
        this.player = player;
    }

    public void update() {
        if (this.interactCooldown > 0) {
            this.interactCooldown--;
        }
        if (this.breakDelay > 0) {
            this.breakDelay--;
        }
        if (this.breaking != null) {
            if (this.updateBreaking()) {
                this.player.swingArm(EnumHand.MAIN_HAND);
            }
        }
    }

    public boolean clickBlock(BlockPos pos) {
        if (this.interactCooldown <= 0) {
            this.interactCooldown = 3;
            if (this.player.capabilities.isCreativeMode) {
                this.breakBlock(pos);
            } else if (!this.isHittingBlock || this.breaking == null) {
                if (BlockSystems.PROXY.isClientPlayer(this.player)) {
                    BlockSystems.NETWORK_WRAPPER.sendToServer(new BreakBlockMessage(this.player, this.blockSystem, pos, BreakBlockMessage.BreakState.START));
                }
                IBlockState state = this.blockSystem.getBlockState(pos);
                boolean realBlock = state.getMaterial() != Material.AIR;
                if (realBlock && this.breakProgress == 0.0F) {
                    state.getBlock().onBlockClicked(this.blockSystem, pos, this.player);
                }
                if (realBlock && state.getPlayerRelativeBlockHardness(this.player, this.blockSystem, pos) >= 1.0F) {
                    this.breakBlock(pos);
                } else {
                    this.isHittingBlock = true;
                    this.startBreaking(pos);
                    this.breakSoundTimer = 0.0F;
                }
            }
            return true;
        }
        return false;
    }

    public boolean updateBreaking() {
        if (this.breaking != null && this.isHittingBlock) {
            IBlockState state = this.blockSystem.getBlockState(this.breaking);
            Block block = state.getBlock();
            if (state.getMaterial() == Material.AIR) {
                this.isHittingBlock = false;
                return false;
            } else {
                this.breakProgress += state.getPlayerRelativeBlockHardness(this.player, this.blockSystem, this.breaking);
                if (this.blockSystem.getMainWorld().isRemote) {
                    if (this.breakSoundTimer % 4.0F == 0.0F) {
                        SoundType soundType = block.getSoundType();
                        Point3d point = this.blockSystem.getTransformedPosition(new Point3d(this.breaking.getX(), this.breaking.getY(), this.breaking.getZ()));
                        BlockSystems.PROXY.playSound(new PositionedSoundRecord(soundType.getHitSound(), SoundCategory.NEUTRAL, (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F, new BlockPos(point.getX(), point.getY(), point.getZ())));
                    }
                    this.breakSoundTimer++;
                    if (this.breakProgress >= 1.0F) {
                        this.isHittingBlock = false;
                        if (BlockSystems.PROXY.isClientPlayer(this.player)) {
                            this.breakBlock(this.breaking);
                        }
                        this.breakProgress = 0.0F;
                        this.breakSoundTimer = 0.0F;
                        this.interactCooldown = 5;
                    }
                } else {
                    if (this.breakProgress >= 0.9F) {
                        BlockPos pos = this.breaking;
                        this.startBreaking(null);
                        this.isHittingBlock = false;
                        this.breakProgress = 0.0F;
                        this.lastBroken = pos;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public void breakBlock(BlockPos pos) {
        IBlockState state = this.blockSystem.getBlockState(pos);
        Block block = state.getBlock();
        if (this.blockSystem.getMainWorld().isRemote) {
            this.blockSystem.playEvent(2001, pos, Block.getStateId(state));
            if (BlockSystems.PROXY.isClientPlayer(this.player)) {
                BlockSystems.NETWORK_WRAPPER.sendToServer(new BreakBlockMessage(this.player, this.blockSystem, pos, BreakBlockMessage.BreakState.BREAK));
            }
        }
        if (!this.player.capabilities.isCreativeMode) {
            ItemStack heldItem = this.player.getHeldItemMainhand();
            if (!heldItem.isEmpty()) {
                heldItem.onBlockDestroyed(this.blockSystem, state, pos, this.player);
                if (heldItem.isEmpty()) {
                    ForgeEventFactory.onPlayerDestroyItem(this.player, heldItem, EnumHand.MAIN_HAND);
                    this.player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                }
            }
            block.harvestBlock(this.blockSystem, this.player, pos, state, this.blockSystem.getTileEntity(pos), heldItem);
        }
        boolean removed = block.removedByPlayer(state, this.blockSystem, pos, this.player, false);
        if (removed) {
            block.onBlockDestroyedByPlayer(this.blockSystem, pos, state);
        }
    }

    public boolean interact(EnumHand hand) {
        if (this.interactCooldown <= 0) {
            this.interactCooldown = 3;
            BlockPos pos = this.mouseOver.getBlockPos();
            if (this.blockSystem.isValid(pos)) {
                Vec3d hitVec = this.mouseOver.hitVec;
                IBlockState state = this.blockSystem.getBlockState(pos);
                float hitX = (float) (hitVec.x - pos.getX());
                float hitY = (float) (hitVec.y - pos.getY());
                float hitZ = (float) (hitVec.z - pos.getZ());
                ItemStack heldItem = this.player.getHeldItem(hand);
                BlockSystems.NETWORK_WRAPPER.sendToServer(new InteractBlockMessage(this.blockSystem, pos, this.mouseOver.sideHit, hitX, hitY, hitZ, hand));
                if (state.getBlock().onBlockActivated(this.blockSystem, pos, state, this.player, hand, this.mouseOver.sideHit, hitX, hitY, hitZ)) {
                    this.player.swingArm(hand);
                    return true;
                } else if (!heldItem.isEmpty()) {
                    float rotationYaw = this.player.rotationYaw;
                    float rotationPitch = this.player.rotationPitch;
                    Vec3d vec = new Vec3d(rotationPitch, rotationYaw, 0.0F);
                    vec.normalize();
                    Vec3d actualRotation = this.blockSystem.getTransformedVector(vec);
                    this.player.rotationPitch = (float) actualRotation.x;
                    this.player.rotationYaw = (float) actualRotation.y;
                    int originalCount = heldItem.getCount();
                    EnumActionResult actionResult = heldItem.onItemUse(this.player, this.blockSystem, pos, hand, this.mouseOver.sideHit, hitX, hitY, hitZ);
                    this.player.rotationPitch = rotationPitch;
                    this.player.rotationYaw = rotationYaw;
                    if (actionResult == EnumActionResult.SUCCESS) {
                        this.player.swingArm(hand);
                    }
                    if (this.player.capabilities.isCreativeMode && heldItem.getCount() < originalCount) {
                        heldItem.setCount(originalCount);
                    }
                    if (heldItem.isEmpty()) {
                        this.player.setHeldItem(hand, ItemStack.EMPTY);
                        ForgeEventFactory.onPlayerDestroyItem(this.player, heldItem, hand);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public RayTraceResult getMouseOver() {
        return this.mouseOver;
    }

    public void setMouseOver(RayTraceResult mouseOver) {
        this.mouseOver = mouseOver;
    }

    public BlockPos getBreaking() {
        return this.breaking;
    }

    public BlockPos getLastBroken() {
        return this.lastBroken;
    }

    public float getBreakProgress() {
        return this.breakProgress;
    }

    public void clearLastBroken() {
        this.lastBroken = null;
    }

    public void startBreaking(BlockPos position) {
        if (position != null) {
            this.clearLastBroken();
        }
        if (position == null && this.breaking != null && BlockSystems.PROXY.isClientPlayer(this.player)) {
            BlockSystems.NETWORK_WRAPPER.sendToServer(new BreakBlockMessage(this.player, this.blockSystem, null, BreakBlockMessage.BreakState.STOP));
        }
        this.breaking = position;
        this.isHittingBlock = true;
        this.breakProgress = 0.0F;
    }

    public boolean onPickBlock() {
        if (this.mouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            IBlockState state = this.blockSystem.getBlockState(this.mouseOver.getBlockPos());
            if (!state.getBlock().isAir(state, this.blockSystem, this.mouseOver.getBlockPos())) {
                BlockSystems.PROXY.pickBlock(this.player, this.mouseOver, this.blockSystem, state);
            }
        }
        return false;
    }

    public double getManagedPosX() {
        return this.managedPosX;
    }

    public double getManagedPosZ() {
        return this.managedPosZ;
    }

    public void setManagedPosX(double managedPosX) {
        this.managedPosX = managedPosX;
    }

    public void setManagedPosZ(double managedPosZ) {
        this.managedPosZ = managedPosZ;
    }
}
