package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemHandler;
import net.gegy1000.blocksystems.server.blocksystem.interaction.BlockSystemInteractionHandler;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class BreakBlockMessage extends BaseMessage<BreakBlockMessage> {
    private int blockSystem;
    private BlockPos position;
    private BreakState breakState;
    private int playerId;
    private EnumFacing side;
    private EnumHand hand;

    public BreakBlockMessage() {
    }

    public BreakBlockMessage(EntityPlayer player, BlockSystem blockSystem, BlockPos position, EnumHand hand, EnumFacing side, BreakState breakState) {
        this.playerId = player.getEntityId();
        this.blockSystem = blockSystem.getId();
        this.position = position;
        this.hand = hand;
        this.side = side;
        this.breakState = breakState;
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeInt(this.playerId);
        if (this.breakState != BreakState.STOP) {
            buf.writeLong(this.position.toLong());
        }
        buf.writeByte((this.side.getIndex() & 7) << 3 | (this.hand.ordinal() & 1) << 2 | (this.breakState.ordinal() & 3));
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.playerId = buf.readInt();
        if (this.breakState != BreakState.STOP) {
            this.position = BlockPos.fromLong(buf.readLong());
        }

        int metadata = buf.readByte();
        this.side = EnumFacing.VALUES[(metadata >> 3 & 7) % EnumFacing.VALUES.length];
        this.hand = EnumHand.values()[(metadata >> 2 & 1) % EnumHand.values().length];
        this.breakState = BreakState.values()[(metadata & 3) % BreakState.values().length];
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP clientPlayer, MessageContext context) {
        BlockSystemHandler blockSystemHandler = BlockSystems.PROXY.getBlockSystemHandler(world);
        BlockSystem blockSystem = blockSystemHandler.getBlockSystem(this.blockSystem);
        Entity playerEntity = world.getEntityByID(this.playerId);
        if (blockSystem != null && playerEntity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) playerEntity;
            BlockSystemInteractionHandler interactionHandler = blockSystemHandler.getInteractionHandler(player);
            if (interactionHandler != null) {
                this.handleState(blockSystem, interactionHandler);
            }
        }
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
        BlockSystemHandler blockSystemHandler = BlockSystems.PROXY.getBlockSystemHandler(world);
        BlockSystem blockSystem = blockSystemHandler.getBlockSystem(this.blockSystem);
        if (blockSystem != null) {
            BlockSystemInteractionHandler interactionHandler = blockSystemHandler.getInteractionHandler(player);
            if (interactionHandler == null) {
                return;
            }

            // If the block is out of reach distance, stop break progress
            if (this.position != null && !interactionHandler.canInteract(blockSystem, this.position)) {
                this.position = null;
                this.breakState = BreakState.STOP;
            }

            this.handleState(blockSystem, interactionHandler);

            BlockSystems.NETWORK_WRAPPER.sendToAllTracking(this, player);
        }
    }

    private void handleState(BlockSystem blockSystem, BlockSystemInteractionHandler interactionHandler) {
        if (this.position != null) {
            interactionHandler.updateTarget(blockSystem, this.position, this.hand, this.side);
        } else {
            interactionHandler.resetTarget();
        }
        switch (this.breakState) {
            case START:
                interactionHandler.handleClick();
                break;
            case BREAK:
                interactionHandler.handleHarvest();
                break;
        }
    }

    public enum BreakState {
        START,
        STOP,
        BREAK
    }
}
