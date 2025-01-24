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

    /**
     *
     * @param owner the internal name of the class that owns the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     * @param isStatic whether the field is static
     */
    public OwnedField(String owner, String name, String descriptor, boolean isStatic) {
        this(Type.getObjectType(owner), name, Type.getType(descriptor), isStatic);
    }

    public String toString() {
        return owner.getInternalName() + "." + name + descriptor;
    }

    public String getOwnerDescriptor() {
        return owner.getDescriptor();
    }

    public String getOwnerInternalName() {
        return owner.getInternalName();
    }

    public String getOwnerBinaryName() {
        return owner.getClassName();
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
