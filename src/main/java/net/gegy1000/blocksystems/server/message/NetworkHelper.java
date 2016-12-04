package net.gegy1000.blocksystems.server.message;

import net.gegy1000.blocksystems.BlockSystems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.List;

public class NetworkHelper {
    public static <T extends BaseMessage<T>> void sendToAllNearExcept(World world, EntityPlayer except, double x, double y, double z, double radius, int dimension, BaseMessage<T> message) {
        List<EntityPlayer> players = world.playerEntities;
        for (EntityPlayer player : players) {
            if (player instanceof EntityPlayerMP && player != except && player.dimension == dimension) {
                double deltaX = x - player.posX;
                double deltaY = y - player.posY;
                double deltaZ = z - player.posZ;
                if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ < radius * radius) {
                    BlockSystems.NETWORK_WRAPPER.sendTo(message, (EntityPlayerMP) player);
                }
            }
        }
    }
}
