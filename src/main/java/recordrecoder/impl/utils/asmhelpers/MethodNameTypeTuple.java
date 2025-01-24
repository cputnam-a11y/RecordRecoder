package recordrecoder.impl.utils.asmhelpers;

import org.objectweb.asm.Type;

import java.util.Objects;

public record MethodNameTypeTuple(String name, Type type) {
    public MethodNameTypeTuple {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(type, "Desc cannot be null");
    }

}
