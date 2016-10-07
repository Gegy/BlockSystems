package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemPlayerHandler;
import net.gegy1000.blocksystems.server.message.BaseMessage;

public class BreakBlockMessage extends BaseMessage<BreakBlockMessage> {
    private int blockSystem;
    private BlockPos position;
    private BreakState breakState;
    private int player;

    public BreakBlockMessage() {
    }

    public BreakBlockMessage(EntityPlayer player, BlockSystem blockSystem, BlockPos position, BreakState breakState) {
        this.blockSystem = blockSystem.getID();
        this.position = position;
        this.breakState = breakState;
        this.player = player.getEntityId();
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeInt(this.player);
        buf.writeByte(this.breakState.ordinal());
        if (this.breakState != BreakState.STOP) {
            buf.writeLong(this.position.toLong());
        }
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.player = buf.readInt();
        this.breakState = BreakState.values()[buf.readByte()];
        if (this.breakState != BreakState.STOP) {
            this.position = BlockPos.fromLong(buf.readLong());
        }
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP clientPlayer, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystem(this.blockSystem);
        Entity playerEntity = world.getEntityByID(this.player);
        if (blockSystem != null && playerEntity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) playerEntity;
            BlockSystemPlayerHandler playerHandler = blockSystem.getPlayerHandler(player);
            if (this.breakState == BreakState.START) {
                playerHandler.startBreaking(this.position);
                blockSystem.getBlockState(this.position).getBlock().onBlockClicked(blockSystem, this.position, player);
            } else if (this.breakState == BreakState.STOP) {
                playerHandler.startBreaking(null);
            } else if (this.breakState == BreakState.BREAK) {
                playerHandler.breakBlock(this.position);
                playerHandler.startBreaking(null);
            }
        }
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystem(this.blockSystem);
        if (blockSystem != null) {
            BlockSystemPlayerHandler playerHandler = blockSystem.getPlayerHandler(player);
            if (this.breakState == BreakState.BREAK) {
                if (player.capabilities.isCreativeMode || ((playerHandler.getBreaking() != null && playerHandler.getBreakProgress() >= 0.9F) || playerHandler.getLastBroken() != null)) {
                    playerHandler.breakBlock(this.position);
                    playerHandler.startBreaking(null);
                    playerHandler.clearLastBroken();
                }
            } else if (this.breakState == BreakState.START) {
                playerHandler.startBreaking(this.position);
                blockSystem.getBlockState(this.position).getBlock().onBlockClicked(blockSystem, this.position, player);
            } else {
                playerHandler.startBreaking(null);
            }
            for (EntityPlayer tracking : world.getEntityTracker().getTrackingPlayers(player)) {
                if (tracking instanceof EntityPlayerMP && tracking != player) {
                    BlockSystems.NETWORK_WRAPPER.sendTo(new BreakBlockMessage(player, blockSystem, this.position, this.breakState), (EntityPlayerMP) tracking);
                }
            }
        }
    }

    public enum BreakState {
        START,
        STOP,
        BREAK
    }
}
