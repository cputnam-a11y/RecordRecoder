package recordrecoder.test;

import net.fabricmc.loader.api.FabricLoader;
import recordrecoder.api.record.ComponentKeyRegistry;
import recordrecoder.api.record.RecordComponentKey;

public class RecordRecoderTest implements Runnable {
    public static final RecordComponentKey<String> KEY;

    @Override
    public void run() {
        ComponentKeyRegistry.INSTANCE.register(KEY);
    }

    static {
        var remapper = FabricLoader.getInstance().getMappingResolver();
        var mappedName = remapper.mapClassName("intermediary", "net.minecraft.class_1281").replace(".", "/");
        KEY = RecordComponentKey.create(
                "addedField",
                mappedName,
                String.class,
                "Hello, World!"
        );
    }
}
