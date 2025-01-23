package recordrecoder.impl;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import recordrecoder.impl.asm.RecordClassTransformer;
import recordrecoder.impl.utils.IDefaultedMixinConfigPlugin;

public class RecordRecoder implements IDefaultedMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        FabricLoader.getInstance().getEntrypointContainers("recordrecoder:register", Runnable.class).stream().map(EntrypointContainer::getEntrypoint).forEach(Runnable::run);
        IMixinTransformer transformer = (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        Extensions extensions = (Extensions) transformer.getExtensions();
        extensions.add(new RecordClassTransformer());
    }
}