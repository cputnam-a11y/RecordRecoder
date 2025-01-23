package recordrecoder.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.tag.TagFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class RecordRecoderTestInitializer implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("recordrecoder-test");

    @Override
    public void onInitialize() {
        TagFile file = new TagFile(List.of(), false);
        System.out.println(RecordRecoderTest.KEY.getOrNull(file));
        assert Objects.equals(RecordRecoderTest.KEY.getOrNull(file), "Hello, World!");
    }
}
