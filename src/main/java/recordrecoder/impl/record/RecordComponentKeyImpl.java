package recordrecoder.impl.record;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import recordrecoder.api.record.RecordComponentKey;
import recordrecoder.impl.RecordRecoder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

import static recordrecoder.impl.utils.asmhelpers.ClassNameHelper.toBinaryName;
import static recordrecoder.impl.utils.asmhelpers.ClassNameHelper.toInternalName;

/**
 * Implementation of the RecordComponentKey interface that provides access to record components.
 * <p>
 * This class allows for type-safe access to record components by providing mechanisms to:
 * <ul>
 *   <li>Retrieve record component values from record instances</li>
 *   <li>Handle the necessary class loading and method handle management</li>
 * </ul>
 *
 * @param <T> The type of the record component this key represents
 */
public final class RecordComponentKeyImpl<T> implements RecordComponentKey<T> {
    private final String targetClassName;
    private final String componentClassName;
    private final Supplier<Class<?>> targetClassGetter;
    private final Supplier<Class<?>> componentClassGetter;
    private final ThreadLocal<T> next;
    private final String fieldName;
    @Nullable
    MethodHandle getter;

    /**
     * Constructs a new RecordComponentKeyImpl using class names.
     *
     * @param fieldName            The name of the field this key represents
     * @param targetClassName      The internal name of the target record class
     * @param componentClassName   The internal name of the component's class
     * @param defaultValueSupplier A supplier that provides default values for the component
     */
    public RecordComponentKeyImpl(String fieldName, String targetClassName, String componentClassName, Supplier<T> defaultValueSupplier) {
        targetClassName = sanitizeFieldName(targetClassName);

        this.targetClassName = targetClassName;
        this.componentClassName = componentClassName;
        this.targetClassGetter = Suppliers.memoize(createTargetClassSupplier(targetClassName));
        this.componentClassGetter = Suppliers.memoize(createComponentClassSupplier(componentClassName));
        this.next = ThreadLocal.withInitial(defaultValueSupplier);
        this.fieldName = fieldName;
    }

    /**
     * Constructs a new RecordComponentKeyImpl using a class name and component class.
     *
     * @param fieldName            The name of the field this key represents
     * @param targetClassName      The internal name of the target record class
     * @param componentClass       The Class object representing the component's type
     * @param defaultValueSupplier A supplier that provides default values for the component
     */
    public RecordComponentKeyImpl(String fieldName, String targetClassName, Class<?> componentClass, Supplier<T> defaultValueSupplier) {
        targetClassName = sanitizeFieldName(targetClassName);

        this.targetClassName = targetClassName;
        this.componentClassName = toInternalName(componentClass.getName());
        this.targetClassGetter = Suppliers.memoize(createTargetClassSupplier(targetClassName));
        this.componentClassGetter = Suppliers.memoize(() -> componentClass);
        this.next = ThreadLocal.withInitial(defaultValueSupplier);
        this.fieldName = fieldName;
    }

    /**
     * Sanitizes the class name by converting dot notation to internal slash notation.
     * Logs a warning if conversion is necessary.
     *
     * @param str The class name to sanitize
     * @return The sanitized class name
     */
    private String sanitizeFieldName(String str) {
        if (str.contains(".")) {
            RecordRecoder.LOGGER.warn("Target class name {} contains '.', replacing with '/'", str);
            RecordRecoder.LOGGER.warn("Whilst this is not a problem (and solved by us, thank us later xD), it is recommended to use the internal name of the class instead!");
            str = str.replace(".", "/");
        }
        return str;
    }

    /**
     * Retrieves the component value from a record instance.
     *
     * @param <I>      The record instance type
     * @param instance The record instance from which to get the component value
     * @return The component value
     * @throws KeyMismatchException   If the key is not applicable to the provided instance type
     * @throws IllegalStateException  If the getter has not been provided yet
     * @throws NullPointerException   If the instance is null
     */
    @Override
    public <I extends Record> T get(I instance) throws KeyMismatchException, IllegalStateException {
        Objects.requireNonNull(instance, "Instance cannot be null");
        if (!targetClassGetter.get().isAssignableFrom(instance.getClass()))
            throw new KeyMismatchException(fieldName, instance.getClass().getSimpleName());
        if (this.getter == null)
            throw new IllegalStateException("Getter not yet provided for RecordComponentKey " + this.getFieldName());
        return getUnchecked(instance);
    }

    /**
     * Internal method that performs the actual invocation of the getter method handle.
     *
     * @param <I>      The record instance type
     * @param instance The record instance from which to get the component value
     * @return The component value
     * @throws RuntimeException If an error occurs during method handle invocation
     */
    @SuppressWarnings({"unchecked", "DataFlowIssue"})
    private <I extends Record> T getUnchecked(I instance) {
        try {
            return (T) getter.invokeExact((Object) instance);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    /**
     * Queues a value to be assigned to this component during the next record instantiation.
     * The value is stored in a ThreadLocal to ensure thread safety.
     *
     * @param value The value to queue
     * @throws IllegalArgumentException If the value is not of the expected component type
     */
    @Override
    public void queueNext(T value) {
        if (!componentClassGetter.get().isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException("Value " + value + " is not of type " + componentClassName);
        }
        next.set(value);
    }

    /**
     * Retrieves and removes the next queued value for this component.
     * Used in ASM generated field initializers.
     *
     * @return The next queued value, or a default value if none was queued
     */
    @SuppressWarnings("unused") // used in asm generated field initializers
    public T getNext() {
        var value = next.get();
        next.remove();
        return value;
    }

    /**
     * Creates a supplier that lazily loads the target class.
     *
     * @param targetClassName The name of the target class
     * @return A supplier that provides the Class object for the target class
     */
    private Supplier<Class<?>> createTargetClassSupplier(String targetClassName) {
        return () -> {
            try {
                return Class.forName(toBinaryName(targetClassName));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to get target class: ", e);
            }
        };
    }

    /**
     * Creates a supplier that lazily loads the component class.
     *
     * @param componentClassName The name of the component class
     * @return A supplier that provides the Class object for the component class
     */
    private Supplier<Class<?>> createComponentClassSupplier(String componentClassName) {
        return () -> {
            try {
                return Class.forName(toBinaryName(componentClassName));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to get component class: ", e);
            }
        };
    }

    /**
     * Sets the method handle used to access the component value from record instances.
     * This method is used by ASM generated static initializers.
     *
     * @param getter The method handle to use for accessing the component value
     */
    @ApiStatus.Internal
    @SuppressWarnings("unused")
    public void provideGetter(MethodHandle getter) {
        this.getter = getter.asType( // currently seems the most efficient, looking into methods of acquiring constant promotion (none found that worked consistently)
                MethodType.methodType(
                        Object.class,
                        Object.class
                )
        );
    }

    /**
     * Gets the internal name of the target record class.
     *
     * @return The internal name of the target record class
     */
    public String getTargetClassName() {
        return targetClassName;
    }

    /**
     * Gets the name of the field this key represents.
     *
     * @return The field name
     */
    public String getFieldName() {
        return fieldName;
    }
}