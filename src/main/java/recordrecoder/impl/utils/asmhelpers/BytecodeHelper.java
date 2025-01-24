package recordrecoder.impl.utils.asmhelpers;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.util.Bytecode;

import java.util.Optional;

public class BytecodeHelper {
    public static Optional<MethodNode> findMethod(ClassNode classNode, MethodNameTypeTuple nameAndType) {
        return Optional.ofNullable(Bytecode.findMethod(classNode, nameAndType.name(), nameAndType.type().getDescriptor()));
    }
}
