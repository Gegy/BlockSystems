package net.gegy1000.blocksystems.server.transformer;

import net.gegy1000.blocksystems.server.core.BlockSystemHooks;
import net.gegy1000.blocksystems.server.core.transformer.TransformerClass;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class MinecraftTransformer extends TransformerClass {
    public MinecraftTransformer() {
        super(Minecraft.class);
    }

    @Override
    public boolean transform(ClassNode classNode, String transformedName) {
        boolean transformed = false;
        MethodNode rightClickMouse = this.getMethod(classNode, "rightClickMouse", void.class);
        if (rightClickMouse != null) {
            LabelNode labelNode = new LabelNode();
            Instruction instruction = this.instruction()
                    .method(INVOKESTATIC, BlockSystemHooks.class, "rightClickMouse", boolean.class)
                    .node(new JumpInsnNode(IFEQ, labelNode))
                    .node(new InsnNode(RETURN))
                    .node(labelNode);
            this.insertBefore(rightClickMouse, node -> {
                if (node.getOpcode() == INVOKESTATIC) {
                    MethodInsnNode methodNode = (MethodInsnNode) node;
                    return methodNode.name.equals("values") && methodNode.owner.equals("net/minecraft/util/EnumHand");
                }
                return false;
            }, instruction.build(), false);
            transformed = true;
        }
        return transformed;
    }
}
