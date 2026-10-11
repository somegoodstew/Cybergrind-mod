package dev.cybergrind.mixin;

import dev.cybergrind.Arena;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Arena enemies never burn in daylight (zombies, skeletons and the rest of the undead). */
@Mixin(Mob.class)
abstract class MobMixin {
	@Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
	private void cybergrind$noSunBurn(final CallbackInfoReturnable<Boolean> cir) {
		if (Arena.isArenaMob((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
