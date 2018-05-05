package net.gegy1000.blocksystems.client.blocksystem;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.interaction.BlockSystemInteractionHandler;
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
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientBlockSystemInteractionHandler implements BlockSystemInteractionHandler {
    private static final int INTERACTION_COOLDOWN = 3;
    private static final int BREAK_COOLDOWN = 5;

    private final EntityPlayer player;

    private Target target;
    private float breakProgress;

    private int interactCooldown;
    private int breakSoundTimer;

    public ClientBlockSystemInteractionHandler(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public EntityPlayer getPlayer() {
        return this.player;
    }

    @Override
    public void update() {
        if (this.interactCooldown > 0) {
            this.interactCooldown--;
        }
        if (this.target != null && this.updateBreaking(this.target.blockSystem, this.target.pos)) {
            this.player.swingArm(this.target.hand);
        }
    }

    private boolean updateBreaking(BlockSystem blockSystem, BlockPos pos) {
        IBlockState state = blockSystem.getBlockState(pos);
        Block block = state.getBlock();

        if (state.getMaterial() == Material.AIR) {
            this.resetTarget();
            return false;
        }

        this.breakProgress += state.getPlayerRelativeBlockHardness(this.player, blockSystem, pos);
        if (this.breakSoundTimer++ % 4 == 0) {
            SoundType soundType = block.getSoundType();
            BlockPos point = blockSystem.getTransformedPosition(pos);
            BlockSystems.PROXY.playSound(new PositionedSoundRecord(soundType.getHitSound(), SoundCategory.NEUTRAL, (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F, point));
        }

        if (this.breakProgress >= 1.0F) {
            this.handleHarvest();
            this.breakSoundTimer = 0;
            this.interactCooldown = BREAK_COOLDOWN;
        }

        return true;
    }

    @Override
    public void updateTarget(BlockSystem blockSystem, BlockPos pos, EnumHand hand, EnumFacing side) {
        if (this.isSameTarget(blockSystem, pos)) {
            return;
        }
        this.breakProgress = 0.0F;
        this.breakProgress = 0;
        this.target = new Target(blockSystem, pos, hand, side);
    }

    @Override
    public void resetTarget() {
        this.breakProgress = 0.0F;
        this.breakSoundTimer = 0;
        this.target = null;
    }

    private boolean isSameTarget(BlockSystem blockSystem, BlockPos pos) {
        return this.target != null && this.target.blockSystem.equals(blockSystem) && this.target.pos.equals(pos);
    }

    @Override
    public void handleClick() {
        if (this.target == null) {
            return;
        }

        if (this.interactCooldown <= 0) {
            this.interactCooldown = INTERACTION_COOLDOWN;

            this.sendBreakState(BreakBlockMessage.BreakState.START);

            if (!this.player.isCreative()) {
                BlockSystem blockSystem = this.target.blockSystem;
                BlockPos pos = this.target.pos;

                // If we are able to destroy the block in one click, do it now
                IBlockState state = blockSystem.getBlockState(pos);
                if (state.getMaterial() != Material.AIR && state.getPlayerRelativeBlockHardness(this.player, blockSystem, pos) >= 1.0F) {
                    this.handleHarvest();
                }
            } else {
                this.handleHarvest();
            }
        }
    }

    @Override
    public void handleHarvest() {
        if (this.target == null) {
            return;
        }

        BlockSystem blockSystem = this.target.blockSystem;
        BlockPos pos = this.target.pos;
        EnumHand hand = this.target.hand;

        IBlockState state = blockSystem.getBlockState(pos);
        Block block = state.getBlock();

        blockSystem.playEvent(2001, pos, Block.getStateId(state));
        this.sendBreakState(BreakBlockMessage.BreakState.BREAK);

        if (!this.player.isCreative()) {
            ItemStack heldItem = this.player.getHeldItem(hand);
            if (!heldItem.isEmpty()) {
                heldItem.onBlockDestroyed(blockSystem, state, pos, this.player);
                if (heldItem.isEmpty()) {
                    ForgeEventFactory.onPlayerDestroyItem(this.player, heldItem, hand);
                    this.player.setHeldItem(hand, ItemStack.EMPTY);
                }
            }
            block.harvestBlock(blockSystem, this.player, pos, state, blockSystem.getTileEntity(pos), heldItem);
        }

        if (block.removedByPlayer(state, blockSystem, pos, this.player, false)) {
            block.onBlockDestroyedByPlayer(blockSystem, pos, state);
        }

        this.resetTarget();
    }

    @Override
    public EnumActionResult handleInteract(BlockSystem blockSystem, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (this.interactCooldown > INTERACTION_COOLDOWN) {
            return EnumActionResult.PASS;
        }

        this.interactCooldown = INTERACTION_COOLDOWN;
        if (!blockSystem.isValid(pos)) {
            return EnumActionResult.PASS;
        }

        IBlockState state = blockSystem.getBlockState(pos);
        ItemStack heldItem = this.player.getHeldItem(hand);
        BlockSystems.NETWORK_WRAPPER.sendToServer(new InteractBlockMessage(blockSystem, pos, side, hitX, hitY, hitZ, hand));

        if (state.getBlock().onBlockActivated(blockSystem, pos, state, this.player, hand, side, hitX, hitY, hitZ)) {
            this.player.swingArm(hand);
            return EnumActionResult.SUCCESS;
        } else if (!heldItem.isEmpty()) {
            // TODO: Rather wrap player here

            int originalCount = heldItem.getCount();
            EnumActionResult actionResult = heldItem.onItemUse(this.player, blockSystem, pos, hand, side, hitX, hitY, hitZ);

            if (actionResult == EnumActionResult.SUCCESS) {
                this.player.swingArm(hand);
            }

            if (this.player.isCreative() && heldItem.getCount() < originalCount) {
                heldItem.setCount(originalCount);
            }

            if (heldItem.isEmpty()) {
                this.player.setHeldItem(hand, ItemStack.EMPTY);
                ForgeEventFactory.onPlayerDestroyItem(this.player, heldItem, hand);
            }

            return actionResult;
        }

        return EnumActionResult.PASS;
    }

    @Override
    public void handlePick(BlockSystem blockSystem, RayTraceResult mouseOver, EnumHand hand) {
        if (mouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = mouseOver.getBlockPos();
            IBlockState state = blockSystem.getBlockState(pos);
            if (!state.getBlock().isAir(state, blockSystem, pos)) {
                BlockSystems.PROXY.pickBlock(this.player, mouseOver, blockSystem, state);
            }
        }
    }

    private void sendBreakState(BreakBlockMessage.BreakState state) {
        if (this.target == null) {
            return;
        }

        BreakBlockMessage message = new BreakBlockMessage(this.player, this.target.blockSystem, this.target.pos, this.target.hand, this.target.side, state);
        BlockSystems.NETWORK_WRAPPER.sendToServer(message);
    }

    private static class Target {
        private final BlockSystem blockSystem;
        private final BlockPos pos;
        private final EnumHand hand;
        private final EnumFacing side;

        private Target(BlockSystem blockSystem, BlockPos pos, EnumHand hand, EnumFacing side) {
            this.blockSystem = blockSystem;
            this.pos = pos;
            this.hand = hand;
            this.side = side;
        }
    }
}
