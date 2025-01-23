package recordrecoder.test;

import recordrecoder.api.record.ComponentKeyRegistry;
import recordrecoder.api.record.RecordComponentKey;

public class RecordRecoderTest implements Runnable {
    public static final RecordComponentKey<String> KEY = RecordComponentKey.create(
            "addedField",
            "net/minecraft/registry/tag/TagFile",
            String.class,
            "Hello, World!"
    );

    @Override
    public void run() {
        ComponentKeyRegistry.INSTANCE.register(KEY);
    }
}
