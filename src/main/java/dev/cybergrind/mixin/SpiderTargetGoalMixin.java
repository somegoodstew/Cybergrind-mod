package dev.cybergrind.mixin;

import dev.cybergrind.Arena;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Arena spiders look for a target in daylight too (spiders only do it in the dark). */
@Mixin(targets = "net.minecraft.world.entity.monster.spider.Spider$SpiderTargetGoal")
abstract class SpiderTargetGoalMixin {
	@Redirect(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getLightLevelDependentMagicValue()F"))
	private float cybergrind$alwaysDark(final Mob spider) {
		return Arena.isArenaMob(spider) ? 0 : spider.getLightLevelDependentMagicValue();
	}
}
