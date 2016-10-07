package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiCommandBlock;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.tileentity.TileEntityEndGateway;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.tileentity.TileEntityStructure;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class UpdateBlockEntityMessage extends BaseMessage<UpdateBlockEntityMessage> {
    private int blockSystem;
    private BlockPos pos;
    private int type;
    private NBTTagCompound data;

    public UpdateBlockEntityMessage() {
    }

    public UpdateBlockEntityMessage(BlockSystem blockSystem, BlockPos pos, int type, NBTTagCompound compound) {
        this.blockSystem = blockSystem.getID();
        this.pos = pos;
        this.type = type;
        this.data = compound;
    }

    public UpdateBlockEntityMessage(BlockSystem blockSystem, SPacketUpdateTileEntity packet) {
        this(blockSystem, packet.blockPos, packet.metadata, packet.nbt);
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeLong(this.pos.toLong());
        buf.writeByte(this.type & 0xFF);
        ByteBufUtils.writeTag(buf, this.data);
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.pos = BlockPos.fromLong(buf.readLong());
        this.type = buf.readByte() & 0xFF;
        this.data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystem(this.blockSystem);
        if (blockSystem != null) {
            if (blockSystem.isBlockLoaded(this.pos)) {
                TileEntity blockEntity = blockSystem.getTileEntity(this.pos);
                boolean commandBlock = this.type == 2 && blockEntity instanceof TileEntityCommandBlock;
                if (this.type == 1 && blockEntity instanceof TileEntityMobSpawner || commandBlock || this.type == 3 && blockEntity instanceof TileEntityBeacon || this.type == 4 && blockEntity instanceof TileEntitySkull || this.type == 5 && blockEntity instanceof TileEntityFlowerPot || this.type == 6 && blockEntity instanceof TileEntityBanner || this.type == 7 && blockEntity instanceof TileEntityStructure || this.type == 8 && blockEntity instanceof TileEntityEndGateway || this.type == 9 && blockEntity instanceof TileEntitySign) {
                    blockEntity.readFromNBT(this.data);
                } else {
                    blockEntity.onDataPacket(client.getConnection().getNetworkManager(), new SPacketUpdateTileEntity(this.pos, this.type, this.data));
                }
                if (commandBlock && client.currentScreen instanceof GuiCommandBlock) {
                    ((GuiCommandBlock) client.currentScreen).updateGui();
                }
            }
        }
    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
    }
}
