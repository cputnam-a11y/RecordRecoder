package recordrecoder.impl.utils.mixindefaults;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

public interface IDefaultedExtension extends IExtension {
    @Override
    default boolean checkActive(MixinEnvironment environment) {
        return true;
    }

    @Override
    default void preApply(ITargetClassContext context) {

    }

    @Override
    default void postApply(ITargetClassContext context) {

    }

    @Override
    default void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {

    }
}
