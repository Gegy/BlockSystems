package net.gegy1000.blocksystems.server.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

@IFMLLoadingPlugin.Name("blocksystems")
@IFMLLoadingPlugin.MCVersion("1.10.2")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions({ "net.ilexiconn.llibrary.server.asm", "net.gegy1000.blocksystems.server.core" })
public class BlockSystemPlugin implements IFMLLoadingPlugin {
    public static boolean loaded;
    public static boolean development;

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "net.gegy1000.blocksystems.server.core.BlockSystemTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        BlockSystemPlugin.loaded = true;
        BlockSystemPlugin.development = !(Boolean) data.get("runtimeDeobfuscationEnabled");
        MappingHandler.loadMappings();
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
