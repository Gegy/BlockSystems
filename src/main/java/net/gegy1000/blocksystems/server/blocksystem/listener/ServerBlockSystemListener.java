package net.gegy1000.blocksystems.server.blocksystem.listener;

import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemServer;
import net.gegy1000.blocksystems.server.message.NetworkHelper;
import net.gegy1000.blocksystems.server.message.blocksystem.PlayEventMessage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class ServerBlockSystemListener implements IWorldEventListener {
    private final MinecraftServer server;
    private final BlockSystemServer blockSystem;

    public ServerBlockSystemListener(BlockSystemServer blockSystem) {
        this.blockSystem = blockSystem;
        this.server = FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    @Override
    public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        this.blockSystem.getChunkTracker().markBlockForUpdate(pos);
    }

    @Override
    public void notifyLightSet(BlockPos pos) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
    }

    @Override
    public void playSoundToAllNearExcept(EntityPlayer player, SoundEvent sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
        double range = volume > 1.0F ? 16.0F * volume : 16.0;
        this.server.getPlayerList().sendToAllNearExcept(player, x, y, z, range, this.blockSystem.provider.getDimension(), new SPacketSoundEffect(sound, category, x, y, z, volume, pitch));
    }

    @Override
    public void playRecord(SoundEvent sound, BlockPos pos) {
    }

    @Override
    public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
    }

    @Override
    public void onEntityAdded(Entity entity) {
    }

    @Override
    public void onEntityRemoved(Entity entity) {
    }

    @Override
    public void broadcastSound(int soundID, BlockPos pos, int data) {
    }

    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
        Vec3d transformed = this.blockSystem.getTransformedPosition(new Vec3d(pos.getX(), pos.getY(), pos.getZ()));
        int dimension = this.blockSystem.getMainWorld().provider.getDimension();
        PlayEventMessage message = new PlayEventMessage(this.blockSystem, pos, type, data, false);
        NetworkHelper.sendToAllNearExcept(player, transformed.xCoord, transformed.yCoord, transformed.zCoord,64.0, dimension, message);
    }

    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
    }

    public static ServerBlockSystemListener get(BlockSystem world) {
        for (IWorldEventListener listener : world.getListeners()) {
            if (listener instanceof ServerBlockSystemListener) {
                return (ServerBlockSystemListener) listener;
            }
        }
        return null;
    }
}
