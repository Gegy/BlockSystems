package net.gegy1000.blocksystems.client;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderHandler;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemPlayerHandler;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BlockSystems.MODID, value = Side.CLIENT)
public class ClientEventHandler {
    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == Phase.START) {
            WorldClient world = MINECRAFT.world;
            if (world != null && world.isRemote && !BlockSystems.PROXY.isPaused(world)) {
                BlockSystems.PROXY.getBlockSystemHandler(world).update();
                BlockSystemRenderHandler.update();
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickAir(PlayerInteractEvent.RightClickEmpty event) {
        EntityPlayer player = event.getEntityPlayer();
        ServerBlockSystemHandler structureHandler = BlockSystems.PROXY.getBlockSystemHandler(event.getWorld());
        structureHandler.interact(structureHandler.get(structureHandler.getMousedOver(player), player), player, event.getHand());
    }

    @SubscribeEvent
    public static void onClickAir(PlayerInteractEvent.LeftClickEmpty event) {
        EntityPlayer player = event.getEntityPlayer();
        BlockSystem mousedOver = BlockSystems.PROXY.getBlockSystemHandler(event.getWorld()).getMousedOver(player);
        if (mousedOver != null) {
            BlockSystemPlayerHandler mouseOverHandler = BlockSystems.PROXY.getBlockSystemHandler(event.getWorld()).get(mousedOver, player);
            if (mouseOverHandler != null) {
                RayTraceResult mouseOver = mouseOverHandler.getMouseOver();
                if (mouseOver != null && mouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                    mouseOverHandler.clickBlock(mouseOver.getBlockPos());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        World world = event.getWorld();
        ServerBlockSystemHandler structureHandler = BlockSystems.PROXY.getBlockSystemHandler(world);
        if (entity instanceof EntityPlayer) {
            structureHandler.addPlayer((EntityPlayer) entity);
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
    public static void onWorldUnload(WorldEvent.Unload event) {
        BlockSystems.PROXY.getBlockSystemHandler(event.getWorld()).unloadWorld();
        if (event.getWorld().isRemote) {
            BlockSystemRenderHandler.removeAll();
        }
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        GlStateManager.enableFog();
        EntityPlayer player = MINECRAFT.player;
        float partialTicks = event.getPartialTicks();
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        BlockSystemRenderHandler.render(player, playerX, playerY, playerZ, partialTicks);
        GlStateManager.disableFog();
    }
}
