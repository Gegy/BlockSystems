package net.gegy1000.blocksystems.server.blocksystem;

import net.gegy1000.blocksystems.server.util.RotatedAABB;
import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerBlockSystemHandler {
    protected World world;

    protected Map<Integer, BlockSystem> blockSystems = new HashMap<>();
    protected Map<EntityPlayer, Map.Entry<BlockSystem, RayTraceResult>> mouseOver = new HashMap<>();

    public ServerBlockSystemHandler(World world) {
        this.world = world;
    }

    public void update() {
        List<Integer> removed = new ArrayList<>();
        for (Map.Entry<Integer, BlockSystem> blockSystem : this.blockSystems.entrySet()) {
            if (blockSystem.getValue().isRemoved()) {
                removed.add(blockSystem.getKey());
            } else {
                blockSystem.getValue().tick();
            }
        }
        for (Integer system : removed) {
            this.blockSystems.remove(system);
        }
        if (this.isServer()) {
            this.mouseOver.clear();
            for (EntityPlayer player : this.world.playerEntities) {
                this.mouseOver.put(player, this.getSelectedBlock(player, null));
            }
        }
    }

    public boolean onItemRightClick(EntityPlayer player, EnumHand hand) {
        boolean success = false;
        success |= this.interact(this.get(this.getMousedOver(player), player), player, hand);
        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem != null) {
            int prevSize = heldItem.stackSize;
            ActionResult<ItemStack> result = heldItem.useItemRightClick(player.world, player, hand);
            if (result.getType() != EnumActionResult.SUCCESS) {
                for (Map.Entry<Integer, BlockSystem> blockSystem : this.blockSystems.entrySet()) {
                    result = heldItem.useItemRightClick(blockSystem.getValue(), player, hand);
                    if (result.getType() == EnumActionResult.SUCCESS) {
                        break;
                    }
                }
            }
            success |= result.getType() == EnumActionResult.SUCCESS;
            ItemStack output = result.getResult();
            if (output != heldItem || output.stackSize != prevSize) {
                if (player.capabilities.isCreativeMode && output.stackSize < prevSize) {
                    output.stackSize = prevSize;
                }
                player.setHeldItem(hand, output);
                if (output.stackSize <= 0) {
                    player.setHeldItem(hand, null);
                    ForgeEventFactory.onPlayerDestroyItem(player, output, hand);
                }
            }
        }
        return success;
    }

    public boolean interact(BlockSystemPlayerHandler handler, EntityPlayer player, EnumHand hand) {
        if (handler != null) {
            RayTraceResult mouseOver = handler.getMouseOver();
            if (mouseOver != null && mouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                return handler.interact(hand);
            }
        }
        return false;
    }

    public Map.Entry<BlockSystem, RayTraceResult> getSelectedBlock(EntityPlayer player, RayTraceResult defaultResult) {
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        double x = player.posX;
        double y = player.posY + player.getEyeHeight();
        double z = player.posZ;
        Vec3d start = new Vec3d(x, y, z);
        float pitchHorizontalFactor = -MathHelper.cos(-pitch * 0.017453292F);
        float deltaY = MathHelper.sin(-pitch * 0.017453292F);
        float deltaX = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI) * pitchHorizontalFactor;
        float deltaZ = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI) * pitchHorizontalFactor;
        double reach = 5.0;
        if (player instanceof EntityPlayerMP) {
            reach = ((EntityPlayerMP) player).interactionManager.getBlockReachDistance();
        }
        Vec3d end = start.addVector(deltaX * reach, deltaY * reach, deltaZ * reach);
        Map<BlockSystem, RayTraceResult> results = new HashMap<>();
        for (Map.Entry<Integer, BlockSystem> entry : this.blockSystems.entrySet()) {
            BlockSystem blockSystem = entry.getValue();
            RotatedAABB bounds = blockSystem.getRotatedBounds();
            if (bounds.aabb().intersectsWith(player.getEntityBoundingBox().expandXyz(5.0))) {
                RayTraceResult result = blockSystem.rayTraceBlocks(start, end);
                if (result != null && result.typeOfHit != RayTraceResult.Type.MISS) {
                    results.put(blockSystem, result);
                }
            }
        }
        if (results.size() > 0) {
            Map.Entry<BlockSystem, RayTraceResult> closest = null;
            double closestDistance = Double.MAX_VALUE;
            for (Map.Entry<BlockSystem, RayTraceResult> entry : results.entrySet()) {
                BlockSystem blockSystem = entry.getKey();
                RayTraceResult result = entry.getValue();
                double distance = result.hitVec.distanceTo(blockSystem.getUntransformedPosition(start));
                if (distance < closestDistance) {
                    closest = entry;
                    closestDistance = distance;
                }
            }
            if (defaultResult != null && defaultResult.typeOfHit != RayTraceResult.Type.MISS) {
                double distance = defaultResult.hitVec.distanceTo(start);
                if (distance < closestDistance) {
                    return null;
                }
            }
            return closest;
        }
        return null;
    }

    public void addBlockSystem(BlockSystem blockSystem) {
        this.blockSystems.put(blockSystem.getID(), blockSystem);
        if (!this.world.isRemote) {
            BlockSystemSavedData.get(this.world).addBlockSystem(blockSystem);
        }
        for (EntityPlayer player : this.world.playerEntities) {
            blockSystem.addPlayerHandler(player);
        }
    }

    public void loadBlockSystem(BlockSystem blockSystem) {
        this.blockSystems.put(blockSystem.getID(), blockSystem);
    }

    public void unloadWorld() {
        this.blockSystems.clear();
    }

    public void removeBlockSystem(BlockSystem blockSystem) {
        this.blockSystems.remove(blockSystem.getID());
        if (!this.world.isRemote) {
            BlockSystemSavedData.get(this.world).removeBlockSystem(blockSystem);
        }
    }

    public void removeBlockSystem(int id) {
        this.blockSystems.remove(id);
    }

    public Map<Integer, BlockSystem> getBlockSystems() {
        return this.blockSystems;
    }

    public BlockSystem getMousedOver(EntityPlayer player) {
        Map.Entry<BlockSystem, RayTraceResult> mouseOver = this.mouseOver.get(player);
        return mouseOver != null ? mouseOver.getKey() : null;
    }

    public RayTraceResult getMousedOverResult(EntityPlayer player) {
        Map.Entry<BlockSystem, RayTraceResult> mouseOver = this.mouseOver.get(player);
        return mouseOver != null ? mouseOver.getValue() : null;
    }

    public BlockSystemPlayerHandler get(BlockSystem blockSystem, EntityPlayer player) {
        return blockSystem != null ? blockSystem.getPlayerHandlers().get(player) : null;
    }

    public BlockSystem getBlockSystem(int id) {
        return this.blockSystems.get(id);
    }

    public void addPlayer(EntityPlayer player) {
        for (Map.Entry<Integer, BlockSystem> entry : this.blockSystems.entrySet()) {
            entry.getValue().addPlayerHandler(player);
        }
    }

    public void removePlayer(EntityPlayer player) {
        for (Map.Entry<Integer, BlockSystem> entry : this.blockSystems.entrySet()) {
            entry.getValue().removePlayerHandler(player);
        }
    }

    public boolean isServer() {
        return true;
    }
}
