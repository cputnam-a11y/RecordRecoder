package recordrecoder.impl.asm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.util.Bytecode;
import recordrecoder.api.record.ComponentKeyRegistry;
import recordrecoder.impl.record.ComponentKeyRegistryImpl;
import recordrecoder.impl.record.RecordComponentKeyImpl;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class RecordClassTransformer implements IExtension {
    public static final String RECORD_INTERNAL_NAME = "java/lang/Record";

    public static void transform(ClassNode classNode) {
        if (!classNode.superName.equals(RECORD_INTERNAL_NAME)) {
            return;
        }
        final ComponentKeyRegistryImpl impl = (ComponentKeyRegistryImpl) ComponentKeyRegistry.INSTANCE;
        List<RecordComponentKeyImpl<?>> keys = impl.getForClass(classNode.name);
        if (keys.isEmpty())
            return;
        final MethodNode staticInitializer;
        MethodNode canonicalConstructor = null;
        final Type[] types = classNode.recordComponents.stream().map(node -> node.descriptor).map(Type::getType).toArray(Type[]::new);
        final InvokeDynamicInsnNode indyToString;
        final InvokeDynamicInsnNode indyHashCode;
        final InvokeDynamicInsnNode indyEquals;
        {
            final MethodNode toStringNode = Bytecode.findMethod(classNode, "toString", "()Ljava/lang/String;");
            final MethodNode hashCodeNode = Bytecode.findMethod(classNode, "hashCode", "()I");
            final MethodNode equalsNode = Bytecode.findMethod(classNode, "equals", "(Ljava/lang/Object;)Z");
            staticInitializer = Bytecode.findMethod(classNode, "<clinit>", "()V");
            for (MethodNode node : classNode.methods) {
                if (node.name.equals("<init>")) {
                    if (Arrays.equals(Type.getArgumentTypes(node.desc), types))
                        canonicalConstructor = node;
                }
            }
            indyToString = map(
                    toStringNode,
                    node -> findIndy(
                            node.instructions,
                            "toString",
                            "(L" + classNode.name + ";)Ljava/lang/String;"
                    ).orElse(null)
            );
            indyHashCode = map(
                    hashCodeNode,
                    node -> findIndy(
                            node.instructions,
                            "hashCode",
                            "(L" + classNode.name + ";)I"
                    ).orElse(null)
            );
            indyEquals = map(
                    equalsNode,
                    node -> findIndy(
                            node.instructions,
                            "equals",
                            "(L" + classNode.name + ";Ljava/lang/Object;)Z"
                    ).orElse(null)
            );
        }
        if (indyEquals == null || indyHashCode == null || indyToString == null || staticInitializer == null || canonicalConstructor == null) {
            return;
        }
        List<String> keyFieldNames = new ArrayList<>();
        for (final RecordComponentKeyImpl<?> key : keys) {
            final UUID uuid = UUID.randomUUID();
            final String fieldName = "keyedField-" + uuid;
            final String keyFieldName = "key-" + uuid;
            keyFieldNames.add(keyFieldName);
            impl.registerNameForKey(key, fieldName);
            RecordClassTransformer.addComponent(classNode, fieldName, key.getFieldName());

            classNode.fields.add(
                    new FieldNode(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            keyFieldName,
                            "Lrecordrecoder/impl/record/RecordComponentKeyImpl;",
                            "Lrecordrecoder/impl/record/RecordComponentKeyImpl;",
                            null
                    )
            );
            {
                final var keyFieldInitializer = generateKeyFieldInitializer(classNode.name, keyFieldName, fieldName);
                final var returnNode = RecordClassTransformer.findFromLast(staticInitializer.instructions, node -> node.getOpcode() == Opcodes.RETURN);
                returnNode.ifPresent(
                        r -> staticInitializer.instructions.insertBefore(r, keyFieldInitializer)
                );
            }
            {
                final var fieldInitializer = generateFieldInitializer(classNode.name, keyFieldName, fieldName);
                var returnNode = RecordClassTransformer.findFromLast(canonicalConstructor.instructions, node -> node.getOpcode() == Opcodes.RETURN);
                if (returnNode.isPresent()) {
                    canonicalConstructor.instructions.insertBefore(returnNode.get(), fieldInitializer);
                }
            }
            implementRecordMethod(indyToString, key, classNode.name, fieldName);
            implementRecordMethod(indyHashCode, key, classNode.name, fieldName);
            implementRecordMethod(indyEquals, key, classNode.name, fieldName);
            MethodNode getter = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    fieldName,
                    "()Ljava/lang/Object;",
                    "()Ljava/lang/Object;",
                    new String[]{}
            );
            getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, classNode.name, fieldName, "Ljava/lang/Object;"));
            getter.instructions.add(new InsnNode(Opcodes.ARETURN));
            classNode.methods.add(getter);
        }
        if (keys.isEmpty())
            return;
        String newDesc = appendArguments(canonicalConstructor.desc, keys.size());
        String newSignature = appendArguments(canonicalConstructor.signature, keys.size());
        final MethodNode newCanonicalConstructor = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "<init>",
                newDesc,
                newSignature,
                new String[]{}
        );
        int offset = Type.getArgumentCount(canonicalConstructor.desc) + 1 /* +1 for this */;
        for (String name : keyFieldNames) {
            newCanonicalConstructor.visitFieldInsn(Opcodes.GETSTATIC, classNode.name, name, "Lrecordrecoder/impl/record/RecordComponentKeyImpl;");
            newCanonicalConstructor.visitVarInsn(Opcodes.ALOAD, offset++);
            newCanonicalConstructor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "recordrecoder/impl/record/RecordComponentKeyImpl", "queueNext", "(Ljava/lang/Object;)V", false);
        }
        Type[] types1 = Type.getArgumentTypes(canonicalConstructor.desc);
        newCanonicalConstructor.visitVarInsn(Opcodes.ALOAD, 0);
        for (int i = 1;/* 1 for this */ i <= types1.length; i++) {
            newCanonicalConstructor.visitVarInsn(getLoadOpcodeForType(types1[i - 1]), i);
        }
        newCanonicalConstructor.instructions.add(
                new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        classNode.name,
                        canonicalConstructor.name,
                        canonicalConstructor.desc,
                        false
                )
        );
        newCanonicalConstructor.visitInsn(Opcodes.RETURN);
        classNode.methods.addFirst(newCanonicalConstructor);
    }

    private static void addComponent(final ClassNode targetClass, final String fieldName, String facingName) {
        addComponent(targetClass.fields, targetClass.recordComponents, fieldName, facingName);
    }

    private static void addComponent(final List<FieldNode> fields, final List<RecordComponentNode> components, final String fieldName, String facingName) {
        var component = new RecordComponentNode(
                fieldName,
                "Ljava/lang/Object;",
                "Ljava/lang/Object;"
        );
        if (component.visibleAnnotations == null)
            component.visibleAnnotations = new ArrayList<>();
        var facingNameNode = new AnnotationNode("Lrecordrecoder/api/record/FacingName;");
        facingNameNode.visit("value", facingName);
        component.visibleAnnotations.add(facingNameNode);

        components.add(component);
        fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                fieldName,
                "Ljava/lang/Object;",
                "Ljava/lang/Object;",
                null
        ));
    }

    private static InsnList generateKeyFieldInitializer(final String recordClassName, final String keyFieldName, final String fieldName) {
        InsnList keyFieldInitializer = new InsnList();
        keyFieldInitializer.add(new FieldInsnNode(Opcodes.GETSTATIC, "recordrecoder/api/record/ComponentKeyRegistry", "INSTANCE", "Lrecordrecoder/api/record/ComponentKeyRegistry;"));
        keyFieldInitializer.add(new TypeInsnNode(Opcodes.CHECKCAST, "recordrecoder/impl/record/ComponentKeyRegistryImpl"));
        keyFieldInitializer.add(new LdcInsnNode(fieldName));
        keyFieldInitializer.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "recordrecoder/impl/record/ComponentKeyRegistryImpl", "getKeyForName", "(Ljava/lang/String;)Lrecordrecoder/impl/record/RecordComponentKeyImpl;", false));
        keyFieldInitializer.add(new TypeInsnNode(Opcodes.CHECKCAST, "recordrecoder/impl/record/RecordComponentKeyImpl"));
        keyFieldInitializer.add(new InsnNode(Opcodes.DUP));
        keyFieldInitializer.add(new FieldInsnNode(Opcodes.PUTSTATIC, recordClassName, keyFieldName, "Lrecordrecoder/impl/record/RecordComponentKeyImpl;"));
        keyFieldInitializer.add(
                new LdcInsnNode(
                        new Handle(
                                Opcodes.H_GETFIELD,
                                recordClassName,
                                fieldName,
                                "Ljava/lang/Object;",
                                false
                        )
                )
        );
        keyFieldInitializer.add(
                new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "recordrecoder/impl/record/RecordComponentKeyImpl",
                        "provideGetter",
                        "(Ljava/lang/invoke/MethodHandle;)V",
                        false
                )
        );
        return keyFieldInitializer;
    }

    private static InsnList generateFieldInitializer(final String recordClassName, final String keyFieldName, final String fieldName) {
        InsnList fieldInitializer = new InsnList();
        fieldInitializer.add(new VarInsnNode(Opcodes.ALOAD, 0));
        fieldInitializer.add(new FieldInsnNode(Opcodes.GETSTATIC, recordClassName, keyFieldName, "Lrecordrecoder/impl/record/RecordComponentKeyImpl;"));
        fieldInitializer.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "recordrecoder/impl/record/RecordComponentKeyImpl", "getNext", "()Ljava/lang/Object;", false));
        fieldInitializer.add(new FieldInsnNode(Opcodes.PUTFIELD, recordClassName, fieldName, "Ljava/lang/Object;"));
        return fieldInitializer;
    }

    private static void implementRecordMethod(final InvokeDynamicInsnNode indy, final RecordComponentKeyImpl<?> key, final String recordClassName, final String fieldName) {

        Object[] newArgs = new Object[indy.bsmArgs.length + 1];
        System.arraycopy(indy.bsmArgs, 0, newArgs, 0, indy.bsmArgs.length);
        newArgs[1] = newArgs[1] + ";" + key.getFieldName();
        newArgs[indy.bsmArgs.length] = new Handle(
                Opcodes.H_GETFIELD,
                recordClassName,
                fieldName,
                "Ljava/lang/Object;",
                false
        );
        indy.bsmArgs = newArgs;
    }

    private static Optional<AbstractInsnNode> findFromLast(final InsnList insns, final Predicate<AbstractInsnNode> predicate) {
        for (AbstractInsnNode node = insns.getLast(); node != null; node = node.getPrevious()) {
            if (predicate.test(node)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private static Optional<InvokeDynamicInsnNode> findIndy(final InsnList insns, final String name, final String desc) {
        for (AbstractInsnNode node : insns) {
            if (node instanceof InvokeDynamicInsnNode indy && name.equals(indy.name) && desc.equals(indy.desc)) {
                return Optional.of(indy);
            }
        }
        return Optional.empty();
    }

    private static <V, T> @Nullable T map(final @Nullable V v, final Function<@NotNull V, T> mapper) {
        return v == null
               ? null
               : mapper.apply(v);
    }

    @Override
    public boolean checkActive(final MixinEnvironment environment) {
        return true;
    }

    @Override
    public void preApply(final ITargetClassContext context) {
    }

    @Override
    public void postApply(final ITargetClassContext context) {
        ClassNode classNode = context.getClassNode();
        if (!classNode.superName.equals("java/lang/Record")) {
            return;
        }
        transform(classNode);
    }

    @Override
    public void export(final MixinEnvironment env, final String name, final boolean force, final ClassNode classNode) {

    }

    private static String appendArguments(String desc, int numAdditional) {
        var splitDesc = desc.split("\\)");
        if (splitDesc.length != 2) {
            return null;
        }
        return splitDesc[0] + "Ljava/lang/Object;".repeat(Math.max(0, numAdditional)) + ")" + splitDesc[1];
    }

    public static int getLoadOpcodeForType(Type type) {
        if (
                type == Type.INT_TYPE
                        || type == Type.SHORT_TYPE
                        || type == Type.BYTE_TYPE
                        || type == Type.CHAR_TYPE
                        || type == Type.BOOLEAN_TYPE
        )
            return Opcodes.ILOAD;
        if (type == Type.FLOAT_TYPE)
            return Opcodes.FLOAD;
        if (type == Type.DOUBLE_TYPE)
            return Opcodes.DLOAD;
        if (type == Type.LONG_TYPE)
            return Opcodes.LLOAD;
        return Opcodes.ALOAD;
    }
}
