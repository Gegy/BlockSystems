package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class SetBlockMessage extends BaseMessage<SetBlockMessage> {
    private int blockSystem;
    private BlockPos position;
    private IBlockState state;

    public SetBlockMessage() {
    }

    public SetBlockMessage(BlockSystem system, BlockPos position) {
        this.blockSystem = system.getId();
        this.position = position;
        this.state = system.getBlockState(position);
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeLong(this.position.toLong());
        buf.writeInt(Block.getStateId(this.state));
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.position = BlockPos.fromLong(buf.readLong());
        this.state = Block.getStateById(buf.readInt());
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystem(this.blockSystem);
        if (blockSystem != null) {
            int x = this.position.getX();
            int y = this.position.getY();
            int z = this.position.getZ();
            blockSystem.markBlockRangeForRenderUpdate(x, y, z, x, y, z);
            blockSystem.setBlockState(this.position, this.state);
        }
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
    }
}
