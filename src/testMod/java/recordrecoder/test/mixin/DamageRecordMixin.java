package recordrecoder.test.mixin;

import net.minecraft.entity.damage.DamageRecord;
import net.minecraft.registry.tag.TagFile;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DamageRecord.class)
@Debug(export = true)
public class DamageRecordMixin {

}
