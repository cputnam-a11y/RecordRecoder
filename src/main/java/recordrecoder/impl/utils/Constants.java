package recordrecoder.impl.utils;

import org.objectweb.asm.Type;
import recordrecoder.api.record.ComponentKeyRegistry;
import recordrecoder.impl.record.ComponentKeyRegistryImpl;
import recordrecoder.impl.record.RecordComponentKeyImpl;
import recordrecoder.impl.utils.asmhelpers.MethodNameTypeTuple;
import recordrecoder.impl.utils.asmhelpers.OwnedField;
import recordrecoder.impl.utils.asmhelpers.OwnedMethod;

import java.lang.invoke.MethodHandle;
import java.util.function.Function;

public interface Constants {
    // region Types
    Type OBJECT = Type.getType(Object.class);
    Type RECORD = Type.getType(Record.class);
    Type STRING = Type.getType(String.class);
    Type METHOD_HANDLE = Type.getType(MethodHandle.class);
    Type VOID = Type.getType("V");
    Type RECORD_COMPONENT_KEY = Type.getType(RecordComponentKeyImpl.class);
    Type RECORD_COMPONENT_KEY_IMPL = Type.getType(RecordComponentKeyImpl.class);
    Type COMPONENT_KEY_REGISTRY = Type.getType(ComponentKeyRegistry.class);
    Type COMPONENT_KEY_REGISTRY_IMPL = Type.getType(ComponentKeyRegistryImpl.class);
    // endregion
    // region Fields
    OwnedField COMPONENT_KEY_REGISTRY$INSTANCE = new OwnedField(
            COMPONENT_KEY_REGISTRY,
            "INSTANCE",
            COMPONENT_KEY_REGISTRY,
            true
    );
    // endregion
    // region Methods
    OwnedMethod COMPONENT_KEY_REGISTRY_IMPL$GET_KEY_FOR_NAME = OwnedMethod.ofInstance(
            COMPONENT_KEY_REGISTRY_IMPL,
            "getKeyForName",
            Type.getMethodType(RECORD_COMPONENT_KEY_IMPL, STRING)
    );

    OwnedMethod RECORD_COMPONENT_KEY_IMPL$QUEUE_NEXT = OwnedMethod.ofInstance(
            RECORD_COMPONENT_KEY_IMPL,
            "queueNext",
            Type.getMethodType(VOID, OBJECT)
    );

    OwnedMethod RECORD_COMPONENT_KEY_IMPL$PROVIDE_GETTER = OwnedMethod.ofInstance(
            RECORD_COMPONENT_KEY_IMPL,
            "provideGetter",
            Type.getMethodType(VOID, METHOD_HANDLE)
    );

    OwnedMethod RECORD_COMPONENT_KEY_IMPL$GET_NEXT = OwnedMethod.ofInstance(
            RECORD_COMPONENT_KEY_IMPL,
            "getNext",
            Type.getMethodType(OBJECT)
    );
    // endregion
    // region Method Types
    MethodNameTypeTuple RECORD$TO_STRING = new MethodNameTypeTuple("toString", Type.getMethodType(STRING));
    MethodNameTypeTuple CLINIT = new MethodNameTypeTuple("<clinit>", Type.getMethodType(VOID));
    // endregion
    // region Indy Types
    Function<String, MethodNameTypeTuple> TO_STRING_INDY = (name) -> new MethodNameTypeTuple(
            "toString",
            Type.getMethodType(
                    STRING,
                    Type.getObjectType(name)
            )
    );

    Function<String, MethodNameTypeTuple> HASH_CODE_INDY = (name) -> new MethodNameTypeTuple(
            "hashCode",
            Type.getMethodType(
                    Type.INT_TYPE,
                    Type.getObjectType(name)
            )
    );

    Function<String, MethodNameTypeTuple> EQUALS_INDY = (name) -> new MethodNameTypeTuple(
            "equals",
            Type.getMethodType(
                    Type.BOOLEAN_TYPE,
                    Type.getObjectType(name),
                    OBJECT
            )
    );
    // endregion
    String ENTRYPOINT_KEY = "recordrecoder:register";
}
