package dev.cybergrind.client;

import dev.cybergrind.Arena;
import dev.cybergrind.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The run's music in place of Minecraft's while a run is going: an intro once (if there is one), then a looping track, as the
 * Cyber Grind plays them. The tracks aren't part of the mod (no copyrighted music in it): they come from the player's own
 * "Cybergrind Music" resource pack, assets/cybergrind/sounds/intro.ogg and music.ogg. Client thread.
 */
public final class RunMusic {
	private static final Identifier LOOP = Identifier.fromNamespaceAndPath("cybergrind", "music");
	private static final Identifier INTRO = Identifier.fromNamespaceAndPath("cybergrind", "intro");
	private static final Identifier LOOP_FILE = Identifier.fromNamespaceAndPath("cybergrind", "sounds/music.ogg");
	private static final Identifier INTRO_FILE = Identifier.fromNamespaceAndPath("cybergrind", "sounds/intro.ogg");

	private static SoundInstance intro;
	private static SoundInstance loop;
	private static boolean wasRunning;
	private static boolean hasLoop;
	private static boolean introLeft;

	private RunMusic() {
	}

	/** While true, Minecraft's own music manager stays quiet (MusicManagerMixin). */
	public static boolean playing() {
		return intro != null || loop != null;
	}

	static void tick(final Minecraft minecraft) {
		Arena arena = Arena.current();
		boolean running = minecraft.level != null && arena != null && arena.active();
		if (running && !wasRunning) {
			// looked up once a run: the tracks come from a resource pack, which can be switched between runs
			hasLoop = minecraft.getResourceManager().getResource(LOOP_FILE).isPresent();
			introLeft = minecraft.getResourceManager().getResource(INTRO_FILE).isPresent();
			if (!hasLoop && Settings.get().music && minecraft.player != null) {
				minecraft.player.sendSystemMessage(Component.literal("[Cybergrind] No run music found: put your track at assets/cybergrind/sounds/music.ogg in the \"Cybergrind Music\" resource pack and turn the pack on.")
					.withStyle(ChatFormatting.GRAY));
			}
		}
		wasRunning = running;

		if (!running || !hasLoop || !Settings.get().music) {
			stop(minecraft);
			return;
		}

		if (intro != null) {
			if (minecraft.getSoundManager().isActive(intro)) {
				return;
			}
			intro = null;
		}
		if (introLeft) {
			introLeft = false;
			minecraft.getMusicManager().stopPlaying();
			intro = music(INTRO, false);
			minecraft.getSoundManager().play(intro);
			return;
		}
		if (loop == null || !minecraft.getSoundManager().isActive(loop)) {
			minecraft.getMusicManager().stopPlaying();
			loop = music(LOOP, true);
			minecraft.getSoundManager().play(loop);
		}
	}

	private static void stop(final Minecraft minecraft) {
		if (intro != null) {
			minecraft.getSoundManager().stop(intro);
			intro = null;
		}
		if (loop != null) {
			minecraft.getSoundManager().stop(loop);
			loop = null;
		}
	}

	/** Not positional, on the Music volume slider. */
	private static SoundInstance music(final Identifier event, final boolean looping) {
		return new SimpleSoundInstance(event, SoundSource.MUSIC, 1, 1, RandomSource.create(), looping, 0,
			SoundInstance.Attenuation.NONE, 0, 0, 0, true);
	}
}
