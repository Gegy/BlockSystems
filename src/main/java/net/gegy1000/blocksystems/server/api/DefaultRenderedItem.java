package net.gegy1000.blocksystems.server.api;

import net.minecraft.util.ResourceLocation;

public interface DefaultRenderedItem {
    default String getResource(ResourceLocation registryName) {
        return registryName.getResourcePath();
    }
}
