package net.gegy1000.blocksystems.server.transformer;

import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.core.transformer.TransformerClass;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.ChunkRenderWorker;
import net.minecraft.util.math.BlockPos;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class ChunkRenderWorkerTransformer extends TransformerClass {
    public ChunkRenderWorkerTransformer() {
        super(ChunkRenderWorker.class);
    }

    @Override
    public boolean transform(ClassNode classNode, String transformedName) {
        boolean transformed = false;
        MethodNode processTask = this.getMethod(classNode, "processTask", ChunkCompileTaskGenerator.class, void.class);
        if (processTask != null) {
            Instruction instruction = this.instruction()
                    .var(ALOAD, 1)
                    .var(ALOAD, 2)
                    .method(INVOKESTATIC, BlockSystemHooks.class, "chunkWorkerGetPosition", ChunkCompileTaskGenerator.class, BlockPos.class, BlockPos.class)
                    .var(ASTORE, 2);
            this.insertAfter(processTask, node -> node.getOpcode() == ASTORE && ((VarInsnNode) node).var == 2, instruction.build(), false);
            transformed = true;
        }
        return transformed;
    }
}
