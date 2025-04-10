package recordrecoder.impl.asm;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import recordrecoder.api.record.ComponentKeyRegistry;
import recordrecoder.impl.RecordRecoder;
import recordrecoder.impl.asm.util.ConstructorUtils;
import recordrecoder.impl.asm.util.LocatorUtils;
import recordrecoder.impl.asm.util.RecordIntrinsicMethods;
import recordrecoder.impl.record.ComponentKeyRegistryImpl;
import recordrecoder.impl.record.RecordComponentKeyImpl;
import recordrecoder.impl.utils.Constants;
import recordrecoder.impl.utils.mixindefaults.IDefaultedExtension;

import java.util.*;
import java.util.function.Predicate;

public class RecordClassTransformer implements IDefaultedExtension {

    private static final String FACING_NAME_ANNOTATION = "Lrecordrecoder/api/record/FacingName;";
    private static final String OBJECT_DESCRIPTOR = "Ljava/lang/Object;";

    @Override
    public void preApply(final ITargetClassContext context) {
        ClassNode classNode = context.getClassNode();
        if (isRecordClass(classNode)) {
            transform(classNode);
        }
    }

    public static void transform(ClassNode classNode) {
        if (!isRecordClass(classNode)) {
            RecordRecoder.LOGGER.warn("Class {} is not a record class, skipping transformation.", classNode.name);
            return;
        }

        final ComponentKeyRegistryImpl registry = (ComponentKeyRegistryImpl) ComponentKeyRegistry.INSTANCE;
        List<RecordComponentKeyImpl<?>> keys = registry.getForClass(classNode.name);

        if (keys.isEmpty()) {
            return;
        }

        // Get record component types
        final Type[] types = extractComponentTypes(classNode);

        // Find necessary methods
        MethodNode canonicalConstructor = LocatorUtils.findCanonicalConstructor(classNode, types);
        if (canonicalConstructor == null) {
            RecordRecoder.LOGGER.warn("Can't find constructor for {}", classNode.name);
            return;
        }

        ensureConstructorMetadata(canonicalConstructor, types);
        final MethodNode staticInitializer = ConstructorUtils.ensureStaticInitializer(classNode);

        // Find intrinsic methods and their InvokeDynamic nodes
        RecordIntrinsicMethods intrinsics = LocatorUtils.findIntrinsicMethods(classNode);

        List<String> keyFieldNames = processKeys(
                classNode,
                keys,
                registry,
                staticInitializer,
                canonicalConstructor,
                intrinsics
        );

        if (!keys.isEmpty()) {
            addExtendedCanonicalConstructor(classNode, canonicalConstructor, keyFieldNames);
            RecordRecoder.LOGGER.info("Transformation of {} complete", classNode.name);
        }
    }

    private static boolean isRecordClass(ClassNode classNode) {
        return classNode.superName.equals(Constants.RECORD.getInternalName());
    }

    private static Type[] extractComponentTypes(ClassNode classNode) {
        return classNode.recordComponents.stream()
                .map(node -> node.descriptor)
                .map(Type::getType)
                .toArray(Type[]::new);
    }

    private static void ensureConstructorMetadata(MethodNode constructor, Type[] types) {
        if (constructor.desc == null) {
            constructor.desc = Type.getMethodDescriptor(Type.VOID_TYPE, types);
        }

        if (constructor.signature == null) {
            constructor.signature = constructor.desc;
        }
    }

    private static List<String> processKeys(
            ClassNode classNode,
            List<RecordComponentKeyImpl<?>> keys,
            ComponentKeyRegistryImpl registry,
            MethodNode staticInitializer,
            MethodNode canonicalConstructor,
            RecordIntrinsicMethods intrinsics) {

        List<String> keyFieldNames = new ArrayList<>(keys.size());

        for (final RecordComponentKeyImpl<?> key : keys) {
            final UUID uuid = UUID.randomUUID();
            final String fieldName = "keyedField-" + uuid;
            final String keyFieldName = "key-" + uuid;
            keyFieldNames.add(keyFieldName);

            registry.registerNameForKey(key, fieldName);
            addComponent(classNode, fieldName, key.getFieldName());
            addKeyField(classNode, keyFieldName);

            // Initialize fields
            addKeyFieldInitializer(classNode, staticInitializer, keyFieldName, fieldName);
            addFieldInitializer(classNode, canonicalConstructor, keyFieldName, fieldName);

            // Implement Record methods if they exist
            implementRecordMethods(intrinsics, key, classNode.name, fieldName);

            // Add getter method
            addGetterMethod(classNode, fieldName);
        }

        return keyFieldNames;
    }

    private static void addKeyField(ClassNode classNode, String keyFieldName) {
        classNode.fields.add(
                new FieldNode(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        keyFieldName,
                        Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor(),
                        Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor(),
                        null
                )
        );
    }

    private static void addGetterMethod(ClassNode classNode, String fieldName) {
        MethodNode getter = new MethodNode(
                Opcodes.ACC_PUBLIC,
                fieldName,
                "()Ljava/lang/Object;",
                "()Ljava/lang/Object;",
                new String[]{}
        );

        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new FieldInsnNode(Opcodes.GETFIELD, classNode.name, fieldName, OBJECT_DESCRIPTOR));
        instructions.add(new InsnNode(Opcodes.ARETURN));

        getter.instructions = instructions;
        classNode.methods.add(getter);
    }

    private static void implementRecordMethods(
            RecordIntrinsicMethods intrinsics,
            RecordComponentKeyImpl<?> key,
            String recordClassName,
            String fieldName) {

        if (intrinsics.string() != null) {
            implementRecordMethod(intrinsics.string(), key, recordClassName, fieldName);
        }

        if (intrinsics.hash() != null) {
            implementRecordMethod(intrinsics.hash(), key, recordClassName, fieldName);
        }

        if (intrinsics.equals() != null) {
            implementRecordMethod(intrinsics.equals(), key, recordClassName, fieldName);
        }
    }

    private static void addExtendedCanonicalConstructor(
            ClassNode classNode,
            MethodNode canonicalConstructor,
            List<String> keyFieldNames) {

        RecordRecoder.LOGGER.info("Adding canonical constructor for {} with {} keys",
                classNode.name, keyFieldNames.size());

        // Create new canonical constructor with additional parameters
        String newDesc = appendArguments(canonicalConstructor.desc, keyFieldNames.size());
        String newSignature = appendArguments(canonicalConstructor.signature, keyFieldNames.size());
        int access = canonicalConstructor.access;
        // As the components are in order, this cannot be varargs because the array param is not last
        access = access & ~Opcodes.ACC_VARARGS;

        final MethodNode newConstructor = ConstructorUtils.createExtendedConstructor(
                classNode, canonicalConstructor, newDesc, newSignature, access, keyFieldNames);

        classNode.methods.addFirst(newConstructor);
    }

    private static void addComponent(final ClassNode targetClass, final String fieldName, String facingName) {
        // Create a new record component
        RecordComponentNode component = new RecordComponentNode(
                fieldName,
                OBJECT_DESCRIPTOR,
                OBJECT_DESCRIPTOR
        );

        // Add facing name annotation
        if (component.visibleAnnotations == null) {
            component.visibleAnnotations = new ArrayList<>();
        }

        AnnotationNode facingNameNode = new AnnotationNode(FACING_NAME_ANNOTATION);
        facingNameNode.visit("value", facingName);
        component.visibleAnnotations.add(facingNameNode);

        // Add component and field
        targetClass.recordComponents.add(component);
        targetClass.fields.add(
                new FieldNode(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                        fieldName,
                        OBJECT_DESCRIPTOR,
                        OBJECT_DESCRIPTOR,
                        null
                )
        );
    }

    private static void addKeyFieldInitializer(
            final ClassNode classNode,
            final MethodNode staticInitializer,
            final String keyFieldName,
            final String fieldName) {

        InsnList keyFieldInitializer = generateKeyFieldInitializer(classNode.name, keyFieldName, fieldName);

        AbstractInsnNode returnNode = findLastReturn(staticInitializer.instructions)
                .orElse(null);

        if (returnNode != null) {
            staticInitializer.instructions.insertBefore(returnNode, keyFieldInitializer);
        } else {
            // If no return instruction found, add to the end
            staticInitializer.instructions.add(keyFieldInitializer);
        }
    }

    private static void addFieldInitializer(
            final ClassNode classNode,
            final MethodNode constructor,
            final String keyFieldName,
            final String fieldName) {

        InsnList fieldInitializer = generateFieldInitializer(classNode.name, keyFieldName, fieldName);

        AbstractInsnNode returnNode = findLastReturn(constructor.instructions)
                .orElse(null);

        if (returnNode != null) {
            constructor.instructions.insertBefore(returnNode, fieldInitializer);
        } else {
            // If no return instruction found, add to the end
            RecordRecoder.LOGGER.warn("Constructor does not seem valid as it does not have a return instruction. Adding field initializer at the end.");
            constructor.instructions.add(fieldInitializer);
            constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        }
    }

    private static InsnList generateKeyFieldInitializer(
            final String recordClassName,
            final String keyFieldName,
            final String fieldName) {

        InsnList instructions = new InsnList();

        // Get the registry instance
        instructions.add(Constants.COMPONENT_KEY_REGISTRY$INSTANCE.get());
        instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, Constants.COMPONENT_KEY_REGISTRY_IMPL.getInternalName()));

        // Get the key for the field name
        instructions.add(new LdcInsnNode(fieldName));
        instructions.add(Constants.COMPONENT_KEY_REGISTRY_IMPL$GET_KEY_FOR_NAME.call());
        instructions.add(new TypeInsnNode(
                Opcodes.CHECKCAST,
                Constants.RECORD_COMPONENT_KEY_IMPL.getInternalName()
        ));

        // Store in static field
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new FieldInsnNode(
                Opcodes.PUTSTATIC,
                recordClassName,
                keyFieldName,
                Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor()
        ));

        // Set up getter handle
        instructions.add(new LdcInsnNode(
                new Handle(
                        Opcodes.H_GETFIELD,
                        recordClassName,
                        fieldName,
                        OBJECT_DESCRIPTOR,
                        false
                )
        ));
        instructions.add(Constants.RECORD_COMPONENT_KEY_IMPL$PROVIDE_GETTER.call());

        return instructions;
    }

    private static InsnList generateFieldInitializer(
            final String recordClassName,
            final String keyFieldName,
            final String fieldName) {

        InsnList instructions = new InsnList();

        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                recordClassName,
                keyFieldName,
                Constants.RECORD_COMPONENT_KEY_IMPL.getDescriptor()
        ));
        instructions.add(Constants.RECORD_COMPONENT_KEY_IMPL$GET_NEXT.call());
        instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, recordClassName, fieldName, OBJECT_DESCRIPTOR));

        return instructions;
    }

    private static void implementRecordMethod(
            final InvokeDynamicInsnNode indy,
            final RecordComponentKeyImpl<?> key,
            final String recordClassName,
            final String fieldName) {

        Object[] newArgs = new Object[indy.bsmArgs.length + 1];
        System.arraycopy(indy.bsmArgs, 0, newArgs, 0, indy.bsmArgs.length);
        newArgs[1] = newArgs[1] + ";" + key.getFieldName();
        newArgs[indy.bsmArgs.length] = new Handle(
                Opcodes.H_GETFIELD,
                recordClassName,
                fieldName,
                OBJECT_DESCRIPTOR,
                false
        );
        indy.bsmArgs = newArgs;
    }

    private static Optional<AbstractInsnNode> findLastReturn(final InsnList insns) {
        return findFromLast(insns, node -> node.getOpcode() == Opcodes.RETURN);
    }

    private static Optional<AbstractInsnNode> findFromLast(
            final InsnList insns,
            final Predicate<AbstractInsnNode> predicate) {

        for (AbstractInsnNode node = insns.getLast(); node != null; node = node.getPrevious()) {
            if (predicate.test(node)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private static String appendArguments(String desc, int numAdditional) {
        var splitDesc = desc.split("\\)");
        if (splitDesc.length != 2) {
            return null;
        }
        return splitDesc[0] + OBJECT_DESCRIPTOR.repeat(Math.max(0, numAdditional)) + ")" + splitDesc[1];
    }
}