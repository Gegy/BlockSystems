package net.gegy1000.blocksystems.server;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.HashMap;
import java.util.Map;

public class ServerProxy {
    public static final Map<World, ServerBlockSystemHandler> BLOCK_SYSTEM_HANDLERS = new HashMap<>();

    public void onPreInit() {
    }

    public void onInit() {

    }

    public void onPostInit() {

    }

    public BlockSystem createBlockSystem(World mainWorld, int id) {
        return new BlockSystemServer(mainWorld instanceof WorldServer ? mainWorld.getMinecraftServer() : FMLCommonHandler.instance().getMinecraftServerInstance(), mainWorld, id);
    }

    public void playSound(ISound sound) {
    }

    public void pickBlock(EntityPlayer player, RayTraceResult mouseOver, World world, IBlockState state) {
    }

    public void scheduleTask(MessageContext context, Runnable runnable) {
        WorldServer world = (WorldServer) context.getServerHandler().player.world;
        world.addScheduledTask(runnable);
    }

    public void handleMessage(BaseMessage message, MessageContext context) {
        EntityPlayerMP player = context.getServerHandler().player;
        this.scheduleTask(context, () -> message.onReceiveServer(player.getServer(), (WorldServer) player.world, player, context));
    }

    public ServerBlockSystemHandler getBlockSystemHandler(World world) {
        return BLOCK_SYSTEM_HANDLERS.computeIfAbsent(world, key -> new ServerBlockSystemHandler(world));
    }

    public boolean isClientPlayer(EntityPlayer player) {
        return false;
    }

    public boolean isPaused(World world) {
        return false;
    }
}
