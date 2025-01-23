package recordrecoder.api.record;

import recordrecoder.impl.record.ComponentKeyRegistryImpl;

import java.util.List;

public interface ComponentKeyRegistry {
    ComponentKeyRegistry INSTANCE = new ComponentKeyRegistryImpl();

    <Q extends RecordComponentKey<T>, T> Q register(Q key);

    List<? extends RecordComponentKey<?>> getForClass(Class<?> clazz);

    List<? extends RecordComponentKey<?>> getForClass(String className);
}
