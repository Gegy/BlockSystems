package net.gegy1000.blocksystems.server.message.blocksystem;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.blocksystem.BlockSystemClient;
import net.gegy1000.blocksystems.client.blocksystem.chunk.ClientBlockSystemChunk;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.gegy1000.blocksystems.server.blocksystem.chunk.PartitionedChunk;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;
import java.util.Map;

public class ChunkMessage extends BaseMessage<ChunkMessage> {
    private int blockSystem;
    private int x;
    private int z;
    private int availableSections;
    private byte[] buffer;
    private List<TileUpdate> tileUpdates;
    private boolean loadChunk;

    public ChunkMessage() {
    }

    public ChunkMessage(BlockSystemServer blockSystem, PartitionedChunk chunk, int mask) {
        this.blockSystem = blockSystem.getId();
        this.x = chunk.x;
        this.z = chunk.z;
        this.loadChunk = mask == 0xFFFF;
        boolean hasSkylight = !blockSystem.getMainWorld().provider.isNether();
        this.buffer = new byte[this.calculateChunkSize(chunk, hasSkylight, mask)];
        this.availableSections = this.extractChunkData(new PacketBuffer(this.getWriteBuffer()), chunk, hasSkylight, mask);
        this.tileUpdates = Lists.newArrayList();
        for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet()) {
            BlockPos pos = entry.getKey();
            TileEntity tile = entry.getValue();
            int storageIndex = pos.getY() >> 4;
            if (this.loadChunk || (mask & 1 << storageIndex) != 0) {
                this.tileUpdates.add(new TileUpdate(chunk.fromPartition(tile.getPos()), tile.getUpdateTag()));
            }
        }
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeInt(this.x);
        buf.writeInt(this.z);
        buf.writeBoolean(this.loadChunk);
        buf.writeInt(this.availableSections);
        buf.writeInt(this.buffer.length);
        buf.writeBytes(this.buffer);
        buf.writeShort(this.tileUpdates.size());
        for (TileUpdate update : this.tileUpdates) {
            update.serialize(buf);
        }
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.x = buf.readInt();
        this.z = buf.readInt();
        this.loadChunk = buf.readBoolean();
        this.availableSections = buf.readInt();
        int bufferSize = buf.readInt();
        this.buffer = new byte[bufferSize];
        buf.readBytes(this.buffer);
        int tileCount = buf.readUnsignedShort();
        this.tileUpdates = Lists.newArrayList();
        for (int i = 0; i < tileCount; i++) {
            this.tileUpdates.add(TileUpdate.deserialize(buf));
        }
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystem(this.blockSystem);
        if (blockSystem != null) {
            BlockSystemClient clientSystem = (BlockSystemClient) blockSystem;
            if (this.loadChunk) {
                clientSystem.loadChunkAction(this.x, this.z, true);
            }
            ClientBlockSystemChunk chunk = (ClientBlockSystemChunk) clientSystem.getChunkFromChunkCoords(this.x, this.z);
            chunk.read(this.getReadBuffer(), this.availableSections, this.loadChunk);
            clientSystem.markBlockRangeForRenderUpdate(this.x << 4, 0, this.z << 4, (this.x << 4) + 15, 255, (this.z << 4) + 15);
            if (!this.loadChunk || !(clientSystem.provider instanceof WorldProviderSurface)) {
                chunk.resetRelightChecks();
            }
            for (TileUpdate update : this.tileUpdates) {
                TileEntity tile = clientSystem.getTileEntity(update.localPos);
                if (tile != null) {
                    tile.handleUpdateTag(update.tag);
                }
            }
        }
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
    }

    private ByteBuf getWriteBuffer() {
        ByteBuf buffer = Unpooled.wrappedBuffer(this.buffer);
        buffer.writerIndex(0);
        return buffer;
    }

    private int extractChunkData(PacketBuffer buffer, PartitionedChunk chunk, boolean skylight, int mask) {
        int availableSections = 0;
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (int storageIndex = 0; storageIndex < sections.length; storageIndex++) {
            ExtendedBlockStorage section = sections[storageIndex];
            if (section != Chunk.NULL_BLOCK_STORAGE && (!this.loadChunk || !section.isEmpty()) && (mask & 1 << storageIndex) != 0) {
                availableSections |= 1 << storageIndex;
                section.getData().write(buffer);
                buffer.writeBytes(section.getBlockLight().getData());
                if (skylight) {
                    buffer.writeBytes(section.getSkyLight().getData());
                }
            }
        }
        if (this.loadChunk) {
            buffer.writeBytes(chunk.getBiomeArray());
        }
        return availableSections;
    }

    private int calculateChunkSize(PartitionedChunk chunk, boolean skylight, int mask) {
        int size = 0;
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (int i = 0; i < sections.length; i++) {
            ExtendedBlockStorage section = sections[i];
            if (section != Chunk.NULL_BLOCK_STORAGE && (!this.loadChunk || !section.isEmpty()) && (mask & 1 << i) != 0) {
                size = size + section.getData().getSerializedSize();
                size = size + section.getBlockLight().getData().length;
                if (skylight) {
                    size += section.getSkyLight().getData().length;
                }
            }
        }
        if (this.loadChunk) {
            size += chunk.getBiomeArray().length;
        }
        return size;
    }

    private PacketBuffer getReadBuffer() {
        return new PacketBuffer(Unpooled.wrappedBuffer(this.buffer));
    }

    private static class TileUpdate {
        private final BlockPos localPos;
        private final NBTTagCompound tag;

        private TileUpdate(BlockPos localPos, NBTTagCompound tag) {
            this.localPos = localPos;
            this.tag = tag;
        }

        public void serialize(ByteBuf buf) {
            buf.writeLong(this.localPos.toLong());
            ByteBufUtils.writeTag(buf, this.tag);
        }

        public static TileUpdate deserialize(ByteBuf buf) {
            BlockPos pos = BlockPos.fromLong(buf.readLong());
            NBTTagCompound compound = ByteBufUtils.readTag(buf);
            return new TileUpdate(pos, compound);
        }
    }
}
