package recordrecoder.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.damage.DamageRecord;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.FallLocation;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagFile;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static recordrecoder.impl.utils.asmhelpers.ClassNameHelper.*;

public class RecordRecoderTestInitializer implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("recordrecoder-test");

    @Override
    public void onInitialize() {
        expect(
                toBinaryName("java/lang/String").equals("java.lang.String"),
                "expected toBinaryName(\"java/lang/String\") to return \"java/lang/String\""
        );
        expect(
                toBinaryName("Ljava/lang/String;").equals("java.lang.String"),
                "expected toBinaryName(\"Ljava/lang/String;\") to return \"java/lang/String\""
        );
        expect(
                toBinaryName("java.lang.String").equals("java.lang.String"),
                "expected toBinaryName(\"java.lang.String\") to return \"java/lang/String\""
        );
        expect(
                toInternalName("java/lang/String").equals("java/lang/String"),
                "expected toInternalName(\"java/lang/String\") to return \"java/lang/String\""
        );
        expect(
                toInternalName("Ljava/lang/String;").equals("java/lang/String"),
                "expected toInternalName(\"Ljava/lang/String;\") to return \"java/lang/String\""
        );
        expect(
                toInternalName("java.lang.String").equals("java/lang/String"),
                "expected toInternalName(\"java.lang.String\") to return \"java/lang/String\""
        );
        expect(
                toDescriptor("java/lang/String").equals("Ljava/lang/String;"),
                "expected toDescriptor(\"java/lang/String\") to return \"Ljava/lang/String;\""
        );
        expect(
                toDescriptor("Ljava/lang/String;").equals("Ljava/lang/String;"),
                "expected toDescriptor(\"Ljava/lang/String;\") to return \"Ljava/lang/String;\""
        );
        expect(
                toDescriptor("java.lang.String").equals("Ljava/lang/String;"),
                "expected toDescriptor(\"java.lang.String\") to return \"Ljava/lang/String;\""
        );
        {
            DamageRecord record = new DamageRecord(null, 0, FallLocation.GENERIC, 0);
            expect(
                    Objects.equals(RecordRecoderTest.KEY.getOrNull(record), "Hello, World!"),
                    "expected RecordComponentKey#getOrNull on default value to return default value"
            );
        }
        {
            RecordRecoderTest.KEY.queueNext("Hullo, Wider World!");
            DamageRecord record = new DamageRecord(null, 0, FallLocation.GENERIC, 0);
            expect(
                    Objects.equals(RecordRecoderTest.KEY.getOrNull(record), "Hullo, Wider World!"),
                    "expected RecordComponentKey#getOrNull on queued value to return queued value"
            );
        }
        {
            DamageRecord record = new DamageRecord(null, 0, FallLocation.GENERIC, 0);
            expect(
                    Objects.equals(RecordRecoderTest.KEY.getOrNull(record), "Hello, World!"),
                    "expected RecordComponentKey#getOrNull to return default value again after constructor clears value"
            );
        }
        {
            Record o = TagKey.of(RegistryKeys.ITEM, Identifier.ofVanilla("test"));
            expect(
                    RecordRecoderTest.KEY.getOrNull(o) == null,
                    "expected RecordComponentKey#getOrNull to return null when called with an object of the wrong type"
            );
            expect(
                    RecordRecoderTest.KEY.getOrNull(null) == null,
                    "expected RecordComponentKey#getOrNull to return null when called with null"
            );
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
