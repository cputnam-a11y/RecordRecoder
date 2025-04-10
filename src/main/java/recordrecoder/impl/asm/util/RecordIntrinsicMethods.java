package recordrecoder.impl.asm.util;

import org.objectweb.asm.tree.InvokeDynamicInsnNode;

public record RecordIntrinsicMethods(
        InvokeDynamicInsnNode string,
        InvokeDynamicInsnNode hash,
        InvokeDynamicInsnNode equals) {
}
