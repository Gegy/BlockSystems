package net.gegy1000.blocksystems.client;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderHandler;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemHandler;
import net.gegy1000.blocksystems.server.blocksystem.interaction.BlockSystemInteractionHandler;
import net.gegy1000.blocksystems.server.util.collision.EncompassedAABB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BlockSystems.MODID, value = Side.CLIENT)
public class ClientEventHandler {
    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == Phase.START) {
            WorldClient world = MINECRAFT.world;
            if (world != null && !BlockSystems.PROXY.isPaused(world)) {
                BlockSystemHandler blockSystemHandler = BlockSystems.PROXY.getBlockSystemHandler(world);
                ClientEventHandler.updateMouseOver(blockSystemHandler, MINECRAFT.player, MINECRAFT.objectMouseOver);

                blockSystemHandler.update();
                BlockSystemRenderHandler.update();

                BlockSystemInteractionHandler interactionHandler = blockSystemHandler.getInteractionHandler(MINECRAFT.player);
                if (interactionHandler != null) {
                    BlockSystem mouseOverSystem = ClientProxy.getMouseOverSystem();
                    RayTraceResult mouseOver = ClientProxy.getMouseOver();

                    if (mouseOverSystem == null || mouseOver == null) {
                        return;
                    }

                    if (!MINECRAFT.gameSettings.keyBindAttack.isKeyDown()) {
                        interactionHandler.resetTarget();
                    }

                    if (MINECRAFT.gameSettings.keyBindPickBlock.isKeyDown()) {
                        interactionHandler.handlePick(mouseOverSystem, mouseOver, EnumHand.MAIN_HAND);
                    }
                }
            }
        }
    }

    private static void updateMouseOver(BlockSystemHandler handler, EntityPlayer player, RayTraceResult currentResult) {
        ClientProxy.resetMouseOver();

        // TODO: Wrap with fake player
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        double x = player.posX;
        double y = player.posY + player.getEyeHeight();
        double z = player.posZ;
        Vec3d start = new Vec3d(x, y, z);
        float pitchHorizontalFactor = -MathHelper.cos((float) -Math.toRadians(pitch));
        float deltaY = MathHelper.sin((float) -Math.toRadians(pitch));
        float deltaX = MathHelper.sin((float) -Math.toRadians(yaw - 180.0F)) * pitchHorizontalFactor;
        float deltaZ = MathHelper.cos((float) -Math.toRadians(yaw - 180.0F)) * pitchHorizontalFactor;

        double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        Vec3d end = start.addVector(deltaX * reach, deltaY * reach, deltaZ * reach);

        Map<BlockSystem, RayTraceResult> results = new HashMap<>();
        for (BlockSystem blockSystem : handler.getBlockSystems()) {
            EncompassedAABB bounds = blockSystem.getRotatedBounds();
            if (bounds.intersects(player.getEntityBoundingBox().grow(reach + 1.0))) {
                RayTraceResult result = blockSystem.rayTraceBlocks(start, end);
                if (result != null && result.typeOfHit != RayTraceResult.Type.MISS) {
                    results.put(blockSystem, result);
                }
            }
        }

        if (!results.isEmpty()) {
            Map.Entry<BlockSystem, RayTraceResult> closest = null;

            double closestDistance = Double.MAX_VALUE;
            for (Map.Entry<BlockSystem, RayTraceResult> entry : results.entrySet()) {
                BlockSystem blockSystem = entry.getKey();
                RayTraceResult result = entry.getValue();
                double distance = result.hitVec.distanceTo(blockSystem.getTransform().toLocalPos(start));
                if (distance < closestDistance) {
                    closest = entry;
                    closestDistance = distance;
                }
            }

            if (currentResult != null && currentResult.typeOfHit != RayTraceResult.Type.MISS) {
                double distance = currentResult.hitVec.distanceTo(start);
                if (distance < closestDistance) {
                    return;
                }
            }

            if (closest != null) {
                ClientProxy.updateMouseOver(closest.getKey(), closest.getValue());
            }
        }
    }

    @SubscribeEvent
    public static void onClickAir(PlayerInteractEvent.LeftClickEmpty event) {
        EntityPlayer player = event.getEntityPlayer();
        BlockSystemInteractionHandler interactionHandler = BlockSystems.PROXY.getBlockSystemHandler(event.getWorld()).getInteractionHandler(player);
        if (interactionHandler != null) {
            ClientEventHandler.updateTarget(event.getWorld(), player);
            interactionHandler.handleClick();
        }
    }

    private static void updateTarget(World world, EntityPlayer player) {
        BlockSystemInteractionHandler interactionHandler = BlockSystems.PROXY.getBlockSystemHandler(world).getInteractionHandler(player);
        if (interactionHandler != null) {
            BlockSystem mouseOverSystem = ClientProxy.getMouseOverSystem();
            RayTraceResult mouseOver = ClientProxy.getMouseOver();

            if (mouseOverSystem == null || mouseOver == null) {
                return;
            }

            if (mouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                interactionHandler.updateTarget(mouseOverSystem, mouseOver.getBlockPos(), EnumHand.MAIN_HAND, mouseOver.sideHit);
            }
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
