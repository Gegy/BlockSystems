package net.gegy1000.blocksystems.server.transformer;

import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.core.transformer.TransformerClass;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ParticleManagerTransformer extends TransformerClass {
    public ParticleManagerTransformer() {
        super(ParticleManager.class);
    }

    @Override
    public boolean transform(ClassNode classNode, String transformedName) {
        MethodNode addEffect = this.getMethod(classNode, "addEffect", Particle.class, void.class);
        if (addEffect != null) {
            Instruction instructions = this.instruction()
                    .var(ALOAD, 1)
                    .method(INVOKESTATIC, BlockSystemHooks.class, "transformEffect", Particle.class, void.class);
            this.insertAfter(addEffect, node -> node.getOpcode() == POP, instructions.build(), false);
            return true;
        }
        return false;
    }
}
