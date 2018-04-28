package net.gegy1000.blocksystems.server.message.blocksystem;

import io.netty.buffer.ByteBuf;
import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.entity.FakePlayerWrapper;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class InteractBlockMessage extends BaseMessage<InteractBlockMessage> {
    private int blockSystem;
    private BlockPos position;
    private float hitX;
    private float hitY;
    private float hitZ;
    private EnumHand hand;
    private EnumFacing side;

    public InteractBlockMessage() {
    }

    public InteractBlockMessage(BlockSystem blockSystem, BlockPos position, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        this.blockSystem = blockSystem.getID();
        this.position = position;
        this.hand = hand;
        this.side = side;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
    }

    @Override
    public void serialize(ByteBuf buf) {
        buf.writeInt(this.blockSystem);
        buf.writeLong(this.position.toLong());
        buf.writeByte(this.hand.ordinal());
        buf.writeByte(this.side.ordinal());
        buf.writeFloat(this.hitX);
        buf.writeFloat(this.hitY);
        buf.writeFloat(this.hitZ);
    }

    @Override
    public void deserialize(ByteBuf buf) {
        this.blockSystem = buf.readInt();
        this.position = BlockPos.fromLong(buf.readLong());
        this.hand = EnumHand.values()[buf.readByte()];
        this.side = EnumFacing.values()[buf.readByte()];
        this.hitX = buf.readFloat();
        this.hitY = buf.readFloat();
        this.hitZ = buf.readFloat();
    }

    @Override
    public void onReceiveClient(Minecraft client, WorldClient world, EntityPlayerSP player, MessageContext context) {

    }

    @Override
    public void onReceiveServer(MinecraftServer server, WorldServer world, EntityPlayerMP player, MessageContext context) {
        BlockSystem blockSystem = BlockSystems.PROXY.getBlockSystemHandler(world).getBlockSystem(this.blockSystem);
        if (blockSystem != null) {
            player = new FakePlayerWrapper(player, blockSystem, this.position);
            IBlockState state = blockSystem.getBlockState(this.position);
            ItemStack heldItem = player.getHeldItem(this.hand);
            if (!state.getBlock().onBlockActivated(blockSystem, this.position, state, player, this.hand, this.side, this.hitX, this.hitY, this.hitZ)) {
                if (!heldItem.isEmpty()) {
                    int originalCount = heldItem.getCount();
                    heldItem.onItemUse(player, blockSystem, this.position, this.hand, this.side, this.hitX, this.hitY, this.hitZ);
                    if (player.capabilities.isCreativeMode && heldItem.getCount() < originalCount) {
                        heldItem.setCount(originalCount);
                    }
                    if (heldItem.isEmpty()) {
                        player.setHeldItem(this.hand, ItemStack.EMPTY);
                        ForgeEventFactory.onPlayerDestroyItem(player, heldItem, this.hand);
                    }
                }
            }
        }
    }
}
