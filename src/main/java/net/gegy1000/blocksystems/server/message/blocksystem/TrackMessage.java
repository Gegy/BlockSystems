package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.message.BaseMessage;

public class TrackMessage extends BaseMessage<TrackMessage> {
    private int blockSystem;
    private double posX;
    private double posY;
    private double posZ;
    private float rotationX;
    private float rotationY;
    private float rotationZ;

    public TrackMessage() {
    }

    public TrackMessage(BlockSystem blockSystem) {
        this.blockSystem = blockSystem.getID();
        this.posX = blockSystem.posX;
        this.posY = blockSystem.posY;
        this.posZ = blockSystem.posZ;
        this.rotationX = blockSystem.rotationX;
        this.rotationY = blockSystem.rotationY;
        this.rotationZ = blockSystem.rotationZ;
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeDouble(this.posX);
        buf.writeDouble(this.posY);
        buf.writeDouble(this.posZ);
        buf.writeFloat(this.rotationX);
        buf.writeFloat(this.rotationY);
        buf.writeFloat(this.rotationZ);
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.posX = buf.readDouble();
        this.posY = buf.readDouble();
        this.posZ = buf.readDouble();
        this.rotationX = buf.readFloat();
        this.rotationY = buf.readFloat();
        this.rotationZ = buf.readFloat();
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.createBlockSystem(world, this.blockSystem);
        blockSystem.setPositionAndRotation(this.posX, this.posY, this.posZ, this.rotationX, this.rotationY, this.rotationZ);
        BlockSystems.PROXY.getBlockSystemHandler(world).addBlockSystem(blockSystem);
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
    }
}
