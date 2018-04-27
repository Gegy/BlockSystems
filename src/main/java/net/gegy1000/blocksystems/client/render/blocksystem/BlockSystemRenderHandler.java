package net.gegy1000.blocksystems.client.render.blocksystem;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.util.math.QuatRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class BlockSystemRenderHandler {
    private static final Map<BlockSystem, BlockSystemRenderer> RENDERERS = new HashMap<>();
    private static final Set<BlockSystem> REMOVE = new HashSet<>();

    public static void addBlockSystem(BlockSystem blockSystem) {
        RENDERERS.put(blockSystem, new BlockSystemRenderer(blockSystem));
    }

    public static void removeBlockSystem(BlockSystem blockSystem) {
        REMOVE.add(blockSystem);
    }

    public static void removeAll() {
        for (Map.Entry<BlockSystem, BlockSystemRenderer> entry : RENDERERS.entrySet()) {
            entry.getValue().delete();
        }
        RENDERERS.clear();
        REMOVE.clear();
    }

    public static BlockSystemRenderer get(BlockSystem blockSystem) {
        return RENDERERS.get(blockSystem);
    }

    public static void render(EntityPlayer player, double playerX, double playerY, double playerZ, float partialTicks) {
        for (BlockSystem blockSystem : REMOVE) {
            BlockSystemRenderer renderer = RENDERERS.remove(blockSystem);
            if (renderer != null) {
                renderer.delete();
            }
        }
        GlStateManager.depthMask(true);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.disableBlend();
        for (Map.Entry<BlockSystem, BlockSystemRenderer> entry : RENDERERS.entrySet()) {
            BlockSystem blockSystem = entry.getKey();
            BlockSystemRenderer renderer = entry.getValue();
            double entityX = blockSystem.prevPosX + (blockSystem.posX - blockSystem.prevPosX) * partialTicks;
            double entityY = blockSystem.prevPosY + (blockSystem.posY - blockSystem.prevPosY) * partialTicks;
            double entityZ = blockSystem.prevPosZ + (blockSystem.posZ - blockSystem.prevPosZ) * partialTicks;

            QuatRotation rotation = blockSystem.prevRotation.slerp(blockSystem.rotation, partialTicks);
            int framerate = Math.min(Minecraft.getDebugFPS(), Minecraft.getMinecraft().gameSettings.limitFramerate);
            framerate = Math.max(framerate, 60);
            long finishTimeNano = Math.max((long) (1000000000 / framerate / 4) - System.nanoTime(), 0);
            renderer.renderBlockSystem(player, entityX - playerX, entityY - playerY, entityZ - playerZ, rotation, partialTicks, finishTimeNano);
        }
        GlStateManager.enableCull();
        GlStateManager.cullFace(GlStateManager.CullFace.BACK);
    }

    public static void update() {
        /*for (Map.Entry<BlockSystem, BlockSystemRenderer> entry : RENDERERS.entrySet()) {
            entry.getValue().update();
        }*/
    }
}
