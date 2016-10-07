package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.chunk.BlockSystemChunk;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MultiBlockUpdateMessage extends BaseMessage<MultiBlockUpdateMessage> {
    private int blockSystem;
    private ChunkPos pos;
    private BlockUpdateData[] updates;

    public MultiBlockUpdateMessage() {
    }

    public MultiBlockUpdateMessage(BlockSystem blockSystem, BlockSystemChunk chunk, int updateCount, short[] updates) {
        this.blockSystem = blockSystem.getID();
        this.pos = new ChunkPos(chunk.xPosition, chunk.zPosition);
        this.updates = new BlockUpdateData[updateCount];
        for (int i = 0; i < this.updates.length; ++i) {
            this.updates[i] = new BlockUpdateData(updates[i], chunk);
        }
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeInt(this.pos.chunkXPos);
        buf.writeInt(this.pos.chunkZPos);
        buf.writeInt(this.updates.length);
        for (BlockUpdateData update : this.updates) {
            buf.writeShort(update.getOffset());
            buf.writeInt(Block.getStateId(update.getState()));
        }
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.pos = new ChunkPos(buf.readInt(), buf.readInt());
        this.updates = new BlockUpdateData[buf.readInt()];
        for (int i = 0; i < this.updates.length; ++i) {
            this.updates[i] = new BlockUpdateData(buf.readShort(), Block.getStateById(buf.readInt()));
        }
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystem(this.blockSystem);
        if (blockSystem != null) {
            for (BlockUpdateData update : this.updates) {
                BlockPos pos = update.getPos();
                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();
                blockSystem.markBlockRangeForRenderUpdate(x, y, z, x, y, z);
                blockSystem.setBlockState(pos, update.getState(), 3);
            }
        }
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
    }

    public class BlockUpdateData {
        private final short pos;
        private final IBlockState state;

        public BlockUpdateData(short pos, IBlockState state) {
            this.pos = pos;
            this.state = state;
        }

        public BlockUpdateData(short pos, BlockSystemChunk chunk) {
            this.pos = pos;
            this.state = chunk.getBlockState(this.getPos());
        }

        public BlockPos getPos() {
            return new BlockPos(MultiBlockUpdateMessage.this.pos.getBlock(this.pos >> 12 & 15, this.pos & 255, this.pos >> 8 & 15));
        }

        public short getOffset() {
            return this.pos;
        }

        public IBlockState getState() {
            return this.state;
        }
    }
}
