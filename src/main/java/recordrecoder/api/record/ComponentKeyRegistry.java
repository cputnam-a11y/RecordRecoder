package recordrecoder.api.record;

import recordrecoder.impl.record.ComponentKeyRegistryImpl;

import java.util.List;
@SuppressWarnings("unused")
public interface ComponentKeyRegistry {
    ComponentKeyRegistry INSTANCE = new ComponentKeyRegistryImpl();

    /**
     * Registers a record component key with the system.
     *
     * <p>This method adds the provided key to an internal collection of keys organized by target class.
     * If the target class already has registered keys, the new key is added to the existing collection
     * and the keys are sorted alphabetically by field name. If this is the first key for the target class,
     * a new collection is created.</p>
     *
     * @param <Q> The type of record component key extending RecordComponentKey<T>
     * @param <T> The type associated with the record component key
     * @param key The record component key to register
     * @return The registered key (same instance that was passed in)
     */
    <Q extends RecordComponentKey<T>, T> Q register(Q key);

    /**
     * Retrieves record component keys for a specified class.
     *
     * @param clazz The Class object for which to retrieve record component keys
     * @return A list of record component keys for the specified class, or an empty list if none exist
     */
    List<? extends RecordComponentKey<?>> getForClass(Class<?> clazz);

    /**
     * Retrieves record component keys for a class specified by its name.
     *
     * @param className The fully qualified name of the class for which to retrieve record component keys
     * @return A list of record component keys for the specified class, or an empty list if none exist
     */
    List<? extends RecordComponentKey<?>> getForClass(String className);
}
