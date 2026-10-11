package dev.cybergrind.client.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** RunMusic pauses its song on its own (not every sound), through the sound engine's channels. */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
	@Accessor("soundEngine")
	SoundEngine cybergrind$soundEngine();
}
