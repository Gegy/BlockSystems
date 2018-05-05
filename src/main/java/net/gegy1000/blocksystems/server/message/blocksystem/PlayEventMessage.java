package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.message.BaseMessage;

public class PlayEventMessage extends BaseMessage<PlayEventMessage> {
    private int blockSystem;
    private BlockPos position;
    private int type;
    private int data;
    private boolean broadcast;

    public PlayEventMessage() {
    }

    public PlayEventMessage(BlockSystem blockSystem, BlockPos position, int type, int data, boolean broadcast) {
        this.blockSystem = blockSystem.getId();
        this.position = position;
        this.type = type;
        this.data = data;
        this.broadcast = broadcast;
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeLong(this.position.toLong());
        buf.writeInt(this.type);
        buf.writeInt(this.data);
        buf.writeBoolean(this.broadcast);
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.position = BlockPos.fromLong(buf.readLong());
        this.type = buf.readInt();
        this.data = buf.readInt();
        this.broadcast = buf.readBoolean();
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystem(this.blockSystem);
        if (blockSystem != null) {
            if (this.broadcast) {
                blockSystem.playBroadcastSound(this.type, this.position, this.data);
            } else {
                blockSystem.playEventServer(null, this.type, this.position, this.data);
            }
        }
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
    }
}
