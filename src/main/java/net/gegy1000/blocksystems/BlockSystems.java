package net.gegy1000.blocksystems;

import net.gegy1000.blocksystems.server.message.blocksystem.TrackMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.UntrackMessage;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.gegy1000.blocksystems.server.ServerProxy;
import net.gegy1000.blocksystems.server.command.BlockSystemCommand;
import net.gegy1000.blocksystems.server.core.BlockSystemPlugin;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.BreakBlockMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.ChunkMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.InteractBlockMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.MultiBlockUpdateMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.PlayEventMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.SetBlockMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.UnloadChunkMessage;
import net.gegy1000.blocksystems.server.message.blocksystem.UpdateBlockEntityMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = BlockSystems.MODID, name = "BlockSystems", version = BlockSystems.VERSION)
public class BlockSystems {
    public static final String MODID = "blocksystems";
    public static final String VERSION = "0.1.0-dev";

    @Mod.Instance(BlockSystems.MODID)
    public static BlockSystems INSTANCE;

    @SidedProxy(clientSide = "net.gegy1000.blocksystems.client.ClientProxy", serverSide = "net.gegy1000.blocksystems.server.ServerProxy")
    public static ServerProxy PROXY;

    public static final Logger LOGGER = LogManager.getLogger("BlockSystems");

    public static final SimpleNetworkWrapper NETWORK_WRAPPER = new SimpleNetworkWrapper(BlockSystems.MODID);

    private static int messageID;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        if (!BlockSystemPlugin.loaded) {
            System.err.println("Failed to load BlockSystems! Missing coremod parameters! (-Dfml.coreMods.load=net.gegy1000.blocksystems.server.core.BlockSystemPlugin)");
            FMLCommonHandler.instance().exitJava(1, false);
        }

        BlockSystems.registerMessage(InteractBlockMessage.class, Side.SERVER);
        BlockSystems.registerMessage(BreakBlockMessage.class, Side.SERVER, Side.CLIENT);
        BlockSystems.registerMessage(ChunkMessage.class, Side.CLIENT);
        BlockSystems.registerMessage(PlayEventMessage.class, Side.CLIENT);
        BlockSystems.registerMessage(SetBlockMessage.class, Side.CLIENT);
        BlockSystems.registerMessage(TrackMessage.class, Side.CLIENT);
        BlockSystems.registerMessage(UntrackMessage.class, Side.CLIENT);
        BlockSystems.registerMessage(UpdateBlockEntityMessage.class, Side.CLIENT);
        BlockSystems.registerMessage(MultiBlockUpdateMessage.class, Side.CLIENT);
        BlockSystems.registerMessage(UnloadChunkMessage.class, Side.CLIENT);

        BlockSystems.PROXY.onPreInit();
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        BlockSystems.PROXY.onInit();
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        BlockSystems.PROXY.onPostInit();
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BlockSystemCommand());
    }

    private static <T extends BaseMessage<T> & IMessageHandler<T, IMessage>> void registerMessage(Class<T> message, Side... sides) {
        for (Side side : sides) {
            NETWORK_WRAPPER.registerMessage(message, message, messageID++, side);
        }
    }
}
