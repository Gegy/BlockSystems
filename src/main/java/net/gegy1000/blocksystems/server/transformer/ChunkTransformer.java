package net.gegy1000.blocksystems.server.transformer;

import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.core.transformer.TransformerClass;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ChunkTransformer extends TransformerClass {
    public ChunkTransformer() {
        super(Chunk.class);
    }

    @Override
    public boolean transform(ClassNode classNode, String transformedName) {
        boolean transformed = false;
        MethodNode initPrimer = this.getMethod(classNode, "<init>", World.class, ChunkPrimer.class, int.class, int.class, void.class);
        if (initPrimer != null) {
            Instruction instruction = this.instruction()
                    .var(ALOAD, 1)
                    .var(ALOAD, 0)
                    .var(ILOAD, 3)
                    .var(ILOAD, 4)
                    .method(INVOKESTATIC, BlockSystemHooks.class, "initChunkPrimer", World.class, Chunk.class, int.class, int.class, void.class);
            this.insertBefore(initPrimer, node -> node.getOpcode() == RETURN, instruction.build(), true);
            transformed = true;
        }
        return transformed;
    }
}
