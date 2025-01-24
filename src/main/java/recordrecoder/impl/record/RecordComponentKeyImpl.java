package recordrecoder.impl.record;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import recordrecoder.api.record.RecordComponentKey;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

import static recordrecoder.impl.utils.asmhelpers.ClassNameHelper.toBinaryName;
import static recordrecoder.impl.utils.asmhelpers.ClassNameHelper.toInternalName;

public final class RecordComponentKeyImpl<T> implements RecordComponentKey<T> {
    private final String targetClassName;
    private final String componentClassName;
    private final Supplier<Class<?>> targetClassGetter;
    private final Supplier<Class<?>> componentClassGetter;
    private final ThreadLocal<T> next;
    private final String fieldName;
    @Nullable
    MethodHandle getter;

    public RecordComponentKeyImpl(String fieldName, String targetClassName, String componentClassName, Supplier<T> defaultValueSupplier) {
        this.targetClassName = targetClassName;
        this.componentClassName = componentClassName;
        this.targetClassGetter = Suppliers.memoize(createTargetClassSupplier(targetClassName));
        this.componentClassGetter = Suppliers.memoize(createComponentClassSupplier(componentClassName));
        this.next = ThreadLocal.withInitial(defaultValueSupplier);
        this.fieldName = fieldName;
    }

    public RecordComponentKeyImpl(String fieldName, String targetClassName, Class<?> componentClass, Supplier<T> defaultValueSupplier) {
        this.targetClassName = targetClassName;
        this.componentClassName = toInternalName(componentClass.getName());
        this.targetClassGetter = Suppliers.memoize(createTargetClassSupplier(targetClassName));
        this.componentClassGetter = Suppliers.memoize(() -> componentClass);
        this.next = ThreadLocal.withInitial(defaultValueSupplier);
        this.fieldName = fieldName;
    }


    @Override
    public <I extends Record> T get(I instance) throws KeyMismatchException, IllegalStateException {
        Objects.requireNonNull(instance, "Instance cannot be null");
        if (!targetClassGetter.get().isAssignableFrom(instance.getClass()))
            throw new KeyMismatchException(fieldName, instance.getClass().getSimpleName());
        if (this.getter == null)
            throw new IllegalStateException("Getter not yet provided for RecordComponentKey " + this.getFieldName());
        return getUnchecked(instance);
    }

    @SuppressWarnings({"unchecked", "DataFlowIssue"})
    private <I extends Record> T getUnchecked(I instance) {
        try {
            return (T) getter.invokeExact((Object) instance);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void queueNext(T value) {
        if (!componentClassGetter.get().isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException("Value " + value + " is not of type " + componentClassName);
        }
        next.set(value);
    }

    @SuppressWarnings("unused") // used in asm generated field initializers
    public T getNext() {
        var value = next.get();
        next.remove();
        return value;
    }

    private Supplier<Class<?>> createTargetClassSupplier(String targetClassName) {
        return () -> {
            try {
                return Class.forName(toBinaryName(targetClassName));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to get target class: ", e);
            }
        };
    }

    private Supplier<Class<?>> createComponentClassSupplier(String componentClassName) {
        return () -> {
            try {
                return Class.forName(toBinaryName(componentClassName));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to get component class: ", e);
            }
        };
    }

    @ApiStatus.Internal
    @SuppressWarnings("unused") // used in asm generated static initializers
    public void provideGetter(MethodHandle getter) {
        this.getter = getter.asType( // currently seems the most efficient, looking into methods of acquiring constant promotion (none found that worked consistently)
                MethodType.methodType(
                        Object.class,
                        Object.class
                )
        );
    }

    public String getTargetClassName() {
        return targetClassName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
