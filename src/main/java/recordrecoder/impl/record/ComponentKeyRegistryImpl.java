package recordrecoder.impl.record;

import com.google.common.collect.HashBiMap;
import org.jetbrains.annotations.ApiStatus;
import recordrecoder.api.record.ComponentKeyRegistry;
import recordrecoder.api.record.RecordComponentKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static recordrecoder.impl.utils.asmhelpers.ClassNameHelper.toInternalName;

public class ComponentKeyRegistryImpl implements ComponentKeyRegistry {
    final HashMap<String, List<RecordComponentKeyImpl<?>>> componentKeys = new HashMap<>();

    final HashBiMap<RecordComponentKeyImpl<?>, String> componentKeyNames = HashBiMap.create();

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
    @SuppressWarnings("unchecked")
    @Override
    public <Q extends RecordComponentKey<T>, T> Q register(Q key) {
        RecordComponentKeyImpl<T> keyImpl = (RecordComponentKeyImpl<T>) key;
        var prior = componentKeys.get(keyImpl.getTargetClassName());
        if (prior == null) {
            componentKeys.put(
                    toInternalName(keyImpl.getTargetClassName()),
                    new ArrayList<>(
                            List.of(keyImpl)
                    )
            );
        } else {
            prior.add(keyImpl);
            prior.sort(Comparator.comparing(RecordComponentKeyImpl::getFieldName));
        }
        return key;
    }

    /**
     * Retrieves record component keys for a specified class.
     *
     * @param clazz The Class object for which to retrieve record component keys
     * @return A list of record component keys for the specified class, or an empty list if none exist
     */
    @Override
    public List<RecordComponentKeyImpl<?>> getForClass(Class<?> clazz) {
        return getForClass(toInternalName(clazz.getName()));
    }

    /**
     * Retrieves record component keys for a class specified by its name.
     *
     * @param className The fully qualified name of the class for which to retrieve record component keys
     * @return A list of record component keys for the specified class, or an empty list if none exist
     */
    @Override
    public List<RecordComponentKeyImpl<?>> getForClass(String className) {
        return componentKeys.getOrDefault(toInternalName(className), List.of());
    }

    /**
    Warning: Do not be an absolute fool like me and remove this method!
     This is used in our asm code, removing this will make the jvm very angry, very fast!
    **/
    @ApiStatus.Internal
    @SuppressWarnings("unused")
    public String getNameForKey(RecordComponentKeyImpl<?> key) {
        return componentKeyNames.get(key);
    }

    /**
     Warning: Do not be an absolute fool like me and remove this method!
     This is used in our asm code, removing this will make the jvm very angry, very fast!
     **/
    @ApiStatus.Internal
    @SuppressWarnings("unused")
    public RecordComponentKeyImpl<?> getKeyForName(String name) {
        return componentKeyNames.inverse().get(name);
    }

    @ApiStatus.Internal
    public void registerNameForKey(RecordComponentKeyImpl<?> key, String name) {
        componentKeyNames.put(key, name);
    }
}
