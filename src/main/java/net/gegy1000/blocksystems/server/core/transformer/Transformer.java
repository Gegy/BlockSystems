package net.gegy1000.blocksystems.server.core.transformer;

import net.gegy1000.blocksystems.server.core.MappingHandler;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class Transformer implements Opcodes {
    private static final Map<String, String> PRIMITIVES = new HashMap<>();

    static {
        PRIMITIVES.put("int", "I");
        PRIMITIVES.put("boolean", "Z");
        PRIMITIVES.put("void", "V");
        PRIMITIVES.put("double", "D");
        PRIMITIVES.put("float", "F");
        PRIMITIVES.put("long", "J");
        PRIMITIVES.put("byte", "B");
        PRIMITIVES.put("short", "S");
        PRIMITIVES.put("char", "C");
    }

    public abstract boolean applies(String transformedName);

    public abstract boolean transform(ClassNode classNode, String transformedName);

    protected AbstractInsnNode getNode(InsnList nodes, Predicate<AbstractInsnNode> validator) {
        for (AbstractInsnNode node : nodes.toArray()) {
            if (validator.test(node)) {
                return node;
            }
        }
        return null;
    }

    protected AbstractInsnNode getNodeLast(InsnList nodes, Predicate<AbstractInsnNode> validator) {
        AbstractInsnNode last = null;
        for (AbstractInsnNode node : nodes.toArray()) {
            if (validator.test(node)) {
                last = node;
            }
        }
        return last;
    }

    protected boolean insertBefore(MethodNode method, Predicate<AbstractInsnNode> validator, InsnList insert, boolean last) {
        return this.insertBefore(method.instructions, validator, insert, last);
    }

    protected boolean insertAfter(MethodNode method, Predicate<AbstractInsnNode> validator, InsnList insert, boolean last) {
        return this.insertAfter(method.instructions, validator, insert, last);
    }

    protected boolean insertBefore(InsnList nodes, Predicate<AbstractInsnNode> validator, InsnList insert, boolean last) {
        AbstractInsnNode node = last ? this.getNodeLast(nodes, validator) : this.getNode(nodes, validator);
        if (node != null) {
            nodes.insertBefore(node, insert);
            return true;
        }
        return false;
    }

    protected boolean insertAfter(InsnList nodes, Predicate<AbstractInsnNode> validator, InsnList insert, boolean last) {
        AbstractInsnNode node = last ? this.getNodeLast(nodes, validator) : this.getNode(nodes, validator);
        if (node != null) {
            nodes.insert(node, insert);
            return true;
        }
        return false;
    }

    protected FieldNode getField(ClassNode node, String name) {
        String mappedName = MappingHandler.getMappedField(node.name, name);
        for (FieldNode field : node.fields) {
            if (field.name.equals(mappedName) || field.name.equals(name)) {
                return field;
            }
        }
        return null;
    }

    protected MethodNode getMethod(ClassNode node, String name, Object... parameters) {
        String descriptor = this.createDescriptor(parameters);
        String mappedName = MappingHandler.getMappedMethod(node.name, name, descriptor);
        for (MethodNode method : node.methods) {
            if ((method.name.equals(mappedName) || method.name.equals(name)) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    public String createDescriptor(Object... parameters) {
        StringBuilder descriptor = new StringBuilder("(");
        if (parameters.length == 1) {
            descriptor = new StringBuilder("()" + this.createVarDescriptor(parameters[0]));
        } else {
            for (int i = 0; i < parameters.length - 1; i++) {
                descriptor.append(this.createVarDescriptor(parameters[i]));
            }
            descriptor.append(")").append(this.createVarDescriptor(parameters[parameters.length - 1]));
        }
        return descriptor.toString();
    }

    protected String createVarDescriptor(Object cls) {
        String name = cls.toString();
        if (cls instanceof String) {
            name = (String) cls;
        } else if (cls instanceof Class) {
            name = ((Class) cls).getName();
        }
        boolean array = name.endsWith("[]");
        if (array) {
            name = name.substring(0, name.length() - 2);
        }
        String primitive = PRIMITIVES.get(name);
        if (primitive != null) {
            return primitive;
        } else {
            String descriptor = "L" + name.replace(".", "/") + ";";
            if (array) {
                descriptor = "[" + descriptor;
            }
            return descriptor;
        }
    }

    protected Instruction instruction() {
        return new Instruction();
    }

    public class Instruction {
        private List<Supplier<AbstractInsnNode>> insert = new LinkedList<>();

        private Instruction() {
        }

        public Instruction var(int opcode, int var) {
            this.insert.add(() -> new VarInsnNode(opcode, var));
            return this;
        }

        public Instruction field(int opcode, Object cls, String name, Object descriptor) {
            String clsName = (String) cls;
            this.insert.add(() -> new FieldInsnNode(opcode, clsName.replace(".", "/"), MappingHandler.getMappedField(clsName, name), Transformer.this.createVarDescriptor(descriptor)));
            return this;
        }

        public Instruction method(int opcode, Object cls, String name, Object... parameters) {
            String descriptor = Transformer.this.createDescriptor(parameters);
            String clsName = (String) cls;
            this.insert.add(() -> new MethodInsnNode(opcode, clsName.replace(".", "/"), MappingHandler.getMappedMethod(clsName, name, descriptor), descriptor, opcode == INVOKEINTERFACE));
            return this;
        }

        public Instruction node(AbstractInsnNode node) {
            this.insert.add(() -> node);
            return this;
        }

        public InsnList build() {
            InsnList build = new InsnList();
            for (Supplier<AbstractInsnNode> node : this.insert) {
                build.add(node.get());
            }
            return build;
        }
    }
}
