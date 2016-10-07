package net.gegy1000.blocksystems.server.command;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;

public class BlockSystemCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "blocksystem";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "blocksystem";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        World world = sender.getEntityWorld();
        if (!world.isRemote && sender instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) sender;
            ServerBlockSystemHandler handler = BlockSystems.PROXY.getBlockSystemHandler(world);
            BlockSystem blockSystem = BlockSystems.PROXY.createBlockSystem(world, BlockSystem.nextID++);
            blockSystem.setPositionAndRotation(player.posX, player.posY, player.posZ, player.rotationPitch, player.rotationYaw, 0.0F);
            handler.addBlockSystem(blockSystem);
        }
    }
}
