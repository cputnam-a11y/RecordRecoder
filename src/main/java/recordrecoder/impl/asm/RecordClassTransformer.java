package recordrecoder.impl.asm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.util.Bytecode;
import recordrecoder.api.record.ComponentKeyRegistry;
import recordrecoder.impl.record.ComponentKeyRegistryImpl;
import recordrecoder.impl.record.RecordComponentKeyImpl;
import recordrecoder.impl.utils.Constants;
import recordrecoder.impl.utils.asmhelpers.BytecodeHelper;
import recordrecoder.impl.utils.asmhelpers.MethodNameTypeTuple;
import recordrecoder.impl.utils.mixindefaults.IDefaultedExtension;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class RecordClassTransformer implements IDefaultedExtension {

    @Override
    public void preApply(final ITargetClassContext context) {
        ClassNode classNode = context.getClassNode();
        if (!classNode.superName.equals(Constants.RECORD.getInternalName())) {
            return;
        }
        transform(classNode);
    }

    public static void transform(ClassNode classNode) {
        if (!classNode.superName.equals(Constants.RECORD.getInternalName())) {
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
            final Optional<MethodNode> toStringNode = BytecodeHelper.findMethod(classNode, Constants.RECORD$TO_STRING);
            final MethodNode hashCodeNode = Bytecode.findMethod(classNode, "hashCode", Type.getMethodType(Type.INT_TYPE).getDescriptor());
            final MethodNode equalsNode = Bytecode.findMethod(classNode, "equals", Type.getMethodType(Type.BOOLEAN_TYPE, Constants.OBJECT).getDescriptor());
            staticInitializer = BytecodeHelper.findMethod(classNode, Constants.CLINIT).orElse(null);
            for (MethodNode node : classNode.methods) {
                if (node.name.equals("<init>")) {
                    if (Arrays.equals(Type.getArgumentTypes(node.desc), types))
                        canonicalConstructor = node;
                }
            }
            indyToString = toStringNode.flatMap(
                            node -> findIndy(
                                    node.instructions,
                                    Constants.TO_STRING_INDY.apply(classNode.name)
                            )
                    )
                    .orElse(null);
            indyHashCode = map(
                    hashCodeNode,
                    node -> findIndy(
                            node.instructions,
                            Constants.HASH_CODE_INDY.apply(classNode.name)
                    ).orElse(null)
            );
            indyEquals = map(
                    equalsNode,
                    node -> findIndy(
                            node.instructions,
                            Constants.EQUALS_INDY.apply(classNode.name)
                    ).orElse(null)
            );
        }
        if (staticInitializer == null || canonicalConstructor == null) {
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
                            Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor(),
                            Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor(),
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
            if (indyToString != null)
                implementRecordMethod(indyToString, key, classNode.name, fieldName);
            if (indyHashCode != null)
                implementRecordMethod(indyHashCode, key, classNode.name, fieldName);
            if (indyEquals != null)
                implementRecordMethod(indyEquals, key, classNode.name, fieldName);
            MethodNode getter = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    fieldName,
                    "()Ljava/lang/Object;",
                    "()Ljava/lang/Object;",
                    new String[]{}
            );
            getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, classNode.name, fieldName, Constants.OBJECT.getDescriptor()));
            getter.instructions.add(new InsnNode(Opcodes.ARETURN));
            classNode.methods.add(getter);
        }
        if (keys.isEmpty())
            return;
        String newDesc = appendArguments(canonicalConstructor.desc, keys.size());
        String newSignature = appendArguments(canonicalConstructor.signature, keys.size());
        int access = canonicalConstructor.access;
        // as the components are in order, this cannot be varargs because the array param is not last
        access = access & ~Opcodes.ACC_VARARGS;
        final MethodNode newCanonicalConstructor = new MethodNode(
                access,
                "<init>",
                newDesc,
                newSignature,
                new String[]{}
        );
        int offset = Type.getArgumentCount(canonicalConstructor.desc) + 1 /* +1 for this */;
        for (String name : keyFieldNames) {
            newCanonicalConstructor.visitFieldInsn(Opcodes.GETSTATIC, classNode.name, name, "Lrecordrecoder/impl/record/RecordComponentKeyImpl;");
            newCanonicalConstructor.visitVarInsn(Opcodes.ALOAD, offset++);
            newCanonicalConstructor.instructions.add(Constants.RECORD_COMPONENT_KEY_IMPL$QUEUE_NEXT.call());
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
                Constants.OBJECT.getDescriptor(),
                Constants.OBJECT.getDescriptor()
        );
        if (component.visibleAnnotations == null)
            component.visibleAnnotations = new ArrayList<>();
        var facingNameNode = new AnnotationNode("Lrecordrecoder/api/record/FacingName;");
        facingNameNode.visit("value", facingName);
        component.visibleAnnotations.add(facingNameNode);

        components.add(component);
        fields.add(
                new FieldNode(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                        fieldName,
                        Constants.OBJECT.getDescriptor(),
                        Constants.OBJECT.getDescriptor(),
                        null
                )
        );
    }

    private static InsnList generateKeyFieldInitializer(final String recordClassName, final String keyFieldName, final String fieldName) {
        InsnList keyFieldInitializer = new InsnList();
        keyFieldInitializer.add(Constants.COMPONENT_KEY_REGISTRY$INSTANCE.get());
        keyFieldInitializer.add(new TypeInsnNode(Opcodes.CHECKCAST, Constants.COMPONENT_KEY_REGISTRY_IMPL.getInternalName()));
        keyFieldInitializer.add(new LdcInsnNode(fieldName));
        keyFieldInitializer.add(Constants.COMPONENT_KEY_REGISTRY_IMPL$GET_KEY_FOR_NAME.call());
        keyFieldInitializer.add(
                new TypeInsnNode(
                        Opcodes.CHECKCAST,
                        Constants.RECORD_COMPONENT_KEY_IMPL.getInternalName()
                )
        );
        keyFieldInitializer.add(new InsnNode(Opcodes.DUP));
        keyFieldInitializer.add(
                new FieldInsnNode(
                        Opcodes.PUTSTATIC,
                        recordClassName,
                        keyFieldName,
                        Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor()
                )
        );
        keyFieldInitializer.add(
                new LdcInsnNode(
                        new Handle(
                                Opcodes.H_GETFIELD,
                                recordClassName,
                                fieldName,
                                Constants.OBJECT.getDescriptor(),
                                false
                        )
                )
        );
        keyFieldInitializer.add(Constants.RECORD_COMPONENT_KEY_IMPL$PROVIDE_GETTER.call());
        return keyFieldInitializer;
    }

    private static InsnList generateFieldInitializer(final String recordClassName, final String keyFieldName, final String fieldName) {
        InsnList fieldInitializer = new InsnList();
        fieldInitializer.add(new VarInsnNode(Opcodes.ALOAD, 0));
        fieldInitializer.add(new FieldInsnNode(Opcodes.GETSTATIC, recordClassName, keyFieldName, Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor()));
        fieldInitializer.add(Constants.RECORD_COMPONENT_KEY_IMPL$GET_NEXT.call());
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

    private static Optional<InvokeDynamicInsnNode> findIndy(final InsnList insns, MethodNameTypeTuple nameAndType) {
        for (AbstractInsnNode node : insns) {
            if (node instanceof InvokeDynamicInsnNode indy && nameAndType.name().equals(indy.name) && nameAndType.type().getDescriptor().equals(indy.desc)) {
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
