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
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Map;

public class ServerEventHandler {
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        ServerBlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
        if (handler != null) {
            handler.update();
        }
        if (world instanceof WorldServer) {
            BlockSystemTrackingHandler trackingHandler = BlockSystemTrackingHandler.get((WorldServer) world);
            trackingHandler.update();
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        World world = event.getWorld();
        Entity entity = event.getEntity();
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            ServerBlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
            Map<Integer, BlockSystem> blockSystems = handler.getBlockSystems();
            handler.addPlayer(player);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        World world = entity.world;
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (world instanceof WorldServer && player instanceof EntityPlayerMP) {
                Map<Integer, BlockSystem> blockSystems = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystems();
                for (Map.Entry<Integer, BlockSystem> entry : blockSystems.entrySet()) {
                    BlockSystem blockSystem = entry.getValue();
                    if (blockSystem instanceof BlockSystemServer) {
                        ((BlockSystemServer) blockSystem).getChunkTracker().removePlayer((EntityPlayerMP) player);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        EntityPlayer player = event.getEntityPlayer();
        EnumHand hand = event.getHand();
        ServerBlockSystemHandler structureHandler = BlockSystems.PROXY.getBlockSystemHandler(event.getWorld());
        if (structureHandler.onItemRightClick(player, hand)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        BlockSystemSavedData.get(world);
        if (world instanceof WorldServer) {
            BlockSystemTrackingHandler.add((WorldServer) world);
        }
        BlockSystemHooks.onWorldLoad(world);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        BlockSystemSavedData.get(world);
        if (world instanceof WorldServer) {
            BlockSystemTrackingHandler.remove((WorldServer) world);
        }
        BlockSystemWorldAccess.unloadWorld(world);
        BlockSystemHooks.onWorldUnload(world);
    }
}
