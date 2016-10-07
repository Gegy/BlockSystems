package net.gegy1000.blocksystems.server.message;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public abstract class BaseMessage<T extends BaseMessage<T>> implements IMessage, IMessageHandler<T, IMessage> {
    @Override
    public void fromBytes(ByteBuf buf) {
        this.deserialize(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        this.serialize(buf);
    }

    public abstract void serialize(ByteBuf buf);
    public abstract void deserialize(ByteBuf buf);

    public abstract void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context);

    public abstract void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context);

    @Override
    public IMessage onMessage(T message, MessageContext context) {
        BlockSystems.PROXY.handleMessage(message, context);
        return null;
    }
}
