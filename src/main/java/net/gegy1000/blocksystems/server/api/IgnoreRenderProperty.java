package net.gegy1000.blocksystems.server.api;

import net.minecraft.block.properties.IProperty;

public interface IgnoreRenderProperty {
    IProperty<?>[] getIgnoredProperties();
}
