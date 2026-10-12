package dev.cybergrind.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Providence: its elder guardian's own goals (swimming, the guardian laser) are taken out; Abilities flies it instead. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("goalSelector")
	GoalSelector cybergrind$goals();

	@Accessor("targetSelector")
	GoalSelector cybergrind$targets();
}
