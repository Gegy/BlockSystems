package net.gegy1000.blocksystems.server.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author pau101
 */
public class Pool<T> {
    private Supplier<T> instanceProvider;

    private int size;

    private List<T> instances;

    public Pool(Supplier<T> instanceProvider, int size) {
        this.instanceProvider = instanceProvider;
        this.size = size;
        this.instances = new ArrayList<>();
    }

    public T getInstance() {
        if (this.instances.isEmpty()) {
            return this.instanceProvider.get();
        }
        return this.instances.remove(0);
    }

    public void freeInstance(T instance) {
        if (this.instances.size() < this.size) {
            this.instances.add(instance);
        }
    }
}