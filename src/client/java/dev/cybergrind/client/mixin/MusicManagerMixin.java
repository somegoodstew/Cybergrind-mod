package dev.cybergrind.client.mixin;

import dev.cybergrind.client.RunMusic;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's music doesn't start while the run's music plays. */
@Mixin(MusicManager.class)
abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void cybergrind$tick(final CallbackInfo ci) {
		if (RunMusic.playing()) {
			ci.cancel();
		}
	}
}
