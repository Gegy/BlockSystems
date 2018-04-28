package net.gegy1000.blocksystems.server.message.blocksystem;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.blocksystem.BlockSystemClient;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.chunk.BlockSystemChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.gegy1000.blocksystems.server.message.BaseMessage;

import java.util.List;
import java.util.Map;

public class ChunkMessage extends BaseMessage<ChunkMessage> {
    private int blockSystem;
    private int x;
    private int z;
    private ChunkPos partitionPosition;
    private int availableSections;
    private byte[] buffer;
    private List<NBTTagCompound> tileEntityTags;
    private boolean loadChunk;

    public ChunkMessage() {
    }

    public ChunkMessage(BlockSystem blockSystem, BlockSystemChunk chunk, int mask) {
        this.blockSystem = blockSystem.getID();
        this.x = chunk.x;
        this.z = chunk.z;
        this.loadChunk = mask == 65535;
        this.partitionPosition = chunk.getPartitionPosition();
        boolean hasSkylight = !blockSystem.getMainWorld().provider.isNether();
        this.buffer = new byte[this.calculateChunkSize(chunk, hasSkylight, mask)];
        this.availableSections = this.extractChunkData(new PacketBuffer(this.getWriteBuffer()), chunk, hasSkylight, mask);
        this.tileEntityTags = Lists.newArrayList();
        for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet()) {
            BlockPos pos = entry.getKey();
            TileEntity tile = entry.getValue();
            int storageIndex = pos.getY() >> 4;
            if (this.loadChunk || (mask & 1 << storageIndex) != 0) {
                this.tileEntityTags.add(tile.getUpdateTag());
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
        buf.writeInt(this.partitionPosition.x);
        buf.writeInt(this.partitionPosition.z);
        buf.writeInt(this.tileEntityTags.size());
        for (NBTTagCompound tag : this.tileEntityTags) {
            ByteBufUtils.writeTag(buf, tag);
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
        this.partitionPosition = new ChunkPos(buf.readInt(), buf.readInt());
        int tileCount = buf.readInt();
        this.tileEntityTags = Lists.newArrayList();
        for (int i = 0; i < tileCount; i++) {
            this.tileEntityTags.add(ByteBufUtils.readTag(buf));
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
            BlockSystemChunk chunk = (BlockSystemChunk) clientSystem.getChunkFromChunkCoords(this.x, this.z);
            chunk.setPartitionPosition(this.partitionPosition);
            chunk.read(this.getReadBuffer(), this.availableSections, this.loadChunk);
            clientSystem.markBlockRangeForRenderUpdate(this.x << 4, 0, this.z << 4, (this.x << 4) + 15, 256, (this.z << 4) + 15);
            if (!this.loadChunk || !(clientSystem.provider instanceof WorldProviderSurface)) {
                chunk.resetRelightChecks();
            }
            for (NBTTagCompound tag : this.tileEntityTags) {
                int x = tag.getInteger("x");
                int y = tag.getInteger("y");
                int z = tag.getInteger("z");
                BlockSystemChunk partitionChunk = clientSystem.getPartitionChunk(new ChunkPos(x >> 4, z >> 4));
                TileEntity tile = clientSystem.getTileEntity(new BlockPos((x & 0xF) + (partitionChunk.x << 4), y & 0xFF, (z & 0xF) + (partitionChunk.z << 4)));
                if (tile != null) {
                    tile.handleUpdateTag(tag);
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

    public int extractChunkData(PacketBuffer buffer, BlockSystemChunk chunk, boolean skylight, int mask) {
        int availableSections = 0;
        ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
        for (int storageIndex = 0; storageIndex < storages.length; storageIndex++) {
            ExtendedBlockStorage storage = storages[storageIndex];
            if (storage != Chunk.NULL_BLOCK_STORAGE && (!this.loadChunk || !storage.isEmpty()) && (mask & 1 << storageIndex) != 0) {
                availableSections |= 1 << storageIndex;
                storage.getData().write(buffer);
                buffer.writeBytes(storage.getBlockLight().getData());
                if (skylight) {
                    buffer.writeBytes(storage.getSkyLight().getData());
                }
            }
        }
        if (this.loadChunk) {
            buffer.writeBytes(chunk.getBiomeArray());
        }
        return availableSections;
    }

    protected int calculateChunkSize(BlockSystemChunk chunk, boolean skylight, int mask) {
        int size = 0;
        ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
        for (int i = 0; i < storages.length; i++) {
            ExtendedBlockStorage storage = storages[i];
            if (storage != Chunk.NULL_BLOCK_STORAGE && (!this.loadChunk || !storage.isEmpty()) && (mask & 1 << i) != 0) {
                size = size + storage.getData().getSerializedSize();
                size = size + storage.getBlockLight().getData().length;
                if (skylight) {
                    size += storage.getSkyLight().getData().length;
                }
            }
        }
        if (this.loadChunk) {
            size += chunk.getBiomeArray().length;
        }
        return size;
    }

    protected PacketBuffer getReadBuffer() {
        return new PacketBuffer(Unpooled.wrappedBuffer(this.buffer));
    }
}
