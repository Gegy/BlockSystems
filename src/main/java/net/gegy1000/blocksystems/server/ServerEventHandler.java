package net.gegy1000.blocksystems.server;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemHandler;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemTrackingHandler;
import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.util.math.QuatRotation;
import net.gegy1000.blocksystems.server.world.BlockSystemWorldAccess;
import net.gegy1000.blocksystems.server.world.data.BlockSystemSavedData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

import javax.vecmath.Point3d;
import java.util.Collection;

@Mod.EventBusSubscriber(modid = BlockSystems.MODID)
public class ServerEventHandler {
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == Phase.START) {
            World world = event.world;
            if (!BlockSystems.PROXY.isPaused(world)) {
                BlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
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
    @SubscribeEvent
    public static void onEntityTick(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        AxisAlignedBB entityBounds = entity.getEntityBoundingBox().grow(0.02);

        BlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(entity.world);
        for (BlockSystem blockSystem : handler.getBlockSystems()) {
            AxisAlignedBB encompassing = blockSystem.getRotatedBounds().getAabb();
            if (entityBounds.intersects(encompassing)) {
                boolean collides = false;
                for (AxisAlignedBB bb : blockSystem.getCollisionBoxes(entity, entityBounds)) {
                    if (entityBounds.intersects(bb)) {
                        collides = true;
                        break;
                    }
                }

                if (collides) {
                    QuatRotation rotation = blockSystem.rotation.difference(blockSystem.prevRotation);

                    Point3d local = blockSystem.getTransform().toLocalPrevPos(new Point3d(entity.posX, entity.posY, entity.posZ));
                    Point3d transformedGlobal = blockSystem.getTransform().toGlobalPos(local);

                    float transformedYaw = entity.rotationYaw + (float) rotation.toYaw();
                    float transformedPitch = entity.rotationPitch + (float) rotation.toPitch();
                    entity.setPositionAndRotation(transformedGlobal.x, transformedGlobal.y, transformedGlobal.z, transformedYaw, transformedPitch);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCollectCollisionBoxes(GetCollisionBoxesEvent event) {
        World world = event.getWorld();
        if (!(world instanceof BlockSystem)) {
            Entity entity = event.getEntity();
            AxisAlignedBB entityBounds = event.getAabb();

            // TODO: This is O(n) and not ideal
            BlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
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
        Entity entity = event.getEntity();
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            BlockSystems.PROXY.getBlockSystemHandler(event.getWorld()).addPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityPlayer) {
            BlockSystems.PROXY.getBlockSystemHandler(entity.world).removePlayer((EntityPlayer) entity);
        }
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        BlockSystems.PROXY.getBlockSystemHandler(event.player.world).removePlayer(event.player);
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world instanceof WorldServer) {
            BlockSystemSavedData.get((WorldServer) world);
            BlockSystemTrackingHandler.add((WorldServer) world);
        }
        BlockSystemHooks.onWorldLoad(world);
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world instanceof WorldServer) {
            BlockSystemSavedData.get((WorldServer) world);
            BlockSystemTrackingHandler.remove((WorldServer) world);
        }
        BlockSystemWorldAccess.unloadWorld(world);
        BlockSystemHooks.onWorldUnload(world);
        BlockSystems.PROXY.unload(world);
    }
}
