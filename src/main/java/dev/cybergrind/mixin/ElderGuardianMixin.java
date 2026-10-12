package dev.cybergrind.mixin;

import dev.cybergrind.Arena;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.ElderGuardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Providence (an arena elder guardian) never curses players: no Mining Fatigue, no ghost face on the screen. */
@Mixin(ElderGuardian.class)
abstract class ElderGuardianMixin {
	@Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
	private void cybergrind$noCurse(final ServerLevel level, final CallbackInfo ci) {
		if (Arena.isArenaMob((ElderGuardian) (Object) this)) {
			ci.cancel();
		}
	}
}
