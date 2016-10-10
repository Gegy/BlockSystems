package net.gegy1000.blocksystems.server.transformer;

import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.core.transformer.TransformerClass;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

public class WorldTransformer extends TransformerClass {
    public WorldTransformer() {
        super(World.class);
    }

    @Override
    public boolean transform(ClassNode classNode, String transformedName) {
        boolean transformed = false;
        MethodNode isOutsideBuildHeight = this.getMethod(classNode, "isOutsideBuildHeight", BlockPos.class, boolean.class);
        if (isOutsideBuildHeight != null) {
            Instruction instruction = this.instruction()
                    .var(ALOAD, 0)
                    .var(ALOAD, 1)
                    .method(INVOKESTATIC, BlockSystemHooks.class, "checkBlockAccess", World.class, BlockPos.class, boolean.class)
                    .node(new InsnNode(IRETURN));
            isOutsideBuildHeight.instructions.clear();
            isOutsideBuildHeight.instructions.add(instruction.build());
            transformed = true;
        }
        MethodNode getChunkFromChunkCoords = this.getMethod(classNode, "getChunkFromChunkCoords", int.class, int.class, Chunk.class);
        if (getChunkFromChunkCoords != null) {
            Instruction instruction = this.instruction()
                    .var(ALOAD, 0)
                    .var(ILOAD, 1)
                    .var(ILOAD, 2)
                    .method(INVOKESTATIC, BlockSystemHooks.class, "getChunk", World.class, int.class, int.class, Chunk.class)
                    .node(new InsnNode(ARETURN));
            getChunkFromChunkCoords.instructions.clear();
            getChunkFromChunkCoords.instructions.add(instruction.build());
            transformed = true;
        }
        return transformed;
    }
}
