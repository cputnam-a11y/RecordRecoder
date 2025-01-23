package recordrecoder.impl.record;

import com.google.common.collect.HashBiMap;
import org.jetbrains.annotations.ApiStatus;
import recordrecoder.api.record.ComponentKeyRegistry;
import recordrecoder.api.record.RecordComponentKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static recordrecoder.impl.utils.ClassNameHelper.toInternalName;

public class ComponentKeyRegistryImpl implements ComponentKeyRegistry {
    HashMap<String, List<RecordComponentKeyImpl<?>>> componentKeys = new HashMap<>();

    HashBiMap<RecordComponentKeyImpl<?>, String> componentKeyNames = HashBiMap.create();

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

    @Override
    public List<RecordComponentKeyImpl<?>> getForClass(Class<?> clazz) {
        return getForClass(toInternalName(clazz.getName()));
    }

    @Override
    public List<RecordComponentKeyImpl<?>> getForClass(String className) {
        return componentKeys.getOrDefault(toInternalName(className), List.of());
    }

    @ApiStatus.Internal
    public String getNameForKey(RecordComponentKeyImpl<?> key) {
        return componentKeyNames.get(key);
    }

    @ApiStatus.Internal
    public RecordComponentKeyImpl<?> getKeyForName(String name) {
        return componentKeyNames.inverse().get(name);
    }

    @ApiStatus.Internal
    public void registerNameForKey(RecordComponentKeyImpl<?> key, String name) {
        componentKeyNames.put(key, name);
    }
}
