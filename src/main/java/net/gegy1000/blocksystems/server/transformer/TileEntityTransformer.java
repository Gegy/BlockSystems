package net.gegy1000.blocksystems.server.transformer;

import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.core.transformer.TransformerClass;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class TileEntityTransformer extends TransformerClass {
    public TileEntityTransformer() {
        super(TileEntity.class);
    }

    @Override
    public boolean transform(ClassNode classNode, String transformedName) {
        boolean transformed = false;
        MethodNode setWorld = this.getMethod(classNode, "setWorld", World.class, void.class);
        if (setWorld != null) {
            Instruction instruction = this.instruction()
                    .var(ALOAD, 0)
                    .var(ALOAD, 1)
                    .method(INVOKESTATIC, BlockSystemHooks.class, "setWorld", TileEntity.class, World.class, World.class)
                    .var(ASTORE, 1);
            setWorld.instructions.insert(instruction.build());
            transformed = true;
        }
        MethodNode setWorldCreate = this.getMethod(classNode, "setWorldCreate", World.class, void.class);
        if (setWorldCreate != null) {
            Instruction instruction = this.instruction()
                    .var(ALOAD, 0)
                    .var(ALOAD, 1)
                    .method(INVOKESTATIC, BlockSystemHooks.class, "setWorld", TileEntity.class, World.class, World.class)
                    .var(ASTORE, 1);
            setWorldCreate.instructions.insert(instruction.build());
            transformed = true;
        }
        MethodNode setPos = this.getMethod(classNode, "setPos", BlockPos.class, void.class);
        if (setPos != null) {
            Instruction instruction = this.instruction()
                    .var(ALOAD, 0)
                    .var(ALOAD, 1)
                    .method(INVOKESTATIC, BlockSystemHooks.class, "setPos", TileEntity.class, BlockPos.class, BlockPos.class)
                    .var(ASTORE, 1);
            setPos.instructions.insert(instruction.build());
            transformed = true;
        }
        return transformed;
    }
}
