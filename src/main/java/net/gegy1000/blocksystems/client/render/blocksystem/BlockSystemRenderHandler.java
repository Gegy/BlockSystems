package net.gegy1000.blocksystems.client.render.blocksystem;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.gegy1000.blocksystems.client.blocksystem.BlockSystemClient;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.util.math.QuatRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BlockSystemRenderHandler {
    private static final Int2ObjectMap<BlockSystemRenderer> RENDERERS = new Int2ObjectOpenHashMap<>();
    private static final IntSet REMOVAL_QUEUE = new IntOpenHashSet();

    public static void addBlockSystem(BlockSystemClient blockSystem) {
        RENDERERS.put(blockSystem.getId(), new BlockSystemRenderer(blockSystem));
    }

    public static void removeBlockSystem(BlockSystem blockSystem) {
        REMOVAL_QUEUE.add(blockSystem.getId());
    }

    public static void unload() {
        RENDERERS.values().forEach(BlockSystemRenderer::delete);
        RENDERERS.clear();
        REMOVAL_QUEUE.clear();
    }

    public static BlockSystemRenderer get(BlockSystem blockSystem) {
        return RENDERERS.get(blockSystem.getId());
    }

    public static void render(EntityPlayer player, double playerX, double playerY, double playerZ, float partialTicks) {
        int framerate = Math.min(Minecraft.getDebugFPS(), Minecraft.getMinecraft().gameSettings.limitFramerate);
        framerate = Math.max(framerate, 60);
        long finishTimeNano = Math.max((long) (1000000000 / framerate / 4) - System.nanoTime(), 0);

        GlStateManager.depthMask(true);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.disableBlend();

        for (BlockSystemRenderer renderer : RENDERERS.values()) {
            BlockSystemClient blockSystem = renderer.getBlockSystem();
            double entityX = blockSystem.prevPosX + (blockSystem.posX - blockSystem.prevPosX) * partialTicks;
            double entityY = blockSystem.prevPosY + (blockSystem.posY - blockSystem.prevPosY) * partialTicks;
            double entityZ = blockSystem.prevPosZ + (blockSystem.posZ - blockSystem.prevPosZ) * partialTicks;

            QuatRotation rotation = blockSystem.prevRotation.slerp(blockSystem.rotation, partialTicks);
            renderer.renderBlockSystem(player, entityX - playerX, entityY - playerY, entityZ - playerZ, rotation, partialTicks, finishTimeNano);
        }

        GlStateManager.enableCull();
        GlStateManager.cullFace(GlStateManager.CullFace.BACK);
    }

    public static void update() {
        for (int blockSystem : REMOVAL_QUEUE) {
            BlockSystemRenderer renderer = RENDERERS.remove(blockSystem);
            if (renderer != null) {
                renderer.delete();
            }
        }

        /*for (Map.Entry<BlockSystem, BlockSystemRenderer> entry : RENDERERS.entrySet()) {
            entry.getValue().update();
        }*/
    }
}
