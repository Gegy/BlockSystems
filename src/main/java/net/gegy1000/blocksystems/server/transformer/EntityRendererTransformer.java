package net.gegy1000.blocksystems.server.transformer;

import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.core.transformer.TransformerClass;
import net.minecraft.client.renderer.EntityRenderer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class EntityRendererTransformer extends TransformerClass {
    public EntityRendererTransformer() {
        super(EntityRenderer.class);
    }

    @Override
    public boolean transform(ClassNode classNode, String transformedName) {
        boolean transformed = false;
        MethodNode getMouseOver = this.getMethod(classNode, "getMouseOver", float.class, void.class);
        if (getMouseOver != null) {
            Instruction instruction = this.instruction().method(INVOKESTATIC, BlockSystemHooks.class, "getMouseOver", void.class);
            this.insertBefore(getMouseOver, (node) -> node.getOpcode() == RETURN, instruction.build(), true);
            transformed = true;
        }
        return transformed;
    }
}
