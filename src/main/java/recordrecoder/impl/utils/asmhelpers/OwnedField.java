package recordrecoder.impl.utils.asmhelpers;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;

import java.util.Objects;

public record OwnedField(Type owner, String name, Type descriptor, boolean isStatic) {

    public OwnedField {
        Objects.requireNonNull(owner, "Owner cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(descriptor, "Descriptor cannot be null");
    }

    public String toString() {
        return owner.getInternalName() + "." + name + descriptor;
    }

    public int opcode(boolean get) {
        if (get) {
            if (isStatic)
                return Opcodes.GETSTATIC;
            else
                return Opcodes.GETFIELD;
        } else {
            if (isStatic)
                return Opcodes.PUTSTATIC;
            else
                return Opcodes.PUTFIELD;
        }
    }

    public FieldInsnNode get() {
        return new FieldInsnNode(opcode(true), owner.getInternalName(), name, descriptor.getDescriptor());
    }

    public FieldInsnNode set() {
        return new FieldInsnNode(opcode(false), owner.getInternalName(), name, descriptor.getDescriptor());
    }
}
