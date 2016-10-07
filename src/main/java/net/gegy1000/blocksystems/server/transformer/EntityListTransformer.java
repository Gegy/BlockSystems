package net.gegy1000.blocksystems.server.transformer;

import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.core.transformer.TransformerClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.function.Predicate;

public class EntityListTransformer extends TransformerClass {
    public EntityListTransformer() {
        super(EntityList.class);
    }

    @Override
    public boolean transform(ClassNode classNode, String transformedName) {
        boolean transformed = false;
        MethodNode createEntityByName = this.getMethod(classNode, "createEntityByName", String.class, World.class, Entity.class);
        MethodNode createEntityByID = this.getMethod(classNode, "createEntityByID", int.class, World.class, Entity.class);
        MethodNode createEntityFromNBT = this.getMethod(classNode, "createEntityFromNBT", NBTTagCompound.class, World.class, Entity.class);
        Instruction createEntityInstruction = this.instruction()
                .var(ALOAD, 1)
                .method(INVOKESTATIC, BlockSystemHooks.class, "getMainWorld", World.class, World.class)
                .var(ASTORE, 1);
        Predicate<AbstractInsnNode> createEntityNode = node -> node.getOpcode() == ASTORE && ((VarInsnNode) node).var == 2;
        if (createEntityByName != null) {
            this.insertAfter(createEntityByName, createEntityNode, createEntityInstruction.build(), false);
            transformed = true;
        }
        if (createEntityByID != null) {
            this.insertAfter(createEntityByID, createEntityNode, createEntityInstruction.build(), false);
            transformed = true;
        }
        if (createEntityFromNBT != null) {
            this.insertAfter(createEntityFromNBT, createEntityNode, createEntityInstruction.build(), false);
            transformed = true;
        }
        return transformed;
    }
}
