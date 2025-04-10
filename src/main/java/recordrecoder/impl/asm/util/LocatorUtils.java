package recordrecoder.impl.asm.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.util.Bytecode;
import recordrecoder.impl.utils.Constants;
import recordrecoder.impl.utils.asmhelpers.BytecodeHelper;
import recordrecoder.impl.utils.asmhelpers.MethodNameTypeTuple;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public class LocatorUtils {

    public static RecordIntrinsicMethods findIntrinsicMethods(ClassNode classNode) {
        final Optional<MethodNode> toStringNode = BytecodeHelper.findMethod(classNode, Constants.RECORD$TO_STRING);
        final MethodNode hashCodeNode = Bytecode.findMethod(classNode, "hashCode",
                Type.getMethodType(Type.INT_TYPE).getDescriptor());
        final MethodNode equalsNode = Bytecode.findMethod(classNode, "equals",
                Type.getMethodType(Type.BOOLEAN_TYPE, Constants.OBJECT).getDescriptor());

        final InvokeDynamicInsnNode indyToString = toStringNode.flatMap(
                node -> findIndy(node.instructions, Constants.TO_STRING_INDY.apply(classNode.name))
        ).orElse(null);

        final InvokeDynamicInsnNode indyHashCode = map(
                hashCodeNode,
                node -> findIndy(node.instructions, Constants.HASH_CODE_INDY.apply(classNode.name)).orElse(null)
        );

        final InvokeDynamicInsnNode indyEquals = map(
                equalsNode,
                node -> findIndy(node.instructions, Constants.EQUALS_INDY.apply(classNode.name)).orElse(null)
        );

        return new RecordIntrinsicMethods(indyToString, indyHashCode, indyEquals);
    }

    private static Optional<InvokeDynamicInsnNode> findIndy(
            final InsnList insns,
            MethodNameTypeTuple nameAndType) {

        for (AbstractInsnNode node : insns) {
            if (node instanceof InvokeDynamicInsnNode indy &&
                    nameAndType.name().equals(indy.name) &&
                    nameAndType.type().getDescriptor().equals(indy.desc)) {
                return Optional.of(indy);
            }
        }
        return Optional.empty();
    }

    public static MethodNode findCanonicalConstructor(ClassNode classNode, Type[] types) {
        return classNode.methods.stream()
                .filter(node -> node.name.equals("<init>") && Arrays.equals(Type.getArgumentTypes(node.desc), types))
                .findFirst()
                .orElse(null);
    }

    private static <V, T> @Nullable T map(final @Nullable V v, final Function<@NotNull V, T> mapper) {
        return v == null ? null : mapper.apply(v);
    }

}
