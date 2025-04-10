package recordrecoder.impl;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.service.MixinService;
import recordrecoder.impl.asm.RecordClassTransformer;
import recordrecoder.impl.utils.Constants;
import recordrecoder.impl.utils.mixindefaults.IDefaultedMixinConfigPlugin;

public class RecordRecoder implements IDefaultedMixinConfigPlugin {
    public static final ILogger LOGGER = MixinService.getService().getLogger("recordrecoder-impl");

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("Preparing to recode your records!");
        // first, register all keys
        FabricLoader.getInstance()
                .getEntrypointContainers(
                        Constants.ENTRYPOINT_KEY,
                        Runnable.class
                )
                .stream()
                .map(EntrypointContainer::getEntrypoint)
                .forEach(Runnable::run);
        // then, register the transformer
        MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
        IMixinTransformer transformer = (IMixinTransformer) environment.getActiveTransformer();
        Extensions extensions = (Extensions) transformer.getExtensions();
        extensions.add(new RecordClassTransformer());

        LOGGER.info("Patented record recoding system (PRRS) initialized!");
    }
}