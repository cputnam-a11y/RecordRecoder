package recordrecoder.impl.utils.asmhelpers;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Objects;

public record OwnedMethod(Type owner, String name, Type descriptor, boolean isStatic, boolean isInterface, boolean isSpecial) {

    public OwnedMethod {
        Objects.requireNonNull(owner, "Owner cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(descriptor, "Descriptor cannot be null");
    }
    /**
     *
     * @param owner the internal name of the class that owns the method
     * @param name the name of the method
     * @param descriptor the descriptor of the method
     * @param isStatic whether the method is static
     * @param isInterface whether the method is an interface method
     * @param isSpecial whether the method is a special method
     */
    public OwnedMethod(String owner, String name, String descriptor, boolean isStatic, boolean isInterface, boolean isSpecial) {
        this(Type.getObjectType(owner), name, Type.getMethodType(descriptor), isStatic, isInterface, isSpecial);
    }

    public String toString() {
        return owner.getInternalName() + "." + name + descriptor;
    }

    public int opcode() {
        if (isStatic)
            return Opcodes.INVOKESTATIC;
        else if (isInterface)
            return Opcodes.INVOKEINTERFACE;
        else if (isSpecial)
            return Opcodes.INVOKESPECIAL;
        else
            return Opcodes.INVOKEVIRTUAL;
    }

    public MethodInsnNode call() {
        return new MethodInsnNode(opcode(), owner.getInternalName(), name, descriptor.getDescriptor(), !isStatic && isInterface);
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

    public static OwnedMethod ofStatic(String owner, String name, String descriptor) {
        return new OwnedMethod(owner, name, descriptor, true, false, false);
    }

    public static OwnedMethod ofInterface(String owner, String name, String descriptor) {
        return new OwnedMethod(owner, name, descriptor, false, true, false);
    }

    public static OwnedMethod ofInstance(String owner, String name, String descriptor) {
        return new OwnedMethod(owner, name, descriptor, false, false, false);
    }

    public static OwnedMethod ofStatic(Type owner, String name, Type descriptor) {
        return new OwnedMethod(owner, name, descriptor, true, false, false);
    }

    public static OwnedMethod ofInterface(Type owner, String name, Type descriptor) {
        return new OwnedMethod(owner, name, descriptor, false, true, false);
    }

    public static OwnedMethod ofInstance(Type owner, String name, Type descriptor) {
        return new OwnedMethod(owner, name, descriptor, false, false, false);
    }
}
