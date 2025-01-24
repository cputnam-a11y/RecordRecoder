package recordrecoder.api.record;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import recordrecoder.impl.record.RecordComponentKeyImpl;

import java.util.Optional;
import java.util.function.Supplier;

@ApiStatus.NonExtendable
public interface RecordComponentKey<T> {
    <I extends Record> T get(I instance) throws KeyMismatchException, IllegalStateException;

    default <I extends Record> @Nullable T getOrNull(I instance) {
        try {
            return this.get(instance);
        } catch (KeyMismatchException | NullPointerException e) {
            return null;
        }
    }

    default <I extends Record> Optional<T> getOptional(I instance) {
        try {
            return Optional.ofNullable(this.get(instance));
        } catch (KeyMismatchException | NullPointerException e) {
            return Optional.empty();
        }
    }

    void queueNext(T value);

    static <T> RecordComponentKey<T> create(String fieldName, String targetClassName, String componentClassName) {
        return create(fieldName, targetClassName, componentClassName, () -> null);
    }

    static <T> RecordComponentKey<T> create(String fieldName, String className, String componentClassName, T defaultValue) {
        return new RecordComponentKeyImpl<>(fieldName, className, componentClassName, () -> defaultValue);
    }

    static <T> RecordComponentKey<T> create(String fieldName, String className, String componentClassName, Supplier<T> defaultValueSupplier) {
        return new RecordComponentKeyImpl<>(fieldName, className, componentClassName, defaultValueSupplier::get);
    }

    static <T> RecordComponentKey<T> create(String fieldName, String targetClassName, Class<T> componentClass) {
        return new RecordComponentKeyImpl<>(fieldName, targetClassName, componentClass, () -> null);
    }

    static <T> RecordComponentKey<T> create(String fieldName, String targetClassName, Class<T> componentClass, T defaultValue) {
        return new RecordComponentKeyImpl<>(fieldName, targetClassName, componentClass, () -> defaultValue);
    }

    static <T> RecordComponentKey<T> create(String fieldName, String targetClassName, Class<T> componentClass, Supplier<T> defaultValueSupplier) {
        return new RecordComponentKeyImpl<>(fieldName, targetClassName, componentClass, defaultValueSupplier::get);
    }

    static <T, V extends Record> T get(RecordComponentKey<T> key, V instance) throws KeyMismatchException {
        return key.get(instance);
    }

    static <T, V extends Record> @Nullable T getOrNull(RecordComponentKey<T> key, V instance) {
        try {
            return key.get(instance);
        } catch (KeyMismatchException e) {
            return null;
        }
    }

    static <T, V extends Record> Optional<T> getOptional(RecordComponentKey<T> key, V instance) {
        try {
            return Optional.ofNullable(key.get(instance));
        } catch (KeyMismatchException e) {
            return Optional.empty();
        }
    }

    final class KeyMismatchException extends Exception {
        public KeyMismatchException(String fieldName, String recordClassName) {
            super("RecordComponentKey " + fieldName + " is not implemented on " + recordClassName);
        }
    }
}
