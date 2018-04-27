package net.gegy1000.blocksystems.server.command;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;
import net.gegy1000.blocksystems.server.util.math.QuatRotation;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockSystemCommand extends CommandBase {
    @Override
    public String getName() {
        return "blocksystem";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "blocksystem";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        World world = sender.getEntityWorld();
        if (!world.isRemote && sender instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) sender;
            ServerBlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
            BlockSystem blockSystem = BlockSystems.PROXY.createBlockSystem(world, BlockSystem.nextID++);

            QuatRotation rotation = new QuatRotation();
            rotation.rotate(180.0 - player.rotationYaw, 0.0, 1.0, 0.0);
            blockSystem.setPositionAndRotation(player.posX, player.posY, player.posZ, rotation);
            handler.addBlockSystem(blockSystem);

            blockSystem.setBlockState(BlockPos.ORIGIN, Blocks.GRASS.getDefaultState(), 3);
        }
    }
}
