package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.gegy1000.blocksystems.server.util.math.QuatRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class TrackMessage extends BaseMessage<TrackMessage> {
    private int blockSystem;
    private double posX;
    private double posY;
    private double posZ;
    private QuatRotation rot;

    public TrackMessage() {
    }

    public TrackMessage(BlockSystem blockSystem) {
        this.blockSystem = blockSystem.getId();
        this.posX = blockSystem.posX;
        this.posY = blockSystem.posY;
        this.posZ = blockSystem.posZ;
        this.rot = blockSystem.rotation.copy();
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeDouble(this.posX);
        buf.writeDouble(this.posY);
        buf.writeDouble(this.posZ);
        this.rot.serialize(buf);
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.posX = buf.readDouble();
        this.posY = buf.readDouble();
        this.posZ = buf.readDouble();

        this.rot = new QuatRotation();
        this.rot.deserialize(buf);
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.createBlockSystem(world, this.blockSystem);
        blockSystem.setPositionAndRotation(this.posX, this.posY, this.posZ, this.rot);
        BlockSystems.PROXY.getBlockSystemHandler(world).addBlockSystem(blockSystem);
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
    }
}
