package net.gegy1000.blocksystems.server.core.transformer;

public abstract class TransformerClass extends Transformer {
    protected String name;

    public TransformerClass(Object obj) {
        if (obj instanceof String) {
            this.name = (String) obj;
        }
    }

    @Override
    public boolean applies(String transformedName) {
        return this.name.equals(transformedName);
    }
}
