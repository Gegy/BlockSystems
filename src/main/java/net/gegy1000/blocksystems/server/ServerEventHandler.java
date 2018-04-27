package net.gegy1000.blocksystems.server;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemTrackingHandler;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;
import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.world.BlockSystemWorldAccess;
import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

import java.util.Collection;

@Mod.EventBusSubscriber(modid = BlockSystems.MODID)
public class ServerEventHandler {
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == Phase.START) {
            World world = event.world;
            if (!BlockSystems.PROXY.isPaused(world)) {
                ServerBlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
                if (handler != null) {
                    handler.update();
                }
                if (world instanceof WorldServer) {
                    BlockSystemTrackingHandler trackingHandler = BlockSystemTrackingHandler.get((WorldServer) world);
                    trackingHandler.update();
                }
            }
        }
    }

    // TODO: Add back with OBB collision
/*    @SubscribeEvent
    public static void onEntityTick(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        AxisAlignedBB entityBounds = entity.getEntityBoundingBox();
    }*/

    @SubscribeEvent
    public static void onCollectCollisionBoxes(GetCollisionBoxesEvent event) {
        World world = event.getWorld();
        if (!(world instanceof BlockSystem)) {
            Entity entity = event.getEntity();
            AxisAlignedBB entityBounds = event.getAabb();

            // TODO: This is O(n) and not ideal
            ServerBlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
            Collection<BlockSystem> blockSystems = handler.getBlockSystems();
            for (BlockSystem blockSystem : blockSystems) {
                // TODO: Keep track of bounds that are being *used*, not just the maximum bounds for the whole blocksystem
                AxisAlignedBB encompassing = blockSystem.getRotatedBounds().getAabb();
                if (entityBounds.intersects(encompassing)) {
                    event.getCollisionBoxesList().addAll(blockSystem.getCollisionBoxes(entity, entityBounds));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        World world = event.getWorld();
        Entity entity = event.getEntity();
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            ServerBlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
            handler.addPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityPlayer) {
            removePlayer((EntityPlayer) entity, entity.world);
        }
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        removePlayer(event.player, event.player.world);
    }

    private static void removePlayer(EntityPlayer player, World world) {
        if (world instanceof WorldServer && player instanceof EntityPlayerMP) {
            Collection<BlockSystem> blockSystems = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystems();
            for (BlockSystem blockSystem : blockSystems) {
                if (blockSystem instanceof BlockSystemServer) {
                    ((BlockSystemServer) blockSystem).getChunkTracker().removePlayer((EntityPlayerMP) player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        EntityPlayer player = event.getEntityPlayer();
        EnumHand hand = event.getHand();
        ServerBlockSystemHandler structureHandler = BlockSystems.PROXY.getBlockSystemHandler(event.getWorld());
        if (structureHandler.onItemRightClick(player, hand)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        BlockSystemSavedData.get(world);
        if (world instanceof WorldServer) {
            BlockSystemTrackingHandler.add((WorldServer) world);
        }
        BlockSystemHooks.onWorldLoad(world);
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        BlockSystemSavedData.get(world);
        if (world instanceof WorldServer) {
            BlockSystemTrackingHandler.remove((WorldServer) world);
        }
        BlockSystemWorldAccess.unloadWorld(world);
        BlockSystemHooks.onWorldUnload(world);
    }
}
