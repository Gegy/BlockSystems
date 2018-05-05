package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.blocksystem.BlockSystemClient;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class UnloadChunkMessage extends BaseMessage<UnloadChunkMessage> {
    private int blockSystem;
    private int chunkX;
    private int chunkZ;

    public UnloadChunkMessage() {
    }

    public UnloadChunkMessage(BlockSystem blockSystem, int chunkX, int chunkZ) {
        this.blockSystem = blockSystem.getId();
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeInt(this.chunkX);
        buf.writeInt(this.chunkZ);
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystem(this.blockSystem);
        if (blockSystem != null) {
            BlockSystemClient clientSystem = (BlockSystemClient) blockSystem;
            clientSystem.loadChunkAction(this.chunkX, this.chunkZ, false);
        }
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
    }
}
