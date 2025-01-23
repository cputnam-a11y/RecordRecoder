package recordrecoder.impl.mixin;

import net.minecraft.registry.tag.TagFile;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TagFile.class)
@Debug(export = true)
public class TagFileMixin {}
