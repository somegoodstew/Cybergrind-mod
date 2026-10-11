package dev.cybergrind.client;

import java.nio.file.Path;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * A song from the music folder, played as music (the Music volume slider, not positional). It isn't a registered sound event:
 * it resolves to itself, a streamed "file" sound whose id SoundBufferLibraryMixin reads from disk.
 */
final class FileSoundInstance extends AbstractSoundInstance {
	FileSoundInstance(final Path file, final boolean looping) {
		super(MusicLibrary.idFor(file), SoundSource.MUSIC, RandomSource.create());
		this.looping = looping;
		this.attenuation = Attenuation.NONE;
		this.relative = true;
		this.volume = 1;
		this.pitch = 1;
	}

	@Override
	public WeighedSoundEvents getOrResolve(final SoundManager manager) {
		if (soundEvent == null) {
			soundEvent = new WeighedSoundEvents(getIdentifier(), null);
			sound = new Sound(getIdentifier(), ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
		}
		return soundEvent;
	}
}
