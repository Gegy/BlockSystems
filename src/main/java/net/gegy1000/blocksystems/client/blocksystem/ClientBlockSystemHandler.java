package net.gegy1000.blocksystems.client.blocksystem;

import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderHandler;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemPlayerHandler;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.Map;

public class ClientBlockSystemHandler extends ServerBlockSystemHandler {
    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();

    private BlockSystem mousedOver;
    private RayTraceResult mousedOverResult;

    public ClientBlockSystemHandler(World world) {
        super(world);
    }

    @Override
    public void update() {
        super.update();

        EntityPlayerSP clientPlayer = MINECRAFT.player;

        if (clientPlayer != null) {
            Map.Entry<BlockSystem, RayTraceResult> mouseOver = this.getSelectedBlock(clientPlayer, MINECRAFT.objectMouseOver);

            BlockSystem currentMousedOver = mouseOver != null ? mouseOver.getKey() : null;

            if (mouseOver != null) {
                BlockSystemPlayerHandler mouseOverHandler = this.get(mouseOver.getKey(), clientPlayer);
                if (mouseOverHandler != null) {
                    RayTraceResult prevMouseOver = mouseOverHandler.getMouseOver();
                    mouseOverHandler.setMouseOver(mouseOver.getValue());
                    if (prevMouseOver == null || !prevMouseOver.getBlockPos().equals(mouseOver.getValue().getBlockPos())) {
                        mouseOverHandler.startBreaking(null);
                    }
                }
            }

            if (this.mousedOver != null && this.mousedOver != currentMousedOver) {
                BlockSystemPlayerHandler mouseOverHandler = this.get(this.mousedOver, clientPlayer);
                if (mouseOverHandler != null) {
                    mouseOverHandler.startBreaking(null);
                    mouseOverHandler.setMouseOver(null);
                }
            }

            this.mousedOver = currentMousedOver;
            this.mousedOverResult = mouseOver != null ? mouseOver.getValue() : null;

            if (this.mousedOver != null && !MINECRAFT.gameSettings.keyBindAttack.isKeyDown()) {
                BlockSystemPlayerHandler mouseOverHandler = this.get(this.mousedOver, clientPlayer);
                if (mouseOverHandler != null) {
                    mouseOverHandler.startBreaking(null);
                }
            }

            if (this.mousedOver != null && MINECRAFT.gameSettings.keyBindPickBlock.isKeyDown()) {
                BlockSystemPlayerHandler handler = this.get(this.mousedOver, clientPlayer);
                if (handler != null) {
                    handler.onPickBlock();
                }
            }
        }
    }

    @Override
    public BlockSystem getMousedOver(EntityPlayer player) {
        return this.mousedOver;
    }

    @Override
    public RayTraceResult getMousedOverResult(EntityPlayer player) {
        return this.mousedOverResult;
    }

    @Override
    public void addBlockSystem(BlockSystem blockSystem) {
        super.addBlockSystem(blockSystem);
        BlockSystemRenderHandler.addBlockSystem(blockSystem);
        blockSystem.addPlayerHandler(MINECRAFT.player);
    }

    @Override
    public void removeBlockSystem(int id) {
        BlockSystem blockSystem = this.getBlockSystem(id);
        if (blockSystem != null) {
            BlockSystemRenderHandler.removeBlockSystem(blockSystem);
        }
        super.removeBlockSystem(id);
    }

    @Override
    public void removeBlockSystem(BlockSystem blockSystem) {
        super.removeBlockSystem(blockSystem);
        BlockSystemRenderHandler.removeBlockSystem(blockSystem);
    }

    @Override
    public boolean isServer() {
        return false;
    }
}
