package net.gegy1000.blocksystems.server.blocksystem.interaction;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.message.blocksystem.SetBlockMessage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.block.BlockStructure;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.ILockableContainer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

import javax.annotation.Nullable;

public class ServerBlockSystemInteractionHandler implements BlockSystemInteractionHandler {
    private static final int BREAK_TICK_BUFFER = 20;

    private final EntityPlayerMP player;

    private Target target;
    private float breakProgress;
    private float breakSpeed;

    private int lastSyncProgress;

    private int finishedBreakingTick;

    public ServerBlockSystemInteractionHandler(EntityPlayerMP player) {
        this.player = player;
    }

    @Override
    public EntityPlayer getPlayer() {
        return this.player;
    }

    @Override
    public void update() {
        if (this.target != null) {
            BlockSystem blockSystem = this.target.blockSystem;
            BlockPos pos = this.target.pos;

            if (this.player.ticksExisted > this.finishedBreakingTick + BREAK_TICK_BUFFER) {
                this.resetTarget();
            } else {
                this.syncBreakProgress(this.updateBreakProgress(blockSystem, pos));
            }
        }
    }

    private int updateBreakProgress(BlockSystem blockSystem, BlockPos pos) {
        IBlockState state = blockSystem.getBlockState(pos);
        if (state.getBlock().isAir(state, blockSystem, pos)) {
            return -1;
        }
        this.breakProgress += this.breakSpeed;
        return MathHelper.floor(this.breakProgress * 10.0F);
    }

    @Override
    public void updateTarget(BlockSystem blockSystem, BlockPos pos, EnumHand hand, EnumFacing side) {
        if (this.isSameTarget(blockSystem, pos)) {
            return;
        }
        this.breakProgress = 0.0F;
        this.syncBreakProgress(-1);
        this.target = new Target(blockSystem, pos, hand, side);
    }

    @Override
    public void resetTarget() {
        this.breakProgress = 0.0F;
        this.syncBreakProgress(-1);
        this.target = null;
    }

    private boolean isSameTarget(BlockSystem blockSystem, @Nullable BlockPos pos) {
        if (this.target == null && pos == null) {
            return true;
        }
        return this.target != null && this.target.blockSystem.equals(blockSystem) && this.target.pos.equals(pos);
    }

    private void syncBreakProgress(int breakProgress) {
        if (breakProgress != this.lastSyncProgress) {
            if (this.target != null) {
                this.target.blockSystem.sendBlockBreakProgress(this.player.getEntityId(), this.target.pos, breakProgress);
            }
            this.lastSyncProgress = breakProgress;
        }
    }

    @Override
    public void handleClick() {
        if (this.target == null || !this.canInteract(this.target.blockSystem, this.target.pos)) {
            return;
        }

        BlockSystem blockSystem = this.target.blockSystem;
        BlockPos pos = this.target.pos;
        EnumHand hand = this.target.hand;
        EnumFacing side = this.target.side;

        double reach = this.player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 2.0;

        PlayerInteractEvent.LeftClickBlock event = ForgeHooks.onLeftClickBlock(this.player, pos, side, ForgeHooks.rayTraceEyeHitVec(this.player, reach));
        if (event.isCanceled()) {
            return;
        }

        if (this.player.isCreative()) {
            if (!blockSystem.extinguishFire(null, pos, side)) {
                this.handleHarvest();
            }
        } else {
            IBlockState state = blockSystem.getBlockState(pos);
            Block block = state.getBlock();

            if (!this.checkHarvestable(blockSystem, pos, hand)) {
                return;
            }

            if (!state.getBlock().isAir(state, blockSystem, pos)) {
                if (event.getUseBlock() != Event.Result.DENY) {
                    block.onBlockClicked(blockSystem, pos, this.player);
                    blockSystem.extinguishFire(null, pos, side);
                }

                float playerRelativeBlockHardness = state.getPlayerRelativeBlockHardness(this.player, this.player.world, pos);
                if (event.getUseItem() == Event.Result.DENY) {
                    return;
                }

                // If we can break this block in 1 tick, do it now
                if (!state.getBlock().isAir(state, blockSystem, pos) && playerRelativeBlockHardness >= 1.0F) {
                    this.handleHarvest();
                } else {
                    this.breakSpeed = playerRelativeBlockHardness;
                    this.finishedBreakingTick = this.player.ticksExisted + MathHelper.floor(1.0F / playerRelativeBlockHardness);
                    this.syncBreakProgress(MathHelper.floor(playerRelativeBlockHardness * 10.0F));
                }
            }
        }
    }

    @Override
    public void handleHarvest() {
        // TODO: Wrap the player in all of these calls

        if (this.target == null || !this.canInteract(this.target.blockSystem, this.target.pos)) {
            return;
        }

        if (this.player.ticksExisted >= this.finishedBreakingTick) {
            BlockSystem blockSystem = this.target.blockSystem;
            BlockPos pos = this.target.pos;
            IBlockState state = blockSystem.getBlockState(pos);
            EnumHand hand = this.target.hand;

            if (!this.checkHarvestable(blockSystem, pos, hand)) {
                return;
            }

            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(blockSystem, pos, state, this.player);
            if (!MinecraftForge.EVENT_BUS.post(event)) {
                int experience = event.getExpToDrop();

                TileEntity entity = blockSystem.getTileEntity(pos);

                ItemStack heldItem = this.player.getHeldItem(hand);

                if (heldItem.isEmpty() || !heldItem.getItem().onBlockStartBreak(heldItem, pos, this.player)) {
                    blockSystem.playEvent(this.player, 2001, pos, Block.getStateId(state));

                    if (!this.player.isCreative()) {
                        ItemStack originalStack = heldItem.isEmpty() ? ItemStack.EMPTY : heldItem.copy();
                        boolean canDrop = state.getBlock().canHarvestBlock(blockSystem, pos, this.player);

                        if (!heldItem.isEmpty()) {
                            heldItem.onBlockDestroyed(blockSystem, state, pos, this.player);
                            if (heldItem.isEmpty()) {
                                ForgeEventFactory.onPlayerDestroyItem(this.player, originalStack, hand);
                            }
                        }

                        if (this.harvestBlock(blockSystem, pos, canDrop)) {
                            if (canDrop) {
                                state.getBlock().harvestBlock(blockSystem, this.player, pos, state, entity, originalStack);
                            }
                            if (experience > 0) {
                                state.getBlock().dropXpOnBlockBreak(blockSystem, pos, experience);
                            }
                        }
                    } else {
                        this.harvestBlock(blockSystem, pos, false);
                    }
                }
            }
        } else {
            BlockSystems.LOGGER.warn("{} tried to harvest block {} ticks before progress completed!", this.player.getName(), this.finishedBreakingTick - this.player.ticksExisted);
        }

        // Block will have been updated on the client, so make sure the client is aware of the new current state
        BlockSystems.NETWORK_WRAPPER.sendTo(new SetBlockMessage(this.target.blockSystem, this.target.pos), this.player);

        this.resetTarget();
    }

    private boolean checkHarvestable(BlockSystem blockSystem, BlockPos pos, EnumHand hand) {
        ItemStack heldItem = this.player.getHeldItem(hand);

        if (this.player.isCreative() && !heldItem.isEmpty()) {
            if (!heldItem.getItem().canDestroyBlockInCreative(blockSystem, pos, heldItem, this.player)) {
                return false;
            }
        }

        if (this.player.isSpectator()) {
            return false;
        }

        if (!this.player.isAllowEdit()) {
            ItemStack stack = this.player.getHeldItem(hand);
            return !stack.isEmpty() && stack.canDestroy(blockSystem.getBlockState(pos).getBlock());
        }

        return true;
    }

    private boolean harvestBlock(BlockSystem blockSystem, BlockPos pos, boolean canDrop) {
        IBlockState state = blockSystem.getBlockState(pos);
        Block block = state.getBlock();

        if (block.removedByPlayer(state, blockSystem, pos, this.player, canDrop)) {
            block.onBlockDestroyedByPlayer(blockSystem, pos, state);
            return true;
        }

        return false;
    }

    @Override
    public EnumActionResult handleInteract(BlockSystem blockSystem, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!this.canInteract(blockSystem, pos)) {
            return EnumActionResult.PASS;
        }

        if (this.player.isSpectator()) {
            return this.handleSpectatorInteract(blockSystem, pos);
        }

        ItemStack heldItem = this.player.getHeldItem(hand);

        double reach = this.player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 2.0;
        PlayerInteractEvent.RightClickBlock event = ForgeHooks.onRightClickBlock(this.player, hand, pos, side, ForgeHooks.rayTraceEyeHitVec(this.player, reach));
        if (event.isCanceled()) {
            return event.getCancellationResult();
        }

        EnumActionResult itemUseFirst = heldItem.onItemUseFirst(this.player, blockSystem, pos, hand, side, hitX, hitY, hitZ);
        if (itemUseFirst != EnumActionResult.PASS) {
            return itemUseFirst;
        }

        if (event.getUseBlock() != Event.Result.DENY) {
            if (!this.player.isSneaking() || heldItem.doesSneakBypassUse(blockSystem, pos, this.player) || event.getUseBlock() == Event.Result.ALLOW) {
                IBlockState state = blockSystem.getBlockState(pos);
                if (state.getBlock().onBlockActivated(blockSystem, pos, state, this.player, hand, side, hitX, hitY, hitZ)) {
                    return EnumActionResult.SUCCESS;
                }
            }
        }

        if (heldItem.isEmpty() || this.player.getCooldownTracker().hasCooldown(heldItem.getItem())) {
            return EnumActionResult.PASS;
        }

        // FIXME: Gross Mojang hardcoding
        if (heldItem.getItem() instanceof ItemBlock && !this.player.canUseCommandBlock()) {
            Block block = ((ItemBlock) heldItem.getItem()).getBlock();
            if (block instanceof BlockCommandBlock || block instanceof BlockStructure) {
                return EnumActionResult.FAIL;
            }
        }

        int previousMeta = heldItem.getMetadata();
        int previousCount = heldItem.getCount();
        ItemStack previousItem = heldItem.copy();

        if (event.getUseItem() != Event.Result.DENY) {
            EnumActionResult useResult = heldItem.onItemUse(this.player, blockSystem, pos, hand, side, hitX, hitY, hitZ);
            if (this.player.isCreative()) {
                heldItem.setItemDamage(previousMeta);
                heldItem.setCount(previousCount);
            } else if (heldItem.isEmpty()) {
                ForgeEventFactory.onPlayerDestroyItem(this.player, previousItem, hand);
            }
            return useResult;
        }

        return EnumActionResult.PASS;
    }

    private EnumActionResult handleSpectatorInteract(BlockSystem blockSystem, BlockPos pos) {
        TileEntity entity = blockSystem.getTileEntity(pos);

        if (entity instanceof ILockableContainer) {
            Block block = blockSystem.getBlockState(pos).getBlock();
            ILockableContainer container = (ILockableContainer) entity;

            if (container instanceof TileEntityChest && block instanceof BlockChest) {
                container = ((BlockChest) block).getLockableContainer(blockSystem, pos);
            }

            if (container != null) {
                this.player.displayGUIChest(container);
                return EnumActionResult.SUCCESS;
            }
        } else if (entity instanceof IInventory) {
            this.player.displayGUIChest((IInventory) entity);
            return EnumActionResult.SUCCESS;
        }

        return EnumActionResult.PASS;
    }

    @Override
    public void handlePick(BlockSystem blockSystem, RayTraceResult mouseOver, EnumHand hand) {
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
