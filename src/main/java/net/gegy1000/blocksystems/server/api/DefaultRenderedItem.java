package net.gegy1000.blocksystems.server.api;

public interface DefaultRenderedItem {
    default String getResource(String unlocalizedName) {
        return unlocalizedName;
    }
}
