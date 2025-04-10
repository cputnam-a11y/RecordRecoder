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
import recordrecoder.impl.RecordRecoder;
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
        // Early return if not a Record class
        if (!classNode.superName.equals(Constants.RECORD.getInternalName())) {
            RecordRecoder.LOGGER.warn("Class {} is not a record class, skipping transformation.", classNode.name);
            return;
        }

        final ComponentKeyRegistryImpl impl = (ComponentKeyRegistryImpl) ComponentKeyRegistry.INSTANCE;
        List<RecordComponentKeyImpl<?>> keys = impl.getForClass(classNode.name);
        if (keys.isEmpty())
            return;

        // Get record component types
        final Type[] types = classNode.recordComponents.stream()
                .map(node -> node.descriptor)
                .map(Type::getType)
                .toArray(Type[]::new);

        // Find necessary methods
        MethodNode canonicalConstructor = null;
        for (MethodNode node : classNode.methods) {
            if (node.name.equals("<init>") && Arrays.equals(Type.getArgumentTypes(node.desc), types)) {
                canonicalConstructor = node;
                break;
            }
        }

        // If no canonical constructor was found, we can't proceed
        if (canonicalConstructor == null) {
            RecordRecoder.LOGGER.warn("Can't find constructor for {}", classNode.name);
            return;
        }

        // If the constructor descriptor or signature is null, rebuild them dynamically.
        if (canonicalConstructor.desc == null) {
            canonicalConstructor.desc = Type.getMethodDescriptor(Type.VOID_TYPE, types);
        }

        if (canonicalConstructor.signature == null) {
            canonicalConstructor.signature = canonicalConstructor.desc;
        }

        final MethodNode staticInitializer = ensureStaticInitializer(classNode);

        // Find intrinsic methods and their InvokeDynamic nodes
        final Optional<MethodNode> toStringNode = BytecodeHelper.findMethod(classNode, Constants.RECORD$TO_STRING);
        final MethodNode hashCodeNode = Bytecode.findMethod(classNode, "hashCode", Type.getMethodType(Type.INT_TYPE).getDescriptor());
        final MethodNode equalsNode = Bytecode.findMethod(classNode, "equals", Type.getMethodType(Type.BOOLEAN_TYPE, Constants.OBJECT).getDescriptor());

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

        List<String> keyFieldNames = new ArrayList<>();
        for (final RecordComponentKeyImpl<?> key : keys) {
            final UUID uuid = UUID.randomUUID();
            final String fieldName = "keyedField-" + uuid;
            final String keyFieldName = "key-" + uuid;
            keyFieldNames.add(keyFieldName);
            impl.registerNameForKey(key, fieldName);
            RecordClassTransformer.addComponent(classNode, fieldName, key.getFieldName());

            // Add static field for the key
            classNode.fields.add(
                    new FieldNode(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            keyFieldName,
                            Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor(),
                            Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor(),
                            null
                    )
            );

            // Initialize the key field in static initializer
            final var keyFieldInitializer = generateKeyFieldInitializer(classNode.name, keyFieldName, fieldName);
            final var returnNode = RecordClassTransformer.findFromLast(staticInitializer.instructions, node -> node.getOpcode() == Opcodes.RETURN);
            if (returnNode.isPresent()) {
                staticInitializer.instructions.insertBefore(returnNode.get(), keyFieldInitializer);
            } else {
                // If no return instruction found, add to the end (before adding our own return)
                staticInitializer.instructions.add(keyFieldInitializer);
            }

            // Initialize the field in the constructor
            final var fieldInitializer = generateFieldInitializer(classNode.name, keyFieldName, fieldName);
            var constructorReturnNode = RecordClassTransformer.findFromLast(canonicalConstructor.instructions, node -> node.getOpcode() == Opcodes.RETURN);
            if (constructorReturnNode.isPresent()) {
                canonicalConstructor.instructions.insertBefore(constructorReturnNode.get(), fieldInitializer);
            } else {
                // If no return instruction found, add to the end (should never happen for a valid constructor)
                RecordRecoder.LOGGER.warn("Constructor does not seems valid as it does not have a return instruction. Adding field initializer at the end.");

                canonicalConstructor.instructions.add(fieldInitializer);
                canonicalConstructor.instructions.add(new InsnNode(Opcodes.RETURN));
            }

            // Implement Record methods if they exist
            if (indyToString != null)
                implementRecordMethod(indyToString, key, classNode.name, fieldName);
            if (indyHashCode != null)
                implementRecordMethod(indyHashCode, key, classNode.name, fieldName);
            if (indyEquals != null)
                implementRecordMethod(indyEquals, key, classNode.name, fieldName);

            // Add getter method
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

        RecordRecoder.LOGGER.info("Adding cannonical constructor for {} with {} keys", classNode.name, keys.size());

        // Create new canonical constructor with additional parameters
        String newDesc = appendArguments(canonicalConstructor.desc, keys.size());
        String newSignature = appendArguments(canonicalConstructor.signature, keys.size());
        int access = canonicalConstructor.access;
        // As the components are in order, this cannot be varargs because the array param is not last
        access = access & ~Opcodes.ACC_VARARGS;

        final MethodNode newCanonicalConstructor = new MethodNode(
                access,
                "<init>",
                newDesc,
                newSignature,
                new String[]{}
        );

        int offset = Type.getArgumentCount(canonicalConstructor.desc) + 1; // +1 for this
        for (String name : keyFieldNames) {
            newCanonicalConstructor.visitFieldInsn(Opcodes.GETSTATIC, classNode.name, name, "Lrecordrecoder/impl/record/RecordComponentKeyImpl;");
            newCanonicalConstructor.visitVarInsn(Opcodes.ALOAD, offset++);
            newCanonicalConstructor.instructions.add(Constants.RECORD_COMPONENT_KEY_IMPL$QUEUE_NEXT.call());
        }

        Type[] types1 = Type.getArgumentTypes(canonicalConstructor.desc);
        newCanonicalConstructor.visitVarInsn(Opcodes.ALOAD, 0);
        for (int i = 1; i <= types1.length; i++) { // 1 for this
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
        RecordRecoder.LOGGER.info("Transformation of {} complete", classNode.name);
    }

    private static MethodNode ensureStaticInitializer(ClassNode classNode) {
        Optional<MethodNode> existingClinit = BytecodeHelper.findMethod(classNode, Constants.CLINIT);

        if (existingClinit.isPresent()) {
            return existingClinit.get();
        }

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
