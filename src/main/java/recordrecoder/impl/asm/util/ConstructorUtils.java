package recordrecoder.impl.asm.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import recordrecoder.impl.RecordRecoder;
import recordrecoder.impl.utils.Constants;
import recordrecoder.impl.utils.asmhelpers.BytecodeHelper;

import java.util.List;

public class ConstructorUtils {

    public static MethodNode createExtendedConstructor(
            ClassNode classNode,
            MethodNode originalConstructor,
            String newDesc,
            String newSignature,
            int access,
            List<String> keyFieldNames) {

        final MethodNode newConstructor = new MethodNode(
                access,
                "<init>",
                newDesc,
                newSignature,
                new String[]{}
        );

        InsnList instructions = new InsnList();

        // Add key queue loading operations
        int offset = Type.getArgumentCount(originalConstructor.desc) + 1; // +1 for this
        for (String name : keyFieldNames) {
            instructions.add(new FieldInsnNode(
                    Opcodes.GETSTATIC,
                    classNode.name,
                    name,
                    "Lrecordrecoder/impl/record/RecordComponentKeyImpl;"
            ));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, offset++));
            instructions.add(Constants.RECORD_COMPONENT_KEY_IMPL$QUEUE_NEXT.call());
        }

        // Call original constructor
        Type[] types = Type.getArgumentTypes(originalConstructor.desc);
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));

        for (int i = 0; i < types.length; i++) {
            instructions.add(new VarInsnNode(getLoadOpcodeForType(types[i]), i + 1));
        }

        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                classNode.name,
                originalConstructor.name,
                originalConstructor.desc,
                false
        ));

        instructions.add(new InsnNode(Opcodes.RETURN));
        newConstructor.instructions = instructions;

        return newConstructor;
    }

    public static MethodNode ensureStaticInitializer(ClassNode classNode) {
        return BytecodeHelper.findMethod(classNode, Constants.CLINIT)
                .orElseGet(() -> {
                    RecordRecoder.LOGGER.info("Static initializer not found, creating one for {}", classNode.name);
                    MethodNode clinit = new MethodNode(
                            Opcodes.ACC_STATIC,
                            "<clinit>",
                            "()V",
                            null,
                            null
                    );
                    clinit.instructions.add(new InsnNode(Opcodes.RETURN));
                    classNode.methods.add(clinit);
                    return clinit;
                });
    }

    private static int getLoadOpcodeForType(Type type) {
        if (type == Type.INT_TYPE ||
                type == Type.SHORT_TYPE ||
                type == Type.BYTE_TYPE ||
                type == Type.CHAR_TYPE ||
                type == Type.BOOLEAN_TYPE) {
            return Opcodes.ILOAD;
        }
        if (type == Type.FLOAT_TYPE) {
            return Opcodes.FLOAD;
        }
        if (type == Type.DOUBLE_TYPE) {
            return Opcodes.DLOAD;
        }
        if (type == Type.LONG_TYPE) {
            return Opcodes.LLOAD;
        }
        return Opcodes.ALOAD;
    }

}
