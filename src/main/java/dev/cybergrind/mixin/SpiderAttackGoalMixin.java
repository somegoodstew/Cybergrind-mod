package dev.cybergrind.mixin;

import dev.cybergrind.Arena;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Arena spiders keep attacking in daylight (spiders give up now and then when it's bright). */
@Mixin(targets = "net.minecraft.world.entity.monster.spider.Spider$SpiderAttackGoal")
abstract class SpiderAttackGoalMixin {
	@Redirect(method = "canContinueToUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;getLightLevelDependentMagicValue()F"))
	private float cybergrind$alwaysDark(final PathfinderMob spider) {
		return Arena.isArenaMob(spider) ? 0 : spider.getLightLevelDependentMagicValue();
	}
}
